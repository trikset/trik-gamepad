package com.trikset.gamepad2

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class KeepAliveTests {

  // KeepAliveTests never interact with the view hierarchy, so they do not
  // need the focus-wait (and must not be slowed by it on a focus-less CI emulator).
  @get:Rule val mActivityTestRule = FocusAwareActivityTestRule(MainActivity::class.java, false)

  @Test
  @Throws(InterruptedException::class)
  fun keepAliveShouldBeReceivedAfterGivenTimePeriod() {
    val timeout = 1000
    val server = DummyServer()
    val client = mActivityTestRule.activity.senderService
    client.setTarget(DummyServer.IP, DummyServer.DEFAULT_PORT)
    client.keepaliveTimeout = timeout
    // In order to set connection up
    client.send("test")

    // Wait for the keepalive to arrive instead of guessing at a sleep budget.
    assertTrue(server.awaitMessage(String.format(Locale.ROOT, "keepalive %d", timeout), 30000))
    server.stopListening()

    val messages = server.receivedMessages.listIterator()
    while (messages.hasNext()) {
      messages.next()
    }
    assertEquals(String.format(Locale.ROOT, "keepalive %d", timeout), messages.previous())
  }

  @Test
  @Throws(InterruptedException::class)
  fun keepaliveMessagesShouldNotBeSentAfterDisconnect() {
    val server = DummyServer()
    val client = mActivityTestRule.activity.senderService
    client.setTarget(DummyServer.IP, DummyServer.DEFAULT_PORT)
    client.keepaliveTimeout = 2000

    // In order to set connection up
    client.send("testtest")
    assertTrue(server.awaitMessage("testtest", 30000))
    client.disconnect("testtest")

    // Give any (incorrectly scheduled) keepalive a chance to appear within a
    // bounded window, then prove none did (keepalive period is 2000ms).
    assertFalse(server.anyMessageWithin(2500))
    server.stopListening()

    val messages = server.receivedMessages.iterator()
    assertTrue(messages.hasNext())
    assertEquals("testtest", messages.next())
    assertFalse(messages.hasNext())
  }
}
