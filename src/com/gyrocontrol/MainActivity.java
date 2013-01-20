package com.gyrocontrol;

import android.app.Activity;
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
import android.widget.VideoView;

//import android.content.pm.ActivityInfo;

public class MainActivity extends Activity implements SensorEventListener {
	// TextView[] mTextViews;

	private class ControlGestureListner extends SimpleOnGestureListener {

		@Override
		public boolean onDown(MotionEvent e) {
			return true; // otherwise gesture-recognition seems to fail
		}

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
				float velocityY) {
			Log.d("EVENT", "Fling");
			return super.onFling(e1, e2, velocityX, velocityY);
		}

		@Override
		public void onLongPress(MotionEvent e) {
			Log.d("EVENT", "LongPress");
		}

		@Override
		public boolean onScroll(MotionEvent e1, MotionEvent e2,
				float distanceX, float distanceY) {
			Log.d("EVENT", "Scroll " + distanceY);
			return true;
		}

		@Override
		public void onShowPress(MotionEvent e) {
			Log.d("EVENT", "ShowPress");
		}

		@Override
		public boolean onSingleTapConfirmed(MotionEvent e) {
			recalibrate();
			return true;
		}

	}

	float[] mCurrent;
	float[] mZero;
	GestureDetector mGestureDetector;

	VideoView mVideoView;
	// int i;

	// private void log () {
	// Log.w("me", Thread.currentThread().getStackTrace()[3].getMethodName());
	// }

	private SensorManager mSensorManager;

	public MainActivity() {
		mCurrent = new float[3];
		mZero = new float[3];
		mGestureDetector = new GestureDetector(new ControlGestureListner());
		// mTextViews = new TextView[4];
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
	public void onSensorChanged(SensorEvent event) {
		if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
			synchronized (this) {
				process(event.values);
			}
		} else {
			Log.i("Sensor", "" + event.sensor.getType());
		}
	}

	private void process(float[] current) {
		// float m = 0;
		// for (int i = 0; i < 3; ++i) {
		// final float d = mCurrent[i] - current[i];
		// m += d * d;
		// }
		//
		// //if ( m <= 0.05)
		// //return;
		//
		float norm = 0;
		for (int i = 0; i < 3; ++i) {
			norm += current[i] * current[i];
		}

		norm = (float) Math.sqrt(norm);

		for (int i = 0; i < 3; ++i) {

			current[i] = current[i] / norm;

			if (Math.abs(mCurrent[i] - current[i]) > 0.01) {
				mCurrent[i] = current[i];
				// mTextViews[i].setText(Float.toString(mCurrent[i]));
			}

		}

		// mTextViews[3].setText(Double.toString(norm));

	}

	private void recalibrate() {
		mZero = mCurrent.clone();
		mZero[2] = 0;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		mVideoView = (VideoView) findViewById(R.id.videoView);
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
		// mTextViews[0] = (TextView)findViewById(R.id.x);
		// mTextViews[1] = (TextView)findViewById(R.id.y);
		// mTextViews[2] = (TextView)findViewById(R.id.z);
		// mTextViews[3] = (TextView)findViewById(R.id.norm);

		this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
	}

	@Override
	protected void onResume() {
		super.onResume();
		mSensorManager.registerListener(this,
				mSensorManager.getDefaultSensor(Sensor.TYPE_ALL),
				SensorManager.SENSOR_DELAY_NORMAL);

	}

	@Override
	protected void onStop() {
		mSensorManager.unregisterListener(this);
		super.onStop();
	}

}
