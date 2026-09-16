package com.trikset.gamepad2

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Direct [UdpTransport] tests for the paths the SenderService-level suite cannot reach: the
 * send-before-open / close-before-open no-ops and the receive loop's resilience to a transient
 * [SocketException]/[IOException] (a poll error must not kill the loop — the next datagram is still
 * delivered). The protocol semantics live in [SenderServiceUdpTest].
 */
@RunWith(RobolectricTestRunner::class)
class UdpTransportTest : RobolectricTestBase() {

  @Test
  fun sendBeforeOpenReturnsFalse() {
    // No socket yet (open() not called): a send must report failure, never throw.
    assertFalse(UdpTransport().send("pad 1 0 0"))
  }

  @Test
  fun closeBeforeOpenIsNoOp() {
    // Nothing opened: close() must be safe (null socket and null receive thread).
    UdpTransport().close()
  }

  @Test
  fun receiveLoopDeliversInboundLinesAndDropsThemWhenNoListener() {
    TestUdpServer().use { server ->
      val transport = UdpTransport()
      transport.open(TestUdpServer.HOST, server.port)
      try {
        transport.send("probe")
        assertTrue(server.awaitReceived("probe"))
        server.sendToLastSender("keepalive 700")
        // Longer than one soTimeout poll so the loop is guaranteed to have processed the line
        // with onMessage == null (the "drop it" branch).
        Thread.sleep(400)

        val received = AtomicReference<String>()
        val latch = CountDownLatch(1)
        transport.onMessage = { line ->
          received.set(line)
          latch.countDown()
        }
        server.sendToLastSender("keepalive 800")
        assertTrue("the second line must reach the listener", latch.await(5, TimeUnit.SECONDS))
        assertEquals("keepalive 800", received.get())
      } finally {
        transport.close()
      }
    }
  }

  @Test
  fun receiveLoopSurvivesAPollError() {
    for (error in listOf(SocketException("transient"), IOException("transient"))) {
      TestUdpServer().use { server ->
        val transport = UdpTransport(datagramBinder = oneShotThrower(error))
        transport.open(TestUdpServer.HOST, server.port)
        try {
          transport.send("probe")
          assertTrue(server.awaitReceived("probe"))
          val received = AtomicReference<String>()
          val latch = CountDownLatch(1)
          transport.onMessage = { line ->
            received.set(line)
            latch.countDown()
          }
          server.sendToLastSender("keepalive 500")
          // The first receive() throws (SocketException/IOException); the loop must survive and
          // still deliver the next datagram.
          assertTrue(
              "a ${error.javaClass.simpleName} must not kill the loop",
              latch.await(5, TimeUnit.SECONDS),
          )
          assertEquals("keepalive 500", received.get())
        } finally {
          transport.close()
        }
      }
    }
  }

  private fun oneShotThrower(error: Exception): DatagramBinder = DatagramBinder { fresh ->
    fresh.close()
    OneShotThrowingSocket(error)
  }

  /** A real [DatagramSocket] whose first [receive] throws [error], then behaves normally. */
  private class OneShotThrowingSocket(private val error: Exception) : DatagramSocket() {
    private var threw = false

    override fun receive(packet: DatagramPacket) {
      if (!threw) {
        threw = true
        throw error
      }
      super.receive(packet)
    }
  }
}
