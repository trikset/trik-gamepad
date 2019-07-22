package com.trikset.gamepad;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuItemCompat;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import com.demo.mjpeg.MjpegView;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Locale;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    static final String TAG = "MainActivity";
    private HideRunnable mHideRunnable;
    @Nullable
    private Runnable mRestartCallback;
    private SensorManager mSensorManager;
    private int mAngle;                     // -100%
    // ...
    // +100%
    private boolean mWheelEnabled;
    private SenderService mSender;
    private int mWheelStep = 7;
    @Nullable
    private MjpegView mVideo;
    @Nullable
    private URL mVideoURL;
    @Nullable
    private SharedPreferences.OnSharedPreferenceChangeListener mSharedPreferencesListener;

    // @SuppressWarnings("deprecation")
    // @TargetApi(16)
    private void createPad(int id, String strId) {
        final SquareTouchPadLayout pad = findViewById(id);
        if (pad != null) {
            pad.setPadName("pad " + strId);
            pad.setSender(getSenderService());
        }
        // if (android.os.Build.VERSION.SDK_INT >= 16) {
        // pad.setBackground(image);
        // } else {
        // pad.setBackgroundDrawable(image);
        // }
    }

    @Override
    public void onAccuracyChanged(final Sensor arg0, final int arg1) {
        // TODO Auto-generated method stub

    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        //supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setHideRunnable(new HideRunnable());
        setSystemUiVisibility(false);
        {
            ActionBar a = getSupportActionBar();
            if (a != null) {

                a.setDisplayShowHomeEnabled(true);
                a.setDisplayUseLogoEnabled(false);
                a.setLogo(R.drawable.trik_gamepad_logo_512x512);

                a.setDisplayShowTitleEnabled(true);
            }
        }

        setSenderService(new SenderService());
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);


        mVideo = findViewById(R.id.video);

        recreateMagicButtons(5);

        {
            getSenderService().setOnDisconnectedListener(new SenderService.OnEventListener<String>() {
                @Override
                public void onEvent(String reason) {
                    toast("Disconnected." + reason);
                }
            });
            getSenderService().setShowTextCallback(new SenderService.OnEventListener<String>() {
                @Override
                public void onEvent(String text) {
                    toast(text);
                }
            });
        }

        {
            final Button btnSettings = findViewById(R.id.btnSettings);
            if (btnSettings != null) {
                btnSettings.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(final View v) {
                        ActionBar a = getSupportActionBar();
                        if (a != null)
                            setSystemUiVisibility(!a.isShowing());
                    }
                });
            }
        }

        {
            final View controlsOverlay = findViewById(R.id.controlsOverlay);
            if (controlsOverlay != null)
                controlsOverlay.bringToFront();
        }

        {
            createPad(R.id.leftPad, "1");
            createPad(R.id.rightPad, "2");
        }

        {

            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
            mSharedPreferencesListener
                    = new SharedPreferences.OnSharedPreferenceChangeListener() {
                private float mPrevAlpha;

                @Override
                public void onSharedPreferenceChanged(@NonNull final SharedPreferences sharedPreferences, final String key) {
                    final String addr = sharedPreferences.getString(SettingsFragment.SK_HOST_ADDRESS, "192.168.77.1");
                    int portNumber = 4444;
                    final String portStr = sharedPreferences.getString(SettingsFragment.SK_HOST_PORT, "4444");
                    try {
                        portNumber = Integer.parseInt(portStr);
                    } catch (@NonNull final NumberFormatException e) {
                        toast("Port number '" + portStr + "' is incorrect.");
                    }
                    final String oldAddr = getSenderService().getHostAddr();
                    getSenderService().setTarget(addr, portNumber);

                    {
                        ActionBar a = getSupportActionBar();

                        if (a != null)
                            a.setTitle(addr);
                        else
                            toast("Can not change title, not a problem");

                    }

                    if (!addr.equalsIgnoreCase(oldAddr)) {

                        // update video stream URI when target addr changed
                        sharedPreferences.edit()
                                .putString(SettingsFragment.SK_VIDEO_URI, "http://" + addr.trim()
                                        + ":8080/?action=stream")
                                .apply();
                    }

                    {
                        final int defAlpha = 100;
                        int padsAlpha = defAlpha;

                        try {
                            padsAlpha = Integer.parseInt(sharedPreferences.getString(SettingsFragment.SK_SHOW_PADS,
                                    String.valueOf(defAlpha)));
                        } catch (NumberFormatException nfe) {
                            // unchanged
                        }

                        final float alpha = Math.max(0, Math.min(255, padsAlpha)) / 255.0f;
                        AlphaAnimation alphaUp = new AlphaAnimation(mPrevAlpha, alpha);
                        mPrevAlpha = alpha;
                        alphaUp.setFillAfter(true);
                        alphaUp.setDuration(2000);
                        final View co = findViewById(R.id.controlsOverlay);
                        if (co != null)
                            co.startAnimation(alphaUp);
                        final View btns = findViewById(R.id.buttons);
                        if (btns != null)
                            btns.startAnimation(alphaUp);
                    }

                    {
                        // "http://trackfield.webcam.oregonstate.edu/axis-cgi/mjpg/video.cgi?resolution=320x240";

                        String videoStreamURI = sharedPreferences.getString(SettingsFragment.SK_VIDEO_URI, "http://"
                                + addr + ":8080/?action=stream");

                        // --no-sout-audio --sout
                        // "#transcode{width=320,height=240,vcodec=mp2v,fps=20}:rtp{ttl=5,sdp=rtsp://:8889/s}"
                        // works only with vcodec=mp4v without audio :(

                        // http://developer.android.com/reference/android/media/MediaPlayer.html
                        // http://developer.android.com/guide/appendix/media-formats.html

                        try {
                            mVideoURL = "".equals(videoStreamURI) ? null : new URI(
                                    videoStreamURI).toURL();
                        } catch (URISyntaxException | MalformedURLException e) {
                            toast("Illegal video stream URL");
                            Log.e(TAG, "onSharedPreferenceChanged: ", e);
                            mVideoURL = null;
                        }
                    }

                    {
                        mWheelStep = Integer
                                .getInteger(
                                        sharedPreferences.getString(SettingsFragment.SK_WHEEL_STEP,
                                                String.valueOf(mWheelStep)), mWheelStep);
                        mWheelStep = Math.max(1, Math.min(100, mWheelStep));
                    }

                    {
                        try {
                            final int timeout = Integer.parseInt(sharedPreferences.getString(
                                    SettingsFragment.SK_KEEPALIVE,
                                    Integer.toString(SenderService.DEFAULT_KEEPALIVE)));
                            if (timeout < SenderService.MINIMAL_KEEPALIVE) {
                                toast(String.format(
                                        Locale.US,
                                        getString(R.string.keepalive_must_be_not_less),
                                        SenderService.MINIMAL_KEEPALIVE));

                                sharedPreferences
                                        .edit()
                                        .putString(
                                                SettingsFragment.SK_KEEPALIVE,
                                                Integer.toString(getSenderService().getKeepaliveTimeout()))
                                        .apply();
                            } else {
                                getSenderService().setKeepaliveTimeout(timeout);
                            }
                        } catch (NumberFormatException e) {
                            toast(getString(R.string.keepalive_must_be_positive_decimal));

                            sharedPreferences
                                    .edit()
                                    .putString(
                                            SettingsFragment.SK_KEEPALIVE,
                                            Integer.toString(getSenderService().getKeepaliveTimeout()))
                                    .apply();
                        }
                    }
                }
            };
            mSharedPreferencesListener.onSharedPreferenceChanged(prefs, SettingsFragment.SK_HOST_ADDRESS);
            prefs.registerOnSharedPreferenceChangeListener(mSharedPreferencesListener);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);

        // TODO: remove this hack
        final CheckBox w = (CheckBox) MenuItemCompat.getActionView(menu.findItem(R.id.wheel));
        w.setText(getResources().getString(R.string.menu_wheel));

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settings:
                final Intent settings = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(settings);
                return true;
            case R.id.wheel:
                mWheelEnabled = !mWheelEnabled;
                item.setChecked(mWheelEnabled);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }

    }

    @Override
    protected void onPause() {
        mSensorManager.unregisterListener(this);
        getSenderService().disconnect("Inactive gamepad");
        if(mVideo!=null) {
            mVideo.stopPlayback();
            if (mRestartCallback != null) {
                mVideo.removeCallbacks(mRestartCallback);
                mRestartCallback = null;
            }
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mVideo != null) {
            if (mRestartCallback != null) {
                mVideo.removeCallbacks(mRestartCallback);
            }

            mRestartCallback = new Runnable() {
                @Override
                public void run() {
                    new StartReadMjpegAsync(mVideo).execute(mVideoURL);
                    if (mRestartCallback != null && mVideo != null)
                        // drop HTTP connection and restart
                        mVideo.postDelayed(mRestartCallback, 30000);
                 }
            };

            mVideo.post(mRestartCallback);
        }

        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ALL),
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onSensorChanged(@NonNull final SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if (!mWheelEnabled)
                return;
            processSensor(event.values);
        } else {
            Log.i("Sensor", String.valueOf(event.sensor.getType()));
        }
    }

    private void processSensor(final float[] values) {
        final double WHEEL_BOOSTER_MULTIPLIER = 1.5;
        final double x = values[0];
        final double y = values[1];
        if (x < 1e-6)
            return;

        int angle = (int) (200 * WHEEL_BOOSTER_MULTIPLIER * Math.atan2(y, x) / Math.PI);

        if (Math.abs(angle) < 10) {
            angle = 0;
        } else if (angle > 100) {
            angle = 100;
        } else if (angle < -100) {
            angle = -100;
        }

        if (Math.abs(mAngle - angle) < mWheelStep)
            return;

        mAngle = angle;

        getSenderService().send("wheel " + mAngle);
    }

    private void recreateMagicButtons(final int count) {
        final ViewGroup buttonsView = findViewById(R.id.buttons);
        if (buttonsView == null)
            return;
        buttonsView.removeAllViews();
        for (int num = 1; num <= count; ++num) {
            final Button btn = new Button(MainActivity.this);
            btn.setHapticFeedbackEnabled(true);
            btn.setGravity(Gravity.CENTER);
            btn.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            final String name = String.valueOf(num);
            btn.setText(name);
            btn.setBackgroundResource(R.drawable.button_shape);

            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View arg0) {
                    SenderService sender = getSenderService();
                    if (sender != null) {
                        sender.send("btn " + name + " down"); // TODO: "up" via
                        // TouchListner
                        btn.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
                                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                    }
                }
            });
            buttonsView.addView(btn);
        }
    }

    @SuppressLint("NewApi")
    private void setSystemUiVisibility(boolean show) {
        int flags = 0;
        final View mainView = findViewById(R.id.main);
        if (mainView == null)
            return;

        final int sdk = Build.VERSION.SDK_INT;

        if (sdk >= Build.VERSION_CODES.KITKAT) {
            flags |= show ? 0 : View.SYSTEM_UI_FLAG_IMMERSIVE;
            //Not using View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY; we use handcrafted autohide
        }

        if (sdk >= Build.VERSION_CODES.JELLY_BEAN) {
            flags |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;

            flags |= show ? 0 : View.SYSTEM_UI_FLAG_FULLSCREEN;
        }

        if (sdk >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            flags |= show ? View.SYSTEM_UI_FLAG_VISIBLE
                    : View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LOW_PROFILE;
            mainView.setSystemUiVisibility(flags);
        }

        else {
            ActionBar a = getSupportActionBar();
            if (a != null) {
                if (show)
                    a.show();
                else
                    a.hide();
            }
        }

        HideRunnable r = getHideRunnable();
        if (r != null) {
            mainView.removeCallbacks(r);
            mainView.postDelayed(r, 3000);
        }

    }

    private void toast(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, text, Toast.LENGTH_LONG).show();
            }
        });
    }

    public SenderService getSenderService() {
        return mSender;
    }

    public void setSenderService(SenderService sender) {
        this.mSender = sender;
    }

    public HideRunnable getHideRunnable() {
        return mHideRunnable;
    }

    public void setHideRunnable(HideRunnable r) {
        this.mHideRunnable = r;
    }

    private class HideRunnable implements Runnable {

        @Override
        public void run() {
            setSystemUiVisibility(false);

        }
    }

    @Override
    protected void onDestroy() {
        mSensorManager.unregisterListener(this);

        if (mVideo != null) {
            if (mRestartCallback != null)
                mVideo.removeCallbacks(mRestartCallback);
            mRestartCallback = null;

            mVideo.stopPlayback();
            mVideo = null;
        }
        final View mainView = findViewById(R.id.main);
        mainView.removeCallbacks(getHideRunnable());
        final ViewGroup buttonsView = findViewById(R.id.buttons);
        if (buttonsView != null) {
            for (int i = 0; i < buttonsView.getChildCount();
                 ++i) {
                buttonsView.getChildAt(i).setOnClickListener(null);
            }
        }

        final Button btnSettings = findViewById(R.id.btnSettings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(null);
        }

        final SquareTouchPadLayout pad1 = findViewById(R.id.leftPad);
        if (pad1 != null)
            pad1.setSender(null);

        final SquareTouchPadLayout pad2 = findViewById(R.id.rightPad);
        if (pad2 != null)
            pad2.setSender(null);


        PreferenceManager.getDefaultSharedPreferences(getBaseContext())
                .unregisterOnSharedPreferenceChangeListener(mSharedPreferencesListener);
        getSenderService().setOnDisconnectedListener(null);
        getSenderService().setShowTextCallback(null);
        setSenderService(null);
        setHideRunnable(null);
        super.onDestroy();
    }
}
