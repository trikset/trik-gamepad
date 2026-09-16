package com.trikset.gamepad2

import android.content.Context

/**
 * Decides what connection-state text to announce to a screen-reader user, deduping consecutive
 * announcements and suppressing the "tap to connect" hint when no target is configured. The
 * announced text is the same the status pill shows, so both stay in sync. Connection state is
 * otherwise color-only (the gear border), which accessibility services cannot see.
 */
class ConnectionAnnouncer(
    private val context: Context,
    private val targetProvider: () -> String?,
) {
  private var lastAnnouncement: String? = null

  fun textFor(state: ConnectionState): String? =
      when (state) {
        is ConnectionState.Connecting ->
            context.getString(R.string.connection_status_connecting, targetProvider() ?: "")
        is ConnectionState.Connected -> context.getString(R.string.connection_status_connected)
        is ConnectionState.Disconnected ->
            // No configured target (video-only device): nothing to connect to, nothing to announce.
            targetProvider()?.let {
              context.getString(R.string.connection_status_disconnected)
            }
      }

  /** Returns the text to announce now (null = stay silent), skipping repeats and silent states. */
  fun nextAnnouncement(state: ConnectionState): String? {
    val text = textFor(state) ?: return null
    if (text == lastAnnouncement) {
      return null
    }
    lastAnnouncement = text
    return text
  }
}
