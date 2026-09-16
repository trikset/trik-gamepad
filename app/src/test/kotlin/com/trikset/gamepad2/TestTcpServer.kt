package com.trikset.gamepad2

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * One-shot test TCP server for the unit suite. Binds an **ephemeral** port (each of the 3 parallel
 * unit-test JVMs gets its own — never a fixed port, TESTING.md), accepts a single client, and
 * records every received line until the socket is closed. Merges the old nested `DummyServer`
 * (SenderServiceTest) and `ReadUntilStopServer` (SenderServiceAdvancedTest).
 *
 * Asserts on *server-received content* must use the bounded [awaitReceived] poll (draining the
 * test's executor/looper), never a bare assert right after `runAll()` — TESTING.md "Why awaits are
 * required". The instrumented `DummyServer` (androidTest) stays separate: it binds the fixed
 * `localhost:12345` by design.
 */
class TestTcpServer : AutoCloseable {

  companion object {
    const val HOST = "localhost"
  }

  private val lock = Any()
  private val messages = ArrayList<String>()
  private val connectedLatch = CountDownLatch(1)
  private val serverSocket = ServerSocket(0)
  @Volatile private var clientSocket: Socket? = null

  val port: Int
    get() = serverSocket.localPort

  val isConnected: Boolean
    get() = clientSocket?.isConnected == true

  fun awaitConnection(timeoutMs: Long = 5_000): Boolean =
      connectedLatch.await(timeoutMs, TimeUnit.MILLISECONDS)

  fun receivedContains(fragment: String): Boolean =
      synchronized(lock) { messages.any { it.contains(fragment) } }

  fun lastMessage(): String? = synchronized(lock) { messages.lastOrNull() }

  /** Bounded poll for at least [count] messages (the old `DummyServer.awaitCommands`). */
  fun awaitCount(count: Int, timeoutMs: Long = 5_000): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      if (synchronized(lock) { messages.size } >= count) return true
      Thread.sleep(20)
    }
    return synchronized(lock) { messages.size >= count }
  }

  /**
   * Bounded poll for [fragment] to appear on the socket. The caller supplies a [drain] that
   * advances the executor/looper (e.g. `{ mExecutor.runAll(); shadowOf(getMainLooper()).idle() }`);
   * the server only owns the read side, so it cannot know the test's scheduling.
   */
  fun awaitReceived(
      fragment: String,
      drain: () -> Unit,
      sleepMs: Long = 50,
      attempts: Int = 20,
  ): Boolean {
    var seen = false
    var count = 0
    while (!seen && count < attempts) {
      count++
      Thread.sleep(sleepMs)
      drain()
      seen = receivedContains(fragment)
    }
    return seen
  }

  /** Closes the accepted client socket, ending the read loop (the server keeps listening). */
  fun closeClient() {
    clientSocket?.let { c ->
      try {
        c.close()
      } catch (ignored: IOException) {
        // already closed
      }
    }
  }

  /**
   * Sends one newline-terminated robot message to the connected client (the robot→app keepalive
   * read-path tests). No-op when no client is connected.
   */
  fun sendRobotMessage(line: String) {
    val socket = clientSocket ?: return
    try {
      PrintWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true).use {
        it.println(line)
      }
    } catch (_: IOException) {
      // client gone
    }
  }

  override fun close() {
    closeClient()
    try {
      serverSocket.close()
    } catch (ignored: IOException) {
      // already closed
    }
  }

  init {
    Thread {
          try {
            serverSocket.use { s ->
              val client = s.accept()
              clientSocket = client
              connectedLatch.countDown()
              client.use {
                val input = BufferedReader(InputStreamReader(client.getInputStream()))
                while (true) {
                  val line = input.readLine() ?: break
                  synchronized(lock) {
                    messages.add(line)
                  }
                }
              }
            }
          } catch (_: IOException) {
            // socket closed -> loop ends
          }
        }
        .start()
  }
}
