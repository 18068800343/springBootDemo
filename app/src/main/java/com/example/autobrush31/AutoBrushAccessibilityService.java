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
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.concurrent.Executor;

public class AutoBrushAccessibilityService extends AccessibilityService {
    public static volatile AutoBrushAccessibilityService instance;
    public static volatile String status="服务未连接";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private volatile String foregroundPackage="";
    private int phase;
    private long phaseAt,lastShotAt;
    private int lastPlayerX=-1,lastPlayerY=-1,stuckFrames=0,moveFlip=0;
    private static final String GAME=AutoConfig.GAME_PACKAGE;

    @Override public void onServiceConnected(){
        super.onServiceConnected();
        instance=this;
        AccessibilityServiceInfo i=getServiceInfo();
        if(i!=null){i.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;setServiceInfo(i);}
        status="无障碍已连接";
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent e){
        if(e!=null&&e.getPackageName()!=null) foregroundPackage=e.getPackageName().toString();
    }
    @Override public void onInterrupt(){stopBrush();}
    @Override public void onDestroy(){stopBrush();if(instance==this)instance=null;super.onDestroy();}

    public boolean startBrush(){
        if(running)return true;
        if(Build.VERSION.SDK_INT<30){status="Android 11及以上才支持截图";return false;}
        running=true;phase=0;lastShotAt=0;lastPlayerX=-1;lastPlayerY=-1;stuckFrames=0;moveFlip=0;
        phaseAt=System.currentTimeMillis();status="正在确认《迷雾大陆》前台…";handler.post(loop);return true;
    }
    public void stopBrush(){running=false;handler.removeCallbacksAndMessages(null);status="已停止";}
    public boolean isRunning(){return running;}

    private boolean isGameForeground(){
        if(GAME.equals(foregroundPackage))return true;
        try{AccessibilityNodeInfo root=getRootInActiveWindow();if(root!=null){CharSequence p=root.getPackageName();if(p!=null){foregroundPackage=p.toString();return GAME.equals(foregroundPackage);}}}catch(Throwable ignored){}
        return false;
    }

    private final Runnable loop=new Runnable(){@Override public void run(){
        if(!running)return;
        long now=System.currentTimeMillis();
        if(!isGameForeground()){status="等待《迷雾大陆》进入前台…";handler.postDelayed(loop,500);return;}
        if(phase==0){
            status="进入源初秘境，正在寻找文字‘地狱31’";tap(AutoConfig.SECRET_X,AutoConfig.SECRET_Y);phase=1;phaseAt=now;
        }else if(phase==1&&now-phaseAt>1200){
            if(tapHell31ByText()){
                status="已按‘地狱31’文字实际位置点击";phase=2;phaseAt=now;
            }else{
                // Canvas games sometimes expose no accessibility text. Keep a
                // conservative fallback, but do not pretend the coordinate is
                // a text match.
                status="未读到‘地狱31’文字，使用校准位置";tap(AutoConfig.HELL31_X,AutoConfig.HELL31_Y);phase=2;phaseAt=now;
            }
        }else if(phase==2&&now-phaseAt>1600){
            status="准备进入已选择的秘境";tap(AutoConfig.ENTER_X,AutoConfig.ENTER_Y);phase=3;phaseAt=now;
        }else if(phase==3&&now-phaseAt>2200&&now-lastShotAt>650){
            shot();lastShotAt=now;
        }
        handler.postDelayed(loop,phase==3?250:220);
    }};

    /** Find the actual visible text node instead of relying on a guessed Y coordinate. */
    private boolean tapHell31ByText(){
        try{
            AccessibilityNodeInfo root=getRootInActiveWindow();
            AccessibilityNodeInfo n=findText(root,"地狱31");
            if(n==null)n=findText(root,"地狱 31");
            if(n==null)return false;
            android.graphics.Rect r=new android.graphics.Rect();n.getBoundsInScreen(r);
            if(r.width()<=0||r.height()<=0)return false;
            int x=r.centerX(),y=r.centerY();
            if(n.isClickable()){
                n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }else{
                tapRaw(x,y);
            }
            try{n.recycle();}catch(Throwable ignored){}
            return true;
        }catch(Throwable ignored){return false;}
    }
    private AccessibilityNodeInfo findText(AccessibilityNodeInfo n,String wanted){
        if(n==null)return null;
        CharSequence t=n.getText();
        if(t!=null&&wanted.equals(t.toString().trim()))return n;
        CharSequence d=n.getContentDescription();
        if(d!=null&&wanted.equals(d.toString().trim()))return n;
        for(int i=0;i<n.getChildCount();i++){
            AccessibilityNodeInfo r=findText(n.getChild(i),wanted);
            if(r!=null)return r;
        }
        return null;
    }

