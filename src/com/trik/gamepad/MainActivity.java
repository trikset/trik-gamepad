package com.trik.gamepad;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.widget.VideoView;

import com.trik.gamepad.SenderService.OnEventListener;

public class MainActivity extends Activity implements SensorEventListener {
    private SensorManager                                      mSensorManager;
    private int                                                mAngle;                    // -100%
                                                                                           // ...
                                                                                           // +100%
    private boolean                                            mWheelEnabled = false;
    protected SenderService                                    mSender;
    private SharedPreferences.OnSharedPreferenceChangeListener mSharedPreferencesListener;
    private VideoView                                          mVideo;

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
        mSender = new SenderService(this);

        recreateMagicButtons(5);

        {
            mSender.setOnDiconnectedListner(new OnEventListener<String>() {
                @Override
                public void onEvent(String reason) {
                    Toast.makeText(MainActivity.this, "Disconnected." + reason, Toast.LENGTH_SHORT).show();
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
            final Button btnSettings = (Button) findViewById(R.id.btnSettings);
            btnSettings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent settings = new Intent(MainActivity.this, SettingsActivity.class);
                    startActivity(settings);
                }
            });
        }

        {
            mVideo = (VideoView) findViewById(R.id.video);
            mVideo.setOnErrorListener(new OnErrorListener() {

                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    // TODO Auto-generated method stub
                    return false;
                }
            });

            mVideo.setOnCompletionListener(new OnCompletionListener() {

                @Override
                public void onCompletion(MediaPlayer mp) {
                    // TODO Auto-generated method stub

                }
            });

            mVideo.setOnPreparedListener(new OnPreparedListener() {

                @Override
                public void onPrepared(MediaPlayer mp) {
                    mVideo.start();
                }
            });

            // video starts playing after URI is read from prefs later
        }

        final View pad1 = findViewById(R.id.leftPad);
        pad1.setOnTouchListener(new TouchPadListener(pad1, "pad 1", mSender));

        final View pad2 = findViewById(R.id.rightPad);
        pad2.setOnTouchListener(new TouchPadListener(pad2, "pad 2", mSender));

        {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
            mSharedPreferencesListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                    String addr = sharedPreferences.getString(SettingsActivity.SK_HOST_ADDRESS, "127.0.0.1");
                    int portNumber = 4444;
                    final String portStr = sharedPreferences.getString(SettingsActivity.SK_HOST_PORT, "4444");
                    try {
                        portNumber = Integer.parseInt(portStr);
                    } catch (NumberFormatException e) {
                        Toast.makeText(MainActivity.this, "Port number '" + portStr + "' is incorrect.",
                                Toast.LENGTH_SHORT).show();
                    }
                    mSender.setTarget(addr, portNumber);

                    {
                        final Boolean showPads = sharedPreferences.getBoolean(SettingsActivity.SK_SHOW_PADS, true);
                        final Drawable padImage =
                                showPads ? getResources().getDrawable(R.drawable.touchpad)
                                        : new ColorDrawable(Color.TRANSPARENT);
                        pad1.setBackgroundDrawable(padImage);
                        pad2.setBackgroundDrawable(padImage);

                    }

                    {
                        String videoStreamURI = "http://" + addr + ":" + (portNumber * 2 + 1);
                        videoStreamURI = sharedPreferences.getString(SettingsActivity.SK_VIDEO_URI, videoStreamURI);
                        // cvlc -vvv video.mp4 --sout
                        // '#transcode{vcodec=h264,vb=100}:standard{access=http,mux=ts{use-key-frames},dst=:9090}'
                        Toast.makeText(MainActivity.this, "Starting video from '" + videoStreamURI + "'.",
                                Toast.LENGTH_LONG).show();
                        mVideo.setVideoURI(Uri.parse(videoStreamURI));
                    }

                }
            };
            mSharedPreferencesListener.onSharedPreferenceChanged(prefs, SettingsActivity.SK_HOST_ADDRESS);
            prefs.registerOnSharedPreferenceChangeListener(mSharedPreferencesListener);
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ALL),
                SensorManager.SENSOR_DELAY_NORMAL);
        mVideo.resume(); // or .start() ???
        {
            // send current config
            // final float hsv[] = new float[3];
            // Color.colorToHSV(PreferenceManager.getDefaultSharedPreferences(this)
            // .getInt("targetColor", 0), hsv);
            // final String hsvRepr = "H:" + hsv[0] + " S:" + hsv[1] + " V:" +
            // hsv[2];
            // mSender.send("config targetColor=\"" + hsvRepr + "\"");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if (!mWheelEnabled)
                return;
            processSensor(event.values);
        } else {
            Log.i("Sensor", "" + event.sensor.getType());
        }
    }

    @Override
    protected void onStop() {
        mVideo.stopPlayback();
        mSensorManager.unregisterListener(this);
        super.onStop();
    }

    private void processSensor(float[] current) {
        final float WHEEL_BOOSTER_MULTIPLIER = 1.5f;
        final float x = current[0];
        final float y = current[1];
        if (x < 1e-6)
            return;

        int angle = (int) (200 * WHEEL_BOOSTER_MULTIPLIER * Math.atan(y / x) / Math.PI);

        if (Math.abs(angle) < 10) {
            angle = 0;
        } else if (angle > 100) {
            angle = 100;
        } else if (angle < -100) {
            angle = -100;
        }

        if (Math.abs(mAngle - angle) < 7)
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
