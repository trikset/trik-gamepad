package com.trik.gamepad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;

public class SquareTouchPadLayout extends RelativeLayout {
    final class TouchPadListener implements OnTouchListener {
        @Override
        public boolean onTouch(final View v, final MotionEvent event) {
            if (v != SquareTouchPadLayout.this)
                return false;

            switch (event.getAction()) {
            default:
                Log.e("TouchEvent", "Unknown:" + event.toString());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getSender().send(getPadName() + " up");
                return true;
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                aX = Math.max(0, Math.min(event.getX(), mMaxX));
                aY = Math.max(0, Math.min(event.getY(), mMaxY));

                final int SENSITIVITY = 3;

                final double SCALE = 1.15;
                final int rX = (int) (200 * SCALE * (aX / mMaxX - 0.5));
                final int rY = -(int) (200 * SCALE * (aY / mMaxY - 0.5));
                final int curY = Math.max(-100, Math.min(rY, 100));
                final int curX = Math.max(-100, Math.min(rX, 100));

                if (Math.abs(curX - mPrevX) > SENSITIVITY || Math.abs(curY - mPrevY) > SENSITIVITY) {
                    mPrevX = curX;
                    mPrevY = curY;
                    getSender().send(getPadName() + ' ' + curX + ' ' + curY);
                }

                return true;
            }
        }
    }

    public float          aY;

    public float          aX;

    private String        mPadName;

    private SenderService mSender;

    int                   mPrevY;

    int                   mPrevX;

    final static int      sDefaultSize = 100;

    Paint                 paint        = new Paint();

    private float         mMaxX;

    private float         mMaxY;

    public SquareTouchPadLayout(final Context context) {
        super(context);
        init();
    }

    public SquareTouchPadLayout(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SquareTouchPadLayout(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    String getPadName() {
        return mPadName;
    };

    SenderService getSender() {
        return mSender;
    };

    private final void init() {
        paint.setColor(Color.RED);
        paint.setStrokeWidth(10.0f);
        paint.setStyle(Paint.Style.STROKE);
        setOnTouchListener(new TouchPadListener());
        setWillNotDraw(false);
        setBackgroundDrawable(getResources().getDrawable(R.drawable.oxygen_actions_transform_move_icon));

    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawCircle(aX, aY, mMaxX / 20, paint);
        invalidate();
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = MeasureSpec.getSize(heightMeasureSpec);
        final int halfPerimeter = width + height;
        final int size = width * height != 0 ? Math.min(width, height) : halfPerimeter != 0 ? halfPerimeter
                : SquareTouchPadLayout.sDefaultSize;
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mMaxX = w;
        mMaxY = h;
        if (oldw == 0 && oldh == 0) {
            aX = w / 2;
            aY = h / 2;
        }

    }

    void setPadName(String padName) {
        mPadName = padName;
    }

    void setSender(SenderService sender) {
        mSender = sender;
    }
}
