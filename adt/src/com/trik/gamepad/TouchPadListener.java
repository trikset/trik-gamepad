package com.trik.gamepad;

import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

final class TouchPadListener implements OnTouchListener {
    final View          mPad;
    int                 mPrevY;
    int                 mPrevX;
    String              mPadName;
    final SenderService mSender;

    TouchPadListener(final View pad, final String padName,
            final SenderService sender) {
        mPad = pad;
        mSender = sender;
        mPadName = padName;
    }

    @Override
    public boolean onTouch(final View v, final MotionEvent event) {
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

            final int SENSITIVITY = 3;

            final float SCALE = 1.15f;
            final int rX = (int) (200 * SCALE * (aX / mMaxX - 0.5f));
            final int rY = -(int) (200 * SCALE * (aY / mMaxY - 0.5f));
            final int curY = Math.max(-100, Math.min(rY, 100));
            final int curX = Math.max(-100, Math.min(rX, 100));

            if (Math.abs(curX - mPrevX) > SENSITIVITY
                    || Math.abs(curY - mPrevY) > SENSITIVITY) {
                mPrevX = curX;
                mPrevY = curY;
                mSender.send(mPadName + ' ' + curX + ' ' + curY);
            }

            return true;
        }
    }
}
