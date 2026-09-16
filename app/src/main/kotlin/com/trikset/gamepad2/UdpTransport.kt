package com.trikset.gamepad2

import com.trikset.gamepad2.diagnostics.AppLog
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

/**
 * [CommandTransport] over UDP: one newline-terminated plain-text command per datagram (the TCP
 * protocol text reused verbatim, so a robot that parses the TCP stream parses UDP the same way).
 * The socket is bound by [DatagramBinder] (Wi-Fi preference, S13 / A2) before it sends.
 *
 * UDP is connectionless, so [open] only prepares the socket and the app is optimistically
 * "Connected" once the first datagram is sent (see DESIGN.md "Gamepad protocol"). A send
 * [IOException] (e.g. the network is gone) drives the "Send failed." disconnect — the same path as
 * TCP. The robot may additionally respond with `keepalive <ms>` / any message; every received line
 * is reported through [onMessage] so the caller can run a liveness timeout (the "any received
 * message resets the liveness timer" rule).
 */
class UdpTransport(
    private val datagramBinder: DatagramBinder = DatagramBinder.identity,
) : CommandTransport {

  private var socket: DatagramSocket? = null
  private var receiveThread: Thread? = null
  @Volatile private var running = false
  override var onMessage: ((String) -> Unit)? = null

  @Throws(IOException::class)
  override fun open(host: String, port: Int) {
    val socket = datagramBinder.bind(DatagramSocket())
    // The socket is effectively write-only from the app's view (the robot only "replies" with
    // optional keepalives; a bound socket is still required for receive). Bind the remote endpoint
    // so stray datagrams from other sources are filtered out.
    socket.connect(InetSocketAddress(host, port))
    socket.soTimeout = RECEIVE_POLL_MS
    this.socket = socket
    running = true
    receiveThread =
        thread(name = "UdpReceive") {
          receiveLoop(socket)
        }
  }

  override fun send(command: String): Boolean {
    val socket = socket ?: return false
    return try {
      val data = (command + "\n").toByteArray(StandardCharsets.UTF_8)
      socket.send(DatagramPacket(data, data.size))
      true
    } catch (e: IOException) {
      AppLog.e(TAG, "UDP send failed", e)
      false
    }
  }

  override fun close() {
    running = false
    try {
      socket?.close()
    } catch (_: IOException) {
      // already closed
    }
    receiveThread?.join()
    socket = null
    receiveThread = null
  }

  /** Reads inbound datagrams until [close]; every received line is reported via [onMessage]. */
  private fun receiveLoop(socket: DatagramSocket) {
    val buffer = ByteArray(RECEIVE_BUFFER_BYTES)
    while (running) {
      try {
        val packet = DatagramPacket(buffer, buffer.size)
        socket.receive(packet)
        val line = String(packet.data, packet.offset, packet.length, StandardCharsets.UTF_8).trim()
        AppLog.d(TAG, "Received from robot: $line")
        onMessage?.invoke(line)
      } catch (_: SocketTimeoutException) {
        // Poll interval elapsed with no datagram: just loop (lets `running` re-check).
      } catch (e: SocketException) {
        if (running) {
          AppLog.e(TAG, "UDP receive failed", e)
        }
      } catch (e: IOException) {
        if (running) {
          AppLog.e(TAG, "UDP receive failed", e)
        }
      }
    }
  }

  private companion object {
    const val TAG = "UdpTransport"
    // Bound a datagram's max size; robot control messages are short, this just bounds the buffer.
    const val RECEIVE_BUFFER_BYTES = 4096
    // How often the receive loop re-checks `running` when no datagram arrives (ms).
    const val RECEIVE_POLL_MS = 200
  }
}
