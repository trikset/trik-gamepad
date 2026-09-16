package com.trikset.gamepad2

import android.os.Handler
import android.os.Looper

/**
 * Bounded MJPEG video-stream retry. While the activity is resumed and the retry-gate predicate
 * ([shouldReload]) returns true (a configured URL with the video view not playing), reloads the
 * stream on a fixed interval, and also immediately when the control connection flips to Connected
 * (a pad touch reconnects the control connection → instant video reload instead of waiting for the
 * next tick). The control state is a reload *trigger*, not part of the gate.
 *
 * Replaces the safety net lost when the original unconditional 30 s MJPEG restart was removed (the
 * socket-leak driver): a failed open / a silently-stalled stream no longer leaves the video black
 * until the user leaves and re-enters. Unlike the 30 s loop, a healthy stream is never touched
 * (ticks are gated by [shouldReload], which includes "not playing").
 *
 * All timers are main-thread [Handler] callbacks, cancelled on pause/destroy (no leaks). The
 * [shouldReload] / [reload] functions are injected so the loop is unit-testable without sockets.
 */
class VideoRetryController(
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    private val retryIntervalMs: Long = DEFAULT_RETRY_INTERVAL_MS,
    private val shouldReload: () -> Boolean,
    private val reload: () -> Unit,
) {
  private val tick = Runnable { onTick() }
  private var active = false

  /** Arm the retry loop; the activity is resumed (ticks only run between onResume/onPause). */
  fun onResume() {
    active = true
    if (shouldReload()) {
      schedule()
    }
  }

  /** Disarm and cancel all pending ticks. */
  fun onPause() {
    active = false
    cancel()
  }

  /**
   * The render thread reported a dead stream: reload immediately (reconnect-on-error), then arm.
   */
  fun onStreamError() {
    reload()
    if (active && shouldReload()) {
      schedule()
    }
  }

  /** A load attempt failed (stream could not be opened): arm the retry loop. */
  fun onLoadFailed() {
    if (active && shouldReload()) {
      schedule()
    }
  }

  /** A load attempt succeeded: disarm — a healthy stream is left alone. */
  fun onLoadSuccess() {
    cancel()
  }

  /** The control connection is Connected (robot reachable): reload right away if needed. */
  fun onControlConnected() {
    if (active && shouldReload()) {
      reload()
    }
  }

  // Runs on the main thread only; active/onPause cannot interleave here, and onPause cancels the
  // pending callback, so no liveness re-checks are needed (a healthy stream is skipped by the
  // shouldReload gate anyway).
  private fun onTick() {
    if (shouldReload()) {
      reload()
    }
    mainHandler.postDelayed(tick, retryIntervalMs)
  }

  private fun schedule() {
    mainHandler.removeCallbacks(tick)
    mainHandler.postDelayed(tick, retryIntervalMs)
  }

  private fun cancel() {
    mainHandler.removeCallbacks(tick)
  }

  companion object {
    /** 5 s tick + ≤ 5 s connect timeout keeps the 10 s recovery budget. */
    const val DEFAULT_RETRY_INTERVAL_MS = 5000L
  }
}
