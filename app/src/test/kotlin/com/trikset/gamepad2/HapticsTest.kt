package com.trikset.gamepad2

import android.view.HapticFeedbackConstants
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HapticsTest {

  @Test
  fun constantsMapToGenericLegacyTrio() {
    // Widely-supported generic constants only — the same on every API level / OEM HAL.
    assertEquals(HapticFeedbackConstants.KEYBOARD_TAP, Haptics.constant(Haptics.Level.TICK))
    assertEquals(HapticFeedbackConstants.VIRTUAL_KEY, Haptics.constant(Haptics.Level.CLICK))
    assertEquals(HapticFeedbackConstants.LONG_PRESS, Haptics.constant(Haptics.Level.HEAVY))
  }

  @Test
  fun rejectSequenceIsTwoStrongPulses() {
    // Disconnect alert: two strong pulses, 200 ms between their starts (user-chosen).
    assertEquals(
        listOf(Haptics.Level.HEAVY, Haptics.Level.HEAVY),
        Haptics.rejectSequence().map { it.second },
    )
    assertEquals(
        listOf(0L, Haptics.REJECT_PULSE_GAP_MS),
        Haptics.rejectSequence().map { it.first },
    )
    assertEquals(200L, Haptics.REJECT_PULSE_GAP_MS)
  }
}
