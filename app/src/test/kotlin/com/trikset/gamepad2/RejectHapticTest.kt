package com.trikset.gamepad2

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.LooperMode.Mode.PAUSED

/** Direct tests for [RejectHaptic]: the two-pulse disconnect alert sequence. */
@RunWith(RobolectricTestRunner::class)
@LooperMode(PAUSED)
class RejectHapticTest : RobolectricTestBase() {

  /** A view that counts the haptic feedback it performs (recorded regardless of system state). */
  private class CountingView(context: android.content.Context) : View(context) {
    val effects = ArrayList<Int>()

    override fun performHapticFeedback(effectId: Int): Boolean {
      effects.add(effectId)
      return true
    }
  }

  @Test
  fun playShouldFireBothPulsesInOrder() {
    val view = CountingView(org.robolectric.RuntimeEnvironment.getApplication())
    val reject = RejectHaptic({ view })

    reject.play()
    org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

    // The sequence is two strong pulses, fired in order (0 ms and 200 ms).
    assertEquals(
        listOf(Haptics.constant(Haptics.Level.HEAVY), Haptics.constant(Haptics.Level.HEAVY)),
        view.effects,
    )
  }

  @Test
  fun playWithNullViewProviderShouldBeSafe() {
    val reject = RejectHaptic({ null })

    reject.play()
    org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
  }

  @Test
  fun cancelShouldDropPendingPulses() {
    val view = CountingView(org.robolectric.RuntimeEnvironment.getApplication())
    val reject = RejectHaptic({ view })

    reject.play()
    reject.cancel()
    org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

    // No pulse fired: the pending sequence was cancelled before the looper ran.
    assertEquals("cancel must drop the queued pulses", emptyList<Int>(), view.effects)
  }

  @Test
  fun playTwiceShouldCancelTheFirstSequence() {
    val view = CountingView(org.robolectric.RuntimeEnvironment.getApplication())
    val reject = RejectHaptic({ view })

    reject.play()
    reject.play()
    org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

    // A second play cancels the first pending sequence: exactly two pulses fire (not four).
    assertEquals(
        "a new play must replace the pending sequence",
        listOf(Haptics.constant(Haptics.Level.HEAVY), Haptics.constant(Haptics.Level.HEAVY)),
        view.effects,
    )
  }
}
