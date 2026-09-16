package com.trikset.gamepad2

import android.os.Handler
import android.os.Looper
import android.view.View

/**
 * Plays the disconnect-alert haptic sequence ([Haptics.rejectSequence]) on the main thread with
 * explicit gaps so the pulses read as a pattern rather than being coalesced. A new play() cancels
 * any pending sequence — a quick reconnect must not leave queued buzzes behind.
 */
class RejectHaptic(
    private val viewProvider: () -> View?,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {
  private val pending = mutableListOf<Runnable>()

  fun play() {
    cancel()
    for ((delay, level) in Haptics.rejectSequence()) {
      val runnable = Runnable { viewProvider()?.haptic(level) }
      pending += runnable
      handler.postDelayed(runnable, delay)
    }
  }

  fun cancel() {
    pending.forEach(handler::removeCallbacks)
    pending.clear()
  }
}
