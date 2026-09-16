package com.trikset.gamepad2.mjpeg

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.trikset.gamepad2.R
import com.trikset.gamepad2.diagnostics.AppLog
import java.io.IOException
import org.apache.commons.io.input.BoundedInputStream

/**
 * MJPEG player view. Frames are decoded on a background thread and presented in [onDraw], which
 * runs on the hardware-accelerated (HWUI) canvas — the center-crop scale is done by the GPU instead
 * of the CPU rasterizer (the old `SurfaceView` + `lockCanvas()` path was software, measured as the
 * Skia `lowp` pipeline at ~29% CPU in the 2026-08-17 on-device profile).
 *
 * The render thread is de-spun: it blocks on the socket read for real frames and sleeps briefly
 * after a dropped frame, so the buffer-full null fast-path cannot spin a core (the same profile
 * measured the property-accessor poll at ~36% CPU).
 *
 * Playback lifecycle is driven by the activity (onPause/onDestroy call [stopPlayback]) — a plain
 * view has no surface-destroyed signal to hang this on.
 */
class MjpegView : View {

  /** Invoked from the render thread when the MJPEG stream fails (e.g. socket closed). */
  fun interface OnStreamErrorListener {
    fun onStreamError()
  }

  /** Invoked from the render thread once per playback cycle when the first frame is decoded. */
  fun interface OnFirstFrameListener {
    fun onFirstFrame()
  }

  private val fpsTextPaint = Paint()
  /** When false (default) the FPS overlay is skipped; read in [onDraw] (UI thread). */
  @Volatile var showFps: Boolean = false
  var scaleMode: ScaleMode
    get() = renderer.scaleMode
    set(value) {
      renderer.scaleMode = value
    }

  private var viewThread: MjpegViewThread? = null
  @Volatile private var input: MjpegInputStream? = null
  @Volatile private var running = false
  @Volatile private var stopping = false
  @Volatile private var dispWidth = 0
  @Volatile private var dispHeight = 0
  private var onStreamErrorListener: OnStreamErrorListener? = null
  private var onFirstFrameListener: OnFirstFrameListener? = null
  @Volatile private var frameReported = false

  private val renderer = MjpegFrameRenderer()

  // Reused until the first frame decodes (lint DrawAllocation: no allocation per draw).
  private val fallbackDestRect = Rect()

  constructor(context: Context) : super(context) {
    init()
  }

  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
    init()
  }

  constructor(
      context: Context,
      attrs: AttributeSet?,
      defStyle: Int,
  ) : super(context, attrs, defStyle) {
    init()
  }

  fun setOnStreamErrorListener(listener: OnStreamErrorListener?) {
    onStreamErrorListener = listener
  }

  fun setOnFirstFrameListener(listener: OnFirstFrameListener?) {
    onFirstFrameListener = listener
    frameReported = false
  }

  private fun init() {
    viewThread = MjpegViewThread()
    // Decorative video surface: never focusable (the XML sets focusable=false; the code must not
    // contradict it, or TalkBack/keyboard navigation stop on a surface with no interaction).
    isFocusable = false
    isFocusableInTouchMode = false
    fpsTextPaint.textAlign = Paint.Align.RIGHT
    fpsTextPaint.textSize = FPS_TEXT_SIZE
    fpsTextPaint.typeface = Typeface.DEFAULT
    fpsTextPaint.color = ContextCompat.getColor(context, R.color.hud_accent_connected)
    dispWidth = width
    dispHeight = height
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    dispWidth = w
    dispHeight = h
  }

  fun setSource(source: MjpegInputStream?) {
    input = source
  }

  /** True while the render thread is consuming frames (started and not stopped). */
  val isPlaying: Boolean
    get() = running

  @Synchronized
  fun startPlayback() {
    if (input != null) {
      running = true
      frameReported = false
      viewThread?.start()
    }
  }

  @Synchronized
  fun stopPlayback() {
    if (running) {
      running = false
      stopping = true
      // The render thread may be blocked in a non-interruptible
      // InputStream.read inside readMjpegFrame(); close() from this thread
      // unblocks it (the documented unblock pattern for a blocked read) so
      // the join below returns instead of timing out and leaking the thread
      // and its HTTP connection across pause/resume cycles.
      val current = input
      if (current != null) {
        try {
          current.close()
        } catch (e: IOException) {
          AppLog.e(TAG, "Failed to close MJPEG stream on stop", e)
        }
      }
      viewThread?.join()
      stopping = false
    }
  }

  /** GPU-backed presentation: the decoded frame is scaled by HWUI on this canvas. */
  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val rect = renderer.lastDestRect ?: fallbackDestRect.also { it.set(0, 0, width, height) }
    renderer.drawFrame(canvas, rect, width, fpsTextPaint, showFps)
  }

  private companion object {
    const val FPS_TEXT_SIZE = 12f
    const val JOIN_TIMEOUT_MS = 3000L
    // After a dropped frame (readMjpegFrame() null fast-path) idle briefly so a
    // full socket buffer cannot spin the loop (measured: property-accessor spin).
    const val FRAME_IDLE_SLEEP_MS = 5L
    const val TAG = "MjpegView"
  }

  private inner class MjpegViewThread {
    private var thread: MjpegRenderThread? = null

    fun join() {
      val current = thread ?: return
      var retry = true
      while (retry) {
        try {
          current.join(JOIN_TIMEOUT_MS)
          retry = false
        } catch (e: InterruptedException) {
          AppLog.e(TAG, "Render thread join interrupted", e)
        }
      }
      thread = null
    }

    fun start() {
      join()
      thread = MjpegRenderThread()
      thread?.start()
    }
  }

  private inner class MjpegRenderThread : Thread() {
    override fun run() {
      renderer.onRenderStarted(System.currentTimeMillis())
      while (running) {
        renderNextFrame()
      }
    }

    @Suppress("SwallowedException") // a broken stream stops the loop and reports the error
    private fun renderNextFrame() {
      var frame: BoundedInputStream? = null
      try {
        val stream = input?.readMjpegFrame()
        if (stream == null) {
          // Fast null return (buffer-full frame drop or EOF recovery): don't
          // spin the loop — let the socket buffer catch up.
          Thread.sleep(FRAME_IDLE_SLEEP_MS)
          return
        }
        frame = stream
        val destRect = renderer.extractFrame(stream, dispWidth, dispHeight)
        if (destRect != null) {
          // A frame is decoded: report the first one of this playback cycle
          // (the loading indicator hides here, before the draw).
          if (!frameReported) {
            frameReported = true
            onFirstFrameListener?.onFirstFrame()
          }
          renderer.recordFrame()
          // Present on the next UI render pass (GPU-backed onDraw).
          postInvalidate()
        }
      } catch (e: IOException) {
        running = false
        if (!stopping) {
          onStreamErrorListener?.onStreamError()
        }
      } finally {
        if (frame != null) {
          try {
            frame.close()
          } catch (e: IOException) {
            running = false
          }
        }
      }
    }
  }
}
