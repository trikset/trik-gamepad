package com.trikset.gamepad2

import com.trikset.gamepad2.diagnostics.AppLog
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

/**
 * [CommandTransport] over one persistent TCP connection (the current robot protocol: one socket,
 * newline-terminated plain-text commands, default `192.168.77.1:4444`). The socket setup and the
 * write/error-check are the pieces moved out of [SenderService] so the transport contract is shared
 * with the UDP transport. The socket is bound by [SocketBinder] (Wi-Fi preference, A2) before it
 * connects.
 *
 * TCP is **optionally bidirectional** (Campaign 28): the input half is no longer shut down and a
 * receive thread reports every inbound line through [onMessage] — a robot that sends `keepalive
 * <ms>` / any control message enables the same liveness rule as UDP. A robot that never replies
 * keeps working exactly as before (nothing is received, liveness stays disabled at `-1`) — the
 * additive protocol rule (DESIGN.md "Gamepad protocol").
 */
class TcpTransport(
    private val socketBinder: SocketBinder = SocketBinder.identity,
) : CommandTransport {

  private var writer: PrintWriter? = null
  private var socket: Socket? = null
  private var receiveThread: Thread? = null
  @Volatile private var running = false

  override var onMessage: ((String) -> Unit)? = null

  @Throws(IOException::class)
  override fun open(host: String, port: Int) {
    val socket = socketBinder.bind(Socket())
    socket.connect(InetSocketAddress(host, port), TIMEOUT)
    socket.tcpNoDelay = true
    socket.keepAlive = true
    socket.setSoLinger(true, 0)
    socket.trafficClass = TRAFFIC_CLASS // high priority, no-delay
    socket.oobInline = true
    this.socket = socket
    writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)
    running = true
    receiveThread =
        thread(name = "TcpReceive") {
          receiveLoop(socket)
        }
  }

  override fun send(command: String): Boolean {
    val writer = writer ?: return false
    writer.println(command)
    return !writer.checkError()
  }

  override fun close() {
    running = false
    try {
      socket?.close()
    } catch (_: IOException) {
      // already closed
    }
    receiveThread?.join()
    receiveThread = null
    socket = null
    writer = null
  }

  /** Reads inbound lines until [close]; every received line is reported via [onMessage]. */
  private fun receiveLoop(socket: Socket) {
    try {
      val reader =
          BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
      while (running) {
        val line = reader.readLine() ?: break
        AppLog.d(TAG, "Received from robot: $line")
        onMessage?.invoke(line)
      }
    } catch (_: IOException) {
      // socket closed -> loop ends (also when the connection dies mid-read)
    }
  }

  private companion object {
    const val TAG = "TcpTransport"
    const val TIMEOUT = 5000
    const val TRAFFIC_CLASS = 0x0F
  }
}
