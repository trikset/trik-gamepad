package com.trik.gamepad

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.view.MenuItemCompat
import android.support.v7.app.ActionBar
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast

import com.demo.mjpeg.MjpegView
import com.trik.gamepad.SenderService.OnEventListener

import java.net.URI
import java.net.URISyntaxException

class MainActivity : AppCompatActivity(), SensorEventListener {
    private val mHideRunnable = HideRunnable()
    private var mSensorManager: SensorManager? = null
    private var mAngle: Int = 0                     // -100%
    // ...
    // +100%
    private var mWheelEnabled: Boolean = false
    private var mSender: SenderService? = null
    private var mWheelStep = 7
    private var mVideo: MjpegView? = null
    private var mVideoURI: URI? = null

    // @SuppressWarnings("deprecation")
    // @TargetApi(16)
    private fun createPad(id: Int, strId: String) {
        val pad = findViewById(id) as SquareTouchPadLayout
        pad.padName = "pad " + strId
        pad.setSender(mSender as SenderService)
        // if (android.os.Build.VERSION.SDK_INT >= 16) {
        // pad.setBackground(image);
        // } else {
        // pad.setBackgroundDrawable(image);
        // }
    }

    override fun onAccuracyChanged(arg0: Sensor, arg1: Int) {
        // TODO Auto-generated method stub

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        //supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSystemUiVisibility(false)
        run {
            val a = supportActionBar
            if (a != null) {

                a.setDisplayShowHomeEnabled(true)
                a.setDisplayUseLogoEnabled(false)
                a.setLogo(R.drawable.trik_icon)

                a.setDisplayShowTitleEnabled(true)
            }
        }

        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mSender = SenderService(this)

        run {
            mVideo = findViewById(R.id.video) as MjpegView
            mVideo!!.setOverlayPosition(MjpegView.POSITION_UPPER_RIGHT)
            mVideo!!.showFps(true)


        }

        recreateMagicButtons(5)

        run {
            mSender!!.setOnDiconnectedListner(object : OnEventListener<String> {
                override fun onEvent(reason: String) {
                    toast("Disconnected." + reason)
                }
            })
        }

        /*
         * { final ToggleButton tglWheel = (ToggleButton)
         * findViewById(R.id.tglWheel); tglWheel.setOnCheckedChangeListener(new
         * CompoundButton.OnCheckedChangeListener() {
         *
         * @Override public void onCheckedChanged(final CompoundButton
         * buttonView, final boolean isChecked) { mWheelEnabled = isChecked; }
         * }); }
         */

        run {
            val btnSettings = findViewById(R.id.btnSettings) as Button
            btnSettings.setOnClickListener(object : View.OnClickListener {

                override fun onClick(v: View) {
                    val a = supportActionBar
                    if (a != null)
                        setSystemUiVisibility(!a.isShowing)
                }
            })

        }

        run {
            val controlsOverlay = findViewById(R.id.controlsOverlay)
            controlsOverlay.bringToFront()
        }

        run {
            createPad(R.id.leftPad, "1")
            createPad(R.id.rightPad, "2")
        }

        run {

            val prefs = PreferenceManager.getDefaultSharedPreferences(baseContext)
            val mSharedPreferencesListener = object : SharedPreferences.OnSharedPreferenceChangeListener {
                internal var mPrevAlpha: Float = 0.toFloat()

                override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
                    val addr = sharedPreferences.getString(SettingsActivity.SK_HOST_ADDRESS, "192.168.77.1")
                    var portNumber = 4444
                    val portStr = sharedPreferences.getString(SettingsActivity.SK_HOST_PORT, "4444")
                    try {
                        portNumber = Integer.parseInt(portStr)
                    } catch (e: NumberFormatException) {
                        toast("Port number '$portStr' is incorrect.")
                    }

                    val oldAddr = mSender!!.hostAddr
                    mSender!!.setTarget(addr, portNumber)

                    if (!addr.equals(oldAddr, ignoreCase = true)) {
                        val a = supportActionBar
                        if (a != null)
                            a.title = addr

                        // update video stream URI when target addr changed
                        sharedPreferences.edit().putString(SettingsActivity.SK_VIDEO_URI, "http://$addr:8080/?action=stream").commit()
                    }

                    run {
                        val defAlpha = 100
                        var padsAlpha = defAlpha

                        try {
                            val optAlpha = sharedPreferences.getString(SettingsActivity.SK_SHOW_PADS,
                                    defAlpha.toString())
                            padsAlpha = Integer.parseInt(optAlpha)
                        } catch (nfe: NumberFormatException) {
                            // unchanged
                        }

                        val alpha = Math.max(0, Math.min(255, padsAlpha)) / 255.0f
                        val alphaUp = AlphaAnimation(mPrevAlpha, alpha)
                        mPrevAlpha = alpha
                        alphaUp.fillAfter = true
                        alphaUp.duration = 2000
                        findViewById(R.id.controlsOverlay).startAnimation(alphaUp)
                        findViewById(R.id.buttons).startAnimation(alphaUp)
                    }

                    run { // "http://trackfield.webcam.oregonstate.edu/axis-cgi/mjpg/video.cgi?resolution=320x240";

                        val videoStreamURI = sharedPreferences.getString(SettingsActivity.SK_VIDEO_URI, "http://$addr:8080/?action=stream")

                        // --no-sout-audio --sout
                        // "#transcode{width=320,height=240,vcodec=mp2v,fps=20}:rtp{ttl=5,sdp=rtsp://:8889/s}"
                        // works only with vcodec=mp4v without audio :(

                        // http://developer.android.com/reference/android/media/MediaPlayer.html
                        // http://developer.android.com/guide/appendix/media-formats.html

                        try {
                            mVideoURI = if ("" == videoStreamURI)
                                null
                            else
                                URI(
                                        videoStreamURI)
                        } catch (e: URISyntaxException) {
                            toast("Illegal video stream URI\n" + e.reason)
                            mVideoURI = null
                        }


                    }

                    run {
                        mWheelStep = Integer.getInteger(
                                sharedPreferences.getString(SettingsActivity.SK_WHEEL_STEP,
                                        mWheelStep.toString()), mWheelStep)!!
                        mWheelStep = Math.max(1, Math.min(100, mWheelStep))
                    }

                }
            }
            mSharedPreferencesListener.onSharedPreferenceChanged(prefs, SettingsActivity.SK_HOST_ADDRESS)
            prefs.registerOnSharedPreferenceChangeListener(mSharedPreferencesListener)
        }

    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu, menu)

        // TODO: remove this hack
        val w = MenuItemCompat.getActionView(menu.findItem(R.id.wheel)) as CheckBox
        w.text = "WHEEL"

        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            R.id.settings -> {
                val settings = Intent(this@MainActivity,  SettingsActivity::class.java)
                startActivity(settings)
                return true
            }
            R.id.wheel -> {
                mWheelEnabled = !mWheelEnabled
                item.setChecked(mWheelEnabled)
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }

    }

    override fun onPause() {
        mSensorManager!!.unregisterListener(this)
        mSender!!.disconnect("Inactive gamepad")
        mVideo!!.stopPlayback()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        StartReadMjpegAsync(mVideo as MjpegView).execute(mVideoURI)

        mSensorManager!!.registerListener(this, mSensorManager!!.getDefaultSensor(Sensor.TYPE_ALL),
                SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            if (!mWheelEnabled)
                return
            processSensor(event.values)
        } else {
            Log.i("Sensor", "" + event.sensor.type)
        }
    }

    private fun processSensor(values: FloatArray) {
        val WHEEL_BOOSTER_MULTIPLIER = 1.5
        val x = values[0].toDouble()
        val y = values[1].toDouble()
        if (x < 1e-6)
        return

        var angle = (200.0 * WHEEL_BOOSTER_MULTIPLIER * Math.atan2(y, x) / Math.PI).toInt()

        if (Math.abs(angle) < 10) {
            angle = 0
        } else if (angle > 100) {
            angle = 100
        } else if (angle < -100) {
            angle = -100
        }

        if (Math.abs(mAngle - angle) < mWheelStep)
            return

        mAngle = angle

        mSender!!.send("wheel " + mAngle)
    }

    private fun recreateMagicButtons(count: Int) {
        val buttonsView = findViewById(R.id.buttons) as ViewGroup
        buttonsView.removeAllViews()
        for (num in 1..count) {
            val btn = Button(this@MainActivity)
            btn.isHapticFeedbackEnabled = true
            btn.gravity = Gravity.CENTER
            btn.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            val name = "" + num
            btn.text = name
            btn.setBackgroundResource(R.drawable.button_shape)

            btn.setOnClickListener(object : View.OnClickListener {
                override fun onClick(arg0: View) {
                    mSender!!.send("btn $name down") // TODO: "up" via
                    // TouchListner
                    btn.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
                            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                }
            })
            buttonsView.addView(btn)
        }
    }

    @SuppressLint("NewApi")
    private fun setSystemUiVisibility(show: Boolean) {
        var flags = 0
        val mainView = findViewById(R.id.main)

        val sdk = Build.VERSION.SDK_INT



        if (sdk >= 14 /*Build.VERSION_CODES.ICE_CREAM_SANDWICH*/) {
            flags = flags or (if (show)
                View.SYSTEM_UI_FLAG_VISIBLE
            else
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LOW_PROFILE)

            if (sdk >= 16 /*Build.VERSION_CODES.JELLY_BEAN*/) {
                flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE

                flags = flags or (if (show) 0 else View.SYSTEM_UI_FLAG_FULLSCREEN)

                if (sdk >= 19 /*Build.VERSION_CODES.KITKAT*/) {
                    flags = flags or (if (show) 0 else View.SYSTEM_UI_FLAG_IMMERSIVE)
                    //Not using View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY; we use handcrafted autohide
                }

            }

            mainView.systemUiVisibility = flags
        } else {
            val a = supportActionBar
            if (a != null) {
                if (show)
                    a.show()
                else
                    a.hide()
            }
        }//        if (sdk >= Build.VERSION_CODES.HONEYCOMB) {
        //            flags |= show ? View.STATUS_BAR_VISIBLE:View.STATUS_BAR_HIDDEN;
        //        }

        if (show) {
            mainView.removeCallbacks(mHideRunnable)
            mainView.postDelayed(mHideRunnable, 2000)
        }

    }

    private fun toast(text: String) {
        runOnUiThread(object : Runnable {
            override fun run() {
                Toast.makeText(this@MainActivity, text, Toast.LENGTH_LONG).show()
            }
        })
    }

    private inner class HideRunnable : Runnable {

        override fun run() {
            setSystemUiVisibility(false)

        }
    }

    companion object {

        internal val TAG = "MainActivity"
    }
}
