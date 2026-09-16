package com.trikset.gamepad2.video

import com.trikset.gamepad2.mjpeg.ScaleMode

interface VideoPlayer {
  val isPlaying: Boolean
  var showFps: Boolean
  var scaleMode: ScaleMode

  fun play(url: String?)

  fun stop()

  fun setOnStreamErrorListener(listener: (() -> Unit)?)

  fun setOnFirstFrameListener(listener: (() -> Unit)?)

  var onPlayResult: ((Boolean) -> Unit)?

  fun release()
}
