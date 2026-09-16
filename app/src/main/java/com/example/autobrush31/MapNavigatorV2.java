package com.example.autobrush31;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.ArrayDeque;

/** Minimap planner: gray-white road is walkable; deep black is fog; dark-gray is non-road. */
public final class MapNavigatorV2 {
    private MapNavigatorV2() {}
    public static final class Result {
        public boolean foundPlayer,roadDirection;
        public int dx,dy,playerX,playerY,targetX,targetY,pathLength,routeCells;
        public float confidence,roadPct,fogPct,nonRoadPct;
        public String debug="";
    }
    public static Result analyze(Bitmap s,int prevDx,int prevDy){
        Result o=new Result();if(s==null||s.isRecycled())return o;int w=s.getWidth(),h=s.getHeight();
        classifyStats(s,o);int[] p=findPlayer(s,w,h);
        if(p[0]<0){o.debug="未找到人物 | 绿色候选像素="+p[2]+" | 道路"+pct(o.roadPct)+" 雾"+pct(o.fogPct)+" 非道路"+pct(o.nonRoadPct);return o;}
        o.foundPlayer=true;o.playerX=p[0];o.playerY=p[1];o.confidence=Math.min(1f,p[2]/18f);
        int cell=Math.max(2,Math.min(4,Math.min(w,h)/55));int[][] dirs={{1,0},{1,1},{0,1},{-1,1},{-1,0},{-1,-1},{0,-1},{1,-1}};
        double best=-1e9;int bdx=0,bdy=0,blen=0;StringBuilder dbg=new StringBuilder();
        for(int[] d:dirs){int c=continuity(s,o.playerX,o.playerY,d[0],d[1],cell,false),relaxed=continuity(s,o.playerX,o.playerY,d[0],d[1],cell,true),wide=wideContinuity(s,o.playerX,o.playerY,d[0],d[1],cell);dbg.append(name(d[0],d[1])).append('=').append(c).append('/').append(relaxed).append('/').append(wide).append(' ');if(c<2&&relaxed<3)continue;double score=Math.max(c*32.0,relaxed*25.0)+wide*8.0;if(prevDx!=0||prevDy!=0){int dot=d[0]*prevDx+d[1]*prevDy;if(dot>0)score+=130;if(dot<0)score-=320;}if(d[0]!=0&&d[1]!=0)score-=10;if(score>best){best=score;bdx=d[0];bdy=d[1];blen=Math.max(c,Math.min(9,relaxed));}}
        if(blen>=2){o.dx=bdx;o.dy=bdy;o.roadDirection=true;o.targetX=o.playerX+bdx*blen*cell;o.targetY=o.playerY+bdy*blen*cell;o.pathLength=blen;o.routeCells=blen;o.debug="人物("+o.playerX+","+o.playerY+") 绿="+p[2]+" 道路"+pct(o.roadPct)+" 雾"+pct(o.fogPct)+" 非道路"+pct(o.nonRoadPct)+" | "+dbg+"=>"+name(bdx,bdy);}
        else o.debug="人物("+o.playerX+","+o.playerY+") 绿="+p[2]+" 未确认道路 | "+dbg;
        return o;
    }
    private static String pct(float v){return String.format(java.util.Locale.US,"%.0f%%",v*100f);}
    private static void classifyStats(Bitmap s,Result o){int step=Math.max(1,Math.min(s.getWidth(),s.getHeight())/120),total=0,road=0,fog=0;for(int y=0;y<s.getHeight();y+=step)for(int x=0;x<s.getWidth();x+=step){int c=s.getPixel(x,y),r=Color.red(c),g=Color.green(c),b=Color.blue(c),lum=(299*r+587*g+114*b)/1000,sp=Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b));total++;if(isRoadPixel(r,g,b,lum,sp))road++;else if(lum<=38&&sp<=45)fog++;}o.roadPct=road/(float)Math.max(1,total);o.fogPct=fog/(float)Math.max(1,total);o.nonRoadPct=Math.max(0f,1f-o.roadPct-o.fogPct);}
    private static int continuity(Bitmap s,int px,int py,int dx,int dy,int cell,boolean relaxed){int good=0,miss=0;for(int i=1;i<=10;i++){int x=px+Math.round(dx*i*cell),y=py+Math.round(dy*i*cell);if(x<0||y<0||x>=s.getWidth()||y>=s.getHeight())break;if(road(s,x,y,cell,relaxed)){good++;miss=0;}else{miss++;if(i>=4&&miss>=3)break;}}return good;}
    private static int wideContinuity(Bitmap s,int px,int py,int dx,int dy,int cell){int good=0;for(int i=2;i<=9;i++){int x=px+Math.round(dx*i*cell),y=py+Math.round(dy*i*cell);for(int side=-1;side<=1;side++){int xx=x+dy*side*cell,yy=y-dx*side*cell;if(road(s,xx,yy,cell,false))good++;}}return good;}
    private static boolean road(Bitmap s,int cx,int cy,int cell,boolean relaxed){int good=0,n=0;for(int y=Math.max(0,cy-cell);y<=Math.min(s.getHeight()-1,cy+cell);y++)for(int x=Math.max(0,cx-cell);x<=Math.min(s.getWidth()-1,cx+cell);x++){int c=s.getPixel(x,y),r=Color.red(c),g=Color.green(c),b=Color.blue(c),lum=(299*r+587*g+114*b)/1000,sp=Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b));if(isRoadPixel(r,g,b,lum,sp)||(relaxed&&lum>=42&&lum<=210&&sp<=70))good++;n++;}return good/(float)Math.max(1,n)>=(relaxed?0.14f:0.16f);}
    private static boolean isRoadPixel(int r,int g,int b,int lum,int sp){return lum>=42&&lum<=210&&sp<=65;}
    private static int[] findPlayer(Bitmap s,int w,int h){
        boolean[] m=new boolean[w*h],seen=new boolean[w*h];int greenPixels=0;
        for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){int c=s.getPixel(x,y),r=Color.red(c),g=Color.green(c),b=Color.blue(c);m[y*w+x]=g>=50&&g>=r+4&&g>=b+1;if(m[y*w+x])greenPixels++;}
        int bx=-1,by=-1,best=-1;
        for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++)if(m[y*w+x]&&!seen[y*w+x]){ArrayDeque<Integer>q=new ArrayDeque<>();q.add(y*w+x);seen[y*w+x]=true;int n=0,sx=0,sy=0,minx=x,maxx=x,miny=y,maxy=y;while(!q.isEmpty()){int id=q.removeFirst(),cx=id%w,cy=id/w;n++;sx+=cx;sy+=cy;minx=Math.min(minx,cx);maxx=Math.max(maxx,cx);miny=Math.min(miny,cy);maxy=Math.max(maxy,cy);for(int k=0;k<4;k++){int nx=cx+(k==0?1:k==1?-1:0),ny=cy+(k==2?1:k==3?-1:0);if(nx<1||ny<1||nx>=w-1||ny>=h-1)continue;int ni=ny*w+nx;if(m[ni]&&!seen[ni]){seen[ni]=true;q.addLast(ni);}}}int area=(maxx-minx+1)*(maxy-miny+1);if(n<4||area>5000)continue;int score=n*3-Math.abs((maxx-minx)-(maxy-miny));if(score>best){best=score;bx=sx/n;by=sy/n;}}
        return new int[]{bx,by,greenPixels};
    }
    private static String name(int x,int y){if(x>0&&y>0)return"↘";if(x>0&&y<0)return"↗";if(x<0&&y>0)return"↙";if(x<0&&y<0)return"↖";if(x>0)return"→";if(x<0)return"←";if(y>0)return"↓";if(y<0)return"↑";return"—";}
}
