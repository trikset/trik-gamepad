// http://stackoverflow.com/questions/10550139/android-ics-and-mjpeg-using-asynctask
package com.demo.mjpeg

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView

import java.io.IOException

class MjpegView : SurfaceView, SurfaceHolder.Callback {
    inner class MjpegViewThread(private val mSurfaceHolder: SurfaceHolder) {
        private var thread: Thread? = null
        private var frameCounter: Int = 0
        private var start: Long = 0
        private var ovl: Bitmap? = null

        private fun destRect(bmw_: Int, bmh_: Int): Rect? {
            val tempx: Int
            val tempy: Int
            if (displayMode == MjpegView.SIZE_BEST_FIT) {
                val bmasp = bmw_.toFloat() / bmh_.toFloat()
                var bmw = dispWidth
                var bmh = (dispWidth / bmasp).toInt()
                if (bmh > dispHeight) {
                    bmh = dispHeight
                    bmw = (dispHeight * bmasp).toInt()
                }
                tempx = dispWidth / 2 - bmw / 2
                tempy = dispHeight / 2 - bmh / 2
                return Rect(tempx, tempy, bmw + tempx, bmh + tempy)
            }
            return null
        }

        private fun initThread() {
            join()
            thread = object : Thread() {
                override fun run() {
                    start = System.currentTimeMillis()
                    val mode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)

                    val p = Paint()
                    while (mRun) {
                        if (surfaceDone) {
                            val c = mSurfaceHolder.lockCanvas()
                            try {
                                synchronized (mSurfaceHolder) {
                                    try {
                                        val bm = mIn!!.readMjpegFrame()

                                        val destRect = destRect(bm.width, bm.height)

                                        if (destRect != null) {

                                            c!!.drawColor(Color.BLACK)
                                            c.drawBitmap(bm, null, destRect, p)
                                            if (showFps) {
                                                p.setXfermode(mode)
                                                if (ovl != null) {
                                                    val height = if ((ovlPos and 1) == 1)
                                                        destRect.top
                                                    else
                                                        destRect.bottom - ovl!!.height
                                                    val width = if ((ovlPos and 8) == 8)
                                                        destRect.left
                                                    else
                                                        destRect.right - ovl!!.width
                                                    c.drawBitmap(ovl, width.toFloat(), height.toFloat(), null)
                                                }
                                                p.setXfermode(null)
                                                frameCounter++
                                                if (System.currentTimeMillis() - start >= 1000) {
                                                    val fps = frameCounter.toString() + " fps"
                                                    frameCounter = 0
                                                    start = System.currentTimeMillis()
                                                    ovl = makeFpsOverlay(overlayPaint!!, fps)
                                                }
                                            }
                                        }
                                    } catch (e: IOException) {
                                        e.getStackTrace()
                                        Log.d(TAG, "catch IOException hit in run", e)
                                    }

                                }
                            } finally {
                                if (c != null) {
                                    mSurfaceHolder.unlockCanvasAndPost(c)
                                }
                            }
                        }
                    }
                }
            }
        }

        fun join() {
            if (thread != null) {
                var retry = true
                while (retry) {
                    try {
                        thread!!.join(3000, 0)
                        retry = false
                    } catch (e: InterruptedException) {
                        Log.e(this.javaClass.simpleName, Log.getStackTraceString(e))
                    }

                }
                thread = null
            }
        }

        private fun makeFpsOverlay(p: Paint, text: String): Bitmap {
            val b = Rect()
            p.getTextBounds(text, 0, text.length(), b)
            val bWidth = b.width() + 2
            val bHeight = b.height() + 2
            val bm = Bitmap.createBitmap(bWidth, bHeight, Bitmap.Config.ARGB_8888)
            val c = Canvas(bm)
            p.color = overlayBackgroundColor
            c.drawRect(0f, 0f, bWidth.toFloat(), bHeight.toFloat(), p)
            p.color = overlayTextColor
            c.drawText(text, (-b.left + 1).toFloat(), bHeight / 2 - (p.ascent() + p.descent()) / 2 + 1, p)
            return bm
        }

        fun setSurfaceSize(width: Int, height: Int) {
            synchronized (mSurfaceHolder) {
                dispWidth = width
                dispHeight = height
            }
        }

        fun start() {
            initThread()
            thread!!.start()
        }
    }

    private var thread: MjpegViewThread? = null
    private var mIn: MjpegInputStream? = null
    private var showFps: Boolean = false
    private var mRun: Boolean = false
    private var surfaceDone: Boolean = false
    private var overlayPaint: Paint? = null
    private var overlayTextColor: Int = 0
    private var overlayBackgroundColor: Int = 0
    private var ovlPos: Int = 0
    private var dispWidth: Int = 0
    private var dispHeight: Int = 0

    private var displayMode: Int = SIZE_BEST_FIT

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init()
    }

    private fun init() {
        val holder = holder
        holder.addCallback(this)
        thread = MjpegViewThread(holder)
        isFocusable = true
        overlayPaint = Paint()
        overlayPaint!!.textAlign = Paint.Align.LEFT
        overlayPaint!!.textSize = 12f
        overlayPaint!!.setTypeface(Typeface.DEFAULT)
        overlayTextColor = Color.WHITE
        overlayBackgroundColor = Color.BLACK
        ovlPos = MjpegView.POSITION_LOWER_RIGHT
        dispWidth = width
        dispHeight = height
    }


    fun setOverlayPosition(p: Int) {
        ovlPos = p
    }


    fun setSource(source: MjpegInputStream?) {
        mIn = source
        // startPlayback();
    }

    fun showFps(b: Boolean) {
        showFps = b
    }

    fun startPlayback() {
        if (mIn != null) {
            mRun = true
            thread!!.start()
        }
    }

    fun stopPlayback() {
        mRun = false
        thread!!.join()
    }

    override fun surfaceChanged(holder: SurfaceHolder, f: Int, w: Int, h: Int) {
        thread!!.setSurfaceSize(w, h)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceDone = true
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceDone = false
        stopPlayback()
    }

    companion object {

        private val TAG = "MjpegView"
        const val POSITION_UPPER_RIGHT = 3

        const val POSITION_LOWER_RIGHT = 6
        const val SIZE_BEST_FIT = 4


    }
}