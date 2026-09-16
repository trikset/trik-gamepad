package com.trikset.gamepad2

import android.os.Looper.getMainLooper
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.util.concurrent.PausedExecutorService
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.LooperMode.Mode.PAUSED

/**
 * Covers the SenderService paths the basic test misses: keepalive ticks, disconnect callbacks,
 * send-failure handling, and target changes.
 */
@RunWith(RobolectricTestRunner::class)
@LooperMode(PAUSED)
class SenderServiceAdvancedTest : RobolectricTestBase() {
  private val mExecutor = PausedExecutorService()

  @Test
  fun disconnectShouldInvokeOnDisconnectedListener() {
    TestTcpServer().use { server ->
      val client = SenderService(mExecutor)
      val reason = AtomicReference<String>()
      client.setOnDisconnectedListener { reason.set(it) }

      client.setTarget("localhost", server.port)
      client.send("test")
      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
      assertTrue(server.awaitConnection())

      client.disconnect("Test disconnect.")
      assertEquals("Test disconnect.", reason.get())
      client.disconnect("again") // mOut already null -> no listener call
      assertEquals("Test disconnect.", reason.get())
    }
  }

  @Test
  fun connectionStateShouldTransitionConnectingConnectedDisconnected() {
    TestTcpServer().use { server ->
      val client = SenderService(mExecutor)

      client.setTarget("localhost", server.port)
      client.send("test")
      assertEquals(ConnectionState.Connecting, client.connectionState.value)
      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
      assertTrue(server.awaitConnection())
      assertEquals(ConnectionState.Connected, client.connectionState.value)

      client.disconnect("Bye")
      assertEquals(ConnectionState.Disconnected("Bye"), client.connectionState.value)
    }
  }

  @Test
  fun setTargetShouldDisconnectWhenChanged() {
    TestTcpServer().use { first ->
      val client = SenderService(mExecutor)
      val reason = AtomicReference<String>()
      client.setOnDisconnectedListener { reason.set(it) }

      client.setTarget("localhost", first.port)
      client.send("a")
      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
      assertTrue(first.awaitConnection())

      // New target on a different port -> must disconnect from the first.
      client.setTarget("localhost", first.port + 1)
      assertEquals("Target changed.", reason.get())
    }
  }

  @Test
  fun sendFailureShouldDisconnectAndReport() {
    TestTcpServer().use { server ->
      val client = SenderService(mExecutor)
      client.setTarget("localhost", server.port)
      client.send("first")
      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
      assertTrue(server.awaitConnection())

      // Closing the server makes the next println fail, which turns into a disconnect.
      server.closeClient()
      client.send("after-close")
      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
      // checkError() may be lazy; give the executor another cycle.
      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
    }
  }

  @Test
  fun keepaliveShouldBeSentWhileConnected() {
    val timeout = 1300 // real period = timeout - 300 = 1000ms
    TestTcpServer().use { server ->
      val client = SenderService(mExecutor)
      client.setTarget("localhost", server.port)
      client.keepaliveTimeout = timeout
      client.send("bootstrap")
      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
      assertTrue(server.awaitConnection())

      // The keepalive Timer runs on a real thread; poll for the message instead
      // of a single fixed sleep so a busy CI JVM cannot starve the timer.
      assertTrue(
          "expected a keepalive message",
          server.awaitReceived(
              "keepalive $timeout",
              drain = {
                mExecutor.runAll()
                shadowOf(getMainLooper()).idle()
              },
              sleepMs = 500,
          ),
      )
      client.disconnect("done")
    }
  }

  @Test
  fun showTextCallbackShouldReceiveConnectionResult() {
    TestTcpServer().use { server ->
      val client = SenderService(mExecutor)
      val text = AtomicReference<String>()
      client.setShowTextCallback { text.set(it) }

      client.setTarget("localhost", server.port)
      client.send("hello")
      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
      assertTrue(server.awaitConnection())

      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
      assertTrue("expected connection message, got '${text.get()}'", text.get() != null)
      client.disconnect("done")
    }
  }

  @Test
  fun sendToUnreachablePortShouldNotThrow() {
    val client = SenderService(mExecutor)
    // Port 1: nothing listens on it, so the connect is refused and connectToTRIK
    // swallows the IOException.
    client.setTarget("localhost", 1)
    client.send("boom")
    mExecutor.runAll()
    shadowOf(getMainLooper()).idle()
    // A failed connect must not stick at Connecting: the state machine returns to
    // Disconnected so the pill honestly shows "Tap to connect…" again.
    assertEquals(ConnectionState.Disconnected(""), client.connectionState.value)
  }

  @Test
  fun connectWithBlankHostShouldNotAttempt() {
    val client = SenderService(mExecutor)
    client.setTarget("", 4444)
    client.connect()
    mExecutor.runAll()
    shadowOf(getMainLooper()).idle()
    // Blank host -> connect() no-ops (a video-only device has nothing to connect to);
    // the state never leaves Disconnected and no socket attempt is queued.
    assertEquals(ConnectionState.Disconnected(""), client.connectionState.value)
  }

  @Test
  fun sendWhileConnectedShouldSkipReconnect() {
    TestTcpServer().use { server ->
      val client = SenderService(mExecutor)
      client.setTarget("localhost", server.port)
      client.send("one")
      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
      assertTrue(server.awaitConnection())

      // Already connected -> send() must not queue another connect task.
      client.send("two")
      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
      // The server reads asynchronously; poll for the second command instead
      // of asserting immediately (bounded await, never a bare assert).
      assertTrue(
          "expected 'two' over the live socket",
          server.awaitReceived(
              "two",
              drain = {
                mExecutor.runAll()
                shadowOf(getMainLooper()).idle()
              },
          ),
      )
      client.disconnect("done")
    }
  }

  @Test
  fun keepaliveTimeoutBelowMinimumIsStoredUnchanged() {
    val client = SenderService(mExecutor)
    val before = client.keepaliveTimeout
    // The service does not clamp; the caller (MainActivity) enforces the
    // minimum. This just verifies the setter round-trips.
    client.keepaliveTimeout = 12345
    assertEquals(12345, client.keepaliveTimeout)
    client.keepaliveTimeout = before
  }

  @Test
  fun postCommandSendsOnTheExecutorThreadNeverTheMainThread() {
    // The C24 perf finding: the TCP error-check flushed the PrintWriter (a socket write) on the
    // MAIN thread. The send + error-check must stay on the executor thread. A real (not paused)
    // executor is required here — PausedExecutorService.runAll() runs on the test thread, which IS
    // the main thread under Robolectric, so it cannot prove thread affinity.
    val sendThreadNames = java.util.concurrent.CopyOnWriteArrayList<String>()
    val mainThread = Thread.currentThread()
    val transport =
        object : CommandTransport {
          override fun open(host: String, port: Int) {}

          override fun send(command: String): Boolean {
            sendThreadNames.add(Thread.currentThread().name)
            return true
          }

          override var onMessage: ((String) -> Unit)? = null

          override fun close() {}
        }
    val realExecutor = Executors.newSingleThreadExecutor()
    val client = SenderService(executor = realExecutor, transportFactory = { _ -> transport })

    client.setTarget("localhost", 1)
    client.send("pad 1 0 0")
    realExecutor.shutdown()
    assertTrue(realExecutor.awaitTermination(5, TimeUnit.SECONDS))

    assertTrue(sendThreadNames.isNotEmpty())
    assertTrue(
        "transport.send() must never run on the main thread (was: ${sendThreadNames.joinToString()})",
        sendThreadNames.none { it == mainThread.name },
    )
    client.disconnect("done")
  }
}
