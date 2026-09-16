package com.trikset.gamepad2

import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Semantic haptic levels mapped to platform haptic constants, plus the disconnect-alert sequence.
 *
 * All feedback travels through [View.performHapticFeedback] (never `Vibrator.vibrate`): the
 * vibrate() entry is the one that enforces the VIBRATE permission, and using it directly would also
 * bypass the system haptics toggle. performHapticFeedback needs no manifest permission and respects
 * both the system setting and the view's isHapticFeedbackEnabled.
 *
 * The constants are the legacy generic trio — KEYBOARD_TAP / VIRTUAL_KEY / LONG_PRESS — supported
 * on every API level and by every OEM HAL; the newer API-30 constants (CONTEXT_CLICK / CONFIRM /
 * ...) are avoided because their device mapping is less predictable. LONG_PRESS resolves to
 * EFFECT_HEAVY_CLICK on modern devices (the strongest effect the S25 advertises: supportedEffects =
 * [CLICK, DOUBLE_CLICK, TICK, HEAVY_CLICK]), so it is the "strong" level.
 */
object Haptics {
  enum class Level {
    TICK,
    CLICK,
    HEAVY,
  }

  fun constant(level: Level): Int =
      when (level) {
        Level.TICK -> HapticFeedbackConstants.KEYBOARD_TAP
        Level.CLICK -> HapticFeedbackConstants.VIRTUAL_KEY
        Level.HEAVY -> HapticFeedbackConstants.LONG_PRESS
      }

  /**
   * Disconnect alert: two strong pulses with 200 ms between their starts. The gap keeps the pulses
   * distinct instead of letting the vibrator coalesce them.
   */
  fun rejectSequence(): List<Pair<Long, Level>> =
      listOf(
          0L to Level.HEAVY,
          REJECT_PULSE_GAP_MS to Level.HEAVY,
      )

  const val REJECT_PULSE_GAP_MS = 200L
}

/** Performs the haptic feedback for [level] on this view (respects system + view settings). */
fun View.haptic(level: Haptics.Level) {
  performHapticFeedback(Haptics.constant(level))
}
