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
import android.view.ViewGroup.LayoutParams;
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
    private boolean                                            mWheelEnabled;
    protected SenderService                                    mSender;
    private SharedPreferences.OnSharedPreferenceChangeListener mSharedPreferencesListener;

    private VideoView                                          mVideo;

    @Override
    public void onAccuracyChanged(final Sensor arg0, final int arg1) {
        // TODO Auto-generated method stub

    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSender = new SenderService(this);

        recreateMagicButtons(5);

        {
            mSender.setOnDiconnectedListner(new OnEventListener<String>() {
                @Override
                public void onEvent(final String reason) {
                    toast("Disconnected." + reason);
                }
            });
        }

        {
            final ToggleButton tglWheel = (ToggleButton) findViewById(R.id.tglWheel);
            tglWheel.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(final CompoundButton buttonView,
                        final boolean isChecked) {
                    mWheelEnabled = isChecked;
                }
            });
        }

        {
            final Button btnSettings = (Button) findViewById(R.id.btnSettings);
            btnSettings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    final Intent settings = new Intent(MainActivity.this,
                            SettingsActivity.class);
                    startActivity(settings);
                }
            });
        }

        {

            // TODO: mVideo = (VideoView) findViewById(R.id.video);
            mVideo.setOnErrorListener(new OnErrorListener() {

                @Override
                public boolean onError(final MediaPlayer mp, final int what,
                        final int extra) {
                    final String errorStr = "What = " + what + ", extra = "
                            + extra;
                    Log.e("VIDEO", errorStr);
                    toast("Error playing video stream " + errorStr);
                    // mVideo.stopPlayback();
                    // mVideo.setBackgroundColor(Color.TRANSPARENT);
                    return true;
                }
            });

            mVideo.setOnCompletionListener(new OnCompletionListener() {
                @Override
                public void onCompletion(final MediaPlayer mp) {
                    Log.i("VIDEO", "End of video stream encountered.");
                    mVideo.resume(); // TODO: Keep-alive instead of this hack
                }
            });

            mVideo.setOnPreparedListener(new OnPreparedListener() {

                @Override
                public void onPrepared(final MediaPlayer mp) {
                    // TODO: Stretch/scale video
                    mp.setLooping(true); // TODO: Doesn't work :(
                    mVideo.start();
                }
            });

            // video starts playing after URI is read from prefs later
        }

        {
            final View controlsOverlay = findViewById(R.id.controlsOverlay);
            controlsOverlay.bringToFront();
        }
        final View pad1 = findViewById(R.id.leftPad);
        pad1.setOnTouchListener(new TouchPadListener(pad1, "pad 1", mSender));

        final View pad2 = findViewById(R.id.rightPad);
        pad2.setOnTouchListener(new TouchPadListener(pad2, "pad 2", mSender));

        {
            final SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(getBaseContext());
            mSharedPreferencesListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(
                        final SharedPreferences sharedPreferences,
                        final String key) {
                    final String addr = sharedPreferences.getString(
                            SettingsActivity.SK_HOST_ADDRESS, "127.0.0.1");
                    int portNumber = 4444;
                    final String portStr = sharedPreferences.getString(
                            SettingsActivity.SK_HOST_PORT, "4444");
                    try {
                        portNumber = Integer.parseInt(portStr);
                    } catch (final NumberFormatException e) {
                        toast("Port number '" + portStr + "' is incorrect.");
                    }
                    mSender.setTarget(addr, portNumber);

                    {
                        final Boolean showPads = sharedPreferences.getBoolean(
                                SettingsActivity.SK_SHOW_PADS, true);
                        final Drawable padImage = showPads ? getResources()
                                .getDrawable(
                                        R.drawable.oxygen_actions_transform_move_icon)
                                : new ColorDrawable(Color.TRANSPARENT);
                        pad1.setBackgroundDrawable(padImage);
                        pad2.setBackgroundDrawable(padImage);

                    }

                    {
                        final String videoStreamURI = sharedPreferences
                                .getString(SettingsActivity.SK_VIDEO_URI, "");
                        // --no-sout-audio --sout
                        // "#transcode{width=320,height=240,vcodec=mp2v,fps=20}:rtp{ttl=5,sdp=rtsp://:8889/s}"
                        // works only with vcodec=mp4v without audio :(

                        // http://developer.android.com/reference/android/media/MediaPlayer.html
                        // http://developer.android.com/guide/appendix/media-formats.html

                        final Uri mVideoURI = videoStreamURI == null ? null
                                : Uri.parse(videoStreamURI);

                        if (mVideoURI != null) {
                            toast("Starting video from '" + videoStreamURI
                                    + "'.");
                            mVideo.setVideoURI(mVideoURI);
                        }
                    }

                }
            };
            mSharedPreferencesListener.onSharedPreferenceChanged(prefs,
                    SettingsActivity.SK_HOST_ADDRESS);
            prefs.registerOnSharedPreferenceChangeListener(mSharedPreferencesListener);
        }

    }

    @Override
    protected void onPause() {
        mVideo.stopPlayback();
        mSensorManager.unregisterListener(this);
        mSender.disconnect("Inactive gamepad");
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_ALL),
                SensorManager.SENSOR_DELAY_NORMAL);
        mVideo.resume();
    }

    @Override
    public void onSensorChanged(final SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if (!mWheelEnabled)
                return;
            processSensor(event.values);
        } else {
            Log.i("Sensor", "" + event.sensor.getType());
        }
    };

    private void processSensor(final float[] values) {
        final double WHEEL_BOOSTER_MULTIPLIER = 1.5;
        final double x = values[0];
        final double y = values[1];
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

    private void recreateMagicButtons(final int count) {
        final ViewGroup buttonsView = (ViewGroup) findViewById(R.id.buttons);
        buttonsView.removeAllViews();
        for (int num = 1; num <= count; ++num) {
            final Button btn = new Button(MainActivity.this);
            btn.setGravity(Gravity.CENTER);
            btn.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT));
            final String name = "" + num + "";
            btn.setText(name);
            btn.setPadding(10, 10, 10, 10);
            btn.setBackgroundResource(R.drawable.button_shape);

            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View arg0) {
                    mSender.send("btn " + name + " down"); // TBD: "up" via
                                                           // TouchListner
                }
            });
            buttonsView.addView(btn);
        }
    }

    void toast(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, text, Toast.LENGTH_LONG)
                        .show();
            }
        });
    }

}
