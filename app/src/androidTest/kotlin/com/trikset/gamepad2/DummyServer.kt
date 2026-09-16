package com.trikset.gamepad2

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.ServerSocket

/**
 * Instrumented-test TCP echo server. Binds the fixed `localhost:12345` and records every received
 * line; `awaitMessage` blocks until a message arrives or the timeout elapses. Kept separate from
 * the unit-test DummyServer (which uses ephemeral ports) — see TESTING.md.
 */
class DummyServer {

  companion object {
    const val IP = "localhost"
    const val DEFAULT_PORT = 12345
  }

  // java.lang.Object is needed for its monitor methods (wait/notifyAll) used as a
  // message-arrival condition; kotlin.Any exposes no wait/notify.
  @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") private val lock = Object()
  val receivedMessages = ArrayList<String>()
  @Volatile private var canStopListening = false

  fun stopListening() {
    canStopListening = true
  }

  /** Waits until the given message has been received, or the timeout elapses. */
  @Throws(InterruptedException::class)
  fun awaitMessage(expected: String, timeoutMillis: Long): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMillis
    synchronized(lock) {
      while (!receivedMessages.contains(expected)) {
        val remaining = deadline - System.currentTimeMillis()
        if (remaining <= 0) {
          return false
        }
        lock.wait(remaining)
      }
      return true
    }
  }

  /**
   * Negative-await: polls for the [quietMillis] window and reports whether any message arrived
   * within it. Callers assert `false` to prove nothing was sent (e.g. no keepalive after
   * disconnect), so a genuinely-idle window is the success case.
   */
  @Throws(InterruptedException::class)
  fun anyMessageWithin(quietMillis: Long): Boolean {
    val baseline = receivedMessages.size
    val deadline = System.currentTimeMillis() + quietMillis
    synchronized(lock) {
      while (System.currentTimeMillis() < deadline) {
        if (receivedMessages.size > baseline) {
          return true
        }
        val remaining = deadline - System.currentTimeMillis()
        if (remaining > 0) {
          lock.wait(remaining)
        }
      }
      return receivedMessages.size > baseline
    }
  }

  init {
    try {
      // Bind synchronously in the constructor so the port is guaranteed
      // listening before the client connects (same lesson as the unit
      // DummyServer: a background bind raced the connect with ECONNREFUSED).
      val server = ServerSocket(DEFAULT_PORT)
      Thread {
            server.use { s ->
              try {
                val client = s.accept()
                val clientInput = BufferedReader(InputStreamReader(client.inputStream))
                while (true) {
                  val message = clientInput.readLine() ?: break
                  if (canStopListening) {
                    break
                  }
                  synchronized(lock) {
                    receivedMessages.add(message)
                    lock.notifyAll()
                  }
                }
              } catch (e: IOException) {
                // Connection reset on client disconnect: loop ends. Must
                // be caught or the instrumentation fails the test with
                // the server thread's uncaught exception.
              }
            }
          }
          .start()
    } catch (e: IOException) {
      throw IllegalStateException("Failed to bind DummyServer", e)
    }
  }
}
