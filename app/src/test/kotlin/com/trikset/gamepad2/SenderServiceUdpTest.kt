package com.trikset.gamepad2

import android.os.Looper.getMainLooper
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

/**
 * The UDP control transport (DESIGN.md "Gamepad protocol"): one newline-terminated command per
 * datagram, optimistic Connected once the first datagram is sent, robot keepalive liveness (any
 * received message resets the clock; a received `keepalive <ms>` sets the expected interval; no
 * message within `ms` + gap → disconnect), and per-keepalive-tick state re-send of the last
 * pad/wheel (buttons are edges and are NOT re-sent).
 */
@RunWith(RobolectricTestRunner::class)
@LooperMode(PAUSED)
class SenderServiceUdpTest : RobolectricTestBase() {
  private val mExecutor = PausedExecutorService()
  private var client: SenderService? = null

  @After
  fun tearDown() {
    client?.disconnect("tearDown")
  }

  private fun udpSender(): SenderService =
      SenderService(
              executor = mExecutor,
              transportFactory = { mode ->
                assertTrue(mode == TransportMode.UDP)
                UdpTransport()
              },
          )
          .also { it.transportMode = TransportMode.UDP }

  /**
   * Connects the UDP sender to [server], sends [commands], and drains the paused executor + main
   * looper so the datagrams are actually on the wire before the caller asserts.
   */
  private fun udpConnected(server: TestUdpServer, vararg commands: String): SenderService {
    client = udpSender()
    client!!.setTarget(TestUdpServer.HOST, server.port)
    client!!.keepaliveTimeout = 10000000 // disable keepalive
    commands.forEach { client!!.send(it) }
    mExecutor.runAll()
    shadowOf(getMainLooper()).idle()
    return client!!
  }

  @Test
  fun udpSendsOneCommandPerDatagram() {
    TestUdpServer().use { server ->
      udpConnected(server, "pad 1 25 25")
      assertTrue(server.awaitReceived("pad 1 25 25"))
      assertEquals(1, server.messageCount())
    }
  }

  @Test
  fun udpIsOptimisticallyConnectedAfterFirstSend() {
    TestUdpServer().use { server ->
      udpConnected(server, "pad 1 0 0")
      // Optimistic: Connected as soon as the first datagram went out — no robot reply required.
      assertEquals(ConnectionState.Connected, client!!.connectionState.value)
    }
  }

  @Test
  fun udpKeepaliveGoesOutAsOneDatagram() {
    TestUdpServer().use { server ->
      udpConnected(server, "pad 1 0 0")
      assertTrue(server.awaitReceived("pad 1 0 0"))
      // Drive a keepalive tick through the sender's postCommand path (same message the timer
      // sends).
      client!!.keepaliveTimeout = 1000
      client!!.postCommand("keepalive 1000")
      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
      assertTrue(server.awaitReceived("keepalive 1000"))
    }
  }

  @Test
  fun udpResendsLastPadAndWheelStateOnKeepaliveTick() {
    TestUdpServer().use { server ->
      udpConnected(server, "pad 1 40 60", "pad 2 up", "wheel 35")
      assertTrue(server.awaitReceived("pad 1 40 60"))
      assertTrue(server.awaitReceived("wheel 35"))

      val beforeResend = server.messageCount()
      // The keepalive tick re-sends the last pad/wheel state (UDP convergence for dropped packets).
      client!!.resendLastState()
      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
      // Bounded poll, never a bare count assert: the resent datagrams arrive on the server's own
      // receive thread (TESTING.md "Why awaits are required"). All three states must be re-sent.
      assertTrue("state must be re-sent", server.awaitCount(beforeResend + 3))
      assertTrue(server.awaitReceived("pad 1 40 60"))
      assertTrue(server.awaitReceived("pad 2 up"))
      assertTrue(server.awaitReceived("wheel 35"))
    }
  }

  @Test
  fun robotKeepaliveSetsTheHeartbeatInterval() {
    TestUdpServer().use { server ->
      udpConnected(server, "pad 1 0 0")
      assertTrue(server.awaitReceived("pad 1 0 0"))
      // Any received robot message resets the liveness clock; `keepalive <ms>` sets the interval.
      client!!.onRobotMessage("keepalive 1200")
      assertEquals(1200, client!!.robotKeepaliveTimeoutMs)
    }
  }

