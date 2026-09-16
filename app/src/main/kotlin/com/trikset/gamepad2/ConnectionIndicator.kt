package com.trikset.gamepad2

import androidx.annotation.ColorRes

/**
 * Maps the TCP [ConnectionState] to the Type 1 HUD accent color — the single source of truth for
 * the connection-state tone used by the gear border, the pad chrome, the magic buttons and the
 * status pill (see DESIGN.md "HUD themes"). Pure so the mapping is directly unit-testable.
 *
 * Color semantics: Connected → green, Connecting → amber, idle/clean-pause → sepia (standby, not an
 * error), real connection error → red. "Color is never the only signal": the pill text, gear
 * contentDescription and accessibility announcements carry the state too.
 */
class ConnectionIndicator {

  @ColorRes
  fun borderColorResource(state: ConnectionState): Int =
      when (state) {
        is ConnectionState.Connecting -> R.color.hud_accent_connecting
        is ConnectionState.Connected -> R.color.hud_accent_connected_dark
        is ConnectionState.Disconnected -> accentColorResource(state)
      }

  @ColorRes
  fun accentColorResource(state: ConnectionState): Int =
      when (state) {
        is ConnectionState.Connecting -> R.color.hud_accent_connecting
        is ConnectionState.Connected -> R.color.hud_accent_connected
        is ConnectionState.Disconnected ->
            if (state.isStandby) R.color.hud_sepia else R.color.hud_accent_error
      }
}
