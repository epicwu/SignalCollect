package com.example.signalcollect;

import android.content.BroadcastReceiver;  
import android.content.Context;  
import android.content.Intent;  
import android.util.Log;  
  
public class AutoBoot extends BroadcastReceiver {  
    //重写onReceive方法  
    @Override  
    public void onReceive(Context context, Intent intent) {  
        //后边的XXX.class就是要启动的服务  
        Intent myprog = new Intent(context,SignalCollect.class);  
        myprog.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); 
        context.startActivity(myprog);         
        Log.e("AutoBoot","Success!");
    }  
  
}  