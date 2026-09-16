package com.trikset.gamepad2

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.RelativeLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.withTranslation
import com.trikset.gamepad2.diagnostics.AppLog
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class SquareTouchPadLayout : RelativeLayout {

  private val paint = Paint()
  private var absX = 0f
  private var absY = 0f
  var padName: String? = null
  var sender: SenderService? = null
  private var adaptivePadPx = -1
  private var maxX = 0f
  private var maxY = 0f
  private val touchPadController = TouchPadController()

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

  private fun init() {
    // Draw the pad indicator with the theme accent (was hardcoded RED).
    paint.color = ContextCompat.getColor(context, R.color.hud_accent_connected)
    paint.strokeWidth = 0f
    paint.style = Paint.Style.STROKE
    paint.alpha = OPAQUE_ALPHA
    setOnTouchListener(TouchPadListener())
    setWillNotDraw(false)
    isHapticFeedbackEnabled = true
  }

  /**
   * Recolors the Type 1 pad chrome (crosshair vector + mode glyph) and the touch dot to the given
   * connection-state accent (green/amber/sepia/red — see [ConnectionIndicator]). The pad glass
   * background and touch behavior are unchanged; tinting the chrome keeps the theme switchable
   * without code changes per theme.
   */
  fun setAccent(@androidx.annotation.ColorRes colorRes: Int) {
    val color = ContextCompat.getColor(context, colorRes)
    paint.color = color
    // The pad chrome is a single child ImageView tagged "padChrome" (crosshair rings, lines and
    // edge arrows in one tintable vector); one SRC_IN filter recolors it (alpha preserved, color
    // replaced). Tags are used (not ids) because both pads host the same tagged view in their own
    // subtrees.
    findViewWithTag<android.widget.ImageView>("padChrome")?.colorFilter =
        android.graphics.PorterDuffColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
    invalidate()
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    // Outer dashed ring (mockup style) — vector drawables cannot express dashes, so it is drawn
    // here, tinted by the same accent as the chrome/glyph.
    val center = maxX / 2f
    val effect = ringDashEffect ?: return
    val s = paint.style
    val w = paint.strokeWidth
    val sw = maxX * OUTER_RING_STROKE_RATIO
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = sw
    paint.pathEffect = effect
    canvas.drawCircle(center, center, maxX * OUTER_RING_RATIO, paint)
    paint.pathEffect = null
    paint.style = s
    paint.strokeWidth = w
    drawKnob(canvas)
  }

  private fun drawKnob(canvas: Canvas) {
    val radius = maxX * KNOB_RADIUS_RATIO
    if (radius <= 0f) {
      return
    }
    ensureKnobPaint(radius)
    val knob = knobPaint
    val glow = glowPaint
    val dot = dotPaint
    if (knob == null || glow == null || dot == null) {
      return
    }
    // Soft glow halo under the knob, then the knob itself, then a bright center dot. The canvas is
    // translated to the touch point so the (origin-centered) gradient follows the knob for free.
    canvas.withTranslation(absX, absY) {
      canvas.drawCircle(0f, 0f, radius * KNOB_GLOW_RATIO, glow)
      canvas.drawCircle(0f, 0f, radius, knob)
      canvas.drawCircle(0f, 0f, radius * KNOB_DOT_RATIO, dot)
    }
  }

  // The knob is a radial gradient (accent -> darkened edge) with a translucent glow halo and a
  // small bright center dot; paints are rebuilt only when the accent color or knob radius changes
  // (setAccent / pad size), so the per-frame draw cost stays flat (no allocation on every touch
  // move).
  private var knobPaint: Paint? = null
  private var glowPaint: Paint? = null
  private var dotPaint: Paint? = null
  private var knobAccent = 0
  private var knobRadius = 0f
  // Dashed outer ring: the DashPathEffect is sized in onSizeChanged (never inside onDraw, where
  // lint DrawAllocation forbids allocations). Null until the first layout pass.
  private var ringDashEffect: android.graphics.DashPathEffect? = null

  private fun ensureKnobPaint(radius: Float) {
    val accent = paint.color
    if (knobPaint != null && accent == knobAccent && radius == knobRadius) {
      return
    }
    knobAccent = accent
    knobRadius = radius
    val darker =
        android.graphics.Color.argb(
            OPAQUE_ALPHA,
            (android.graphics.Color.red(accent) * KNOB_EDGE_DIM_RATIO).toInt(),
            (android.graphics.Color.green(accent) * KNOB_EDGE_DIM_RATIO).toInt(),
            (android.graphics.Color.blue(accent) * KNOB_EDGE_DIM_RATIO).toInt(),
        )
    val center =
        android.graphics.Color.argb(
            OPAQUE_ALPHA,
            (android.graphics.Color.red(accent) + OPAQUE_ALPHA * KNOB_CENTER_LIGHT_RATIO)
                .toInt()
                .coerceAtMost(OPAQUE_ALPHA),
            (android.graphics.Color.green(accent) + OPAQUE_ALPHA * KNOB_CENTER_LIGHT_RATIO)
                .toInt()
                .coerceAtMost(OPAQUE_ALPHA),
            (android.graphics.Color.blue(accent) + OPAQUE_ALPHA * KNOB_CENTER_LIGHT_RATIO)
                .toInt()
                .coerceAtMost(OPAQUE_ALPHA),
        )
    knobPaint =
        Paint().apply {
          shader =
              android.graphics.RadialGradient(
                  0f,
                  0f,
                  radius,
                  center,
                  darker,
                  android.graphics.Shader.TileMode.CLAMP,
              )
          style = Paint.Style.FILL
        }
    glowPaint =
        Paint().apply {
          color = accent
          alpha = KNOB_GLOW_ALPHA
          style = Paint.Style.FILL
        }
    dotPaint =
        Paint().apply {
          color = android.graphics.Color.WHITE
          alpha = KNOB_DOT_ALPHA
          style = Paint.Style.FILL
        }
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    if (adaptivePadPx < 0) {
      val dm = resources.displayMetrics
      val maxPadPx = resources.getDimensionPixelSize(R.dimen.hud_pad_size)
      val hPx = maxOf(dm.widthPixels, dm.heightPixels)
      adaptivePadPx = minOf(maxPadPx, (hPx * PAD_SIZE_SCREEN_RATIO).toInt())
    }
    val spec = MeasureSpec.makeMeasureSpec(adaptivePadPx, MeasureSpec.EXACTLY)
    setMeasuredDimension(adaptivePadPx, adaptivePadPx)
    super.onMeasure(spec, spec)
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    maxX = w.toFloat()
    maxY = h.toFloat()
    // The dashed ring's dash lengths scale with the pad size; rebuild the effect here (not per
    // frame, or lint DrawAllocation fails).
    val sw = w.toFloat() * OUTER_RING_STROKE_RATIO
    ringDashEffect =
        android.graphics.DashPathEffect(
            floatArrayOf(sw * RING_DASH_LENGTH_RATIO, sw * RING_GAP_LENGTH_RATIO),
            0f,
        )
    if (oldw == 0 && oldh == 0) {
      setAbsXY(w / 2.0f, h / 2.0f)
    }
  }

  override fun performClick(): Boolean = super.performClick()

  fun send(command: String) {
    val currentSender = sender
    if (currentSender != null) {
      currentSender.send("$padName $command")
    }
  }

  fun setAbsXY(x: Float, y: Float) {
    absX = x
    absY = y
    invalidate()
  }

  // internal so the inner TouchPadListener reaches it without a synthetic
  // accessor (lint SyntheticAccessor)
  internal fun onTouch(view: View, event: MotionEvent): Boolean {
    if (view !== this) {
      return false
    }

    return when (event.action) {
      MotionEvent.ACTION_UP -> {
        parent?.requestDisallowInterceptTouchEvent(false)
        send("up")
        performClick()
        // User design: one CLICK on release — the acknowledgement is stronger than the down tick
        // so a full press reads as a deliberate gesture. ACTION_CANCEL must NOT vibrate.
        haptic(Haptics.Level.CLICK)
        true
      }
      MotionEvent.ACTION_CANCEL -> {
        parent?.requestDisallowInterceptTouchEvent(false)
        send("up")
        performClick()
        true
      }
      MotionEvent.ACTION_DOWN -> {
        parent?.requestDisallowInterceptTouchEvent(true)
        performClick()
        sendCoordinate(event)
        // One light tick when a thumb lands: the "every interaction is noticeable" anchor. It is a
        // discrete per-touch event, NOT per-move feedback (the old per-move buzz was the C24
        // "queued after finger lift" noise).
        haptic(Haptics.Level.TICK)
        true
      }
      MotionEvent.ACTION_MOVE -> {
        parent?.requestDisallowInterceptTouchEvent(true)
        performClick()
        sendCoordinate(event)
        true
      }
      else -> {
        AppLog.e(TAG, "Unknown touch event: $event")
        true
      }
    }
  }

  private fun sendCoordinate(event: MotionEvent) {
    val x = max(0f, min(event.x, maxX))
    val y = max(0f, min(event.y, maxY))
    setAbsXY(x, y)

    val command = touchPadController.nextCoordinates(x, y, maxX, maxY)
    if (command != null) {
      send(String.format(Locale.ROOT, "%d %d", command.x, command.y))
    }
  }

  private inner class TouchPadListener : OnTouchListener {
    override fun onTouch(view: View, event: MotionEvent): Boolean =
        this@SquareTouchPadLayout.onTouch(view, event)
  }

  private companion object {
    const val OPAQUE_ALPHA = 255
    // Adaptive pad formula: padPx = min(260dp px, screenTallestPx × PAD_SIZE_SCREEN_RATIO).
    // 0.63 → a narrow phone (360dp landscape) gets ~227dp pads, leaving reasonable video area.
    const val PAD_SIZE_SCREEN_RATIO = 0.63f
    // Mockup-style joystick knob: radius ~12% of the pad, with a glow halo (1.6x) and a small
    // bright center dot (0.35x). Paints rebuild only on accent change (see ensureKnobPaint).
    const val KNOB_RADIUS_RATIO = 0.12f
    const val KNOB_GLOW_RATIO = 1.6f
    const val KNOB_DOT_RATIO = 0.35f
    const val KNOB_GLOW_ALPHA = 60
    const val KNOB_DOT_ALPHA = 200
    // Outer dashed ring (mockup): diameter ~62% of the pad (radius = 31% of the pad), stroke ~1.2%.
    const val OUTER_RING_RATIO = 0.31f
    const val OUTER_RING_STROKE_RATIO = 0.012f
    // Dashed-ring dash/gap lengths, relative to the stroke width.
    const val RING_DASH_LENGTH_RATIO = 5f
    const val RING_GAP_LENGTH_RATIO = 3f
    // Knob gradient: edge color = accent dimmed, center = accent lightened, both by these ratios.
    const val KNOB_EDGE_DIM_RATIO = 0.55f
    const val KNOB_CENTER_LIGHT_RATIO = 0.25f
    const val TAG = "TouchPad"
  }
}
