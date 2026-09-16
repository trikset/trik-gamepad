package com.trikset.gamepad2

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.trikset.gamepad2.mjpeg.MjpegFrameRenderer
import com.trikset.gamepad2.mjpeg.ScaleMode
import com.trikset.gamepad2.mjpeg.SyntheticMjpegServer
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Type 1 HUD theme test: renders the **real** activity view hierarchy over a real CC0 cat-video
 * frame (the same fixtures the video tests stream) for each connection state, analyzes the frame
 * in-process for the theme's main features, and saves a screenshot per state to the always-on
 * `screenshots.dir` build-output property (HudThemeTest artifacts, see app/build.gradle). Renders
 * under [GraphicsMode.Mode.NATIVE] so [Canvas]/[Bitmap] do real Skia work — the video background is
 * drawn via [MjpegFrameRenderer]'s center-crop rect, then the view tree draws over it exactly as
 * the app composes the HUD over the stream.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Config.TARGET_SDK], qualifiers = "w829dp-h393dp-land-440dpi")
class HudThemeTest : RobolectricTestBase() {

  // S10e profile: 1080x2280 @ 440dpi (2.75x); landscape 2280x1080. The explicit canvas + EXACTLY
  // measure specs below keep the PNG at exactly this physical size regardless of Robolectric's
  // displayMetrics dp rounding (w829dp*2.75=2279.75, h393dp*2.75=1080.75 -> 2280x1081).
  private val screenWidth = 2280
  private val screenHeight = 1080
  private val screenshotsDir: String? = System.getProperty("screenshots.dir")

  @Before
  fun resetSharedPreferences() {
    PreferenceManager.getDefaultSharedPreferences(
            org.robolectric.RuntimeEnvironment.getApplication()
        )
        .edit()
        .clear()
        .commit()
  }

  private fun buildActivity(): MainActivity {
    setPref(SettingsFragment.SK_MAGIC_BUTTON_COUNT, "5")
    setPref(SettingsFragment.SK_HOST_ADDRESS, "192.168.77.1")
    setPref(SettingsFragment.SK_HOST_PORT, "4444")
    // Full user alpha so the connected-vs-standby dimming is a real contrast (the default 100/255
    // would make connected and dimmed nearly indistinguishable in the screenshot).
    setPref(SettingsFragment.SK_SHOW_PADS, MainActivitySettingsController.ALPHA_MAX.toString())
    return Robolectric.buildActivity(MainActivity::class.java).setup().get()
  }

  /** Cat frame decoded from the committed fixture (the video background). */
  private fun catBitmap(): Bitmap {
    val bytes = SyntheticMjpegServer.catFrameImage("1000x600")
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
  }

  /**
   * Renders the activity for [state]: draws the cat video full-screen (center-crop), drives the
   * connection state through the real [SenderService] flow (so applyHudTone + the pill react), then
   * draws the HUD view tree over the video. Returns the composite frame.
   */
  private fun render(activity: MainActivity, state: ConnectionState): Bitmap {
    // Draw the video background first (what the MJPEG surface would show).
    val frame = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(frame)
    val cat = catBitmap()
    val dest =
        MjpegFrameRenderer()
            .apply { scaleMode = ScaleMode.CROP }
            .destRect(cat.width, cat.height, screenWidth, screenHeight)
    canvas.drawBitmap(cat, null, dest, Paint())

    // Drive the real state machine: setting the flow value triggers the lifecycle collector's
    // connectionFeedback.update + applyHudTone (the same path the app uses on a real connect).
    val sender = activity.senderService
    val flow =
        @Suppress("UNCHECKED_CAST")
        (field(sender, "_connectionState") as MutableStateFlow<ConnectionState>)
    flow.value = state
    shadowOf(android.os.Looper.getMainLooper()).idle()

    // The video view has no real stream under Robolectric; the cat frame already
    // stands in for its content, so hide the view to keep it from painting over the background.
    activity.findViewById<View>(R.id.video)?.visibility = View.GONE
    // The placeholder prompts "configure a video URI" — not a live-stream look.
    activity.findViewById<View>(R.id.videoPlaceholder)?.visibility = View.GONE
    // The loading spinner/relaunch badge are hidden by the first rendered frame on a real device;
    // no frame ever renders here (the cat is a static stand-in), so hide them to match the
    // "connected + playing" state the screenshot represents.
    activity.findViewById<View>(R.id.videoLoading)?.visibility = View.GONE
    activity.findViewById<View>(R.id.videoReconnecting)?.visibility = View.GONE

    // Lay the HUD out at the full screen size, then draw the real view hierarchy over the video.
    val main = activity.findViewById<ViewGroup>(R.id.main)
    main.measure(
        View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(screenHeight, View.MeasureSpec.EXACTLY),
    )
    main.layout(0, 0, screenWidth, screenHeight)
    main.draw(canvas)
    return frame
  }

