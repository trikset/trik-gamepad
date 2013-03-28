package com.trik.car;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.trik.handy.R;

public class MainActivity extends Activity {

    // GestureDetector mGestureDetector;

    // private SensorManager mSensorManager;

    // private int mPower; // -100% ... +100%
    // private int mAngle; // -100% ... +100%
    protected SenderService mSender;

    // private boolean mWheelEnabled = false;

    // private TextView mDirectionView;

    public MainActivity() {
        // mGestureDetector = new GestureDetector(new ControlGestureListner());
        mSender = new SenderService();
    }

    // @Override
    // public void onAccuracyChanged(Sensor arg0, int arg1) {
    // // TODO Auto-generated method stub
    //
    // }

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

    // @Override
    // public void onSensorChanged(SensorEvent event) {
    // if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
    // if (!mWheelEnabled)
    // return;
    // synchronized (this) {
    // process(event.values);
    // }
    // } else {
    // Log.i("Sensor", "" + event.sensor.getType());
    // }
    // }

    // private void process(float[] current) {
    // final float WHEEL_BOOSTER_MULTIPLIER = 1.5f;
    // final float x = current[0];
    // final float y = current[1];
    // // final float norm = (float) Math.sqrt(x*x+y*y); // w/o Z
    //
    // final int angle = (int) (200 * WHEEL_BOOSTER_MULTIPLIER * Math.atan(y /
    // x) / Math.PI);
    //
    // setCarAngle(angle);
    //
    // }

    // @Override
    // protected void onStop() {
    // mSensorManager.unregisterListener(this);
    // super.onStop();
    // }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // mDirectionView = (TextView) findViewById(R.id.tvControl);
        // mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        // mVideoView = (VideoView) findViewById(R.id.videoView);
        // findViewById(R.id.main).setOnTouchListener(new View.OnTouchListener()
        // {
        // @Override
        // public boolean onTouch(View v, MotionEvent event) {
        // if (v.onTouchEvent(event)) {
        // return true;
        // } else if (mGestureDetector.onTouchEvent(event)) {
        // return true;
        // } else {
        // return false;
        // }
        // }
        // });

        final Button btnForward = (Button) findViewById(R.id.btnForward);
        // btnForward.setOnTouchListener(new OnTouchListener() {
        // @Override
        // public boolean onTouch(View v, MotionEvent event) {
        // // TODO Auto-generated method stub
        // return false;
        // }
        // })

        btnForward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String speed = btnForward.isPressed() ? "100" : "0";
                mSender.send("left " + speed);
                mSender.send("right " + speed);

            }
        });

        final Button btnBackward = (Button) findViewById(R.id.btnBackward);
        btnBackward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String speed = btnBackward.isPressed() ? "-100" : "0";
                mSender.send("left " + speed);
                mSender.send("right " + speed);

            }
        });

        final Button btnLeft = (Button) findViewById(R.id.btnLeft);
        btnLeft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String speed = btnLeft.isPressed() ? "100" : "0";
                mSender.send("left " + speed);
                mSender.send("right -" + speed);

            }
        });

        final Button btnRight = (Button) findViewById(R.id.btnRight);
        btnRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String speed = btnRight.isPressed() ? "100" : "0";
                mSender.send("left -" + speed);
                mSender.send("right " + speed);
            }
        });

        final Button btnStop = (Button) findViewById(R.id.btnStop);
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // String speed = btnStop.isPressed() ? "100" : "0";
                mSender.send("left 0");
                mSender.send("right 0");
            }
        });

        // ((SeekBar) findViewById(R.id.sbPower)).setOnSeekBarChangeListener(new
        // OnSeekBarChangeListener() {
        // @Override
        // public void onStopTrackingTouch(SeekBar seekBar) {
        //
        // }
        //
        // @Override
        // public void onProgressChanged(SeekBar seekBar, int progress, boolean
        // fromUser) {
        // setCarPower(progress);
        // }
        //
        // @Override
        // public void onStartTrackingTouch(SeekBar seekBar) {
        // // TODO Auto-generated method stub
        //
        // }
        // });

        final ToggleButton tglConnect = (ToggleButton) findViewById(R.id.tglConnect);
        tglConnect.setOnClickListener(new ToggleButton.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean connected;
                String toastText;
                if (tglConnect.isChecked())
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

        final ToggleButton tglHand = (ToggleButton) findViewById(R.id.tglHand);
        tglHand.setOnClickListener(new ToggleButton.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSender.send("hand " + (tglHand.isChecked() ? "-100" : "100"));
                // Toast.makeText(getBaseContext(), "Wheel turned " +
                // (mWheelEnabled ? "ON" : "OFF"), Toast.LENGTH_SHORT)
                // .show();

            }

        });

        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }
    // @Override
    // protected void onResume() {
    // super.onResume();
    // mSensorManager.registerListener(this,
    // mSensorManager.getDefaultSensor(Sensor.TYPE_ALL),
    // SensorManager.SENSOR_DELAY_NORMAL);
    //
    // }
}
