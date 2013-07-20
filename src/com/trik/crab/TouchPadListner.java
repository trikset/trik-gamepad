package com.trik.crab;

import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.RelativeLayout;

final class TouchPadListner implements OnTouchListener {
    final View          mCircle;
    final View          mPad;
    int                 mPrevY;
    int                 mPrevX;
    String              mPadName;
    final float         mCircleRadius;
    final SenderService mSender;

    TouchPadListner(View pad, String padName, View circle, SenderService sender) {
        mCircle = circle;
        mPad = pad;
        mCircleRadius = circle.getWidth() / 2;
        mSender = sender;
        mPadName = padName;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (v != mPad)
            return false;

        switch (event.getAction()) {
        default:
            Log.e("TouchEvent", "Unknown:" + event.toString());
            return true;
        case MotionEvent.ACTION_UP:
            mSender.send(mPadName + " up");
            return true;
        case MotionEvent.ACTION_DOWN:
        case MotionEvent.ACTION_MOVE:
            final float aX = event.getX();
            final float aY = event.getY();
            final float mMaxX = mPad.getWidth();
            final float mMaxY = mPad.getHeight();

            if (aX < 0 || aY < 0 || aX > mMaxX || aY > mMaxY)
                return false;

            final RelativeLayout.LayoutParams lps =
                    (RelativeLayout.LayoutParams) mCircle.getLayoutParams();

            lps.setMargins((int) (aX - mCircleRadius), (int) (aY - mCircleRadius), -(int) mCircleRadius,
                    -(int) mCircleRadius);

            mCircle.setLayoutParams(lps);

            final int SENSITIVITY = 4;

            final int rX = (int) (200 * aX / mMaxX - 100);
            final int rY = -(int) (200 * aY / mMaxY - 100);
            final int curX = Math.max(-100, Math.min(rY, 100));
            final int curY = Math.max(-100, Math.min(rX, 100));

            if (Math.abs(curY - mPrevX) > SENSITIVITY)
            {
                mPrevX = curY;
                mSender.send(mPadName + " x " + curY);
            }

            if (Math.abs(curX - mPrevY) > SENSITIVITY)
            {
                mPrevY = curX;
                mSender.send(mPadName + " y " + curX);
            }

            return true;
        }
    }
}
