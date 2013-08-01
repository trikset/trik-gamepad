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

    TouchPadListener(View pad, String padName, SenderService sender) {
        mPad = pad;
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

            final int SENSITIVITY = 4;

            final int rX = (int) (200 * aX / mMaxX - 100);
            final int rY = -(int) (200 * aY / mMaxY - 100);
            final int curY = Math.max(-100, Math.min(rY, 100)); // ???
            final int curX = Math.max(-100, Math.min(rX, 100)); // ???

            if (Math.abs(curX - mPrevX) > SENSITIVITY || Math.abs(curY - mPrevY) > SENSITIVITY)
            {
                mPrevX = curX;
                mPrevY = curY;
                mSender.send(mPadName + " " + curX + " " + curY);
            }

            return true;
        }
    }
}