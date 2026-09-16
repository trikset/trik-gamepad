@file:Suppress("DEPRECATION") // Depends on the deprecated rule by design (see KDoc).

package com.trikset.gamepad2

import android.app.Activity
import android.os.SystemClock
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule

/**
 * ActivityTestRule that waits for the app window to gain focus before each test, dismissing any
 * focus-stealing system overlay (e.g. the first-immersion confirmation) that CI's headless
 * swiftshader GPU leaves in front of the app window.
 *
 * Pass [waitForFocus] = false for tests that never interact with the view hierarchy (e.g.
 * KeepAliveTests) so they are not slowed by the wait.
 */
open class FocusAwareActivityTestRule<T : Activity>(
    activityClass: Class<T>,
    private val waitForFocus: Boolean = true,
) : ActivityTestRule<T>(activityClass) {

  override fun afterActivityLaunched() {
    super.afterActivityLaunched()
    if (waitForFocus) {
      waitForWindowFocus()
    }
  }

  private fun hasWindowFocus(): Boolean {
    var focused = false
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      focused = activity.hasWindowFocus()
    }
    return focused
  }

  private fun waitForWindowFocus() {
    val start = SystemClock.uptimeMillis()
    val deadline = start + FOCUS_TIMEOUT_MS
    var backPresses = 0
    while (SystemClock.uptimeMillis() < deadline) {
      if (hasWindowFocus()) {
        return
      }
      // A system overlay (e.g. the first-immersion confirmation) can hold window focus
      // indefinitely; dismissing it with BACK lets the app regain focus. Only press while
      // the app window is NOT focused, and never more than a few times, so a healthy
      // activity is never sent BACK.
      if (
          backPresses < MAX_BACK_PRESSES &&
              SystemClock.uptimeMillis() - start >= BACK_PRESS_DELAY_MS
      ) {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        backPresses++
      }
      try {
        Thread.sleep(FOCUS_POLL_MS)
      } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        return
      }
    }
    throw AssertionError("App window never gained focus within $FOCUS_TIMEOUT_MS ms")
  }

  private companion object {
    const val FOCUS_TIMEOUT_MS = 45_000L
    const val FOCUS_POLL_MS = 500L
    const val BACK_PRESS_DELAY_MS = 5_000L
    const val MAX_BACK_PRESSES = 3
  }
}
