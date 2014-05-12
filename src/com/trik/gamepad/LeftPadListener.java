package com.trik.gamepad;

import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

final class LeftPadListener implements OnTouchListener {
    final View          mPad;
    int                 mPrevY;
    int                 mPrevX;
    final SenderService mSender;

    LeftPadListener(View pad, SenderService sender) {
        mPad = pad;
        mSender = sender;
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
            return true;
        case MotionEvent.ACTION_DOWN:
        case MotionEvent.ACTION_MOVE:
            final float aX = event.getX();
            final float aY = event.getY();
            final float mMaxX = mPad.getWidth();
            final float mMaxY = mPad.getHeight();

            final int SENSITIVITY = 3;

            final float SCALE = 1.15F;
            final int rX = (int) (200 * SCALE * (aX / mMaxX - 0.5));
            final int rY = -(int) (200 * SCALE * (aY / mMaxY - 0.5));
            final int curY = Math.max(-100, Math.min(rY, 100));
            final int curX = Math.max(-100, Math.min(rX, 100));

            if (Math.abs(curX - mPrevX) > SENSITIVITY || Math.abs(curY - mPrevY) > SENSITIVITY)
            {
                if (Math.abs(curX) < 20 && Math.abs(curY) < 20) {
                    mSender.send("direct: brick.stop();");
                } else if (curX < curY && curX > -curY) {
                    mSender.send("direct: " + Scripts.FORWARD);
                } else if (curX > curY && curX < -curY) {
                    mSender.send("direct: " + Scripts.BACK);
                } else {
                    mSender.send("direct: " + Scripts.STEER.replaceAll("@@STEERING@@", curX + ""));
                }

                mPrevX = curX;
                mPrevY = curY;
            }

            return true;
        }
    }
}
