package com.trikset.gamepad2

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.trikset.gamepad2.glyphs.GlyphRendering

/**
 * Owns the connection-state feedback chrome: recolors the settings-button border by
 * [ConnectionState], shows a transient glass error pill for connection errors, and renders a
 * centered glass status pill (⏻ tap-to-connect / ↺ connecting, hidden once connected) whose tap
 * retries the connection. The pills are [TextView]s: the state glyph and the error text are both
 * bundled-font, so the whole HUD reads one connection state. The views are injected as providers so
 * the object can be constructed before the view hierarchy exists (like [SystemUiController]);
 * recoloring the existing gear border adds no new touch surface, and the status pill is the single
 * tappable status affordance.
 */
class ConnectionFeedback(
    private val context: Context,
    private val settingsButtonProvider: () -> Button?,
    private val rootViewProvider: () -> View?,
    private val statusTextProvider: () -> TextView?,
    private val targetProvider: () -> String?,
    private val connectAction: () -> Unit,
) {
  private val indicator = ConnectionIndicator()
  private val announcer = ConnectionAnnouncer(context, targetProvider)
  private var errorHideRunnable: Runnable? = null

  /** Wires the tap-to-connect action onto the status pill; call once after the views exist. */
  fun attach() {
    val status = statusTextProvider()
    // The pill glyphs (⏻ / ↺) are bundled in res/font/symbols_mono.ttf so they render identically
    // on every device (they are rare codepoints missing from most system fonts); the shared
    // GlyphRendering applies the typeface + font-padding-free alignment for the pill and gear.
    status?.let {
      GlyphRendering.configure(it)
      it.setOnClickListener { connectAction() }
    }
    settingsButtonProvider()?.let(GlyphRendering::configure)
  }

  fun update(state: ConnectionState) {
    paintGearBorder(state)
    setStatusText(state)
    setStatusIcon(state)
    announceState(state)
  }

  /**
   * Announces connection-state changes for screen-reader users (the state is otherwise color-only).
   */
  private fun announceState(state: ConnectionState) {
    val announcement = announcer.nextAnnouncement(state) ?: return
    // announceForAccessibility (deprecated since API 33 in favor of live regions) is the simplest
    // cross-version "speak this now" API: a no-op without an accessibility service and works on
    // minSdk 23; a live region on the pill cannot announce "Connected" because the pill hides.
    @Suppress("DEPRECATION") statusTextProvider()?.announceForAccessibility(announcement)
  }

  private fun paintGearBorder(state: ConnectionState) {
    val btn = settingsButtonProvider() ?: return
    val layerDrawable = btn.background as? LayerDrawable ?: return
    val shape =
        layerDrawable.findDrawableByLayerId(R.id.settingsButtonBg) as? GradientDrawable ?: return
    val color = ContextCompat.getColor(context, indicator.borderColorResource(state))
    val stroke = context.resources.getDimensionPixelSize(R.dimen.settings_button_stroke)
    shape.setStroke(stroke, color)
    btn.invalidate()
    // The gear is the persistent status indicator; its description carries the state so a
    // screen-reader user who focuses it hears Connecting/Connected/Disconnected.
    btn.contentDescription = context.getString(stateDescriptionResource(state))
  }

  private fun stateDescriptionResource(state: ConnectionState): Int =
      when (state) {
        is ConnectionState.Connected -> R.string.settings_button_description_connected
        is ConnectionState.Connecting -> R.string.settings_button_description_connecting
        is ConnectionState.Disconnected -> R.string.settings_button_description_disconnected
      }

  private fun setStatusText(state: ConnectionState) {
    val status = statusTextProvider() ?: return
    when (state) {
      is ConnectionState.Connecting -> {
        // Symbol-only HUD: the pill shows a rotating arrow; the target host is announced for
        // screen readers (the robot chip already shows it visually).
        status.text = context.getString(R.string.connection_status_connecting_icon)
        status.contentDescription =
            context.getString(R.string.connection_status_connecting, targetProvider() ?: "")
        status.visibility = View.VISIBLE
      }
      // Connected -> no pill: the video stream / gear border conveys the state, and the
      // centered text would sit over the video.
      is ConnectionState.Connected -> status.visibility = View.GONE
      is ConnectionState.Disconnected ->
          // No configured target (e.g. a video-only device): no "tap to connect" affordance
          // with nothing to connect to. The red gear + video placeholder already signal it.
          if (targetProvider() != null) {
            status.text = context.getString(R.string.connection_status_disconnected_icon)
            status.contentDescription = context.getString(R.string.connection_status_disconnected)
            status.visibility = View.VISIBLE
          } else {
            status.visibility = View.GONE
          }
    }
  }

  /**
   * Recolors the pill text (and its icon glyph) to the accent. The pill is icon-only in the Type 1
   * HUD (a unicode glyph, not a compound drawable): the accent tint colors the glyph itself.
   */
  private fun setStatusIcon(state: ConnectionState) {
    val status = statusTextProvider() ?: return
    val accent = ContextCompat.getColor(context, indicator.accentColorResource(state))
    status.setTextColor(accent)
  }

  /**
   * Shows a transient content-sized glass error pill (ConnectionError), then auto-dismisses it.
   * Positioned at the vertical midpoint between the status pill and the magic-button row so it
   * never covers either (fits its message by construction: wrap_content in the layout). Replaces
   * the Material Snackbar - same transient semantics, but a HUD-native element (no library
   * dependency, always fits content, see MEMORY.md "HUD error pill").
   */
  fun error(message: String) {
    val root = rootViewProvider() ?: return
    val pill = root.findViewById<TextView>(R.id.connectionError) ?: return
    pill.text = message
    pill.contentDescription = message
    // Position at the vertical center between the status pill and the buttons row.
    val status = statusTextProvider()
    val buttons = root.findViewById<View>(R.id.buttons)
    val midpointY =
        if (status?.isVisible == true && buttons?.isVisible == true) {
          (status.bottom + buttons.top) / 2 - pill.height / 2
        } else {
          0
        }
    pill.translationY = midpointY.toFloat()
    pill.visibility = View.VISIBLE
    pill.alpha = 0f
    pill.animate().alpha(1f).setDuration(FADE_MS).start()
    errorHideRunnable?.let(root::removeCallbacks)
    errorHideRunnable = Runnable {
      pill
          .animate()
          .alpha(0f)
          .setDuration(FADE_MS)
          .withEndAction {
            pill.visibility = View.GONE
          }
          .start()
    }
    root.postDelayed(errorHideRunnable, ERROR_SHOW_MS)
  }

  private companion object {
    /** Error-pill visibility window (matches the old Snackbar LENGTH_LONG feel). */
    const val ERROR_SHOW_MS = 3500L
    const val FADE_MS = 200L
  }
}
