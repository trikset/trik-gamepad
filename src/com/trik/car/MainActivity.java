package com.trik.car;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

public class MainActivity extends Activity implements SensorEventListener {
    // TextView[] mTextViews;

    GestureDetector         mGestureDetector;

    // VideoView mVideoView;

    private SensorManager   mSensorManager;

    private int             mPower;               // -100% ... +100%
    private int             mAngle;               // -100% ... +100%
    protected SenderService mSender;
    private boolean         mWheelEnabled = false;

    private TextView        mDirectionView;

    public MainActivity() {
        mGestureDetector = new GestureDetector(new ControlGestureListner());
        mSender = new SenderService();
    }

    @Override
    public void onAccuracyChanged(Sensor arg0, int arg1) {
        // TODO Auto-generated method stub

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
        case com.trik.car.R.id.menuSettings:
            Intent settings = new Intent(this, SettingsActivity.class);
            startActivity(settings);
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    };

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if (!mWheelEnabled)
                return;
            synchronized (this) {
                process(event.values);
            }
        } else {
            Log.i("Sensor", "" + event.sensor.getType());
        }
    }

    private void process(float[] current) {
        final int ACCEL_SENSITIVITY_STEPS_PER_PI = 50; // in radians
        final float WHEEL_BOOSTER_MULTIPLIER = 1.5;
        final float x = current[0];
        final float y = current[1];
        // final float norm = (float) Math.sqrt(x*x+y*y); // w/o Z

        final float angle = (float) (WHEEL_BOOSTER_MULTIPLIER * Math.atan(y / x));

        if (ACCEL_SENSITIVITY_STEPS_PER_PI * Math.abs(mCurrentAngle - angle) > Math.PI) {
            mAngle = angle;
            changeCarAngle(angleIncrement)
        }
    }

    @Override
    protected void onStop() {
        mSensorManager.unregisterListener(this);
        super.onStop();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDirectionView = (TextView) findViewById(R.id.tvControl);
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        // mVideoView = (VideoView) findViewById(R.id.videoView);
        findViewById(R.id.main).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (v.onTouchEvent(event)) {
                    return true;
                } else if (mGestureDetector.onTouchEvent(event)) {
                    return true;
                } else {
                    return false;
                }
            }
        });

        findViewById(R.id.btnStop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                carStop();
            }
        });

        final ToggleButton tglConnect = (ToggleButton) findViewById(R.id.tglConnect);
        tglConnect.setOnClickListener(new ToggleButton.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean connected = mSender.connect();
                tglConnect.setChecked(connected);
                Toast.makeText(getBaseContext(), "Connection " + (connected ? "established." : "error."),
                        Toast.LENGTH_SHORT).show();
            }

        });

        final ToggleButton tglWheel = (ToggleButton) findViewById(R.id.tglWheel);
        tglWheel.setOnClickListener(new ToggleButton.OnClickListener() {
            @Override
            public void onClick(View v) {
                mWheelEnabled = tglWheel.isChecked();
                Toast.makeText(getBaseContext(), "Wheel turned " + (mWheelEnabled ? "ON" : "OFF"), Toast.LENGTH_SHORT)
                        .show();
            }

        });

        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ALL),
                SensorManager.SENSOR_DELAY_NORMAL);

    }

    void changeCarPower(int powerIncrement) {
        mPower = powerIncrement + mPower;
        if (mPower > 100)
            mPower = 100;
        if (mPower < -100)
            mPower = -100;
        mSender.send("power " + mPower);
        Log.d("Car", "Power:" + mPower);
    }

    void changeCarAngle(int angleIncrement) {
        mAngle = angleIncrement + mAngle;
        setCarAngle();
    }

    private void setCarAngle() {
        if (mAngle > 100)
            mAngle = 100;
        if (mAngle < -100)
            mAngle = -100;
        mSender.send("angle " + mAngle);
        Log.d("Car", "Angle:" + mAngle);
    }

    void carStop() {
        changeCarPower(-mPower);
        changeCarAngle(-mAngle);
        Log.d("Car", "Stop");
    }

    private class ControlGestureListner extends SimpleOnGestureListener {

        @Override
        public boolean onDown(MotionEvent e) {
            return true; // otherwise gesture-recognition seems to fail
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            final float BARIER = 20;
            final float abs = Math.abs(velocityY);
            if (abs > BARIER)
                changeCarPower(-(int) (100 * velocityY / abs));
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            Log.d("EVENT", "LongPress");
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            changeCarPower((int) distanceY);
            changeCarAngle(-(int) distanceX);
            return true;
        }

    }

}
