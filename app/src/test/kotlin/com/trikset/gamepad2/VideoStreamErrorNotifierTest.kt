package com.trikset.gamepad2

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VideoStreamErrorNotifierTest : RobolectricTestBase() {

  @Test
  fun shouldNotifyOnFirstCall() {
    assertTrue(VideoStreamErrorNotifier(throttleMs = 15_000).shouldNotify(1_000L))
  }

  @Test
  fun shouldSuppressNotificationsWithinThrottleWindow() {
    val notifier = VideoStreamErrorNotifier(throttleMs = 15_000)
    assertTrue(notifier.shouldNotify(1_000L))
    assertFalse("second call inside the window must be suppressed", notifier.shouldNotify(2_000L))
    assertFalse(notifier.shouldNotify(15_999L))
  }

  @Test
  fun shouldAllowNotificationAfterThrottleWindowElapses() {
    val notifier = VideoStreamErrorNotifier(throttleMs = 15_000)
    assertTrue(notifier.shouldNotify(1_000L))
    assertTrue(
        "a call after the window must notify again",
        notifier.shouldNotify(16_000L),
    )
  }
}
