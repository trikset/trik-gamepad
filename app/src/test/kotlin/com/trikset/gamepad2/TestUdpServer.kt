package com.trikset.gamepad2

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Bidirectional UDP test server for the unit suite. Binds an **ephemeral** port (each of the 3
 * parallel unit-test JVMs gets its own — never a fixed port, TESTING.md), records every received
 * datagram line, and can send robot→app lines back (e.g. an optional `keepalive <ms>`). The UDP
 * twin of [TestTcpServer]; the robot keeps the same newline-terminated text protocol, one command
 * per datagram.
 */
class TestUdpServer : AutoCloseable {

  companion object {
    const val HOST = "localhost"
  }

  private val socket = DatagramSocket(0)
  private val messages = CopyOnWriteArrayList<String>()
  private val firstMessageLatch = CountDownLatch(1)
  private val lastSender = AtomicReference<InetSocketAddress>()

  val port: Int
    get() = socket.localPort

  init {
    Thread {
          val buffer = ByteArray(4096)
          while (!socket.isClosed) {
            try {
              val packet = DatagramPacket(buffer, buffer.size)
              socket.receive(packet)
              lastSender.set(packet.socketAddress as InetSocketAddress)
              messages.add(
                  String(packet.data, packet.offset, packet.length, StandardCharsets.UTF_8).trim()
              )
              firstMessageLatch.countDown()
            } catch (_: IOException) {
              // socket closed -> loop ends
            }
          }
        }
        .start()
  }

  /** Bounded poll for at least one message (the server reads on its own thread). */
  fun awaitFirstMessage(timeoutMs: Long = 5_000): Boolean =
      firstMessageLatch.await(timeoutMs, TimeUnit.MILLISECONDS)

  fun receivedContains(fragment: String): Boolean = messages.any { it.contains(fragment) }

  fun lastMessage(): String? = messages.lastOrNull()

  fun messageCount(): Int = messages.size

  /** Bounded poll for the message count to reach at least [count] (the resend asserts). */
  fun awaitCount(count: Int, timeoutMs: Long = 5_000): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      if (messages.size >= count) return true
      Thread.sleep(20)
    }
    return messages.size >= count
  }

  /** Bounded poll for [fragment] to appear among the received datagrams. */
  fun awaitReceived(fragment: String, timeoutMs: Long = 5_000): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      if (receivedContains(fragment)) return true
      Thread.sleep(20)
    }
    return receivedContains(fragment)
  }

  /** Sends a robot→app line to the last datagram sender (the app's UDP socket). */
  fun sendToLastSender(line: String) {
    val target = lastSender.get() ?: return
    val bytes = (line + "\n").toByteArray(StandardCharsets.UTF_8)
    socket.send(DatagramPacket(bytes, bytes.size, target))
  }

  override fun close() {
    socket.close()
  }
}
