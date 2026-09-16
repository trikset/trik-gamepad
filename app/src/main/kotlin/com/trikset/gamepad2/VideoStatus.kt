package com.trikset.gamepad2

/**
 * Persistent status of the MJPEG video stream, tracked by [MainActivity] from the stream's event
 * signals (URL config, loading, first frame, stream error). Distinct from the control
 * [ConnectionState]: a robot's TCP link and its video stream can be up/down independently.
 */
enum class VideoStatus {

  /** No video URL configured (video disabled) — the eye glyph renders in muted gray. */
  DISABLED,

  /** A stream URL is configured and a load/reconnect is in flight — amber. */
  LOADING,

  /** A stream that WAS playing is reloading (a reconnect) — amber. */
  RECONNECTING,

  /** The first frame rendered / the view is playing — green. */
  PLAYING,

  /** The last load or stream failed and retries are armed — red. */
  UNAVAILABLE,
}
