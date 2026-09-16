package com.trikset.gamepad2

import android.os.Looper.getMainLooper
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.util.concurrent.PausedExecutorService
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.LooperMode.Mode.PAUSED

@RunWith(RobolectricTestRunner::class)
@LooperMode(PAUSED)
class SenderServiceTest : RobolectricTestBase() {
  private val mExecutor = PausedExecutorService()
  private var client: SenderService? = null

  // A connected client keeps a real keepalive scheduler thread alive. Disconnect
  // every client in @After so no keepalive task can land on a later test.
  @After
  fun tearDown() {
    client?.disconnect("tearDown")
  }

  @Test
  fun senderServiceShouldConnectToServerSuccessfullyAfterSendingCommand() {
    TestTcpServer().use { server ->
      client = SenderService(mExecutor)
      client!!.setTarget(TestTcpServer.HOST, server.port)
      client!!.send("")
      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
      assertTrue(server.awaitConnection())
      assertTrue(server.isConnected)
    }
  }

  @Test
  fun senderServiceShouldSendSingleCommandCorrectly() {
    TestTcpServer().use { server ->
      client = SenderService(mExecutor)
      client!!.setTarget(TestTcpServer.HOST, server.port)
      client!!.keepaliveTimeout = 10000000 // to disable keep-alive messages
      client!!.send("Test; check")
      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
      assertTrue(server.awaitCount(1))
      assertEquals("Test; check", server.lastMessage())
    }
  }

  @Test
  fun senderServiceShouldSendMultipleCommandsCorrectly() {
    TestTcpServer().use { server ->
      client = SenderService(mExecutor)
      client!!.setTarget(TestTcpServer.HOST, server.port)
      client!!.keepaliveTimeout = 10000000 // to disable keep-alive messages

      for (i in 0 until 5) {
        client!!.send(String.format(Locale.ROOT, "%d checking", i))
      }
      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
      assertTrue(server.awaitCount(5))
      assertEquals("4 checking", server.lastMessage())
    }
  }

  @Test
  fun setTargetShouldSetServerSuccessfully() {
    client = SenderService(mExecutor)
    client!!.setTarget("someaddr-test", 0)
    assertEquals("someaddr-test", client!!.hostAddr)
  }

  @Test
  fun connectShouldEstablishConnectionWithoutSending() {
    TestTcpServer().use { server -> establishConnection(server) }
  }

  @Test
  fun connectWhenAlreadyConnectedShouldNotReconnect() {
    TestTcpServer().use { server ->
      establishConnection(server)
      // A second connect while connected (mOut != null) must not drop/re-open.
      client!!.connect()
      mExecutor.runAll()
      assertTrue(server.isConnected)
    }
  }

  /** Connects [client] to [server] via the public connect() entry point and asserts the state. */
  private fun establishConnection(server: TestTcpServer): SenderService {
    client = SenderService(mExecutor)
    client!!.setTarget(TestTcpServer.HOST, server.port)
    client!!.connect()
    mExecutor.runAll()
    shadowOf(getMainLooper()).idle()
    assertTrue(server.awaitConnection())
    assertTrue(server.isConnected)
    return client!!
  }

  @Test
  fun senderServiceShouldReturnCorrectKeepaliveTimeout() {
    client = SenderService(mExecutor)
    client!!.keepaliveTimeout = 3453
    assertEquals(3453, client!!.keepaliveTimeout)
    client!!.keepaliveTimeout = 1234
    assertEquals(1234, client!!.keepaliveTimeout)
  }

  @Test
  fun connectWithBlankOrNullHostShouldNotConnect() {
    client = SenderService(mExecutor)
    // Fresh sender: host is null -> the isNullOrBlank guard blocks the connect.
    client!!.connect()
    mExecutor.runAll()
    assertTrue(client!!.connectionState.value is ConnectionState.Disconnected)
    // Blank host (video-only) is equally a no-op.
    client!!.setTarget("", 4444)
    client!!.connect()
    mExecutor.runAll()
    assertTrue(client!!.connectionState.value is ConnectionState.Disconnected)
  }

  @Test
  fun sendAndDisconnectShouldCompleteTheSendPath() {
    TestTcpServer().use { server ->
      client = SenderService(mExecutor)
      client!!.setTarget(TestTcpServer.HOST, server.port)
      client!!.keepaliveTimeout = 10000000
      client!!.send("ping")
      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
      assertTrue(server.awaitCount(1))
      client!!.disconnect("done")
    }
  }
}
