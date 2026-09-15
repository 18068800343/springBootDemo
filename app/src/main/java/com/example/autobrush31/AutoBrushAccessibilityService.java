package com.example.autobrush31;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import java.util.concurrent.Executor;

public class AutoBrushAccessibilityService extends AccessibilityService {
 public static volatile AutoBrushAccessibilityService instance;
 public static volatile String status="服务未连接";
 private final Handler handler=new Handler(Looper.getMainLooper());
 private volatile boolean running;
 private volatile String foregroundPackage="";
 private int phase; private long phaseAt,lastShotAt;
 private static final String GAME="com.hortor.mwdl.gf";
 private static final int RW=691,RH=1536,SECRET_X=620,SECRET_Y=1090,HELL_X=335,HELL_Y=895,ENTER_X=345,ENTER_Y=1270,JOY_X=355,JOY_Y=1125,ML=25,MT=105,MR=285,MB=270;
 @Override public void onServiceConnected(){super.onServiceConnected();instance=this;AccessibilityServiceInfo i=getServiceInfo();if(i!=null){i.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;setServiceInfo(i);}status="无障碍已连接";}
 @Override public void onAccessibilityEvent(AccessibilityEvent e){if(e!=null&&e.getPackageName()!=null)foregroundPackage=e.getPackageName().toString();}
 @Override public void onInterrupt(){stopBrush();}
 @Override public void onDestroy(){stopBrush();if(instance==this)instance=null;super.onDestroy();}
 public boolean startBrush(){if(running)return true;if(Build.VERSION.SDK_INT<30){status="Android 11及以上才支持截图";return false;}running=true;phase=0;lastShotAt=0;phaseAt=System.currentTimeMillis();status="等待迷雾大陆进入前台…";handler.post(loop);return true;}
 public void stopBrush(){running=false;handler.removeCallbacksAndMessages(null);status="已停止";}
 public boolean isRunning(){return running;}
 private final Runnable loop=()->{if(!running)return;long now=System.currentTimeMillis();if(!GAME.equals(foregroundPackage)){status="等待迷雾大陆前台…";handler.postDelayed(loop,500);return;}if(phase==0){status="选择源初秘境";tap(SECRET_X,SECRET_Y);phase=1;phaseAt=now;}else if(phase==1&&now-phaseAt>1800){status="选择地狱31";tap(HELL_X,HELL_Y);phase=2;phaseAt=now;}else if(phase==2&&now-phaseAt>1800){status="进入地狱31";tap(ENTER_X,ENTER_Y);phase=3;phaseAt=now;}else if(phase==3&&now-phaseAt>2500&&now-lastShotAt>850){shot();lastShotAt=now;}handler.postDelayed(loop,phase==3?350:250);};
 private void shot(){try{Executor ex=c->handler.post(c);takeScreenshot(0,ex,new TakeScreenshotCallback(){public void onSuccess(ScreenshotResult r){try{Bitmap b=Bitmap.wrapHardwareBuffer(r.getHardwareBuffer(),r.getColorSpace());if(b!=null){Bitmap c=b.copy(Bitmap.Config.ARGB_8888,false);frame(c);}}catch(Throwable t){status="截图处理失败";}finally{if(r.getHardwareBuffer()!=null)r.getHardwareBuffer().close();}}public void onFailure(int e){status="截图失败 "+e;}});}catch(Throwable t){status="截图调用失败";}}
 private void frame(Bitmap f){if(f==null||f.isRecycled())return;int w=f.getWidth(),h=f.getHeight();try{int l=sx(ML,w),t=sy(MT,h),r=sx(MR,w),b=sy(MB,h);l=Math.max(0,Math.min(l,w-1));r=Math.max(l+1,Math.min(r,w));t=Math.max(0,Math.min(t,h-1));b=Math.max(t+1,Math.min(b,h));Bitmap m=Bitmap.createBitmap(f,l,t,r-l,b-t);MapNavigator.Result q=MapNavigator.analyze(m);m.recycle();if(!q.foundPlayer||q.confidence<0.12f){status="等待小地图识别玩家…";return;}status="小地图规划 "+q.dx+","+q.dy+" 路径"+q.pathLength;move(q.dx,q.dy,Math.max(450,Math.min(800,450+q.pathLength*30)));}finally{f.recycle();}}
 private DisplayMetrics dm(){DisplayMetrics d=new DisplayMetrics();try{WindowManager w=(WindowManager)getSystemService(WINDOW_SERVICE);if(w!=null)w.getDefaultDisplay().getRealMetrics(d);}catch(Throwable e){}return d.widthPixels>0?d:getResources().getDisplayMetrics();}
 private int sx(int x,int w){return Math.round(x*w/(float)RW);}private int sy(int y,int h){return Math.round(y*h/(float)RH);}
 private void tap(int x,int y){DisplayMetrics d=dm();Path p=new Path();p.moveTo(sx(x,d.widthPixels),sy(y,d.heightPixels));dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,100)).build(),null,null);}
 private void move(int x,int y,long ms){if(x==0&&y==0)return;DisplayMetrics d=dm();int a=sx(JOY_X,d.widthPixels),b=sy(JOY_Y,d.heightPixels);float z=(float)Math.sqrt(x*x+y*y),r=110f*d.widthPixels/RW;Path p=new Path();p.moveTo(a,b);p.lineTo(Math.round(a+r*x/z),Math.round(b+r*y/z));dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,ms)).build(),null,null);}
}
