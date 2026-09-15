package com.example.autobrush31;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import java.util.concurrent.Executor;

/** No-root game assistant. It only uses Android Accessibility APIs and screenshots. */
public class AutoBrushAccessibilityService extends AccessibilityService {
    public static volatile AutoBrushAccessibilityService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private int phase;
    private long phaseAt;
    private int moveCount;
    private static final int REF_W=691, REF_H=1536;
    private static final int SECRET_X=620, SECRET_Y=1090;
    private static final int HELL31_X=335, HELL31_Y=895;
    private static final int ENTER_X=345, ENTER_Y=1270;
    private static final int JOY_X=355, JOY_Y=1125;
    private static final int MAP_L=25, MAP_T=105, MAP_R=285, MAP_B=270;

    @Override public void onCreate(){ super.onCreate(); instance=this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt(){ stopBrush(); }
    @Override public android.os.IBinder onBind(android.content.Intent intent){ return super.onBind(intent); }
    @Override public void onDestroy(){ stopBrush(); instance=null; super.onDestroy(); }

    public void startBrush(){
        if(running) return;
        running=true; phase=0; moveCount=0; phaseAt=System.currentTimeMillis();
        handler.post(loop);
    }
    public void stopBrush(){ running=false; handler.removeCallbacksAndMessages(null); }
    public boolean isRunning(){ return running; }

    private final Runnable loop = new Runnable(){ @Override public void run(){
        if(!running) return;
        long now=System.currentTimeMillis();
        if(phase==0){ tapScaled(SECRET_X,SECRET_Y); phase=1; phaseAt=now; }
        else if(phase==1 && now-phaseAt>1000){ tapScaled(HELL31_X,HELL31_Y); phase=2; phaseAt=now; }
        else if(phase==2 && now-phaseAt>900){ tapScaled(ENTER_X,ENTER_Y); phase=3; phaseAt=now; }
        else if(phase==3 && now-phaseAt>1800){ requestShot(); }
        handler.postDelayed(this, phase==3 ? 900 : 300);
    }};

    private void requestShot(){
        if(android.os.Build.VERSION.SDK_INT < 30){ return; }
        try{
            Executor ex = command -> handler.post(command);
            takeScreenshot(0, ex, new TakeScreenshotCallback(){
                @Override public void onSuccess(ScreenshotResult result){
                    Bitmap b=null;
                    try { b=Bitmap.wrapHardwareBuffer(result.getHardwareBuffer(), result.getColorSpace());
                        if(b!=null) processFrame(b.copy(Bitmap.Config.ARGB_8888,false));
                    } finally { if(result.getHardwareBuffer()!=null) result.getHardwareBuffer().close(); }
                }
                @Override public void onFailure(int errorCode){ }
            });
        }catch(Throwable ignored){}
    }

    private void processFrame(Bitmap full){
        if(full==null || full.isRecycled()) return;
        int sw=full.getWidth(), sh=full.getHeight();
        int l=scaleX(MAP_L,sw), t=scaleY(MAP_T,sh), r=scaleX(MAP_R,sw), b=scaleY(MAP_B,sh);
        l=Math.max(0,Math.min(l,sw-1)); r=Math.max(l+1,Math.min(r,sw));
        t=Math.max(0,Math.min(t,sh-1)); b=Math.max(t+1,Math.min(b,sh));
        Bitmap map=Bitmap.createBitmap(full,l,t,r-l,b-t);
        MapNavigator.Result res=MapNavigator.analyze(map);
        map.recycle(); full.recycle();
        if(res.foundPlayer && res.confidence>=0.12f){
            // Alternate axis every few moves to avoid getting stuck at a wall.
            int dx=res.dx, dy=res.dy;
            if(moveCount%7==6){ int tmp=dx;dx=dy;dy=-tmp; }
            moveJoystick(dx,dy,700);
            moveCount++;
        } else {
            moveJoystick(moveCount%2==0?1:0,moveCount%2==0?0:1,550);
            moveCount++;
        }
    }

    private void tapScaled(int x,int y){ dispatchTap(scaleX(x,getResources().getDisplayMetrics().widthPixels),scaleY(y,getResources().getDisplayMetrics().heightPixels)); }
    private int scaleX(int x,int actualW){ return Math.round(x*actualW/(float)REF_W); }
    private int scaleY(int y,int actualH){ return Math.round(y*actualH/(float)REF_H); }

    private void dispatchTap(int x,int y){
        Path p=new Path(); p.moveTo(x,y);
        GestureDescription.StrokeDescription s=new GestureDescription.StrokeDescription(p,0,80);
        dispatchGesture(new GestureDescription.Builder().addStroke(s).build(),null,null);
    }
    private void moveJoystick(int dx,int dy,long duration){
        float len=(float)Math.sqrt(dx*dx+dy*dy); if(len<0.1f) return;
        int sx=scaleX(JOY_X,getResources().getDisplayMetrics().widthPixels);
        int sy=scaleY(JOY_Y,getResources().getDisplayMetrics().heightPixels);
        float radius=110f;
        int ex=Math.round(sx+radius*dx/len), ey=Math.round(sy+radius*dy/len);
        Path p=new Path(); p.moveTo(sx,sy); p.lineTo(ex,ey);
        GestureDescription.StrokeDescription s=new GestureDescription.StrokeDescription(p,0,duration);
        dispatchGesture(new GestureDescription.Builder().addStroke(s).build(),null,null);
    }
}
