package com.example.signalcollect;

import java.text.SimpleDateFormat;


import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.StrictMode;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;
import android.view.Menu;
import android.widget.TextView;

@SuppressLint("NewApi")
public class SignalCollect extends Activity {

	//power management
	PowerManager powerManager = null;
	WakeLock wakeLock = null;
	
	boolean stateDebug = true;
	private static final String TAG = "State_Info";  
	static BackgroundCollecting backCollect = null;
	
	// global variables
	int runningTime = 0;
	double gpsLat=0,gpsLon=0;
	
	// Location manager
	private LocationManager locManager = null;   
	//Tele manager
	TelephonyManager teleManager = null;
	//WiFi manager
	WifiManager wifiManager = null;
	
	//textViews
	TextView tvTick = null; 
	TextView tvGPS = null;
	TextView tvModel = null;
	TextView tvDebug = null;
	TextView tvWiFi = null;
	

	
	//handler, receiving latest info from background service and display it
	private Handler myHandler = new Handler(){  
	          
	        public void handleMessage(Message msg) {
	        	if (msg.what == 1)
	        	{
	        		Bundle bundle = msg.getData();
	        		runningTime =Integer.parseInt((String) bundle.get("tick"));
	        		if (bundle.getString("lat")!=null)
	        		gpsLat = Double.parseDouble(bundle.getString("lat"));
	        		if (bundle.getString("lon")!=null)
	        		gpsLon = Double.parseDouble(bundle.getString("lon"));
	        		if (bundle.getString("neighbor")!=null)
	        			tvDebug.setText(bundle.getString("neighbor"));
	        		Log.e("Counter", Integer.toString(runningTime));
	        		
	        		if (tvWiFi != null)
	        		{
	        			tvWiFi.setText((String)bundle.get("wifi"));
	        		}
	        	}
	        	SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd");    
	        	String date=sdf.format(new java.util.Date()); 
	        	tvTick.setText(Integer.toString(runningTime)+" "+date);
	        	tvGPS.setText("lat:" + gpsLat + "\nlon:" + gpsLon);
	        };  
	};  
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_signal_collect);
		
		if (android.os.Build.VERSION.SDK_INT > 9) {
		    StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
		    StrictMode.setThreadPolicy(policy);
		}
		
		/*
		 * Hold the CPU
		*/
		powerManager = (PowerManager) getSystemService(POWER_SERVICE);
		wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"MyWakelockTag");
		wakeLock.acquire();
		
		tvTick = (TextView) findViewById(R.id.tvTick);
		tvGPS = (TextView) findViewById(R.id.txViewGPS);
		tvModel = (TextView) findViewById(R.id.txViewModel);
		
		String MODEL_NAME = Build.MODEL;
		MODEL_NAME = MODEL_NAME.replace(" ", "_");
		MODEL_NAME = MODEL_NAME.replace("-", "_");
		tvModel.setText(MODEL_NAME);
		
		tvDebug = (TextView) findViewById(R.id.txViewDebug);
		tvWiFi = (TextView) findViewById(R.id.wifiInfo);
		
		//启动采集服务 start collecting information
		// 获取系统LocationManager服务  
        locManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);  
        //获取tele manager
        teleManager = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        wifiManager = (WifiManager)getSystemService(Context.WIFI_SERVICE);  
        //Gsm manager
        if (backCollect == null)
        {
        	backCollect = new BackgroundCollecting();
        	backCollect.start(new Messenger(myHandler),locManager,teleManager,wifiManager,MODEL_NAME);
        }
        

        
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.signal_collect, menu);
		return true;
	}
	@Override
	protected void onStart() {  
        super.onStart();
        
        //------------------------
        if (stateDebug) Log.e(TAG, "###start onStart###");
    }  
    @Override  
    protected void onPause() {  
        super.onPause();  
        if (stateDebug) Log.e(TAG, "###start onPause###");  
    }  
    @Override  
    protected void onStop() {  
        super.onStop();  
        if (stateDebug) Log.e(TAG, "###start onStop###");  
    }  
    @Override  
    protected void onDestroy() {  
        super.onDestroy();  
        if (stateDebug) Log.e(TAG, "###onDestroy###");
        //关闭采集服务
        //close the collecting service
        wakeLock.release();
        if (backCollect!=null)
        backCollect.end();
    }  	
    
    @Override  
    public void onBackPressed() {
    	// back键不会退出，而是隐藏界面
    	// when user press 'BACK' button, hide user interface, do not exit
        Intent i= new Intent(Intent.ACTION_MAIN);   
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);   
        i.addCategory(Intent.CATEGORY_HOME);   
        startActivity(i);  
    }
}