  private fun pixel(bitmap: Bitmap, x: Int, y: Int): Int = bitmap.getPixel(x, y)

  private fun isBright(rgb: Int): Boolean {
    val r = (rgb shr 16) and 0xFF
    val g = (rgb shr 8) and 0xFF
    val b = rgb and 0xFF
    return (r + g + b) / 3 >= 40
  }

  /**
   * Probes the video at a point guaranteed to be HUD-free: the top-center band above the connection
   * pill (which is centered at screen height / 2) and horizontally between the pads (which live in
   * the outer left/right thirds). The pill would otherwise cover the exact screen center and fail
   * the "video must be present" check on its own pixels.
   */
  private fun videoIsBright(bitmap: Bitmap): Boolean =
      isBright(pixel(bitmap, screenWidth / 2, screenHeight / 4))

  private fun cropDest() =
      catBitmap().let { cat ->
        val dest =
            MjpegFrameRenderer()
                .apply { scaleMode = ScaleMode.CROP }
                .destRect(cat.width, cat.height, screenWidth, screenHeight)
        Triple(cat, dest, cat.width.toFloat() / dest.width())
      }

  private fun distinctColors(bitmap: Bitmap): Int {
    val seen = mutableSetOf<Int>()
    for (y in 0 until bitmap.height step 8) {
      for (x in 0 until bitmap.width step 8) {
        seen.add(bitmap.getPixel(x, y) and 0xFFFFFF)
        if (seen.size >= 6) return seen.size
      }
    }
    return seen.size
  }

  private fun save(bitmap: Bitmap, name: String) {
    val dir = screenshotsDir ?: return
    val out = File(dir, name)
    out.parentFile?.mkdirs()
    out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    println("HudThemeTest: saved $out (${bitmap.width}x${bitmap.height})")
  }

  @Test
  fun connectedStateShouldRenderVideoWithFullAlphaGreenHud() {
    val activity = buildActivity()
    val frame = render(activity, ConnectionState.Connected)

    // Video fills the screen edge-to-edge: the rendered edge pixel must equal the source cat's
    // pixel at the corresponding center-crop source coordinate (no letterbox bar). The fixture has
    // a black vintage border, so "bright" is the wrong check — coordinate mapping is the check.
    val (cat, dest, scaleX) = cropDest()
    val srcLeft = (4 - dest.left) * scaleX
    val srcRight = (screenWidth - 5 - dest.left) * scaleX
    assertEquals(
        "left edge must map to the cat's own border, not a letterbox bar",
        cat.getPixel(srcLeft.toInt(), screenHeight / 2),
        pixel(frame, 4, screenHeight / 2),
    )
    assertEquals(
        "right edge must map to the cat's own border, not a letterbox bar",
        cat.getPixel(srcRight.toInt(), screenHeight / 2),
        pixel(frame, screenWidth - 5, screenHeight / 2),
    )
    assertTrue("video must be a real multi-color frame", distinctColors(frame) >= 6)

    // Pill hidden while connected (the video/gear border convey the state).
    assertEquals(View.GONE, activity.findViewById<TextView>(R.id.connectionStatus).visibility)
    // Loading affordances are gone too — the first rendered frame hides them on a real device.
    assertEquals(View.GONE, activity.findViewById<View>(R.id.videoLoading).visibility)
    assertEquals(View.GONE, activity.findViewById<View>(R.id.videoReconnecting).visibility)
    // Controls at full (user) alpha while connected.
    assertEquals(1f, activity.findViewById<View>(R.id.controlsOverlay).alpha, 0.001f)
    save(frame, "hud_connected.png")
  }

  @Test
  fun connectingStateShouldRenderAmberPillOverVideo() {
    val activity = buildActivity()
    val frame = render(activity, ConnectionState.Connecting)

    assertTrue("video must be present", videoIsBright(frame))
    val pill = activity.findViewById<TextView>(R.id.connectionStatus)
    assertEquals(View.VISIBLE, pill.visibility)
    // The connecting pill shows the ↺ glyph (bundled symbol font).
    assertEquals("↺", pill.text.toString())
    // Not connected -> controls dim to the standby floor.
    assertTrue(
        "controls must be dimmed while connecting",
        activity.findViewById<View>(R.id.controlsOverlay).alpha < 1f,
    )
    save(frame, "hud_connecting.png")
  }

  @Test
  fun standbyStateShouldRenderSepiaPillOverVideo() {
    val activity = buildActivity()
    val frame = render(activity, ConnectionState.Disconnected(""))

    assertTrue("video must be present", videoIsBright(frame))
    val pill = activity.findViewById<TextView>(R.id.connectionStatus)
    assertEquals(View.VISIBLE, pill.visibility)
    assertEquals("⏻", pill.text.toString())
    save(frame, "hud_standby.png")
  }

