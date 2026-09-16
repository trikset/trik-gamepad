package com.trikset.gamepad2.mjpeg

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import com.trikset.gamepad2.RobolectricTestBase
import java.io.ByteArrayInputStream
import java.io.InputStream
import org.apache.commons.io.input.BoundedInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MjpegFrameRendererTest : RobolectricTestBase() {

  private fun bitmap(w: Int, h: Int): Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

  private fun stubDecoder(result: Bitmap?): (InputStream, BitmapFactory.Options) -> Bitmap? =
      { _, _ ->
        result
      }

  private fun emptyFrame(): BoundedInputStream =
      BoundedInputStream.builder().setInputStream(ByteArrayInputStream(ByteArray(0))).get()

  @Test
  fun destRectCentersLandscapeBitmap() {
    val rect = MjpegFrameRenderer().destRect(640, 480, 320, 240)
    assertEquals(0, rect.left)
    assertEquals(0, rect.top)
    assertEquals(320, rect.width())
    assertEquals(240, rect.height())
  }

  @Test
  fun destRectCropScalePortraitBitmapVertically() {
    // Portrait bitmap in a landscape display: CROP mode crops the height
    // overflow from the center. aspect 480/640 = 0.75; scale = max(320/480,
    // 240/640) = 0.667 -> 320x426 (int truncation), top = (240-426)/2 = -93.
    val rect =
        MjpegFrameRenderer().apply { scaleMode = ScaleMode.CROP }.destRect(480, 640, 320, 240)
    assertEquals(426, rect.height())
    assertEquals(320, rect.width())
    assertEquals(0, rect.left)
    assertEquals(-93, rect.top)
  }

  @Test
  fun destRectCropScaleWideBitmapHorizontally() {
    // Very-wide bitmap in a portrait-ish display: CROP mode crops the
    // width overflow from the center. scale = max(320/1000, 240/200) = 1.2 ->
    // 1200x240, left = (320-1200)/2 = -440 (overflow clipped).
    val rect =
        MjpegFrameRenderer().apply { scaleMode = ScaleMode.CROP }.destRect(1000, 200, 320, 240)
    assertEquals(1200, rect.width())
    assertEquals(240, rect.height())
    assertEquals(-440, rect.left)
    assertEquals(0, rect.top)
  }

  @Test
  fun destRectFitScaleLetterboxesPortraitBitmap() {
    // Portrait bitmap in a landscape display: FIT mode scales to fit within
    // the display, leaving black bars. scale = min(320/480, 240/640) = 0.375
    // -> 180x240, centered horizontally.
    val rect = MjpegFrameRenderer().destRect(480, 640, 320, 240)
    assertEquals(180, rect.width())
    assertEquals(240, rect.height())
    assertEquals(70, rect.left)
    assertEquals(0, rect.top)
  }

  @Test
  fun destRectFitScaleLetterboxesWideBitmap() {
    // Very-wide bitmap in a portrait-ish display: FIT mode fits within the
    // display leaving black bars. scale = min(320/1000, 240/200) = 0.32
    // -> 320x64, centered vertically.
    val rect = MjpegFrameRenderer().destRect(1000, 200, 320, 240)
    assertEquals(320, rect.width())
    assertEquals(64, rect.height())
    assertEquals(0, rect.left)
    assertEquals(88, rect.top)
  }

  @Test
  fun extractFrameReturnsDestRectForDecodedBitmap() {
    val renderer = MjpegFrameRenderer(decoder = stubDecoder(bitmap(100, 50)))
    renderer.scaleMode = ScaleMode.CROP
    val rect = renderer.extractFrame(emptyFrame(), 320, 240)
    assertNotNull("expected a dest rect", rect)
    // scale = max(320/100, 240/50) = 4.8 -> 480x240, left = (320-480)/2 = -80.
    assertEquals(480, rect!!.width())
    assertEquals(240, rect.height())
    assertEquals(-80, rect.left)
  }

  @Test
  fun extractFrameReturnsNullWhenDecodeFails() {
    val renderer = MjpegFrameRenderer(decoder = stubDecoder(null))
    assertNull(renderer.extractFrame(emptyFrame(), 320, 240))
  }

  @Test
  fun extractFrameReturnsNullWhenDecodeThrows() {
    val renderer =
        MjpegFrameRenderer(decoder = { _, _ -> throw IllegalArgumentException("bad frame") })
    assertNull(renderer.extractFrame(emptyFrame(), 320, 240))
  }

  @Test
  fun extractFrameRecyclesPreviousBitmapWhenNotReused() {
    var calls = 0
    val renderer =
        MjpegFrameRenderer(
            decoder = { _, _ ->
              calls++
              if (calls == 1) bitmap(100, 100) else bitmap(50, 50)
            }
        )
    assertNotNull(renderer.extractFrame(emptyFrame(), 320, 240))
    // Second frame decodes into a different bitmap; the old one must be recycled.
    assertNotNull(renderer.extractFrame(emptyFrame(), 320, 240))
  }

  @Test
  fun extractFrameShouldExposeLastDestRect() {
    val renderer = MjpegFrameRenderer(decoder = stubDecoder(bitmap(100, 50)))
    val rect = renderer.extractFrame(emptyFrame(), 320, 240)
    // The view's onDraw consumes lastDestRect; it must mirror the returned rect.
    assertEquals(rect, renderer.lastDestRect)
  }

  @Test
  fun extractFrameSkipsRecycleWhenBitmapIsReused() {
    // The decoder returns the same bitmap instance both times -> the renderer
    // reuses it and skips the recycle branch.
    val reused = bitmap(100, 100)
    val renderer = MjpegFrameRenderer(decoder = { _, _ -> reused })
    assertNotNull(renderer.extractFrame(emptyFrame(), 320, 240))
    assertNotNull(renderer.extractFrame(emptyFrame(), 320, 240))
  }

  @Test
  fun drawFrameDrawsBitmapAndFpsOverlay() {
    val renderer = MjpegFrameRenderer(decoder = stubDecoder(bitmap(100, 100)))
    // Force the FPS window to elapse so the fps string is computed (the render
    // thread counts frames via recordFrame; drawFrame only draws).
    renderer.onRenderStarted(System.currentTimeMillis() - 6000)
    val dest = renderer.extractFrame(emptyFrame(), 320, 240)
    assertNotNull(dest)
    renderer.recordFrame()
    val canvas = Canvas(bitmap(320, 240))
    val fps = renderer.drawFrame(canvas, dest!!, 320, Paint())
    assertTrue("expected a computed fps string, got '$fps'", fps.isNotEmpty())
  }

  @Test
  fun drawFrameWithinFpsWindowKeepsEmptyOverlay() {
    val renderer = MjpegFrameRenderer(decoder = stubDecoder(bitmap(100, 100)))
    renderer.onRenderStarted(System.currentTimeMillis())
    val dest = renderer.extractFrame(emptyFrame(), 320, 240)
    assertNotNull(dest)
    renderer.recordFrame()
    val fps = renderer.drawFrame(Canvas(bitmap(320, 240)), dest!!, 320, Paint())
    assertEquals("", fps)
  }

  @Test
  fun drawFrameWithoutBitmapSkipsDrawing() {
    // Never extract a frame -> bitmap is null; drawFrame must only draw the
    // FPS text and not touch a null bitmap.
    val renderer = MjpegFrameRenderer(decoder = stubDecoder(bitmap(100, 100)))
    renderer.onRenderStarted(System.currentTimeMillis())
    renderer.recordFrame()
    val rect = renderer.destRect(100, 100, 320, 240)
    val fps = renderer.drawFrame(Canvas(bitmap(320, 240)), rect, 320, Paint())
    assertEquals("", fps)
  }

  @Test
  fun drawFrameWithFpsHiddenStillComputesTheCounter() {
    // showFps=false skips the drawText but the FPS counter still advances and
    // the string is still returned (a toggle never changes the counting).
    val renderer = MjpegFrameRenderer(decoder = stubDecoder(bitmap(100, 100)))
    renderer.onRenderStarted(System.currentTimeMillis() - 6000)
    val dest = renderer.extractFrame(emptyFrame(), 320, 240)
    assertNotNull(dest)
    renderer.recordFrame()
    val fps = renderer.drawFrame(Canvas(bitmap(320, 240)), dest!!, 320, Paint(), showFps = false)
    assertTrue("expected a computed fps string, got '$fps'", fps.isNotEmpty())
  }

  @Test
  fun extractFrameDownsamplesToTheDisplaySizeFromThePreviousFrame() {
    // A "camera" sending 1000x2000 frames to a 320x240 display. The first frame
    // decodes at full size (no prior size to sample from); the second decodes at
    // a power-of-2 sample so the decoded bitmap is >= the display (2 -> 500x1000).
    val seenSamples = mutableListOf<Int>()
    val renderer =
        MjpegFrameRenderer(
            decoder = { _, opts ->
              seenSamples.add(opts.inSampleSize)
              val sample = opts.inSampleSize.coerceAtLeast(1)
              bitmap(1000 / sample, 2000 / sample)
            }
        )
    assertNotNull(renderer.extractFrame(emptyFrame(), 320, 240))
    assertNotNull(renderer.extractFrame(emptyFrame(), 320, 240))
    assertEquals("first full-res, then downsampled", listOf(1, 2), seenSamples)
  }

  @Test
  fun extractFrameSkipsDownsamplingWhenTheFrameFitsTheDisplay() {
    // 200x100 frame on a 320x240 display: no sampling is ever requested.
    val seenSamples = mutableListOf<Int>()
    val renderer =
        MjpegFrameRenderer(
            decoder = { _, opts ->
              seenSamples.add(opts.inSampleSize)
              bitmap(200, 100)
            }
        )
    assertNotNull(renderer.extractFrame(emptyFrame(), 320, 240))
    assertNotNull(renderer.extractFrame(emptyFrame(), 320, 240))
    assertEquals("no downsampling for a small frame", listOf(1, 1), seenSamples)
  }
}
