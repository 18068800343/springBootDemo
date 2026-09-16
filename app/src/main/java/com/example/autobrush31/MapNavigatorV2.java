package com.example.autobrush31;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.ArrayDeque;

/** Planner v2: use visible grey corridor continuity first; black is never a direct walk target. */
public final class MapNavigatorV2 {
    private MapNavigatorV2() {}
    public static final class Result {
        public boolean foundPlayer,roadDirection;
        public int dx,dy,playerX,playerY,targetX,targetY,pathLength,routeCells;
        public float confidence;
        public String debug="";
    }
    public static Result analyze(Bitmap s,int prevDx,int prevDy){
        Result o=new Result();if(s==null||s.isRecycled())return o;int w=s.getWidth(),h=s.getHeight();
        int[] p=findPlayer(s,w,h);if(p[0]<0){o.debug="未找到绿色人物";return o;}o.foundPlayer=true;o.playerX=p[0];o.playerY=p[1];o.confidence=Math.min(1f,p[2]/45f);
        int cell=Math.max(2,Math.min(4,Math.min(w,h)/55));
        int[][] dirs={{1,0},{1,1},{0,1},{-1,1},{-1,0},{-1,-1},{0,-1},{1,-1}};
        double best=-1e9;int bdx=0,bdy=0,blen=0;StringBuilder dbg=new StringBuilder();
        for(int[] d:dirs){
            int c=continuity(s,o.playerX,o.playerY,d[0],d[1],cell);int wide=wideContinuity(s,o.playerX,o.playerY,d[0],d[1],cell);
            if(c<2)continue;double score=c*32.0+wide*8.0;
            if(prevDx!=0||prevDy!=0){int dot=d[0]*prevDx+d[1]*prevDy;if(dot>0)score+=130;if(dot<0)score-=320;}
            if(d[0]!=0&&d[1]!=0)score-=10;
            dbg.append(name(d[0],d[1])).append('=').append(c).append('/').append(wide).append(' ');
            if(score>best){best=score;bdx=d[0];bdy=d[1];blen=Math.max(c,wide);}
        }
        // A straight black line at the minimap boundary is ignored. We only require
        // a confirmed grey corridor in the chosen direction, so the character itself
        // can cover the corridor junction without turning around.
        if(blen>=2&&continuity(s,o.playerX,o.playerY,bdx,bdy,cell)>=2){
            o.dx=bdx;o.dy=bdy;o.roadDirection=true;o.targetX=o.playerX+bdx*blen*cell;o.targetY=o.playerY+bdy*blen*cell;o.pathLength=blen;o.routeCells=blen;o.debug=dbg+"=>"+name(bdx,bdy);return o;
        }
        o.debug=dbg.length()==0?"没有连续灰色道路":dbg.toString();return o;
    }
    private static int continuity(Bitmap s,int px,int py,int dx,int dy,int cell){int good=0;for(int i=1;i<=9;i++){int x=px+Math.round(dx*i*cell),y=py+Math.round(dy*i*cell);if(x<0||y<0||x>=s.getWidth()||y>=s.getHeight())break;if(road(s,x,y,cell))good++;else if(i>=4)break;}return good;}
    private static int wideContinuity(Bitmap s,int px,int py,int dx,int dy,int cell){int good=0;for(int i=2;i<=8;i++){int x=px+Math.round(dx*i*cell),y=py+Math.round(dy*i*cell);for(int side=-1;side<=1;side++){int xx=x+(dy*side*cell),yy=y-(dx*side*cell);if(road(s,xx,yy,cell))good++;}}return good;}
    private static boolean road(Bitmap s,int cx,int cy,int cell){int good=0,n=0;for(int y=Math.max(0,cy-cell);y<=Math.min(s.getHeight()-1,cy+cell);y++)for(int x=Math.max(0,cx-cell);x<=Math.min(s.getWidth()-1,cx+cell);x++){int c=s.getPixel(x,y),r=Color.red(c),g=Color.green(c),b=Color.blue(c),lum=(299*r+587*g+114*b)/1000,sp=Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b));if(lum>=42&&lum<=195&&sp<=65)good++;n++;}return good/(float)Math.max(1,n)>=.16f;}
    private static int[] findPlayer(Bitmap s,int w,int h){boolean[] m=new boolean[w*h],seen=new boolean[w*h];for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){int c=s.getPixel(x,y),r=Color.red(c),g=Color.green(c),b=Color.blue(c);m[y*w+x]=g>=65&&g>=r+8&&g>=b+3;}int bx=-1,by=-1,best=-1;for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++)if(m[y*w+x]&&!seen[y*w+x]){ArrayDeque<Integer>q=new ArrayDeque<>();q.add(y*w+x);seen[y*w+x]=true;int n=0,sx=0,sy=0,minx=x,maxx=x,miny=y,maxy=y;while(!q.isEmpty()){int id=q.removeFirst(),cx=id%w,cy=id/w;n++;sx+=cx;sy+=cy;minx=Math.min(minx,cx);maxx=Math.max(maxx,cx);miny=Math.min(miny,cy);maxy=Math.max(maxy,cy);for(int k=0;k<4;k++){int nx=cx+(k==0?1:k==1?-1:0),ny=y+(k==2?1:k==3?-1:0);if(nx<1||ny<1||nx>=w-1||ny>=h-1)continue;int ni=ny*w+nx;if(m[ni]&&!seen[ni]){seen[ni]=true;q.addLast(ni);}}}int area=(maxx-minx+1)*(maxy-miny+1);if(n<12||area>5000)continue;int score=n*3-Math.abs((maxx-minx)-(maxy-miny))*2;if(score>best){best=score;bx=sx/n;by=sy/n;}}return new int[]{bx,by,Math.max(0,best/3)};}
    private static String name(int x,int y){if(x>0&&y>0)return"↘";if(x>0&&y<0)return"↗";if(x<0&&y>0)return"↙";if(x<0&&y<0)return"↖";if(x>0)return"→";if(x<0)return"←";if(y>0)return"↓";if(y<0)return"↑";return"—";}
}
