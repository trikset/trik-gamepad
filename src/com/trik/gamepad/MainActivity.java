package com.trik.gamepad;

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
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.trik.crab.R;
import com.trik.gamepad.SenderService.OnEventListener;

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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        recreateMagicButtons(5);

        {
            final ToggleButton tglConnect = (ToggleButton) findViewById(R.id.tglConnect);

            OnEventListener<String> onDisconnect = new OnEventListener<String>() {
                @Override
                public void onEvent(String reason) {
                    tglConnect.setChecked(false);
                    Toast.makeText(MainActivity.this, "Disconnected." + reason, Toast.LENGTH_SHORT).show();
                }
            };

            mSender.setOnDiconnectedListner(onDisconnect);

            tglConnect.setOnClickListener(new ToggleButton.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!tglConnect.isChecked())
                    {
                        mSender.disconnect("");
                    } else {
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
                        String addr = prefs.getString(SettingsActivity.SK_HOST_ADDRESS, "127.0.0.1");
                        boolean connected = mSender.connect(addr);
                        tglConnect.setChecked(connected);
                        Toast.makeText(MainActivity.this, "Connection " + (connected ? "established." : "error.")
                                , Toast.LENGTH_SHORT).show();
                    }
                }

            });
        }

        {
            final ToggleButton tglWheel = (ToggleButton) findViewById(R.id.tglWheel);
            tglWheel.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    mWheelEnabled = isChecked;
                }
            });
        }

        {
            final View pad = findViewById(R.id.leftPad);
            pad.setOnTouchListener(new TouchPadListener(pad, "pad 1", mSender));
        }
        {
            final View pad = findViewById(R.id.rightPad);
            pad.setOnTouchListener(new TouchPadListener(pad, "pad 2", mSender));
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
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ALL),
                SensorManager.SENSOR_DELAY_NORMAL);
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
    protected void onStop() {
        mSensorManager.unregisterListener(this);
        super.onStop();
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
                || Math.abs(mAngle - angle - 50) < 5 * WHEEL_BOOSTER_MULTIPLIER)
            return;

        mAngle = angle;

        mSender.send("wheel " + mAngle);
    }

    private void recreateMagicButtons(int count) {
        ViewGroup buttonsView = (ViewGroup) findViewById(R.id.buttons);
        buttonsView.removeAllViews();
        for (int i = 0; i < count; ++i) {
            final Button btn = new Button(MainActivity.this);
            btn.setGravity(Gravity.CENTER);
            final String name = i + 1 + "";
            btn.setText(name);
            btn.setPadding(10, 10, 10, 10);

            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    mSender.send("btn " + name + " down"); // TBD: "up" via
                                                           // TouchListner
                }
            });
            buttonsView.addView(btn);
        }
    }

}
