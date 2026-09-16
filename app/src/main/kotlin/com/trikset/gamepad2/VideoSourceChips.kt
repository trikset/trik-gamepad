package com.trikset.gamepad2

import androidx.annotation.StringRes
import androidx.core.net.toUri

/**
 * The four video-source preset chips shown in Robot settings (Video category, below the video-URI
 * row): Camera 1 / Camera 2 / USB / No video. One object is the single source of truth for both the
 * port table and the target-URI computation so the chips and the
 * [SettingsFragment.effectiveVideoUri] default can never disagree.
 *
 * The port table (Camera 1 = 8080, Camera 2 = 8081, USB = 8082) mirrors the robot's MJPEG stream
 * layout: 8080 is the host-derived default URI's port. It is **provisional until verified on a real
 * robot** (the user's accepted "fine for now, no RTSP on the robot yet" - see DECISIONS.md
 * "[2026-09-02] Video-source preset chips").
 */
enum class VideoSourceChip(
    @param:StringRes val descriptionRes: Int,
    val port: Int?,
    val glyph: String,
) {
  CAMERA_1(R.string.video_chip_camera_1, VideoSourceChips.PORT_CAMERA_1, "1"),
  CAMERA_2(R.string.video_chip_camera_2, VideoSourceChips.PORT_CAMERA_2, "2"),
  USB(R.string.video_chip_usb, VideoSourceChips.PORT_USB, "\uDB81\uDD53"),
  /** Explicitly disables the video stream (writes an empty URI), never "restores a default". */
  NONE(R.string.video_chip_none, null, "\uDB80\uDE09"),
}

/**
 * Pure target-URI computation for the preset chips. [targetFor] returns the new value to store
 * under [SettingsFragment.SK_VIDEO_URI], or `null` when no URI can be produced (blank stored URI
 * AND blank host) - the caller surfaces the "Set the robot IP address first" notice.
 */
object VideoSourceChips {
  const val PORT_CAMERA_1 = 8080
  const val PORT_CAMERA_2 = 8081
  const val PORT_USB = 8082

  fun defaultStreamUri(host: String, port: Int): String = "http://$host:$port/?action=stream"

  /**
   * Rewrites only the port of a stored stream URI, keeping scheme/host/path/query intact. Returns
   * `null` when the stored value carries no host to rewrite (falls back to [defaultStreamUri]).
   */
  internal fun rewritePort(storedUri: String, port: Int): String? {
    val parsed = runCatching { storedUri.toUri() }.getOrNull() ?: return null
    val host = parsed.host
    if (host.isNullOrBlank()) return null
    // Uri.Builder has no port() setter, so rebuild the authority (userinfo@host:port) by hand.
    // encodedAuthority() takes the already-encoded separators verbatim (authority() would
    // percent-encode the ':' into %3A).
    val authority = (parsed.userInfo?.let { "$it@" } ?: "") + "$host:$port"
    return parsed.buildUpon().encodedAuthority(authority).build().toString()
  }

  /** The stored URI to write for [chip]; `null` = cannot build (see object doc). */
  fun targetFor(storedUri: String?, host: String, chip: VideoSourceChip): String? {
    val port = chip.port
    if (port == null) {
      // "No video": explicit disable (empty URI).
      return ""
    }
    val rebuilt = storedUri.orEmpty().takeIf { it.isNotBlank() }?.let { rewritePort(it, port) }
    return rebuilt ?: if (host.isBlank()) null else defaultStreamUri(host, port)
  }
}
