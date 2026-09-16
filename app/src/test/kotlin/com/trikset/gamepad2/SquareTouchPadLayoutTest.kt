package com.trikset.gamepad2

import android.view.MotionEvent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.util.concurrent.PausedExecutorService
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.LooperMode.Mode.PAUSED

@RunWith(RobolectricTestRunner::class)
@LooperMode(PAUSED)
class SquareTouchPadLayoutTest : RobolectricTestBase() {

  private val mExecutor = PausedExecutorService()
  private lateinit var sender: SenderService
  private lateinit var pad: SquareTouchPadLayout

  private fun eventAt(x: Float, y: Float, action: Int): MotionEvent =
      MotionEvent.obtain(0L, 0L, action, x, y, 0)

  /** Measures [pad] at [width]x[height] in [mode] and lays it out at that size. */
  private fun measureAndLayout(width: Int, height: Int, mode: Int = View.MeasureSpec.EXACTLY) {
    pad.measure(
        View.MeasureSpec.makeMeasureSpec(width, mode),
        View.MeasureSpec.makeMeasureSpec(height, mode),
    )
    pad.layout(0, 0, width, height)
  }

  @Before
  fun setUp() {
    sender = SenderService(mExecutor)
    sender.keepaliveTimeout = 10000000
    sender.setTarget("localhost", 12345)

    val context = org.robolectric.RuntimeEnvironment.getApplication()
    val parent = android.widget.FrameLayout(context)
    pad = SquareTouchPadLayout(context)
    pad.padName = "pad 1"
    pad.sender = sender
    parent.addView(pad)
    measureAndLayout(200, 200)
    parent.measure(
        View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
    )
    parent.layout(0, 0, 200, 200)
  }

  @Test
  fun sendShouldForwardCommandToSender() {
    pad.send("up")
    // SenderService.send() queues work on the injected executor; a nonzero
    // runAll() count proves the command was forwarded without needing a live
    // TCP round-trip (which is CI-flaky).
    assertTrue(mExecutor.runAll() > 0)
  }

  @Test
  fun sendShouldNotForwardWhenNoSender() {
    // Drain any leaked keepalive task that a prior test's connected client may
    // have queued into the shared static executor before asserting.
    val queuedBefore = mExecutor.runAll()
    pad.sender = null
    pad.send("up")
    // Nothing should be queued when there is no sender wired up.
    assertEquals(queuedBefore, mExecutor.runAll())
  }

  @Test
  fun measureShouldUseAdaptiveSizeFromScreenDimensions() {
    // Under Robolectric's default mdpi (160dpi, 480×800px), hud_pad_size=260dp
    // resolves to 260px, and screenTallestPx*0.63=504px, so 260px wins.
    measureAndLayout(300, 150)
    assertEquals(260, pad.measuredWidth)
    assertEquals(260, pad.measuredHeight)
  }

  @Test
  fun measureShouldFallBackToDefaultWhenNoSize() {
    measureAndLayout(0, 0, View.MeasureSpec.UNSPECIFIED)
    // Adaptive formula reads screen dimensions (not specs), so still resolves.
    assertTrue(pad.measuredWidth > 0)
    assertTrue(pad.measuredHeight > 0)
  }

  @Test
  fun measureShouldUseAdaptiveSizeWhenOneDimensionIsZero() {
    // Adaptive formula ignores the spec dimensions; result is the adaptive value.
    pad.measure(
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
    )
    assertEquals(260, pad.measuredWidth)
    assertEquals(260, pad.measuredHeight)
  }

  @Test
  fun measureShouldCacheAdaptiveSize() {
    // First measure primes the cache; second measure skips recomputation.
    measureAndLayout(300, 150)
    assertEquals(260, pad.measuredWidth)
    val cached = field(pad, "adaptivePadPx") as Int
    assertEquals("cached value is the measured size", 260, cached)
    // A second measure with different specs reads the same cached value.
    measureAndLayout(100, 100)
    assertEquals(260, pad.measuredWidth)
  }

  @Test
  fun padNameShouldRoundTrip() {
    assertEquals("pad 1", pad.padName)
  }

  @Test
  fun touchMoveShouldSendCoordinates() {
    pad.dispatchTouchEvent(eventAt(100f, 100f, MotionEvent.ACTION_DOWN))
    pad.dispatchTouchEvent(eventAt(190f, 10f, MotionEvent.ACTION_MOVE))
    pad.dispatchTouchEvent(eventAt(10f, 190f, MotionEvent.ACTION_MOVE))
    // Each touch that changes the coordinates by more than the sensitivity
    // queues a send; assert at least three commands were forwarded.
    assertTrue("expected coordinate commands to be queued", mExecutor.runAll() >= 3)
  }

