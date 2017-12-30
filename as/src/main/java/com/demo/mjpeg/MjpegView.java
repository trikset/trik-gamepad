// http://stackoverflow.com/questions/10550139/android-ics-and-mjpeg-using-asynctask
package com.demo.mjpeg;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import org.apache.commons.io.input.BoundedInputStream;

import java.io.IOException;
import java.util.Locale;

public class MjpegView extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "MjpegView";
    private MjpegViewThread thread;
    private MjpegInputStream mIn;
    private boolean mRun;
    private boolean surfaceDone;
    private final Paint fpsTextPaint = new Paint();
    private int dispWidth;
    private int dispHeight;

    public MjpegView(Context context) {
        super(context);
        init(context);
    }

    public MjpegView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        SurfaceHolder holder = getHolder();
        holder.addCallback(this);
        thread = new MjpegViewThread(holder);
        setFocusable(true);
        fpsTextPaint.setTextAlign(Paint.Align.RIGHT);
        fpsTextPaint.setTextSize(12);
        fpsTextPaint.setTypeface(Typeface.DEFAULT);
        fpsTextPaint.setColor(Color.WHITE);
        dispWidth = getWidth();
        dispHeight = getHeight();
    }


    public void setSource(MjpegInputStream source) {
        mIn = source;
    }

    public synchronized void startPlayback() {
        startPlaybackInternal();
    }

    private void startPlaybackInternal() {
        if (mIn != null) {
            mRun = true;
            thread.start();
        }
    }

    public synchronized void stopPlayback() {
        stopPlaybackInternal();
    }

    private void stopPlaybackInternal() {
        if (mRun) {
            mRun = false;
            thread.join();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int f, int w, int h) {
        thread.setSurfaceSize(w, h);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceDone = true;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceDone = false;
        stopPlayback();
    }

    class MjpegViewThread {
        private final SurfaceHolder mSurfaceHolder;
        @Nullable
        private Thread thread;

        public MjpegViewThread(SurfaceHolder surfaceHolder) {
            mSurfaceHolder = surfaceHolder;
        }

        private void initThread() {
            join();
            if (thread != null)
                join();
            thread = new MjpegRenderThread(this.mSurfaceHolder);
        }

        public void join() {
            if (thread != null) {
                boolean retry = true;
                while (retry) {
                    try {
                        thread.join(3000, 0);
                        retry = false;
                    } catch (InterruptedException e) {
                        Log.e(this.getClass().getSimpleName(), Log.getStackTraceString(e));
                    }
                }
                thread = null;
            }
        }

        public void setSurfaceSize(int width, int height) {
            synchronized (mSurfaceHolder) {
                dispWidth = width;
                dispHeight = height;
            }
        }

        public void start() {
            initThread();
            if (thread != null)
                thread.start();
        }

    }

    /// TODO: rewrite from scratch. needs redesign.
    private class MjpegRenderThread extends Thread {
        private final SurfaceHolder mSurfaceHolder;
        private int mFrameCounter;
        private long mStart;

        @Nullable
        private Bitmap mBitmap;
        @Nullable
        private BoundedInputStream mFrame;
        @NonNull
        private final byte[] mTempStorage = new byte[100000];
        private final PorterDuffXfermode mMode = new PorterDuffXfermode(PorterDuff.Mode.DST_OVER);
        private String mFpsStr = "";

        public MjpegRenderThread(SurfaceHolder holder) {
            this.mSurfaceHolder = holder;
        }

        @Override
        public void run() {
            mStart = System.currentTimeMillis();

            while (mRun) {
                if (!surfaceDone)
                    continue;
                Canvas canvas = null;
                try {
                    mFrame = mIn.readMjpegFrame();
                    if (mFrame == null)
                        continue;

                    Rect destRect = ExtractNextBitmap();
                    if (destRect == null)
                        continue;


                    if (null == (canvas = mSurfaceHolder.lockCanvas()))
                        continue;

                    if (mBitmap != null)
                        DrawToCanvas(canvas, destRect);

                } catch (IOException e) {
                    mRun = false;
                } finally {

                    if (canvas != null) {
                        mSurfaceHolder.unlockCanvasAndPost(canvas);
                        canvas = null;
                    }
                    if (mFrame != null)
                        try {
                            // Stream is bound to mBitmap, but we do not need
                            // both of them later, mBitmap to be reused.
                            mFrame.close();
                        } catch (IOException e) {
                            mRun = false;
                        }
                    mFrame = null;
                }
            }
        }

        synchronized private Rect ExtractNextBitmap() {

            BitmapFactory.Options opts = new BitmapFactory.Options();
//            opts.inJustDecodeBounds = true;
//            try {
//                mFrame.mark(mFrame.available());
//                BitmapFactory.decodeStream(mFrame, null, opts);
//                mFrame.reset();
//            } catch (IOException e) {
//                return null;
//            }


//            opts.inSampleSize = 1;
//            while (destRect.width() < opts.outWidth >> 1 && destRect.height() < opts.outHeight >> 1) {
//                opts.inSampleSize <<= 1;
//                opts.outWidth >>= 1;
//                opts.outHeight >>= 1;
//            }
//
//            opts.inScaled = true;

            //final int scale = (int) getResources().getDisplayMetrics().density;
            //opts.inTargetDensity = scale * opts.inSampleSize;
            //opts.inScreenDensity = scale;
            //opts.inDensity = scale * opts.outHeight / destRect.height();

            opts.inBitmap = mBitmap; // reuse if possible
            opts.inMutable = true;

            opts.inPurgeable = true;
            opts.inInputShareable = true;
            //opts.inPreferredConfig = Bitmap.Config.RGB_565;
            opts.inTempStorage = mTempStorage;

            Bitmap bm;

            try {
                //opts.inJustDecodeBounds = false;
                bm = BitmapFactory.decodeStream(mFrame, null, opts);
            } catch (IllegalArgumentException e) {
                bm = null;
            }

            if (bm == null)
                return null;

            if (mBitmap != null && bm != mBitmap) {
                // Was not reused
                mBitmap.recycle();
                Log.v(TAG, "Bitmap was not reused, recycled.");
            }

            mBitmap = bm;
            //mBitmap.setDensity(Bitmap.DENSITY_NONE);
            return destRect(bm.getWidth(), bm.getHeight());
        }

        private void DrawToCanvas(@NonNull Canvas canvas, @NonNull Rect destRect) {

            mFrameCounter++;
            final long now = System.currentTimeMillis();
            final long elapsedMs = now - mStart;
            final int TIME_FRAME_MS = 5000;
            if (elapsedMs >= TIME_FRAME_MS) {
                Log.v(TAG, "elapsed " + elapsedMs);
                mStart = now;
                float fps = 1000.0f * mFrameCounter / TIME_FRAME_MS;
                mFrameCounter = 0;
                mFpsStr = String.format(Locale.getDefault(), "%.1f", fps);
            }

            synchronized (mSurfaceHolder) {
                canvas.drawColor(Color.BLACK);
                synchronized (this) {
                    if (mBitmap != null)
                        canvas.drawBitmap(mBitmap, null, destRect, null);
                }
                canvas.drawText(mFpsStr, dispWidth - 1, -fpsTextPaint.ascent(), fpsTextPaint);
            }


        }


        @NonNull
        private Rect destRect(int bmw, int bmh) {
            int tempX;
            int tempY;
            float bmasp = (float) bmw / (float) bmh;
            bmw = dispWidth;
            bmh = (int) (dispWidth / bmasp);
            if (bmh > dispHeight) {
                bmh = dispHeight;
                bmw = (int) (dispHeight * bmasp);
            }
            tempX = dispWidth / 2 - bmw / 2;
            tempY = dispHeight / 2 - bmh / 2;
            return new Rect(tempX, tempY, bmw + tempX, bmh + tempY);
        }

    }


}