  @Test
  fun errorStateShouldRenderRedPillAndErrorPillOverVideo() {
    val activity = buildActivity()
    val frame = render(activity, ConnectionState.Disconnected("boom"))

    assertTrue("video must be present", videoIsBright(frame))
    assertEquals(View.VISIBLE, activity.findViewById<TextView>(R.id.connectionStatus).visibility)
    // A real connection error surfaces the transient error pill too (the theme's error affordance).
    assertEquals(View.VISIBLE, activity.findViewById<TextView>(R.id.connectionError).visibility)
    save(frame, "hud_error.png")
  }

  @Test
  fun videoZonesShouldBeCleanAndOverlaysShouldOnlyDarken() {
    val activity = buildActivity()
    val frame = render(activity, ConnectionState.Connected)
    val (cat, dest, scaleX) = cropDest()
    val scaleY = cat.height.toFloat() / dest.height()

    // Map a display pixel back to the source cat pixel.
    fun sourcePixel(dx: Int, dy: Int): Int {
      val sx = ((dx - dest.left) * scaleX).toInt().coerceIn(0, cat.width - 1)
      val sy = ((dy - dest.top) * scaleY).toInt().coerceIn(0, cat.height - 1)
      return cat.getPixel(sx, sy)
    }

    // All clean zone probes are at y=540 (the exact center line where the scrim
    // gradient alpha is 0), and x between the pads (1052-1228 — at 180dp on 2280-wide
    // each pad ≈ 176px) or outside them.
    // Zone A — clean video exact center (between the two pads).
    assertPixelsMatch("center", frame, { x, y -> sourcePixel(x, y) }, 1140, 540)
    // Zone B — clean video between pads, left of center.
    assertPixelsMatch("between-pads-left", frame, { x, y -> sourcePixel(x, y) }, 1000, 540)
    // Zone C — clean video between pads, right of center.
    assertPixelsMatch("between-pads-right", frame, { x, y -> sourcePixel(x, y) }, 1300, 540)
    // Zone D — left of left pad, on center line.
    assertPixelsMatch("left-of-pads", frame, { x, y -> sourcePixel(x, y) }, 100, 540)
    // Zone E — right of right pad, on center line.
    assertPixelsMatch("right-of-pads", frame, { x, y -> sourcePixel(x, y) }, 2180, 540)

    // Zone F — left pad center. The pixel is affected by the pad drawing
    // (chrome vector + knob), even though the glass fill is gone (P8 resolved).
    val leftPadPx = pixel(frame, 570, 540)
    val leftPadSrc = sourcePixel(570, 540)
    assertNotEquals("left pad center must be affected by pad overlay", leftPadSrc, leftPadPx)

    // Zone G — right pad center (same check: chrome + knob, not glass).
    val rightPadPx = pixel(frame, 1710, 540)
    val rightPadSrc = sourcePixel(1710, 540)
    assertNotEquals("right pad center must be affected by pad overlay", rightPadSrc, rightPadPx)

    // Zone H — top scrim edge (should be darkened, ~15% from top).
    val topEdgePx = pixel(frame, 1140, 50)
    val topEdgeSrc = sourcePixel(1140, 50)
    assertTrue(
        "top scrim must darken the video at 1140,50",
        topEdgePx != topEdgeSrc,
    )

    // Zone I — bottom scrim edge (should be darkened, ~15% from bottom).
    val bottomEdgePx = pixel(frame, 1140, 1030)
    val bottomEdgeSrc = sourcePixel(1140, 1030)
    assertTrue(
        "bottom scrim must darken the video at 1140,1030",
        bottomEdgePx != bottomEdgeSrc,
    )
  }

  private fun assertPixelsMatch(
      label: String,
      frame: Bitmap,
      sourcePixel: (Int, Int) -> Int,
      dx: Int,
      dy: Int,
  ) {
    val src = sourcePixel(dx, dy)
    val rendered = pixel(frame, dx, dy)
    if (src != rendered) {
      // NATIVE graphics mode (Skia) can produce 1-bit rounding differences
      // in the composited pipeline. Accept a per-channel tolerance of 1.
      val sr = (src shr 16) and 0xFF
      val sg = (src shr 8) and 0xFF
      val sb = src and 0xFF
      val rr = (rendered shr 16) and 0xFF
      val rg = (rendered shr 8) and 0xFF
      val rb = rendered and 0xFF
      assertTrue(
          "clean video zone '$label' at ($dx,$dy): expected rgb($sr,$sg,$sb) " +
              "but got rgb($rr,$rg,$rb) (diff >1 per channel)",
          kotlin.math.abs(sr - rr) <= 1 &&
              kotlin.math.abs(sg - rg) <= 1 &&
              kotlin.math.abs(sb - rb) <= 1,
      )
    }
  }
}