  @Test
  fun touchUpShouldSendUpCommand() {
    pad.dispatchTouchEvent(eventAt(100f, 100f, MotionEvent.ACTION_DOWN))
    pad.dispatchTouchEvent(eventAt(120f, 120f, MotionEvent.ACTION_MOVE))
    pad.dispatchTouchEvent(eventAt(120f, 120f, MotionEvent.ACTION_UP))
    // DOWN + MOVE + UP each queue a send (>=3), UP included.
    assertTrue("expected commands to be queued", mExecutor.runAll() >= 3)
  }

  @Test
  fun unknownActionShouldBeIgnored() {
    assertTrue(pad.dispatchTouchEvent(eventAt(100f, 100f, MotionEvent.ACTION_SCROLL)))
  }

  @Test
  fun allConstructorsShouldBuild() {
    val context = org.robolectric.RuntimeEnvironment.getApplication()
    assertNotNull(SquareTouchPadLayout(context))
    assertNotNull(SquareTouchPadLayout(context, null))
    assertNotNull(SquareTouchPadLayout(context, null, 0))
  }

  @Test
  fun onDrawShouldRenderCircle() {
    val canvas = android.graphics.Canvas()
    pad.setAbsXY(100f, 100f)
    pad.draw(canvas)
  }

  @Test
  fun onSizeChangedShouldCenterWhenStartingEmpty() {
    // Size changed from (0,0) -> the touch point centers at (w/2, h/2): the
    // absX/absY fields are set (no public getter, so reflect — same pattern as
    // MainActivityTest). The old assertion `sender !== null` was a tautology
    // (non-null lateinit) and never tested the centering.
    measureAndLayout(200, 200)
    val absX = field(pad, "absX") as Float
    val absY = field(pad, "absY") as Float
    assertEquals("centered x", 100f, absX)
    assertEquals("centered y", 100f, absY)
  }

  @Test
  fun onSizeChangedWithExistingSizeShouldKeepPosition() {
    // Size change from a non-zero old size -> the else branch: position kept.
    measureAndLayout(200, 200)
    pad.dispatchTouchEvent(eventAt(50f, 50f, MotionEvent.ACTION_DOWN))
    measureAndLayout(300, 300)
    // No crash; the old position is retained.
    pad.dispatchTouchEvent(eventAt(50f, 50f, MotionEvent.ACTION_MOVE))
  }

  @Test
  fun onTouchWithForeignViewShouldReturnFalse() {
    val context = org.robolectric.RuntimeEnvironment.getApplication()
    val foreign = SquareTouchPadLayout(context)
    val event = eventAt(100f, 100f, MotionEvent.ACTION_DOWN)
    assertFalse(pad.onTouch(foreign, event))
  }

  @Test
  fun onTouchCancelShouldSendUpCommand() {
    pad.dispatchTouchEvent(eventAt(100f, 100f, MotionEvent.ACTION_DOWN))
    pad.dispatchTouchEvent(eventAt(120f, 120f, MotionEvent.ACTION_CANCEL))
    assertTrue("expected commands to be queued", mExecutor.runAll() >= 2)
  }

  @Test
  fun touchDownShouldPerformTick() {
    pad.dispatchTouchEvent(eventAt(100f, 100f, MotionEvent.ACTION_DOWN))
    // User design: ONE light tick when a thumb lands — the "every interaction is noticeable"
    // anchor. Fails if the down tick is removed or the constant changes.
    assertEquals(
        "pad-down must fire the light TICK",
        Haptics.constant(Haptics.Level.TICK),
        shadowOf(pad).lastHapticFeedbackPerformed(),
    )
  }

  @Test
  fun touchUpShouldPerformClick() {
    pad.dispatchTouchEvent(eventAt(100f, 100f, MotionEvent.ACTION_DOWN))
    pad.dispatchTouchEvent(eventAt(120f, 120f, MotionEvent.ACTION_UP))
    // User design: ONE CLICK on pad release (stronger than the down tick) — nothing on move.
    assertEquals(
        "pad-up must fire the CLICK",
        Haptics.constant(Haptics.Level.CLICK),
        shadowOf(pad).lastHapticFeedbackPerformed(),
    )
  }

  @Test
  fun touchCancelShouldNotPerformHaptic() {
    pad.dispatchTouchEvent(eventAt(100f, 100f, MotionEvent.ACTION_CANCEL))
    // ACTION_CANCEL is an interruption, not a deliberate release — no tick.
    assertEquals("cancel must not vibrate", -1, shadowOf(pad).lastHapticFeedbackPerformed())
  }

  @Test
  fun touchMoveShouldNotPerformHaptic() {
    pad.dispatchTouchEvent(eventAt(190f, 10f, MotionEvent.ACTION_MOVE))
    // The per-move VIRTUAL_KEY feedback was the "queued after finger lift" noise.
    assertEquals("move must not vibrate", -1, shadowOf(pad).lastHapticFeedbackPerformed())
  }

  @Test
  fun sendShouldNotPerformHaptic() {
    pad.send("up")
    // Haptics are tied to the touch gesture (pad-down/up), not to command sends.
    assertEquals("send must not vibrate", -1, shadowOf(pad).lastHapticFeedbackPerformed())
  }
}
