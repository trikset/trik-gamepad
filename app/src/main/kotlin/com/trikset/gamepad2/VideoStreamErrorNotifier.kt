package com.trikset.gamepad2

/**
 * Throttles the "video stream unavailable" notification so a dead stream surfaces one Snackbar per
 * failure episode instead of one per bounded-retry tick (5 s). Pure and clock-injectable so the
 * throttle boundary is directly unit-testable; [MainActivity] owns the actual Snackbar.
 */
class VideoStreamErrorNotifier(
    private val throttleMs: Long = THROTTLE_MS,
) {
  private var lastShownAtMs = 0L

  /** True when the first notification or at least [throttleMs] has passed since the last one. */
  fun shouldNotify(nowMs: Long = System.currentTimeMillis()): Boolean {
    val due = lastShownAtMs == NEVER_SHOWN || nowMs - lastShownAtMs >= throttleMs
    if (due) {
      lastShownAtMs = nowMs
    }
    return due
  }

  companion object {
    /** ~3 retry ticks at 5 s: one notification per episode, not per tick. */
    const val THROTTLE_MS = 15_000L

    private const val NEVER_SHOWN = 0L
  }
}
