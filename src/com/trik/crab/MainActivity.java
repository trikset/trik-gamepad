package com.trik.crab;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.Toast;
import android.widget.ToggleButton;

public class MainActivity extends Activity implements SensorEventListener {
    private SensorManager   mSensorManager;
    private int             mAngle;                             // -100%
                                                                 // ...
                                                                 // +100%
    private boolean         mWheelEnabled = false;
    protected SenderService mSender       = new SenderService();

    @Override
    public void onAccuracyChanged(Sensor arg0, int arg1) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if (!mWheelEnabled)
                return;
            synchronized (this) {
                processSensor(event.values);
            }
        } else {
            Log.i("Sensor", "" + event.sensor.getType());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menuSettings:
            Intent settings = new Intent(this, SettingsActivity.class);
            startActivity(settings);
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        super.setContentView(R.layout.activity_main);
        super.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        {
            final ToggleButton tglConnect = (ToggleButton) findViewById(R.id.tglConnect);
            tglConnect.setOnClickListener(new ToggleButton.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean connected;
                    String toastText;
                    if (!tglConnect.isChecked())
                    {
                        mSender.disconnect();
                        connected = false;
                        toastText = "Disconnected.";
                    } else {
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
                        String addr = prefs.getString(SettingsActivity.SK_HOST_ADDRESS, "127.0.0.1");
                        connected = mSender.connect(addr);
                        toastText = "Connection " + (connected ? "established." : "error.");
                    }
                    tglConnect.setChecked(connected);
                    Toast.makeText(getBaseContext(), toastText, Toast.LENGTH_SHORT).show();
                }

            });
        }

        {
            final ToggleButton tglWheel = (ToggleButton) findViewById(R.id.tglWheel);
            tglWheel.setOnClickListener(new ToggleButton.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mWheelEnabled = tglWheel.isChecked();
                    Toast.makeText(getBaseContext(), "Wheel turned " + (mWheelEnabled ? "ON" : "OFF"),
                            Toast.LENGTH_SHORT)
                            .show();
                }
            });
        }

        {
            final View tvBaseControl = findViewById(R.id.tvBaseControl);
            tvBaseControl.setOnTouchListener(new OnTouchListener() {

                int mPrevLeft;
                int mPrevRight;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (v != tvBaseControl)
                        return false;

                    final int SENSITIVITY = 10;
                    switch (event.getAction()) {
                    default:
                        Log.e("TouchEvent", "Unknown:" + event.toString());
                        return false;
                    case MotionEvent.ACTION_UP:
                        mSender.send("left 0");
                        mSender.send("right 0");
                        return true;
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_MOVE:
                        final float aX = event.getX();
                        final float aY = event.getY();
                        final float mMaxX = tvBaseControl.getWidth();
                        final float mMaxY = tvBaseControl.getHeight();
                        if (aX < 0 || aY < 0 || aX > mMaxX || aY > mMaxY)
                            return false;
                        final int rX = (int) (200 * aX / mMaxX - 100);
                        final int rY = -(int) (200 * aY / mMaxY - 100);
                        final int left = Math.max(-100, Math.min(rY + rX, 100));
                        final int right = Math.max(-100, Math.min(rY - rX, 100));

                        if (Math.abs(left - mPrevLeft) > SENSITIVITY)
                        {
                            mPrevLeft = left;
                            mSender.send("left " + left);
                        }

                        if (Math.abs(right - mPrevRight) > SENSITIVITY)
                        {
                            mPrevRight = right;
                            mSender.send("right " + right);
                        }

                        return true;
                    }

                }

            });
        }
        {
            final View tvArmControl = findViewById(R.id.tvArmControl);
            tvArmControl.setOnTouchListener(new OnTouchListener() {

                int mPrevHand;
                int mPrevArm;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (v != tvArmControl)
                        return false;

                    final int SENSITIVITY = 3;
                    switch (event.getAction()) {
                    default:
                        Log.e("TouchEvent", "Unknown:" + event.toString());
                        return false;
                    case MotionEvent.ACTION_UP:
                        mSender.send("arm 0");
                        mSender.send("hand 0");
                        return true;
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_MOVE:
                        final float aX = event.getX();
                        final float aY = event.getY();
                        final float mMaxX = tvArmControl.getWidth();
                        final float mMaxY = tvArmControl.getHeight();
                        if (aX < 0 || aY < 0 || aX > mMaxX || aY > mMaxY)
                            return false;
                        final int rX = (int) (200 * aX / mMaxX - 100);
                        final int rY = -(int) (200 * aY / mMaxY - 100);
                        final int arm = Math.max(-100, Math.min(rY, 100));
                        final int hand = Math.max(-100, Math.min(rX, 100));

                        if (Math.abs(hand - mPrevHand) > SENSITIVITY)
                        {
                            mPrevHand = hand;
                            mSender.send("hand " + hand);
                        }

                        if (Math.abs(arm - mPrevArm) > SENSITIVITY)
                        {
                            mPrevArm = arm;
                            mSender.send("arm " + arm);
                        }

                        return true;
                    }
                }
            });
        }
    }

    private void processSensor(float[] current) {
        final float WHEEL_BOOSTER_MULTIPLIER = 1.5f;
        final float x = current[0];
        final float y = current[1];

        int angle = (int) (200 * WHEEL_BOOSTER_MULTIPLIER * Math.atan(y / x) / Math.PI);

        if (angle > 100) {
            angle = 100;
        } else if (angle < -100) {
            angle = -100;
        }

        if (Math.abs(angle) < 10 * WHEEL_BOOSTER_MULTIPLIER
                || Math.abs(mAngle - angle) < 5 * WHEEL_BOOSTER_MULTIPLIER) {
            return;
        }

        mAngle = angle;
        mSender.send("left " + mAngle);
        mSender.send("right " + (-mAngle));
    }

    @Override
    protected void onStop() {
        mSensorManager.unregisterListener(this);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ALL),
                SensorManager.SENSOR_DELAY_NORMAL);
    }

}
