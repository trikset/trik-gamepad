package com.trikset.gamepad2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Direct tests for [ConnectionAnnouncer]: state→text mapping, target gating, repeat dedup. */
@RunWith(RobolectricTestRunner::class)
class ConnectionAnnouncerTest : RobolectricTestBase() {

  private val context = org.robolectric.RuntimeEnvironment.getApplication()

  private fun announcer(target: String? = "192.168.77.1:4444"): ConnectionAnnouncer =
      ConnectionAnnouncer(context) { target }

  @Test
  fun textForShouldMapConnecting() {
    assertEquals(
        "Connecting to 192.168.77.1:4444…",
        announcer().textFor(ConnectionState.Connecting),
    )
  }

  @Test
  fun textForShouldUseTheConfiguredTarget() {
    val a = ConnectionAnnouncer(context) { "10.0.0.9:8080" }
    assertEquals("Connecting to 10.0.0.9:8080…", a.textFor(ConnectionState.Connecting))
  }

  @Test
  fun textForShouldMapConnected() {
    assertEquals("Connected", announcer().textFor(ConnectionState.Connected))
  }

  @Test
  fun textForShouldMapDisconnectedWithTarget() {
    assertEquals("Tap to connect…", announcer().textFor(ConnectionState.Disconnected("x")))
  }

  @Test
  fun textForShouldBeSilentForDisconnectedWithoutTarget() {
    assertNull(announcer(target = null).textFor(ConnectionState.Disconnected("x")))
  }

  @Test
  fun nextAnnouncementShouldDedupeConsecutiveRepeats() {
    val a = announcer()
    assertEquals("Connected", a.nextAnnouncement(ConnectionState.Connected))
    assertNull(
        "a repeat of the same state must be silent",
        a.nextAnnouncement(ConnectionState.Connected),
    )
  }

  @Test
  fun nextAnnouncementShouldAnnounceAgainAfterStateChanges() {
    val a = announcer()
    a.nextAnnouncement(ConnectionState.Connected)
    assertEquals("Connecting to 192.168.77.1:4444…", a.nextAnnouncement(ConnectionState.Connecting))
    assertEquals("Connected", a.nextAnnouncement(ConnectionState.Connected))
  }

  @Test
  fun nextAnnouncementShouldStaySilentWithoutTarget() {
    val a = announcer(target = null)
    assertNull(a.nextAnnouncement(ConnectionState.Disconnected("x")))
  }
}
