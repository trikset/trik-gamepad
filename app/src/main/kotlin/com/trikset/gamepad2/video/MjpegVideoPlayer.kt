package com.trikset.gamepad2.video

import android.os.Handler
import android.os.Looper
import com.trikset.gamepad2.ConnectionOpener
import com.trikset.gamepad2.RawSocketHttpStream
import com.trikset.gamepad2.SocketBinder
import com.trikset.gamepad2.WifiConnectionOpener
import com.trikset.gamepad2.WifiSocketBinder
import com.trikset.gamepad2.diagnostics.AppLog
import com.trikset.gamepad2.mjpeg.MjpegInputStream
import com.trikset.gamepad2.mjpeg.MjpegView
import com.trikset.gamepad2.mjpeg.ScaleMode
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MjpegVideoPlayer(
    private val view: MjpegView,
    injectedExecutor: Executor? = null,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    private val socketBinder: SocketBinder = WifiSocketBinder(),
    private val connectionOpener: ConnectionOpener = WifiConnectionOpener(),
) : VideoPlayer {

  /**
   * The executor this player runs its open/play work on (own one unless the caller injected it).
   */
  private val executor: Executor = injectedExecutor ?: Executors.newSingleThreadExecutor()

  /** Whether [release] must shut down [executor]; a caller-injected executor stays caller-owned. */
  private val ownsExecutor: Boolean = injectedExecutor == null

  override val isPlaying: Boolean
    get() = view.isPlaying

  override var showFps: Boolean
    get() = view.showFps
    set(value) {
      view.showFps = value
    }

  override var scaleMode: ScaleMode
    get() = view.scaleMode
    set(value) {
      view.scaleMode = value
    }

  private var pendingStreamErrorListener: (() -> Unit)? = null
  private var pendingFirstFrameListener: (() -> Unit)? = null

  override var onPlayResult: ((Boolean) -> Unit)? = null

  override fun play(url: String?) {
    executor.execute {
      val stream = openStream(url)
      mainHandler.post {
        view.stopPlayback()
        view.setSource(stream)
        view.startPlayback()
        onPlayResult?.invoke(stream != null)
      }
    }
  }

  override fun stop() {
    mainHandler.post {
      view.stopPlayback()
    }
  }

  override fun setOnStreamErrorListener(listener: (() -> Unit)?) {
    pendingStreamErrorListener = listener
    view.setOnStreamErrorListener(listener?.let { MjpegView.OnStreamErrorListener { it() } })
  }

  override fun setOnFirstFrameListener(listener: (() -> Unit)?) {
    pendingFirstFrameListener = listener
    view.setOnFirstFrameListener(listener?.let { MjpegView.OnFirstFrameListener { it() } })
  }

  override fun release() {
    setOnStreamErrorListener(null)
    setOnFirstFrameListener(null)
    view.stopPlayback()
    // Shut down only the executor this player created (an injected executor is owned by the
    // caller). The default single-thread executor is NON-daemon, so without the shutdown every
    // discarded player leaked its thread forever — players are recreated on each video-URI change
    // and on every activity recreation, so the leaks accumulated process-lifetime.
    if (ownsExecutor) {
      (executor as? ExecutorService)?.shutdown()
    }
  }

  internal fun openStream(url: String?): MjpegInputStream? {
    if (url == null) return null
    return try {
      val parsed = URI(url).toURL()
      val stream =
          if (parsed.protocol.equals("http", ignoreCase = true)) {
            RawSocketHttpStream.open(parsed, socketBinder)
          } else {
            val connection = connectionOpener.open(parsed) as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.inputStream
          }
      MjpegInputStream(stream)
    } catch (e: IOException) {
      AppLog.e(TAG, "Failed to open MJPEG stream.", e)
      null
    } catch (e: java.net.URISyntaxException) {
      AppLog.e(TAG, "Failed to parse MJPEG stream URL.", e)
      null
    }
  }

  private companion object {
    const val CONNECT_TIMEOUT_MS = 5000
    const val READ_TIMEOUT_MS = 5000
    const val TAG = "MjpegVideoPlayer"
  }
}
