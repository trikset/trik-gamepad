package com.trikset.gamepad2.mjpeg

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.trikset.gamepad2.diagnostics.AppLog
import java.io.InputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import org.apache.commons.io.input.BoundedInputStream

private const val FRAME_TEMP_STORAGE_BYTES = 100000
private const val FPS_WINDOW_MS = 5000

private fun defaultMjpegDecoder(stream: InputStream, opts: BitmapFactory.Options): Bitmap? =
    BitmapFactory.decodeStream(stream, null, opts)

/**
 * Device-independent render logic for the MJPEG player: decodes a JPEG frame, computes the
 * center-crop destination rectangle (the frame covers the display, cropping any aspect mismatch
 * from the center) and draws it with the FPS overlay. Kept separate from [MjpegView]'s render
 * thread so it is testable under Robolectric (inject a decoder and a plain [Canvas]); the renderer
 * owns the reusable bitmap so decoding can reuse it across frames.
 */
class MjpegFrameRenderer(
    private val decoder: (InputStream, BitmapFactory.Options) -> Bitmap? = ::defaultMjpegDecoder,
    private val tempStorage: ByteArray = ByteArray(FRAME_TEMP_STORAGE_BYTES),
) {

  // Read by the view's onDraw (UI render pass) after the render thread decodes: @Volatile for
  // cross-thread visibility of the latest frame + its center-crop rectangle.
  @Volatile private var bitmap: Bitmap? = null
  /** The most recent frame's destination rectangle, for the view's [android.view.View.onDraw]. */
  @Volatile var lastDestRect: Rect? = null
  // Source size of the last decoded frame (pre-downsample), to derive the next frame's sample.
  private var sourceFrameWidth = 0
  private var sourceFrameHeight = 0
  private var frameCounter = 0
  private var startTimeMs = 0L
  @Volatile private var fpsString = ""
  @Volatile var scaleMode = ScaleMode.FIT
  private var lastShownFps = -1
  private val fpsPillPaint =
      Paint().apply {
        color = 0x88000000.toInt()
        style = Paint.Style.FILL
        isAntiAlias = true
      }

  private companion object {
    const val TAG = "MjpegFrameRenderer"
    const val MILLIS_PER_SECOND = 1000.0f
    const val FPS_PILL_PAD = 4f
    const val FPS_PILL_CORNER = 6f
  }

  /**
   * Center-crops a [bitmapWidth]x[bitmapHeight] frame to cover the display area (a camera feed
   * fills the whole screen edge-to-edge; the crop removes the aspect mismatch from the center). The
   * returned [Rect] may sit partially outside the display — the canvas clips the overflow.
   */
  fun destRect(bitmapWidth: Int, bitmapHeight: Int, dispWidth: Int, dispHeight: Int): Rect {
    val scale =
        when (scaleMode) {
          ScaleMode.FIT ->
              minOf(dispWidth.toFloat() / bitmapWidth, dispHeight.toFloat() / bitmapHeight)
          ScaleMode.CROP ->
              maxOf(dispWidth.toFloat() / bitmapWidth, dispHeight.toFloat() / bitmapHeight)
        }
    val bmw = (bitmapWidth * scale).toInt()
    val bmh = (bitmapHeight * scale).toInt()
    val tempX = dispWidth / 2 - bmw / 2
    val tempY = dispHeight / 2 - bmh / 2
    return Rect(tempX, tempY, bmw + tempX, bmh + tempY)
  }

  fun onRenderStarted(startTimeMs: Long) {
    this.startTimeMs = startTimeMs
  }

  /**
   * Decodes [frame] into the renderer's reusable bitmap and returns the destination rectangle for
   * the current display size, or null when the frame cannot be decoded.
   */
  @Suppress("SwallowedException") // a decode failure just means "skip this frame"
  fun extractFrame(frame: BoundedInputStream, dispWidth: Int, dispHeight: Int): Rect? {
    val opts =
        BitmapFactory.Options().apply {
          // Decode at the smallest power-of-2 sample whose result still covers the display, so a
          // camera larger than the screen is not fully decoded into memory every frame (the source
          // size is known from the previous frame; the first frame decodes at full size).
          inSampleSize = sampleSize(sourceFrameWidth, sourceFrameHeight, dispWidth, dispHeight)
          inBitmap = bitmap // reuse if possible
          inMutable = true
          inTempStorage = tempStorage
        }
    val decoded: Bitmap? =
        try {
          decoder(frame, opts)
        } catch (e: IllegalArgumentException) {
          null
        }
    if (decoded == null) return null

    val previous = bitmap
    if (previous != null && decoded !== previous) {
      // Was not reused
      previous.recycle()
      AppLog.v(TAG, "Bitmap was not reused, recycled.")
    }
    bitmap = decoded
    // Record the frame's source (pre-sample) size once, for the next frame's inSampleSize.
    if (sourceFrameWidth == 0) {
      sourceFrameWidth = decoded.width * opts.inSampleSize
      sourceFrameHeight = decoded.height * opts.inSampleSize
    }
    return destRect(decoded.width, decoded.height, dispWidth, dispHeight).also { lastDestRect = it }
  }

  /**
   * The power-of-2 `inSampleSize` that still decodes a frame covering [dispWidth]x[dispHeight].
   * Unknown source size (first frame) or a source no larger than the display → 1 (no sampling).
   */
  private fun sampleSize(srcWidth: Int, srcHeight: Int, dispWidth: Int, dispHeight: Int): Int {
    // Unknown source (first frame) or unknown display (not yet laid out) -> no sampling.
    if (srcWidth <= 0 || srcHeight <= 0) return 1
    if (dispWidth <= 0 || dispHeight <= 0) return 1
    var sample = 1
    val halfW = srcWidth / 2
    val halfH = srcHeight / 2
    // The decoded size must stay >= the display (a crop, never an upscale).
    while (halfW / sample >= dispWidth && halfH / sample >= dispHeight) {
      sample *= 2
    }
    return sample
  }

  /**
   * Counts one decoded frame into the FPS window (called from the render thread per decoded frame,
   * so the overlay reflects the VIDEO rate — the draw happens at the UI vsync). Returns the current
   * fps string (may be empty until the first window elapses).
   */
  fun recordFrame(): String {
    frameCounter++
    val now = System.currentTimeMillis()
    val elapsedMs = now - startTimeMs
    if (elapsedMs >= FPS_WINDOW_MS) {
      startTimeMs = now
      val fps = MILLIS_PER_SECOND * frameCounter / FPS_WINDOW_MS
      frameCounter = 0
      val currentInt = fps.roundToInt()
      if (abs(currentInt - lastShownFps) >= 2 || lastShownFps < 0) {
        lastShownFps = currentInt
        fpsString = String.format(Locale.getDefault(), "%d", currentInt)
      }
    }
    return fpsString
  }

  /**
   * Draws the current bitmap into [destRect] (center-crop scale) plus the FPS overlay; returns the
   * fps string.
   */
  fun drawFrame(
      canvas: Canvas,
      destRect: Rect,
      dispWidth: Int,
      fpsTextPaint: Paint,
      showFps: Boolean = true,
  ): String {
    canvas.drawColor(Color.BLACK)
    val current = bitmap
    if (current != null) {
      canvas.drawBitmap(current, null, destRect, null)
    }
    if (showFps && fpsString.isNotEmpty()) {
      val textWidth = fpsTextPaint.measureText(fpsString)
      val textHeight = -fpsTextPaint.ascent() + fpsTextPaint.descent()
      val pillPad = FPS_PILL_PAD
      val pillRect =
          RectF(
              dispWidth - 1 - textWidth - pillPad * 2,
              0f,
              dispWidth - 1 + pillPad,
              textHeight + pillPad * 2,
          )
      canvas.drawRoundRect(pillRect, FPS_PILL_CORNER, FPS_PILL_CORNER, fpsPillPaint)
      canvas.drawText(
          fpsString,
          (dispWidth - 1).toFloat(),
          pillPad - fpsTextPaint.ascent(),
          fpsTextPaint,
      )
    }
    return fpsString
  }
}
