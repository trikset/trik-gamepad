package com.trikset.gamepad2

import com.trikset.gamepad2.mjpeg.SyntheticMjpegServer
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Host-side mock of a TRIK robot for on-device smoke tests and profiling: a log-only TCP command
 * port (the app's `SenderService` writes plain-text commands; an optional `--tcp-keepalive <ms>`
 * makes the robot emit `keepalive <ms>` to exercise the TCP read path), a log-only UDP command port
 * (one command per datagram), and a steady MJPEG stream. Pure Kotlin, lives in the test source set
 * so it reuses the committed CC0 cat fixtures and `SyntheticMjpegServer`, and never ships in a
 * release APK. Start it with `./gradlew runDummyRobotServer`; the phone only needs the host's LAN
 * IP (ports are the app defaults 4444 / 8080).
 *
 * Implements the DESIGN.md "Gamepad protocol (source of truth)" **contract**, not the firmware: the
 * TCP server enforces the robot-side keepalive disconnect (a client that announced `keepalive <ms>`
 * and then falls silent for `<ms>` is dropped — the original keepalive design, detecting an
 * unreachable gamepad) and accepts `custom <message>`. See DESIGN.md "Keepalive semantics" and the
 * "custom" matrix row.
 */
object DummyRobotServer {

  const val TCP_PORT = 4444
  const val MJPEG_PORT = 8080

  private const val KEEPALIVE_PREFIX = "keepalive "
  private const val CUSTOM_PREFIX = "custom "
  // Watchdog poll granularity: the disconnect fires within one poll of the announced interval.
  private const val KEEPALIVE_POLL_MS = 100L

  private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

  @JvmStatic
  fun main(args: Array<String>) {
    // Optional `--tcp-keepalive <ms>`: the robot emits `keepalive <ms>` on each TCP connection so
    // the app's TCP keepalive read path can be exercised (additive protocol rule — by default the
    // robot never replies, exactly like the real one).
    val tcpKeepaliveMs =
        args.indexOf("--tcp-keepalive").let { i ->
          if (i >= 0 && i + 1 < args.size) args[i + 1].toIntOrNull() else null
        }
    val mjpeg =
        SyntheticMjpegServer(
            framesPerConnection = Int.MAX_VALUE,
            frameIntervalMs = 20,
            port = MJPEG_PORT,
        )
    val tcp = DummyRobotTcpServer(TCP_PORT, tcpKeepaliveMs)
    val udp = DummyRobotUdpServer(TCP_PORT)
    val tcpNote = if (tcpKeepaliveMs != null) "; robot emits keepalive $tcpKeepaliveMs ms" else ""
    println("DummyRobotServer")
    println("  MJPEG:  http://<host-ip>:$MJPEG_PORT/?action=stream")
    println(
        "  TCP:    <host-ip>:$TCP_PORT (log-only; enforces keepalive disconnect + custom$tcpNote)"
    )
    println("  UDP:    <host-ip>:$TCP_PORT (log-only, one command per datagram)")
    println("  LAN addresses:")
    for (address in lanIpv4Addresses()) {
      println("    $address  (MJPEG http://$address:$MJPEG_PORT/?action=stream)")
    }
    mjpeg.start()
    tcp.start()
    udp.start()
    println("Serving. Ctrl+C to stop.")
    // Periodic stats line: stdout is block-buffered when redirected to a file,
    // so the explicit flush keeps the profiling session's counters live.
    val stats = Thread {
      while (true) {
        Thread.sleep(STATS_INTERVAL_MS)
        System.out.println(
            "${LocalTime.now().format(TIME_FORMAT)} STATS accepted=${mjpeg.acceptedConnections.get()} " +
                "frames=${mjpeg.servedFrames.get()} tcpClients=${tcp.clients}"
        )
        System.out.flush()
      }
    }
    stats.isDaemon = true
    stats.start()
    Thread.currentThread().join()
  }

  private const val STATS_INTERVAL_MS = 5000L

  /** Closes the given resource, tolerating an already-closed socket (idempotent). */
  private fun closeQuietly(close: () -> Unit) {
    try {
      close()
    } catch (_: IOException) {
      // already closed
    }
  }

  /**
   * All non-loopback IPv4 addresses of the host (the phone connects over Wi-Fi to one of these).
   */
  fun lanIpv4Addresses(): List<String> =
      NetworkInterface.getNetworkInterfaces()
          .asSequence()
          .filter { it.isUp && !it.isLoopback }
          .flatMap { it.inetAddresses.asSequence() }
          .filterIsInstance<Inet4Address>()
          .mapNotNull { it.hostAddress }
          .toList()

  /** Log-only TCP server: accepts any number of connections and prints every received line. */
  class DummyRobotTcpServer(private val port: Int, private val tcpKeepaliveMs: Int? = null) {
    private val serverSocket = ServerSocket(port)
    private var running = true
    val clients = AtomicInteger(0)

    fun start() {
      Thread {
            while (running) {
              try {
                val client = serverSocket.accept()
                clients.incrementAndGet()
                Thread { readLoop(client) }.start()
              } catch (_: IOException) {
                // server socket closed on stop()
              }
            }
          }
          .apply { isDaemon = true }
          .start()
    }