    private void shot(){
        try{
            Executor ex=c->handler.post(c);
            takeScreenshot(0,ex,new TakeScreenshotCallback(){
                public void onSuccess(ScreenshotResult r){
                    try{Bitmap b=Bitmap.wrapHardwareBuffer(r.getHardwareBuffer(),r.getColorSpace());if(b!=null){Bitmap c=b.copy(Bitmap.Config.ARGB_8888,false);frame(c);}}
                    catch(Throwable t){status="截图处理失败";}
                    finally{if(r.getHardwareBuffer()!=null)r.getHardwareBuffer().close();}
                }
                public void onFailure(int e){status="截图失败 "+e;}
            });
        }catch(Throwable t){status="截图调用失败";}
    }

    private void frame(Bitmap f){
        if(f==null||f.isRecycled())return;
        int w=f.getWidth(),h=f.getHeight();
        try{
            int l=sx(AutoConfig.MAP_L,w),t=sy(AutoConfig.MAP_T,h),r=sx(AutoConfig.MAP_R,w),b=sy(AutoConfig.MAP_B,h);
            l=Math.max(0,Math.min(l,w-1));r=Math.max(l+1,Math.min(r,w));t=Math.max(0,Math.min(t,h-1));b=Math.max(t+1,Math.min(b,h));
            Bitmap m=Bitmap.createBitmap(f,l,t,r-l,b-t);
            MapNavigator.Result q=MapNavigator.analyze(m);m.recycle();
            if(!q.foundPlayer||q.confidence<0.25f){status="正在用小地图图像定位人物…";return;}

            // Compare player coordinates from successive minimap frames. If the
            // sprite stays in place for several plans, shorten the next hold and
            // reverse/rotate the preferred direction instead of endlessly pushing
            // against one wall.
            if(lastPlayerX>=0){
                int dd=Math.abs(q.playerX-lastPlayerX)+Math.abs(q.playerY-lastPlayerY);
                if(dd<=1)stuckFrames++;else stuckFrames=Math.max(0,stuckFrames-2);
            }
            lastPlayerX=q.playerX;lastPlayerY=q.playerY;

            int dx=q.dx,dy=q.dy;
            if(stuckFrames>=3){
                moveFlip++;
                if((moveFlip&1)==1){int z=dx;dx=-dy;dy=z;}else{dx=-dx;dy=-dy;}
                status="检测到人物卡住，重新规划脱离方向";
            }else{
                status="小地图图像规划：方向"+dx+","+dy+" 路径"+q.pathLength+"（偏向道路中心）";
            }
            if(dx==0&&dy==0)return;
            long hold=stuckFrames>=3?220:Math.max(260,Math.min(480,280+q.routeCells*35));
            move(dx,dy,hold);
        }finally{f.recycle();}
    }

    private DisplayMetrics dm(){
        DisplayMetrics d=new DisplayMetrics();
        try{WindowManager w=(WindowManager)getSystemService(WINDOW_SERVICE);if(w!=null)w.getDefaultDisplay().getRealMetrics(d);}catch(Throwable ignored){}
        return d.widthPixels>0?d:getResources().getDisplayMetrics();
    }
    private int sx(int x,int w){return Math.round(x*w/(float)AutoConfig.REF_W);}
    private int sy(int y,int h){return Math.round(y*h/(float)AutoConfig.REF_H);}

    private void tap(int x,int y){
        if(!isGameForeground())return;DisplayMetrics d=dm();tapRaw(sx(x,d.widthPixels),sy(y,d.heightPixels));
    }
    private void tapRaw(int x,int y){
        Path p=new Path();p.moveTo(x,y);
        dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,100)).build(),null,null);
    }
    private void move(int x,int y,long ms){
        if(!isGameForeground()||(x==0&&y==0))return;
        DisplayMetrics d=dm();int a=sx(AutoConfig.JOY_X,d.widthPixels),b=sy(AutoConfig.JOY_Y,d.heightPixels);
        float z=(float)Math.sqrt(x*x+y*y);float radius=112f*d.widthPixels/AutoConfig.REF_W;
        Path p=new Path();p.moveTo(a,b);p.lineTo(Math.round(a+radius*x/z),Math.round(b+radius*y/z));
        dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,ms)).build(),null,null);
    }
}
