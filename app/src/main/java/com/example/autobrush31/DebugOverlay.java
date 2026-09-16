package com.example.autobrush31;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/** Floating live minimap diagnostic panel. */
public final class DebugOverlay {
    private static WindowManager wm;private static Panel panel;private static WindowManager.LayoutParams lp;private static AutoBrushAccessibilityService service;private static Bitmap map;
    private static volatile String direction="—",detail="等待小地图…",diag="";private static volatile float confidence,roadPct,fogPct,nonRoadPct;private static volatile int playerX=-1,playerY=-1,targetX=-1,targetY=-1,pathLength;private static volatile boolean road,foundPlayer;
    private DebugOverlay(){}
    public static boolean show(AutoBrushAccessibilityService s){if(s==null||!Settings.canDrawOverlays(s))return false;service=s;if(panel!=null)return true;wm=(WindowManager)s.getSystemService(Context.WINDOW_SERVICE);panel=new Panel(s);lp=new WindowManager.LayoutParams(dp(390),dp(600),android.os.Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,android.graphics.PixelFormat.TRANSLUCENT);lp.gravity=Gravity.TOP|Gravity.RIGHT;lp.x=dp(6);lp.y=dp(65);try{wm.addView(panel,lp);return true;}catch(Throwable t){panel=null;return false;}}
    public static void hide(){if(panel!=null&&wm!=null)try{wm.removeView(panel);}catch(Throwable ignored){}panel=null;if(map!=null&&!map.isRecycled())map.recycle();map=null;service=null;}
    public static void update(Bitmap m,MapNavigator.Result r,String status){if(m!=null&&!m.isRecycled()){Bitmap c=m.copy(Bitmap.Config.ARGB_8888,false);Bitmap old=map;map=c;if(old!=null&&!old.isRecycled())old.recycle();}if(r!=null){playerX=r.playerX;playerY=r.playerY;targetX=r.targetX;targetY=r.targetY;confidence=r.confidence;road=r.roadDirection;foundPlayer=r.foundPlayer;pathLength=r.pathLength;roadPct=r.roadPct;fogPct=r.fogPct;nonRoadPct=r.nonRoadPct;direction=dir(r.dx,r.dy);diag=r.debug==null?"":r.debug;}detail=status==null?"":status;if(panel!=null)panel.postInvalidate();}
    private static String dir(int x,int y){if(x==0&&y==0)return"—";if(x>0&&y<0)return"↗ 右上";if(x<0&&y<0)return"↖ 左上";if(x>0&&y>0)return"↘ 右下";if(x<0&&y>0)return"↙ 左下";if(x>0)return"→ 右";if(x<0)return"← 左";if(y>0)return"↓ 下";return"↑ 上";}
    private static int dp(int n){return Math.round(n*(service==null?1:service.getResources().getDisplayMetrics().density));}
    private static String clip(String s,int n){if(s==null)return"";return s.length()>n?s.substring(0,n):s;}
    private static final class Panel extends View{final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);float downX,downY;int startX,startY;Panel(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        protected void onDraw(Canvas c){super.onDraw(c);float d=getResources().getDisplayMetrics().density;p.setStyle(Paint.Style.FILL);p.setColor(0xF0101010);c.drawRoundRect(new RectF(0,0,getWidth(),getHeight()),18*d,18*d,p);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(18*d);p.setColor(Color.WHITE);c.drawText("自动刷图31 · 小地图诊断",14*d,27*d,p);p.setTypeface(Typeface.DEFAULT);p.setTextSize(11*d);p.setColor(0xFFAAAAAA);c.drawText("下面这张图是程序实际识别到的原始小地图",14*d,45*d,p);
            float l=10*d,t=54*d,r=getWidth()-10*d,b=300*d;p.setColor(Color.BLACK);c.drawRoundRect(new RectF(l,t,r,b),10*d,10*d,p);Bitmap m=map;if(m!=null&&!m.isRecycled())c.drawBitmap(m,null,new RectF(l+4*d,t+4*d,r-4*d,b-4*d),p);
            if(m!=null&&!m.isRecycled()&&playerX>=0){float sx=(r-l-8*d)/m.getWidth(),sy=(b-t-8*d)/m.getHeight();float x=l+4*d+playerX*sx,y=t+4*d+playerY*sy;p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3*d);p.setColor(0xFF00FF66);c.drawCircle(x,y,9*d,p);if(targetX>=0){float tx=l+4*d+targetX*sx,ty=t+4*d+targetY*sy;p.setColor(0xFFFFA000);c.drawLine(x,y,tx,ty,p);p.setStyle(Paint.Style.FILL);c.drawCircle(tx,ty,6*d,p);}}
            p.setStyle(Paint.Style.FILL);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(17*d);p.setColor(road?0xFF62E28B:0xFFFFC857);c.drawText("方向："+direction,14*d,328*d,p);p.setTypeface(Typeface.DEFAULT);p.setTextSize(11*d);p.setColor(Color.WHITE);
            c.drawText("人物："+(foundPlayer?"已找到":"未找到")+"  坐标("+playerX+","+playerY+")  置信度 "+String.format(java.util.Locale.US,"%.0f%%",confidence*100),14*d,348*d,p);
            c.drawText("地图分类：道路 "+pct(roadPct)+"   深黑雾 "+pct(fogPct)+"   非道路 "+pct(nonRoadPct),14*d,367*d,p);
            c.drawText("目标("+targetX+","+targetY+")  路径长度 "+pathLength,14*d,385*d,p);
            p.setColor(0xFFBDBDBD);c.drawText("状态："+clip(detail,52),14*d,405*d,p);
            p.setColor(0xFF80CBC4);c.drawText("诊断：",14*d,425*d,p);p.setColor(0xFFE0E0E0);String s=diag;for(int i=0;i<4;i++){String line=part(s,52,i);if(line.length()==0)break;c.drawText(line,14*d,442*d+i*16*d,p);}
            p.setColor(0xFF333333);c.drawRoundRect(new RectF(10*d,510*d,185*d,552*d),8*d,8*d,p);c.drawRoundRect(new RectF(195*d,510*d,380*d,552*d),8*d,8*d,p);p.setColor(Color.WHITE);p.setTextSize(13*d);c.drawText("停止",82*d,537*d,p);c.drawText("重新规划",255*d,537*d,p);p.setColor(0xFF777777);p.setTextSize(10*d);c.drawText("拖动窗口移动 · 方向分数格式：直线/容错/横向",14*d,575*d,p);
        }
        private String pct(float v){return String.format(java.util.Locale.US,"%.0f%%",v*100f);}
        private String part(String s,int n,int index){if(s==null)return"";int from=index*n;if(from>=s.length())return"";int to=Math.min(s.length(),from+n);return s.substring(from,to);}
        public boolean onTouchEvent(MotionEvent e){switch(e.getAction()){case MotionEvent.ACTION_DOWN:downX=e.getRawX();downY=e.getRawY();startX=lp.x;startY=lp.y;return true;case MotionEvent.ACTION_MOVE:if(Math.abs(e.getRawX()-downX)>8||Math.abs(e.getRawY()-downY)>8){lp.x=startX+(int)(downX-e.getRawX());lp.y=startY+(int)(e.getRawY()-downY);try{wm.updateViewLayout(this,lp);}catch(Throwable ignored){}}return true;case MotionEvent.ACTION_UP:if(Math.abs(e.getRawX()-downX)<8&&Math.abs(e.getRawY()-downY)<8&&e.getY()>500*getResources().getDisplayMetrics().density){if(e.getX()<190*getResources().getDisplayMetrics().density){if(service!=null)service.stopBrush();}else if(service!=null)service.requestReplan();}return true;}return true;}
    }
}