    fun stop() {
      running = false
      DummyRobotServer.closeQuietly { serverSocket.close() }
    }

    private fun readLoop(client: Socket) {
      try {
        client.use {
          val reader = BufferedReader(InputStreamReader(client.getInputStream()))
          val keepaliveThread = tcpKeepaliveMs?.let { startKeepaliveEmitter(client, it) }
          // Robot-side keepalive watchdog state (see watchKeepalive / announceKeepaliveIfPresent).
          val lastMessageMs = AtomicLong(System.currentTimeMillis())
          val announcedKeepaliveMs = AtomicInteger(0)
          val watchdogThread = Thread {
            watchKeepalive(client, lastMessageMs, announcedKeepaliveMs)
          }
          watchdogThread.isDaemon = true
          watchdogThread.start()
          while (true) {
            val line = reader.readLine() ?: break
            // Any incoming message proves the client is alive (re-charges the watchdog clock).
            lastMessageMs.set(System.currentTimeMillis())
            announceKeepaliveIfPresent(line, announcedKeepaliveMs)
            val marker = if (line.startsWith(CUSTOM_PREFIX)) "TCP< custom" else "TCP<"
            System.out.println(
                "${LocalTime.now().format(TIME_FORMAT)} $marker ${client.inetAddress.hostAddress}: $line"
            )
            System.out.flush()
          }
          watchdogThread.interrupt()
          keepaliveThread?.interrupt()
        }
      } catch (_: IOException) {
        // client disconnected or dropped by the keepalive watchdog
      }
    }

    /**
     * Parses an announced `keepalive <ms>` into the watchdog state: `> 0` arms the client watchdog
     * with `<ms>` (the client commits to sending some message at least every `<ms>`); `<= 0`
     * disarms it. Malformed values are ignored (tolerant parsing, like the firmware).
     */
    private fun announceKeepaliveIfPresent(line: String, announcedKeepaliveMs: AtomicInteger) {
      if (line.startsWith(KEEPALIVE_PREFIX)) {
        line.substring(KEEPALIVE_PREFIX.length).trim().toIntOrNull()?.let {
          announcedKeepaliveMs.set(it)
        }
      }
    }

    /**
     * Drops the client after it falls silent for the announced keepalive interval (the robot-side
     * disconnect for an unreachable gamepad — the original keepalive design). A friendly ERROR line
     * is the conformance signal: a gamepad client that announces `keepalive <ms>` and stays silent
     * is dropped, exactly like the real robot's single-shot timer.
     */
    private fun watchKeepalive(
        client: Socket,
        lastMessageMs: AtomicLong,
        announcedKeepaliveMs: AtomicInteger,
    ) {
      try {
        while (!client.isClosed && !Thread.currentThread().isInterrupted) {
          val ms = announcedKeepaliveMs.get()
          if (ms > 0 && System.currentTimeMillis() - lastMessageMs.get() > ms) {
            System.out.println(
                "${LocalTime.now().format(TIME_FORMAT)} TCP! ${client.inetAddress.hostAddress}: " +
                    "ERROR keepalive timeout — client announced keepalive $ms but sent nothing for " +
                    "$ms ms; disconnecting (gamepad may be unreachable)"
            )
            System.out.flush()
            DummyRobotServer.closeQuietly { client.close() }
            return
          }
          try {
            Thread.sleep(KEEPALIVE_POLL_MS)
          } catch (_: InterruptedException) {
            return
          }
        }
      } catch (_: IOException) {
        // socket already closed
      }
    }

    /** Emits `keepalive <ms>` on the client socket every `<ms>` (the robot-keepalive read path). */
    private fun startKeepaliveEmitter(client: Socket, ms: Int): Thread {
      val writer =
          PrintWriter(OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true)
      return Thread {
            while (running && !client.isClosed && !Thread.currentThread().isInterrupted) {
              val line = "keepalive $ms"
              writer.println(line)
              writer.flush()
              System.out.println(
                  "${LocalTime.now().format(TIME_FORMAT)} TCP> ${client.inetAddress.hostAddress}: $line"
              )
              System.out.flush()
              try {
                Thread.sleep(ms.toLong())
              } catch (_: InterruptedException) {
                return@Thread
              }
            }
          }
          .apply { isDaemon = true }
          .also { it.start() }
    }
  }

  /** Log-only UDP server: prints every received datagram (one command per datagram over UDP). */
  class DummyRobotUdpServer(private val port: Int) {
    private val serverSocket = DatagramSocket(port)
    @Volatile private var running = true

    fun start() {
      Thread {
            val buffer = ByteArray(4096)
            while (running) {
              try {
                val packet = DatagramPacket(buffer, buffer.size)
                serverSocket.receive(packet)
                val line =
                    String(packet.data, packet.offset, packet.length, StandardCharsets.UTF_8).trim()
                System.out.println(
                    "${LocalTime.now().format(TIME_FORMAT)} UDP< " +
                        "${packet.address.hostAddress}: $line"
                )
                System.out.flush()
              } catch (_: IOException) {
                // server socket closed on stop()
              }
            }
          }
          .apply { isDaemon = true }
          .start()
    }

    fun stop() {
      running = false
      DummyRobotServer.closeQuietly { serverSocket.close() }
    }
  }
}
