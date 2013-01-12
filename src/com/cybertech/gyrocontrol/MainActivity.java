package com.cybertech.gyrocontrol;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.app.Activity;
import android.content.pm.ActivityInfo;
//import android.content.pm.ActivityInfo;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends Activity implements SensorEventListener {
	TextView[]  mTextViews;
	
	float[] mCurrent;
	float[] mZero;
	//int i;
	
	private SensorManager mSensorManager;
    private void log () {
    	Log.w("me", Thread.currentThread().getStackTrace()[3].getMethodName());
    }
    
    public MainActivity() {    	
    	mCurrent = new float[3];
    	mZero = new float[3];    	
    	mTextViews = new TextView[4];
    }
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSensorManager=(SensorManager)getSystemService(SENSOR_SERVICE);        
        mTextViews[0] = (TextView)findViewById(R.id.x);        
        mTextViews[1] = (TextView)findViewById(R.id.y);        
        mTextViews[2] = (TextView)findViewById(R.id.z);        
        mTextViews[3] = (TextView)findViewById(R.id.norm);        
        
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);              
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {    	
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

	@Override
	public void onAccuracyChanged(Sensor arg0, int arg1) {
		// TODO Auto-generated method stub
	
	}

	@Override
	public void onSensorChanged(SensorEvent event) {		
		if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){			
			synchronized (this) {
				process(event.values);
			} 
		} else {
			Log.i("Sensor", ""+ event.sensor.getType());
		}
	}
		
	public void onClick(View view) {	
		//recalibrate();		
	}
	
    private void recalibrate() {
    	mZero = mCurrent.clone();    	
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
  
    private void process(float[] current) {    	
//        float m = 0;
//    	for (int i = 0; i < 3; ++i) { 
//    		final float d = mCurrent[i] - current[i]; 
//    		m += d * d; 
//    	}
//    	
//    	//if ( m <= 0.05) 
//    		//return;
//    	
    	float norm = 0;
        for (int i =  0; i < 3; ++i) {
        	norm += current[i] * current[i]; 
        }

        norm = (float)Math.sqrt(norm);
        
        for (int i =  0; i < 3; ++i) {
        	
        	current[i] = current[i]/norm;
        	
        	if (Math.abs(mCurrent[i] - current[i]) > 0.01) {
    		  mCurrent[i] = current[i];
    		  mTextViews[i].setText(Float.toString(mCurrent[i]));
        	}
        	
        }

        mTextViews[3].setText(Double.toString(norm));
        
    }
	
}
