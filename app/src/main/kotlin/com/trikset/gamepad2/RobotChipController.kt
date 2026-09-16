package com.trikset.gamepad2

import android.content.Context
import android.graphics.PorterDuff
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Owns the robot-target chip (top-left of the gamepad HUD): the host text, the CONTROL glyph tinted
 * to the connection accent, and the VIDEO glyph tinted to the stream status, plus the chip's
 * dynamic contentDescription (a11y: "color is never the only signal"). Extracted from MainActivity
 * so the chip logic is directly testable without the activity; the views are injected as providers
 * and [controlAccentProvider] reports the current control connection accent at description time.
 */
class RobotChipController(
    private val context: Context,
    private val chipProvider: () -> View?,
    private val chipTextProvider: () -> TextView?,
    private val controlAccentProvider: () -> Int,
) {

  // Last known video-stream status (drives the chip eye glyph). Kept here so the glyph can be
  // re-painted on the same call sites that already observe stream events; the control side uses
  // the reactive connectionState flow instead (see MainActivity.applyHudTone).
  var videoStatus: VideoStatus = VideoStatus.DISABLED
    private set

  fun setHost(host: String) {
    chipTextProvider()?.text = host
  }

  /** Records the video-stream status and repaints the eye glyph + dynamic contentDescription. */
  fun setVideoStatus(status: VideoStatus) {
    if (videoStatus == status) return
    videoStatus = status
    applyVideoStatusIcon()
  }

  /**
   * Paints the CONTROL glyph to the connection accent and refreshes the description. Called from
   * MainActivity.applyHudTone on every state change; the host text stays neutral white and the
   * VIDEO glyph is painted separately by [setVideoStatus].
   */
  fun paintControlAccent(accent: Int) {
    tintChipGlyph("controlStatusIcon", accent)
    refreshChipDescription()
  }

  /** Paints the VIDEO glyph to the [videoStatus] color (green/amber/red/gray). */
  private fun applyVideoStatusIcon() {
    tintChipGlyph("videoStatusIcon", VideoStatusIndicator().colorResource(videoStatus))
    refreshChipDescription()
  }

  private fun tintChipGlyph(tag: String, @androidx.annotation.ColorRes colorRes: Int) {
    val accent = ContextCompat.getColor(context, colorRes)
    chipProvider()?.findViewWithTag<ImageView>(tag)?.setColorFilter(accent, PorterDuff.Mode.SRC_IN)
  }

  /**
   * Rebuilds the chip's contentDescription from the host + both connection states so a
   * screen-reader user hears e.g. "192.168.0.1, control connected, video streaming. Open robot
   * settings" — color is never the only signal. Falls back to the click-only description.
   */
  private fun refreshChipDescription() {
    val chip = chipProvider() ?: return
    val host =
        chipTextProvider()?.text?.toString() ?: MainActivitySettingsController.TARGET_CHIP_EMPTY
    val controlWord = controlWordResource(controlAccentProvider())
    val videoWord = videoWordResource(videoStatus)
    val status =
        context.getString(
            R.string.target_chip_status_format,
            host,
            context.getString(controlWord),
            context.getString(videoWord),
        )
    chip.contentDescription = context.getString(R.string.target_chip_description) + ". " + status
  }

  private fun controlWordResource(accent: Int): Int =
      when (accent) {
        R.color.hud_accent_connected -> R.string.chip_control_connected
        R.color.hud_accent_connecting -> R.string.chip_control_connecting
        R.color.hud_sepia -> R.string.chip_control_standby
        else -> R.string.chip_control_disconnected
      }

  private fun videoWordResource(status: VideoStatus): Int =
      when (status) {
        VideoStatus.PLAYING -> R.string.chip_video_playing
        VideoStatus.LOADING -> R.string.chip_video_loading
        VideoStatus.RECONNECTING -> R.string.chip_video_reconnecting
        VideoStatus.UNAVAILABLE -> R.string.chip_video_unavailable
        VideoStatus.DISABLED -> R.string.chip_video_disabled
      }
}
