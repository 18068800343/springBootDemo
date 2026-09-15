package com.example.autobrush31;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import java.util.concurrent.Executor;

/** No-root game assistant. Uses only Android Accessibility APIs and screenshots. */
public class AutoBrushAccessibilityService extends AccessibilityService {
    public static volatile AutoBrushAccessibilityService instance;
    public static volatile String status = "服务未连接";
    private static final String TAG = "AutoBrush31";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private int phase;
    private long phaseAt;
    private int moveCount;
    private long lastShotAt;

    private static final int REF_W=691, REF_H=1536;
    private static final int SECRET_X=620, SECRET_Y=1090;
    private static final int HELL31_X=335, HELL31_Y=895;
    private static final int ENTER_X=345, ENTER_Y=1270;
    private static final int JOY_X=355, JOY_Y=1125;
    private static final int MAP_L=25, MAP_T=105, MAP_R=285, MAP_B=270;

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        instance=this;
        AccessibilityServiceInfo info=getServiceInfo();
        if(info!=null){
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            setServiceInfo(info);
        }
        status="无障碍已连接";
        Log.i(TAG,status);
    }

    @Override public void onCreate(){ super.onCreate(); Log.i(TAG,"service created"); }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt(){ stopBrush(); }
    @Override public void onDestroy(){ stopBrush(); if(instance==this) instance=null; status="服务已断开"; super.onDestroy(); }

    public boolean startBrush(){
        if(running) return true;
        if(Build.VERSION.SDK_INT < 30){ status="Android 11及以上才支持截图自动导航"; return false; }
        running=true; phase=0; moveCount=0; lastShotAt=0; phaseAt=System.currentTimeMillis();
        status="自动刷图启动中";
        handler.post(loop);
        return true;
    }

    public void stopBrush(){ running=false; handler.removeCallbacksAndMessages(null); status="已停止"; }
    public boolean isRunning(){ return running; }

    private final Runnable loop = new Runnable(){ @Override public void run(){
        if(!running) return;
        long now=System.currentTimeMillis();
        if(phase==0){
            status="点击：源初秘境";
            tapScaled(SECRET_X,SECRET_Y);
            phase=1; phaseAt=now;
        } else if(phase==1 && now-phaseAt>1300){
            status="点击：地狱31";
            tapScaled(HELL31_X,HELL31_Y);
            phase=2; phaseAt=now;
        } else if(phase==2 && now-phaseAt>1200){
            status="点击：进入秘境";
            tapScaled(ENTER_X,ENTER_Y);
            phase=3; phaseAt=now;
        } else if(phase==3 && now-phaseAt>2200 && now-lastShotAt>800){
            requestShot();
            lastShotAt=now;
        }
        handler.postDelayed(this, phase==3 ? 500 : 250);
    }};

    private void requestShot(){
        if(Build.VERSION.SDK_INT < 30) return;
        try{
            Executor ex = command -> handler.post(command);
            takeScreenshot(0, ex, new TakeScreenshotCallback(){
                @Override public void onSuccess(ScreenshotResult result){
                    Bitmap b=null;
                    try {
                        b=Bitmap.wrapHardwareBuffer(result.getHardwareBuffer(), result.getColorSpace());
                        if(b!=null){
                            Bitmap copy=b.copy(Bitmap.Config.ARGB_8888,false);
                            processFrame(copy);
                        }
                    } catch(Throwable e){
                        status="截图处理失败";
                        Log.e(TAG,"screenshot process",e);
                    } finally {
                        if(result.getHardwareBuffer()!=null) result.getHardwareBuffer().close();
                    }
                }
                @Override public void onFailure(int errorCode){
                    status="截图失败 code="+errorCode;
                    Log.e(TAG,status);
                }
            });
        }catch(Throwable e){
            status="截图调用失败："+e.getClass().getSimpleName();
            Log.e(TAG,"takeScreenshot",e);
        }
    }

    private void processFrame(Bitmap full){
        if(full==null || full.isRecycled()) return;
        int sw=full.getWidth(), sh=full.getHeight();
        try {
            int l=scaleX(MAP_L,sw), t=scaleY(MAP_T,sh), r=scaleX(MAP_R,sw), b=scaleY(MAP_B,sh);
            l=Math.max(0,Math.min(l,sw-1)); r=Math.max(l+1,Math.min(r,sw));
            t=Math.max(0,Math.min(t,sh-1)); b=Math.max(t+1,Math.min(b,sh));
            Bitmap map=Bitmap.createBitmap(full,l,t,r-l,b-t);
            MapNavigator.Result res=MapNavigator.analyze(map);
            map.recycle();
            if(res.foundPlayer && res.confidence>=0.12f){
                int dx=res.dx, dy=res.dy;
                if(moveCount%7==6){ int tmp=dx; dx=dy; dy=-tmp; }
                status="自动探索：方向 "+dx+","+dy;
                moveJoystick(dx,dy,650);
                moveCount++;
            } else {
                status="自动探索：寻找玩家位置";
                moveJoystick(moveCount%2==0?1:0,moveCount%2==0?0:1,500);
                moveCount++;
            }
        } finally { full.recycle(); }
    }

    private void tapScaled(int x,int y){
        int w=getResources().getDisplayMetrics().widthPixels, h=getResources().getDisplayMetrics().heightPixels;
        dispatchTap(scaleX(x,w),scaleY(y,h));
    }
    private int scaleX(int x,int actualW){ return Math.round(x*actualW/(float)REF_W); }
    private int scaleY(int y,int actualH){ return Math.round(y*actualH/(float)REF_H); }

    private void dispatchTap(int x,int y){
        Path p=new Path(); p.moveTo(x,y);
        GestureDescription.StrokeDescription s=new GestureDescription.StrokeDescription(p,0,80);
        boolean ok=dispatchGesture(new GestureDescription.Builder().addStroke(s).build(),null,null);
        if(!ok){ status="点击手势发送失败"; Log.e(TAG,status+" x="+x+" y="+y); }
    }

    private void moveJoystick(int dx,int dy,long duration){
        float len=(float)Math.sqrt(dx*dx+dy*dy); if(len<0.1f) return;
        int sx=scaleX(JOY_X,getResources().getDisplayMetrics().widthPixels);
        int sy=scaleY(JOY_Y,getResources().getDisplayMetrics().heightPixels);
        float radius=110f;
        int ex=Math.round(sx+radius*dx/len), ey=Math.round(sy+radius*dy/len);
        Path p=new Path(); p.moveTo(sx,sy); p.lineTo(ex,ey);
        GestureDescription.StrokeDescription s=new GestureDescription.StrokeDescription(p,0,duration);
        boolean ok=dispatchGesture(new GestureDescription.Builder().addStroke(s).build(),null,null);
        if(!ok){ status="摇杆手势发送失败"; Log.e(TAG,status); }
    }
}
