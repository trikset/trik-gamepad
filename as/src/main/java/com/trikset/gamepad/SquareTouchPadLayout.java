package com.trikset.gamepad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;

import java.util.Locale;

public class SquareTouchPadLayout extends RelativeLayout {

    final static private int sDefaultSize = 100;
    final private Paint paint = new Paint();
    private float mAbsY;
    private float mAbsX;
    private String mPadName;
    private SenderService mSender;
    private int mPrevY;
    private int mPrevX;
    private float mMaxX;
    private float mMaxY;

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

    private float getAbsX() {
        return mAbsX;
    }

    private float getAbsY() {
        return mAbsY;
    }

    String getPadName() {
        return mPadName;
    }

    void setPadName(String padName) {
        mPadName = padName;
    }

    private void init() {
        paint.setColor(Color.RED);
        paint.setStrokeWidth(0);
        paint.setStyle(Paint.Style.STROKE);
        paint.setAlpha(255);
        setOnTouchListener(new TouchPadListener());
        setOnClickListener(v -> performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING));
        setWillNotDraw(false);
        setHapticFeedbackEnabled(true);
        setBackgroundDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.oxygen_actions_transform_move_icon, null));

    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawCircle(getAbsX(), getAbsY(), mMaxX / 20, paint);
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = MeasureSpec.getSize(heightMeasureSpec);
        final int halfPerimeter = width + height;
        final int size = ((width * height) != 0) ? Math.min(width, height) : ((halfPerimeter != 0) ? halfPerimeter
                : SquareTouchPadLayout.sDefaultSize);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mMaxX = w;
        mMaxY = h;
        if (oldw == 0 && oldh == 0) {
            setAbsXY(w / 2.0f, h / 2.0f);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    void send(String command) {
        if (mSender != null) {
            mSender.send(getPadName() + ' ' + command);
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }
    }

    void setAbsXY(float x, float y) {
        mAbsX = x;
        mAbsY = y;
        invalidate();
    }

    void setSender(SenderService sender) {
        mSender = sender;
    }

    final class TouchPadListener implements OnTouchListener {

        /**
         * @noinspection DefaultNotLastCaseInSwitch
         */
        @Override
        public boolean onTouch(@NonNull final View v, @NonNull final MotionEvent event) {
            if (v != SquareTouchPadLayout.this) {
                return false;
            }

            switch (event.getAction()) {
                default:
                    Log.e("TouchEvent", "Unknown:" + event);

                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    send("up");
                    v.performClick();
                    return true;
                case MotionEvent.ACTION_DOWN:
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    v.performClick();
                case MotionEvent.ACTION_MOVE:
                    setAbsXY(Math.max(0, Math.min(event.getX(), mMaxX)), Math.max(0, Math.min(event.getY(), mMaxY)));

                    final int SENSITIVITY = 3;

                    final double SCALE = 1.15;
                    final int rX = (int) (200 * SCALE * (getAbsX() / mMaxX - 0.5));
                    final int rY = -(int) (200 * SCALE * (getAbsY() / mMaxY - 0.5));
                    final int curY = Math.max(-100, Math.min(rY, 100));
                    final int curX = Math.max(-100, Math.min(rX, 100));

                    if (Math.abs(curX - mPrevX) > SENSITIVITY || Math.abs(curY - mPrevY) > SENSITIVITY) {
                        mPrevX = curX;
                        mPrevY = curY;
                        send(String.format(Locale.US, "%d %d", curX, curY));
                    }

                    return true;
            }
        }
    }
}
