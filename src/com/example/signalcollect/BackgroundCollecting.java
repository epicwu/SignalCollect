package com.example.signalcollect;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.text.format.Time;
import android.util.Log;

public class BackgroundCollecting {
	static String ServerIP = "82.130.27.36";
	
	TimerTask task = null;
	Timer timer = null;
	int tick;
	Messenger uiHandler = null;
	//GPS info
	LocationManager locManager = null;
	
	//cellid info
	TelephonyManager teleManager = null;
	int networkType;
	//
	GsmCellLocation gsmLoc = null;
	 
	double lat = 0,lon = 0;
	String MODEL,OPERATOR;
	int cdmaSS,gsmSS;
	MyPhoneStateListener myListener = null;
	
	//DB
	MongoHelper mongo = null;
	

	//Wifi manager
	WifiManager wifiManager = null;
	
	ArrayList<SignalRecord> rec =null;
	
	void updateView(Location loc)
	{
		if (loc == null)
		{
			lat = 0;
			lon = 0;
		} else
		{
			lat = loc.getLatitude();
			lon = loc.getLongitude();
		}
	}
	
	void start(Messenger ui, LocationManager loc,TelephonyManager telMa, WifiManager wifi, String model) {
        wifiManager = wifi;
		//mongo
        mongo = new MongoHelper(model,ServerIP);
        //------------------------
        mongo.connect();
        mongo.checkCollection();
		Log.e("checkCollection","Success!");
		uiHandler = ui;
		locManager = loc;
		teleManager = telMa;
		OPERATOR =teleManager.getSimOperatorName() + "("+teleManager.getSimOperator()+")";
		 networkType = teleManager.getNetworkType();
		 Log.e("network type",Integer.toString(networkType));
		MODEL = model;
		tick = 0;
        myListener = new MyPhoneStateListener();//初始化对象  
        teleManager.listen(myListener ,PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);//Registers a listener object to receive notification of changes in specified telephony states.设置监听器监听特定事件的状态  
		
		
		//Updates information every 1000 ms, when difference is larger than 1 meter. 
		locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,  
                1000, 1, new LocationListener() {  
   
                     @Override  
                     public void onLocationChanged(Location location) {  
                         // 当GPS定位信息发生改变时，更新位置  
                         updateView(location);  
                     }  
   
                     @Override  
                     public void onProviderDisabled(String provider) {  
                         updateView(null);  
                     }  
   
                     @Override  
                     public void onProviderEnabled(String provider) {  
                         // 当GPS LocationProvider可用时，更新位置  
                         updateView(locManager  
                                 .getLastKnownLocation(provider));  
   
                     }  
   
                     @Override  
                     public void onStatusChanged(String provider, int status,  
                             Bundle extras) {  
                     }  
                 });  	
		
		
        task = new TimerTask(){  
        	public void run() {
        		tick = tick + 1;
            	SignalRecord newSig = new SignalRecord();
            	
        	/***** send data to UI ****/
        		
        		//步骤2：使用Message.obtain()获得一个空的Message对象
                Message msg = Message.obtain( );  
               //步骤3：填充message的信息。
                
                msg.what = 1;
                Bundle bundle = new Bundle(); 
                bundle.putString("tick",Integer.toString(tick));
                /******GPS信息****/
                if (tick > 40)
                {
                	bundle.putString("lat",Double.toString(lat));
                	bundle.putString("lon",Double.toString(lon));
                }
                /*****cellid信息******/                
       		 	gsmLoc = (GsmCellLocation) teleManager.getCellLocation();

                List<NeighboringCellInfo> list = teleManager.getNeighboringCellInfo();
                String tmp = OPERATOR + "\n";
                tmp += "lac: " + Integer.toString(gsmLoc.getLac()) + " cid: " + Integer.toString(gsmLoc.getCid()) + " ss:" + gsmSS + "\n";
                newSig.lac = gsmLoc.getLac();
                newSig.cellid = gsmLoc.getCid();
                newSig.ss = gsmSS;
                newSig.lat = lat;
                newSig.lon = lon;
                if (!list.isEmpty()) {
                            for (NeighboringCellInfo info : list) {

                                int cid = info.getCid();
                                // 获取邻居小区LAC，LAC:
                                // 位置区域码。为了确定移动台的位置，每个GSM/PLMN的覆盖区都被划分成许多位置区，LAC则用于标识不同的位置区。
                                int lac = info.getLac();
                                // 获取邻居小区信号强度
                                int ss = -113 + 2 * info.getRssi();
                                tmp +="cid:"+Integer.toString(cid)+" ss:"+Integer.toString(ss) +"\n";
                            }
                 }
                
                //WIFI INFORMATION 
                
               bundle.putString("wifi", showWIFIDetail());
               bundle.putString("neighbor", tmp);
                msg.setData(bundle);
               //步骤4：通过Messenger信使将消息发送出去。
                /***** send data to UI ****/
                if (uiHandler!=null)
					try {
						uiHandler.send(msg);
					} catch (RemoteException e) {
						//Error processing
						Log.e("uiHandlerError!","uiHandlerError!");
					}
            
                //record the signal;
                Time t=new Time(); // or Time t=new Time("GMT+8"); 加上Time Zone资料。  
                t.setToNow();
                newSig.time = t ;
                if ((t.minute % 10 == 0) && (t.second == 0))
                {
                	saveFile(t);
                }
                mongo.insert(newSig);
                rec.add(newSig);
            }  
        };  
        if (timer==null)
        {
        	timer = new Timer();
        	rec = new ArrayList<SignalRecord>();
        	timer.schedule(task, 0 ,1000);
        }
	}
	void saveFile(Time t)
	{
		if (rec.size() < 1) return ;
        // 通过Environment得到SD的状态和路径  
        if (Environment.getExternalStorageState().equals(  
                Environment.MEDIA_MOUNTED)) {
        	
        	SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd");    
        	String fileName = sdf.format(new java.util.Date());
        	fileName += "_" + Integer.toString(t.hour) +"_"+Integer.toString(t.minute);
        	fileName += ".txt";
        	//Log.e("directory", Environment.getExternalStorageDirectory().getPath());
        	
            File doc = new File(Environment.getExternalStorageDirectory().getPath() +"/celldata");
            if (!doc.exists()) {
                    doc.mkdir();
            }
            
            File file = new File(doc,fileName);  
            
            try {  
                // 写入信号  
                FileOutputStream fos = new FileOutputStream(file);
                fos.write((MODEL + "\n").getBytes());
                fos.write((OPERATOR+"\n").getBytes());
                                
                for (int i = 0 ; i < rec.size();++i)
                {
                	SignalRecord now = rec.get(i);
                	String str = "";
                	str+=Integer.toString(now.time.minute) +" "+Integer.toString(now.time.second);
                	str+=" "+Integer.toString(now.lac);
                	str+=" "+Integer.toString(now.cellid);
                	str+=" "+Integer.toString(now.ss);
                	str+=" "+Double.toString(now.lat);
                	str+=" "+Double.toString(now.lon)+"\n";
                  
                	fos.write(str.getBytes());
                }
            } catch (Exception e) {  
                // TODO Auto-generated catch block  
                Log.e("SaveFile", "Can't save file");
            }
        }  
		rec.clear();		
	}
	void end() {
        Time t=new Time(); // or Time t=new Time("GMT+8"); 加上Time Zone资料。  
        t.setToNow();
		saveFile(t);
		rec = null;
		timer.cancel();
		task.cancel();
	}
	private class MyPhoneStateListener extends PhoneStateListener{  
		//监听器类  
        /*得到信号的强度由每个tiome供应商,有更新*/  

        @Override  
  
        public void onSignalStrengthsChanged(SignalStrength signalStrength){  
  
        super.onSignalStrengthsChanged(signalStrength);//调用超类的该方法，在网络信号变化时得到回答信号  
  
        //Toast.makeText(getApplicationContext(), "Go to Firstdroid!!! GSM Cinr = "+ String.valueOf(signalStrength.getGsmSignalStrength()), Toast.LENGTH_SHORT).show();//cinr：Carrier to Interference plus Noise Ratio（载波与干扰和噪声比）  
        gsmSS =  -113 + 2 * signalStrength.getGsmSignalStrength();  
        }  
  
    }
	public String showWIFIDetail()  
    {  
		if (wifiManager == null) return "no Wifi connection";
        WifiInfo info = wifiManager.getConnectionInfo(); 
        String ret = "";
        ret += "SSID = " + info.getSSID() + "\n";
        ret += "RSSI = " + info.getRssi() + " \n";
          
        /* 
        info.getBSSID()；      获取BSSID地址。 
        info.getSSID()；       获取SSID地址。  需要连接网络的ID 
        info.getIpAddress()；  获取IP地址。4字节Int, XXX.XXX.XXX.XXX 每个XXX为一个字节 
        info.getMacAddress()； 获取MAC地址。 
        info.getNetworkId()；  获取网络ID。 
        info.getLinkSpeed()；  获取连接速度，可以让用户获知这一信息。 
        info.getRssi()；       获取RSSI，RSSI就是接受信号强度指示 
         */  
        return ret;
    }  
      

}
