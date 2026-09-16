package com.example.autobrush31;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

/** Lightweight floating diagnostic panel for the minimap planner. */
public final class DebugOverlay {
    private static WindowManager wm;
    private static Panel panel;
    private static WindowManager.LayoutParams lp;
    private static AutoBrushAccessibilityService service;
    private static Bitmap map;
    private static volatile String direction="—", detail="等待小地图…";
    private static volatile float confidence;
    private static volatile int playerX=-1, playerY=-1, targetX=-1, targetY=-1;
    private static volatile boolean road;
    private DebugOverlay() {}

    public static boolean show(AutoBrushAccessibilityService s){
        if(s==null || !android.provider.Settings.canDrawOverlays(s)) return false;
        service=s;
        if(panel!=null) return true;
        wm=(WindowManager)s.getSystemService(Context.WINDOW_SERVICE);
        panel=new Panel(s);
        lp=new WindowManager.LayoutParams(dp(300),dp(390),
                android.os.Build.VERSION.SDK_INT>=26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.RIGHT; lp.x=dp(10); lp.y=dp(80);
        try{wm.addView(panel,lp);return true;}catch(Throwable t){panel=null;return false;}
    }
    public static void hide(){if(panel!=null&&wm!=null){try{wm.removeView(panel);}catch(Throwable ignored){}}panel=null;map=null;service=null;}
    public static void update(Bitmap m, MapNavigator.Result r, String status){
        if(m!=null){Bitmap c=m.copy(Bitmap.Config.ARGB_8888,false);Bitmap old=map;map=c;if(old!=null&&!old.isRecycled())old.recycle();}
        if(r!=null){playerX=r.playerX;playerY=r.playerY;targetX=r.targetX;targetY=r.targetY;confidence=r.confidence;road=r.roadDirection;direction=dir(r.dx,r.dy);}
        detail=status==null?"":status;
        if(panel!=null)panel.postInvalidate();
    }
    private static String dir(int x,int y){if(x==0&&y==0)return "—";if(x>0&&y<0)return "↗ 右上";if(x<0&&y<0)return "↖ 左上";if(x>0&&y>0)return "↘ 右下";if(x<0&&y>0)return "↙ 左下";if(x>0)return "→ 右";if(x<0)return "← 左";if(y>0)return "↓ 下";return "↑ 上";}
    private static int dp(int n){return Math.round(n*(service==null?1:service.getResources().getDisplayMetrics().density));}

    private static final class Panel extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); float downX,downY; int startX,startY;
        Panel(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        protected void onDraw(Canvas c){super.onDraw(c);float d=getResources().getDisplayMetrics().density;
            p.setStyle(Paint.Style.FILL);p.setColor(0xEE171717);c.drawRoundRect(new RectF(0,0,getWidth(),getHeight()),18*d,18*d,p);
            p.setColor(0xFFFFFFFF);p.setTextSize(20*d);p.setTypeface(Typeface.DEFAULT_BOLD);c.drawText("自动刷图31 · 寻路调试",16*d,28*d,p);
            p.setTypeface(Typeface.DEFAULT);p.setTextSize(14*d);p.setColor(0xFFBDBDBD);c.drawText("实时小地图 / 路线判断",16*d,49*d,p);
            float l=14*d,t=60*d,r=getWidth()-14*d,b=245*d;p.setColor(0xFF050505);c.drawRoundRect(new RectF(l,t,r,b),10*d,10*d,p);
            Bitmap m=map;if(m!=null&&!m.isRecycled()){c.drawBitmap(m,null,new RectF(l+5*d,t+5*d,r-5*d,b-5*d),p);}
            if(playerX>=0&&map!=null&&!map.isRecycled()){float sx=(r-l-10*d)/map.getWidth(),sy=(b-t-10*d)/map.getHeight();float x=l+5*d+playerX*sx,y=t+5*d+playerY*sy;p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3*d);p.setColor(0xFF00FF66);c.drawCircle(x,y,8*d,p);if(targetX>=0){float tx=l+5*d+targetX*sx,ty=t+5*d+targetY*sy;p.setColor(0xFFFFA000);c.drawLine(x,y,tx,ty,p);p.setStyle(Paint.Style.FILL);c.drawCircle(tx,ty,5*d,p);} }
            p.setStyle(Paint.Style.FILL);p.setTextSize(19*d);p.setTypeface(Typeface.DEFAULT_BOLD);p.setColor(road?0xFF65E38A:0xFFFFC857);c.drawText("方向："+direction,16*d,275*d,p);
            p.setTypeface(Typeface.DEFAULT);p.setTextSize(13*d);p.setColor(0xFFE0E0E0);c.drawText(String.format(java.util.Locale.US,"置信度：%.0f%%   人物：( %d, %d )",confidence*100,playerX,playerY),16*d,297*d,p);
            p.setColor(0xFFAAAAAA);c.drawText(detail.length()>34?detail.substring(0,34):detail,16*d,318*d,p);
            p.setColor(0xFF3A3A3A);c.drawRoundRect(new RectF(14*d,335*d,138*d,375*d),9*d,9*d,p);c.drawRoundRect(new RectF(148*d,335*d,286*d,375*d),9*d,9*d,p);
            p.setColor(Color.WHITE);p.setTextSize(14*d);c.drawText("暂停",58*d,360*d,p);c.drawText("重新规划",178*d,360*d,p);
            p.setColor(0xFF777777);p.setTextSize(11*d);c.drawText("拖动窗口可移动位置",16*d,386*d,p);
        }
        public boolean onTouchEvent(MotionEvent e){float d=getResources().getDisplayMetrics().density;switch(e.getAction()){case MotionEvent.ACTION_DOWN:downX=e.getRawX();downY=e.getRawY();startX=lp.x;startY=lp.y;return true;case MotionEvent.ACTION_MOVE:if(Math.abs(e.getRawX()-downX)>8||Math.abs(e.getRawY()-downY)>8){lp.x=startX+(int)(downX-e.getRawX());lp.y=startY+(int)(e.getRawY()-downY);try{wm.updateViewLayout(this,lp);}catch(Throwable ignored){}}return true;case MotionEvent.ACTION_UP:if(Math.abs(e.getRawX()-downX)<8&&Math.abs(e.getRawY()-downY)<8){if(e.getY()>330*d&&e.getX()<145*d){if(service!=null)service.stopBrush();}else if(e.getY()>330*d){if(service!=null)service.requestReplan();}}return true;}return true;}
    }
}
