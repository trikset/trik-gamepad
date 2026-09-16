package com.trikset.gamepad2

/**
 * Reactive connection state of the [SenderService], exposed as a
 * [androidx.lifecycle.ViewModel]-backed [kotlinx.coroutines.flow.StateFlow]. Consumers collect it
 * with [androidx.lifecycle.repeatOnLifecycle] instead of the former one-shot disconnect callback.
 */
sealed interface ConnectionState {

  /** Reason used when the app pauses the session (a clean, non-error disconnect). */
  companion object {
    const val PAUSE_DISCONNECT_REASON = "Inactive gamepad"
  }

  data object Connecting : ConnectionState

  data object Connected : ConnectionState

  data class Disconnected(val reason: String) : ConnectionState {
    /**
     * True for non-error disconnects (never connected yet, clean pause) — a *standby* state, not a
     * failure. The HUD renders standby in sepia and only real connection errors in red.
     */
    val isStandby: Boolean
      get() = reason.isEmpty() || reason == PAUSE_DISCONNECT_REASON
  }
}
