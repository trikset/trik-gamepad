package com.trik.gamepad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.RelativeLayout

import java.util.Locale

class SquareTouchPadLayout : RelativeLayout {
    private val paint = Paint()
    private var absY: Float = 0.toFloat()
    private var absX: Float = 0.toFloat()
    internal var padName: String? = null
    private var mSender: SenderService? = null
    private var mPrevY: Int = 0
    private var mPrevX: Int = 0
    private var mMaxX: Float = 0.toFloat()
    private var mMaxY: Float = 0.toFloat()

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        init()
    }

    private fun init() {
        paint.color = Color.RED
        paint.strokeWidth = 0f
        paint.style = Paint.Style.STROKE
        paint.alpha = 255
        setOnTouchListener(TouchPadListener())
        setOnClickListener(object : View.OnClickListener {

            override fun onClick(v: View) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)

            }
        })
        setWillNotDraw(false)
        isHapticFeedbackEnabled = true
        setBackgroundDrawable(resources.getDrawable(R.drawable.oxygen_actions_transform_move_icon))

    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawCircle(absX, absY, mMaxX / 20, paint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = View.MeasureSpec.getSize(widthMeasureSpec)
        val height = View.MeasureSpec.getSize(heightMeasureSpec)
        val halfPerimeter = width + height
        val size = if (width * height != 0)
            Math.min(width, height)
        else if (halfPerimeter != 0)
            halfPerimeter
        else
            SquareTouchPadLayout.sDefaultSize
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mMaxX = w.toFloat()
        mMaxY = h.toFloat()
        if (oldw == 0 && oldh == 0) {
            setAbsXY((w / 2).toFloat(), (h / 2).toFloat())
        }
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    internal fun send(command: String) {
        mSender!!.send(padName + ' ' + command)
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
    }

    internal fun setAbsXY(x: Float, y: Float) {
        absX = x
        absY = y
        invalidate()
    }

    internal fun setSender(sender: SenderService) {
        mSender = sender
    }

    internal inner class TouchPadListener : View.OnTouchListener {

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            if (v !== this@SquareTouchPadLayout)
                return false

            when (event.action) {

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    send("up")
                    v.performClick()
                    return true
                }
                MotionEvent.ACTION_DOWN -> {
                    v.performClick()
                    setAbsXY(Math.max(0f, Math.min(event.x, mMaxX)), Math.max(0f, Math.min(event.y, mMaxY)))

                    val SENSITIVITY = 3

                    val SCALE = 1.15
                    val rX = (200.0 * SCALE * (absX / mMaxX - 0.5)).toInt()
                    val rY = -(200.0 * SCALE * (absY / mMaxY - 0.5)).toInt()
                    val curY = Math.max(-100, Math.min(rY, 100))
                    val curX = Math.max(-100, Math.min(rX, 100))

                    if (Math.abs(curX - mPrevX) > SENSITIVITY || Math.abs(curY - mPrevY) > SENSITIVITY) {
                        mPrevX = curX
                        mPrevY = curY
                        send("%d %d".format(Locale.US, curX, curY))
                    }

                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    setAbsXY(Math.max(0f, Math.min(event.x, mMaxX)), Math.max(0f, Math.min(event.y, mMaxY)))
                    val SENSITIVITY = 3
                    val SCALE = 1.15
                    val rX = (200.0 * SCALE * (absX / mMaxX - 0.5)).toInt()
                    val rY = -(200.0 * SCALE * (absY / mMaxY - 0.5)).toInt()
                    val curY = Math.max(-100, Math.min(rY, 100))
                    val curX = Math.max(-100, Math.min(rX, 100))
                    if (Math.abs(curX - mPrevX) > SENSITIVITY || Math.abs(curY - mPrevY) > SENSITIVITY) {
                        mPrevX = curX
                        mPrevY = curY
                        send("%d %d".format(Locale.US, curX, curY))
                    }
                    return true

                }
                else -> {
                    Log.e("TouchEvent", "Unknown:" + event.toString())

                    return true
                }
            }
        }
    }

    companion object {

        private val sDefaultSize = 100
    }
}