  @Test
  fun robotMessageResetsTheLivenessClock() {
    TestUdpServer().use { server ->
      udpConnected(server, "pad 1 0 0")
      assertTrue(server.awaitReceived("pad 1 0 0"))
      client!!.onRobotMessage("keepalive 1200")
      val now = System.currentTimeMillis()
      client!!.onRobotMessage("some other control message")
      // After a fresh message, a liveness check at `now` must not disconnect (interval + gap not
      // elapsed).
      client!!.checkRobotLiveness(now)
      shadowOf(getMainLooper()).idle()
      assertEquals(ConnectionState.Connected, client!!.connectionState.value)
    }
  }

  @Test
  fun missingRobotKeepaliveDisconnectsAfterIntervalPlusGap() {
    TestUdpServer().use { server ->
      udpConnected(server, "pad 1 0 0")
      assertTrue(server.awaitReceived("pad 1 0 0"))
      client!!.onRobotMessage("keepalive 1200")
      val now = System.currentTimeMillis()
      // The robot announced a 1200 ms heartbeat but went silent: > 1200 + 2000 gap must disconnect.
      client!!.checkRobotLiveness(now + SenderService.ROBOT_KEEPALIVE_GAP_MS + 1201)
      shadowOf(getMainLooper()).idle()
      assertEquals(
          ConnectionState.Disconnected("Robot keepalive missed."),
          client!!.connectionState.value,
      )
    }
  }

  @Test
  fun keepaliveMinusOneDisablesRobotLiveness() {
    TestUdpServer().use { server ->
      udpConnected(server, "pad 1 0 0")
      assertTrue(server.awaitReceived("pad 1 0 0"))
      // `keepalive -1` = no expectation (disabled / unlimited).
      client!!.onRobotMessage("keepalive -1")
      val now = System.currentTimeMillis()
      client!!.checkRobotLiveness(now + 100000)
      shadowOf(getMainLooper()).idle()
      assertEquals(ConnectionState.Connected, client!!.connectionState.value)
    }
  }

  @Test
  fun keepaliveZeroDisablesRobotLiveness() {
    TestUdpServer().use { server ->
      udpConnected(server, "pad 1 0 0")
      assertTrue(server.awaitReceived("pad 1 0 0"))
      // `keepalive 0` = disabled (protocol: keepalive <= 0 disables the expectation).
      client!!.onRobotMessage("keepalive 0")
      val now = System.currentTimeMillis()
      client!!.checkRobotLiveness(now + 100000)
      shadowOf(getMainLooper()).idle()
      assertEquals(ConnectionState.Connected, client!!.connectionState.value)
    }
  }

  @Test
  fun unknownRobotMessageIsIgnored() {
    TestUdpServer().use { server ->
      udpConnected(server, "pad 1 0 0")
      assertTrue(server.awaitReceived("pad 1 0 0"))
      client!!.onRobotMessage("some future telemetry message")
      shadowOf(getMainLooper()).idle()
      assertEquals(ConnectionState.Connected, client!!.connectionState.value)
      // No malformed-keepalive warning path was hit either.
      client!!.onRobotMessage("keepalive not-a-number")
      shadowOf(getMainLooper()).idle()
      assertEquals(ConnectionState.Connected, client!!.connectionState.value)
    }
  }

  @Test
  fun tcpRobotKeepaliveSetsTheHeartbeatInterval() {
    TestTcpServer().use { server ->
      // The robot announces its heartbeat interval over the TCP stream; the app must accept it.
      val intervalSeen = announceTcpRobotKeepalive(server, 1200)
      assertTrue("TCP robot keepalive must set the heartbeat interval", intervalSeen)
    }
  }

  @Test
  fun tcpRobotMessageResetsTheLivenessClock() {
    TestTcpServer().use { server ->
      announceTcpRobotKeepalive(server, 1200)
      val now = System.currentTimeMillis()
      // A fresh robot message over TCP resets the liveness clock: no disconnect at `now`.
      server.sendRobotMessage("some other control message")
      client!!.checkRobotLiveness(now)
      shadowOf(getMainLooper()).idle()
      assertEquals(ConnectionState.Connected, client!!.connectionState.value)
    }
  }

  @Test
  fun tcpMissingRobotKeepaliveDisconnectsAfterIntervalPlusGap() {
    TestTcpServer().use { server ->
      announceTcpRobotKeepalive(server, 1200)
      val now = System.currentTimeMillis()
      // The robot announced a 1200 ms heartbeat but went silent over TCP: gap + 1201 must
      // disconnect.
      client!!.checkRobotLiveness(now + SenderService.ROBOT_KEEPALIVE_GAP_MS + 1201)
      shadowOf(getMainLooper()).idle()
      assertEquals(
          ConnectionState.Disconnected("Robot keepalive missed."),
          client!!.connectionState.value,
      )
    }
  }

