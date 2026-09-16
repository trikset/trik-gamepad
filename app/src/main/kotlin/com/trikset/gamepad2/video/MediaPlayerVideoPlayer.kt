package com.trikset.gamepad2.video

import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import androidx.core.net.toUri
import com.trikset.gamepad2.diagnostics.AppLog
import com.trikset.gamepad2.mjpeg.ScaleMode

class MediaPlayerVideoPlayer(
    private val textureView: TextureView,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) : VideoPlayer {

  override val isPlaying: Boolean
    get() = mediaPlayer?.isPlaying == true

  override var showFps: Boolean = false

  override var scaleMode: ScaleMode
    get() = ScaleMode.FIT
    set(_) {}

  override var onPlayResult: ((Boolean) -> Unit)? = null

  private var mediaPlayer: MediaPlayer? = null
  private var pendingUrl: String? = null
  private var pendingErrorListener: (() -> Unit)? = null
  private var pendingFirstFrameListener: (() -> Unit)? = null
  private var prepared = false
  private var firstFrameReported = false

  init {
    textureView.surfaceTextureListener =
        object : TextureView.SurfaceTextureListener {
          override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            val url = pendingUrl ?: return
            if (!prepared) {
              prepareAndStart(url)
            }
          }

          override fun onSurfaceTextureSizeChanged(
              surface: SurfaceTexture,
              width: Int,
              height: Int,
          ) {}

          override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            stop()
            return true
          }

          override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
  }

  private fun postOnPlayResult(result: Boolean) {
    mainHandler.post { onPlayResult?.invoke(result) }
  }

  private fun postStreamError() {
    mainHandler.post { pendingErrorListener?.invoke() }
  }

  private fun postFirstFrame() {
    mainHandler.post { pendingFirstFrameListener?.invoke() }
  }

  override fun play(url: String?) {
    if (url == null) {
      stop()
      postOnPlayResult(false)
      return
    }
    stop()
    pendingUrl = url
    if (textureView.isAvailable) {
      prepareAndStart(url)
    }
  }

  private fun prepareAndStart(url: String) {
    try {
      firstFrameReported = false
      val player =
          MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setSurface(Surface(textureView.surfaceTexture))
            setDataSource(textureView.context, url.toUri())
            setOnPreparedListener { mp ->
              prepared = true
              mp.start()
              postOnPlayResult(true)
            }
            setOnErrorListener { _, what, extra ->
              AppLog.e(TAG, "MediaPlayer error: what=$what extra=$extra")
              postStreamError()
              postOnPlayResult(false)
              true
            }
            setOnInfoListener { _, what, _ ->
              if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                if (!firstFrameReported) {
                  firstFrameReported = true
                  postFirstFrame()
                }
              }
              false
            }
            prepareAsync()
          }
      mediaPlayer = player
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
      AppLog.e(TAG, "Failed to prepare RTSP stream: $url", e)
      postOnPlayResult(false)
    }
  }

  override fun stop() {
    pendingUrl = null
    prepared = false
    mediaPlayer?.apply {
      try {
        stop()
        release()
      } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        AppLog.e(TAG, "Error stopping MediaPlayer", e)
      }
    }
    mediaPlayer = null
  }

  override fun setOnStreamErrorListener(listener: (() -> Unit)?) {
    pendingErrorListener = listener
  }

  override fun setOnFirstFrameListener(listener: (() -> Unit)?) {
    pendingFirstFrameListener = listener
  }

  override fun release() {
    stop()
    pendingErrorListener = null
    pendingFirstFrameListener = null
    textureView.surfaceTextureListener = null
  }

  private companion object {
    const val TAG = "MediaPlayerVideoPlayer"
  }
}
