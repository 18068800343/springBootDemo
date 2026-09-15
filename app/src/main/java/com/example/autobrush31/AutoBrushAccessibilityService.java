package com.example.autobrush31;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.Text;
import java.util.concurrent.Executor;

public class AutoBrushAccessibilityService extends AccessibilityService {
    public static volatile AutoBrushAccessibilityService instance;
    public static volatile String status="服务未连接";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private volatile String foregroundPackage="";
    private int phase;
    private long phaseAt,lastShotAt,lastOcrAt;
    private int lastPlayerX=-1,lastPlayerY=-1,stuckFrames=0;
    private boolean ocrBusy=false, selected31=false;
    private static final String GAME=AutoConfig.GAME_PACKAGE;

    @Override public void onServiceConnected(){
        super.onServiceConnected(); instance=this;
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
        running=true;phase=0;lastShotAt=0;lastOcrAt=0;lastPlayerX=-1;lastPlayerY=-1;stuckFrames=0;ocrBusy=false;selected31=false;
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
            status="进入源初秘境";tap(AutoConfig.SECRET_X,AutoConfig.SECRET_Y);phase=1;phaseAt=now;lastOcrAt=0;
        }else if(phase==1&&now-phaseAt>900){
            if(!ocrBusy&&now-lastOcrAt>700){lastOcrAt=now;status="正在OCR识别秘境列表，寻找‘地狱31’…";shotForOcr();}
            // Do not click the old 31/33 boundary blindly. If OCR cannot see it,
            // keep scanning instead of accidentally entering 33.
            if(now-phaseAt>12000&&!selected31){status="仍未识别到‘地狱31’，暂停点击，避免误选地狱33";phaseAt=now-7000;}
        }else if(phase==2&&now-phaseAt>1600){
            status="准备进入已识别的地狱31";tap(AutoConfig.ENTER_X,AutoConfig.ENTER_Y);phase=3;phaseAt=now;
        }else if(phase==3&&now-phaseAt>2200&&now-lastShotAt>650){shot();lastShotAt=now;}
        handler.postDelayed(loop,phase==3?250:220);
    }};

    private void shotForOcr(){
        ocrBusy=true;
        try{
            Executor ex=c->handler.post(c);
            takeScreenshot(0,ex,new TakeScreenshotCallback(){
                public void onSuccess(ScreenshotResult r){
                    Bitmap b=null;
                    try{
                        Bitmap hw=Bitmap.wrapHardwareBuffer(r.getHardwareBuffer(),r.getColorSpace());
                        if(hw!=null)b=hw.copy(Bitmap.Config.ARGB_8888,false);
                    }catch(Throwable ignored){}
                    finally{if(r.getHardwareBuffer()!=null)r.getHardwareBuffer().close();}
                    if(b!=null)recognize31(b);else ocrBusy=false;
                }
                public void onFailure(int e){ocrBusy=false;status="OCR截图失败，继续扫描";}
            });
        }catch(Throwable t){ocrBusy=false;status="OCR截图调用失败，继续扫描";}
    }

    private void recognize31(Bitmap b){
        try{
            InputImage image=InputImage.fromBitmap(b,0);
            TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build()).process(image)
                .addOnSuccessListener(text->{
                    try{
                        if(!running||phase!=1)return;
                        Text.Line hit=find31(text);
                        if(hit!=null){
                            Rect r=new Rect();hit.getBoundingBox().round(r);
                            int x=r.centerX(),y=r.centerY();
                            status="OCR确认‘地狱31’，点击实际文字位置";
                            tapRaw(x,y);selected31=true;phase=2;phaseAt=System.currentTimeMillis();
                        }else status="OCR未发现‘地狱31’，继续扫描，不误选33";
                    }finally{ocrBusy=false;b.recycle();}
                })
                .addOnFailureListener(e->{ocrBusy=false;b.recycle();status="OCR识别失败，继续扫描";});
        }catch(Throwable t){ocrBusy=false;b.recycle();status="OCR初始化失败，继续扫描";}
    }

    private Text.Line find31(Text text){
        if(text==null)return null;
        for(Text.TextBlock block:text.getTextBlocks())for(Text.Line line:block.getLines()){
            String s=line.getText().replace(" ","").replace("：",":");
            if(s.contains("地狱31")||s.contains("地狱3l")||s.matches(".*地狱.*31.*"))return line;
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
            if(!q.foundPlayer||q.confidence<0.25f){status="正在用小地图定位人物…";return;}
            if(lastPlayerX>=0){
                int dd=Math.abs(q.playerX-lastPlayerX)+Math.abs(q.playerY-lastPlayerY);
                if(dd<=1)stuckFrames++;else stuckFrames=Math.max(0,stuckFrames-2);
            }
            lastPlayerX=q.playerX;lastPlayerY=q.playerY;
            if(stuckFrames>=3){
                status="人物连续未移动，停止继续顶黑区，等待重新规划";
                return;
            }
            if(!q.roadDirection){status="前方没有确认的灰色道路，保持原地，不进入黑色区域";return;}
            int dx=q.dx,dy=q.dy;
            if(dx==0&&dy==0)return;
            status="沿灰色道路中心移动：方向"+dx+","+dy+" 路径"+q.pathLength;
            long hold=Math.max(180,Math.min(360,220+q.routeCells*25));
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
    private void tap(int x,int y){if(!isGameForeground())return;DisplayMetrics d=dm();tapRaw(sx(x,d.widthPixels),sy(y,d.heightPixels));}
    private void tapRaw(int x,int y){Path p=new Path();p.moveTo(x,y);dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,100)).build(),null,null);}
    private void move(int x,int y,long ms){
        if(!isGameForeground()||(x==0&&y==0))return;
        DisplayMetrics d=dm();int a=sx(AutoConfig.JOY_X,d.widthPixels),b=sy(AutoConfig.JOY_Y,d.heightPixels);
        float z=(float)Math.sqrt(x*x+y*y);float radius=112f*d.widthPixels/AutoConfig.REF_W;
        Path p=new Path();p.moveTo(a,b);p.lineTo(Math.round(a+radius*x/z),Math.round(b+radius*y/z));
        dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,ms)).build(),null,null);
    }
}