  /**
   * Connects the TCP sender, waits for a command on the wire, has the robot announce a [ms]
   * heartbeat, and returns whether the app accepted the interval (bounded poll — the reply arrives
   * on the transport's own receive thread, TESTING.md "Why awaits are required").
   */
  private fun announceTcpRobotKeepalive(server: TestTcpServer, ms: Int): Boolean {
    tcpConnected(server, "pad 1 0 0")
    assertTrue(server.awaitReceived("pad 1 0 0", drain = { mExecutor.runAll() }))
    server.sendRobotMessage("keepalive $ms")
    return runBounded { client?.robotKeepaliveTimeoutMs == ms }
  }

  @Test
  fun tcpUnknownRobotMessageIsIgnored() {
    TestTcpServer().use { server ->
      tcpConnected(server, "pad 1 0 0")
      assertTrue(server.awaitReceived("pad 1 0 0", drain = { mExecutor.runAll() }))
      // Additive rule: a future telemetry message over TCP must not disturb the connection.
      server.sendRobotMessage("some future telemetry message")
      shadowOf(getMainLooper()).idle()
      assertEquals(ConnectionState.Connected, client!!.connectionState.value)
    }
  }

  /**
   * Connects the TCP default transport to [server] and drains the executor + main looper so the
   * socket is up and the command is on the wire before the caller asserts.
   */
  private fun tcpConnected(server: TestTcpServer, command: String): SenderService {
    client = SenderService(mExecutor) // TCP default transport
    client!!.setTarget(TestTcpServer.HOST, server.port)
    client!!.keepaliveTimeout = 10000000
    client!!.send(command)
    mExecutor.runAll()
    shadowOf(getMainLooper()).idle()
    assertTrue(server.awaitConnection())
    return client!!
  }

  @Test
  fun resendLastStateOverTcpIsNoOp() {
    TestTcpServer().use { server ->
      tcpConnected(server, "pad 1 0 0")
      assertTrue(server.awaitReceived("pad 1 0 0", drain = { mExecutor.runAll() }))

      // TCP never re-sends state; the early-return branch keeps the socket silent.
      client!!.resendLastState()
      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
      // Still exactly one line on the socket (the original pad command); nothing re-sent.
      assertTrue(
          "TCP must not re-send anything",
          !server.awaitCount(2, timeoutMs = 400),
      )
    }
  }

  @Test
  fun udpResendLastStateSkipsPadStatesThatWereNeverSent() {
    TestUdpServer().use { server ->
      // Only pad1 was ever sent: pad2/wheel are null and must be skipped on resend.
      udpConnected(server, "pad 1 40 60")
      assertTrue(server.awaitReceived("pad 1 40 60"))

      val beforeResend = server.messageCount()
      client!!.resendLastState()
      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
      // Exactly one datagram re-sent (pad1); pad2/wheel never existed. Bounded poll: the resend
      // arrives on the server's receive thread.
      assertTrue("pad1 must be re-sent", server.awaitCount(beforeResend + 1))
      assertEquals(
          "only pad1 must be re-sent (pad2/wheel never existed)",
          beforeResend + 1,
          server.messageCount(),
      )
    }
  }

  @Test
  fun defaultTransportFactorySupportsUdpMode() {
    TestUdpServer().use { server ->
      // The default factory (no injected transportFactory) must build a UDP transport.
      client = SenderService(mExecutor).also { it.transportMode = TransportMode.UDP }
      client!!.setTarget(TestUdpServer.HOST, server.port)
      client!!.keepaliveTimeout = 10000000
      client!!.send("pad 1 5 5")
      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
      assertTrue(server.awaitReceived("pad 1 5 5"))
    }
  }

  @Test
  fun transportChangeDisconnects() {
    TestTcpServer().use { server ->
      tcpConnected(server, "pad 1 0 0")
      assertEquals(ConnectionState.Connected, client!!.connectionState.value)
      // Switching to UDP must tear down the TCP connection; the next command reconnects over UDP.
      client!!.transportMode = TransportMode.UDP
      assertEquals(
          ConnectionState.Disconnected("Transport changed."),
          client!!.connectionState.value,
      )
    }
  }
}
