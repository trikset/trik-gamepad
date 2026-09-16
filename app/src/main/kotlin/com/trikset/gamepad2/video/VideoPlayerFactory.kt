package com.trikset.gamepad2.video

import android.view.TextureView
import com.trikset.gamepad2.mjpeg.MjpegView

object VideoPlayerFactory {

  fun create(
      urlStr: String?,
      mjpegView: MjpegView,
      textureView: TextureView,
  ): VideoPlayer {
    val isRtsp = urlStr?.startsWith("rtsp:", ignoreCase = true) == true
    return if (isRtsp) {
      textureView.visibility = android.view.View.VISIBLE
      mjpegView.visibility = android.view.View.GONE
      MediaPlayerVideoPlayer(textureView)
    } else {
      mjpegView.visibility = android.view.View.VISIBLE
      textureView.visibility = android.view.View.GONE
      MjpegVideoPlayer(mjpegView)
    }
  }
}
