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

//import android.content.pm.ActivityInfo;

public class MainActivity extends Activity implements SensorEventListener {
	// TextView[] mTextViews;

	private class ControlGestureListner extends SimpleOnGestureListener {

		@Override
		public boolean onDown(MotionEvent e) {
			return true; // otherwise gesture-recognition seems to fail
		}

		// @Override
		// public boolean onFling(MotionEvent e1, MotionEvent e2, float
		// velocityX,
		// float velocityY) {
		// // Log.d("EVENT", "Fling");
		// if (velocityY > 0)
		// startCar();
		// else
		// stopCar();
		//
		// return super.onFling(e1, e2, velocityX, velocityY);
		// }

		@Override
		public void onLongPress(MotionEvent e) {
			Log.d("EVENT", "LongPress");
		}

		@Override
		public boolean onScroll(MotionEvent e1, MotionEvent e2,
				float distanceX, float distanceY) {
			// Log.d("EVENT", "Scroll " + distanceY);
			changeCarPower((int) distanceY);
			changeCarAngle(-(int) distanceX);
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

	float[] mCurrentAccel;
	float[] mZeroAccel;
	GestureDetector mGestureDetector;

	// VideoView mVideoView;

	private SensorManager mSensorManager;

	private int mPower; // -100% ... +100%
	private int mAngle; // -100% ... +100%

	public MainActivity() {
		mCurrentAccel = new float[3];
		mZeroAccel = new float[3];
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

			if (Math.abs(mCurrentAccel[i] - current[i]) > 0.01) {
				mCurrentAccel[i] = current[i];
				// mTextViews[i].setText(Float.toString(mCurrent[i]));
			}

		}

		// mTextViews[3].setText(Double.toString(norm));

	}

	private void recalibrate() {
		mZeroAccel = mCurrentAccel.clone();
		mZeroAccel[2] = 0;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

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

		findViewById(R.id.btnStop).setOnClickListener(
				new View.OnClickListener() {

					@Override
					public void onClick(View v) {
						carStop();
						// ((Button)v).sette

					}
				});
		// mTextViews[0] = (TextView)findViewById(R.id.x);
		// mTextViews[1] = (TextView)findViewById(R.id.y);
		// mTextViews[2] = (TextView)findViewById(R.id.z);
		// mTextViews[3] = (TextView)findViewById(R.id.norm);

		this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
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

	void changeCarPower(int powerIncrement) {
		mPower = powerIncrement + mPower;
		if (mPower > 100)
			mPower = 100;
		if (mPower < -100)
			mPower = -100;

		Log.d("Car", "Power:" + mPower);
	}

	void changeCarAngle(int angleIncrement) {
		mAngle = angleIncrement + mAngle;
		if (mAngle > 100)
			mAngle = 100;
		if (mAngle < -100)
			mAngle = -100;

		Log.d("Car", "Angle:" + mAngle);
	}

	void carStop() {
		if (mPower == 0)
			return;
		changeCarPower(-mPower);
		Log.d("Car", "Stop");
	}
}
