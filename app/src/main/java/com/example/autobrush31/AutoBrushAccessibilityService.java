package com.example.autobrush31;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class AutoBrushAccessibilityService extends Service {
    public static AutoBrushAccessibilityService instance;
    private boolean running;
    @Override public void onCreate(){ super.onCreate(); instance=this; }
    @Override public int onStartCommand(Intent intent,int flags,int startId){ return START_NOT_STICKY; }
    @Override public IBinder onBind(Intent intent){ return null; }
    @Override public void onDestroy(){ running=false; instance=null; super.onDestroy(); }
    public void startBrush(){ running=true; }
    public void stopBrush(){ running=false; }
    public boolean isRunning(){ return running; }
}
