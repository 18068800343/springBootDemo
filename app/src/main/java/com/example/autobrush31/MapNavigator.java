package com.example.autobrush31;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.ArrayDeque;

/** Minimap navigation: follow confirmed gray road; black is unknown, never a walk target. */
public final class MapNavigator {
    private MapNavigator() {}
    public static final class Result {
        public boolean foundPlayer, roadDirection;
        public int dx, dy, playerX, playerY, targetX, targetY, pathLength, routeCells;
        public float confidence;
    }
    public static Result analyze(Bitmap src) { return analyze(src, 0, 0); }
    public static Result analyze(Bitmap src, int prevDx, int prevDy) {
        Result o=new Result(); if(src==null||src.isRecycled())return o;
        int w=src.getWidth(),h=src.getHeight(); if(w<30||h<30)return o;
        int[] p=findPlayer(src,w,h); if(p[0]<0)return o;
        o.foundPlayer=true;o.playerX=p[0];o.playerY=p[1];o.confidence=Math.min(1f,p[2]/60f);
        final int[][] dirs={{1,0},{1,-1},{0,-1},{-1,-1},{-1,0},{-1,1},{0,1},{1,1}};
        int bestDir=-1,bestScore=Integer.MIN_VALUE,bestLen=0;
        for(int i=0;i<dirs.length;i++){
            int dx=dirs[i][0],dy=dirs[i][1],grayRun=0,grayTotal=0,blackAfter=0,lastGray=0;
            for(int d=12;d<=78;d+=3){
                int cx=o.playerX+dx*d,cy=o.playerY+dy*d;float g=localGray(src,cx,cy,dx==0?7:6,dy==0?7:6);boolean dark=localDark(src,cx,cy,5);
                if(g>=.25f){grayTotal++;grayRun++;lastGray=d;}else if(dark){if(grayRun>=2)blackAfter++;}else if(grayRun>=2)break;
            }
            if(grayRun<2)continue;
            int score=grayRun*100+grayTotal*8+Math.min(blackAfter,5)*12+lastGray;
            // Direction memory prevents the player from bouncing between the
            // two sides of a road when the green marker hides the road edge.
            // Reverse is allowed only when the previous corridor is genuinely gone.
            if(prevDx!=0||prevDy!=0){
                if(dx==-prevDx&&dy==-prevDy)score-=900;
                else if(dx==prevDx&&dy==prevDy)score+=650;
                else if(dx*prevDx+dy*prevDy>0)score+=220;
            }
            if(score>bestScore){bestScore=score;bestDir=i;bestLen=lastGray;}
        }
        if(bestDir<0)return o;
        int dx=dirs[bestDir][0],dy=dirs[bestDir][1];
        boolean firstRoad=false;
        for(int d=10;d<=21&&!firstRoad;d+=3)firstRoad=localGray(src,o.playerX+dx*d,o.playerY+dy*d,7,7)>=.20f;
        if(!firstRoad)return o;
        o.roadDirection=true;o.dx=dx;o.dy=dy;int len=Math.max(14,Math.min(38,bestLen));
        o.targetX=o.playerX+dx*len;o.targetY=o.playerY+dy*len;o.pathLength=Math.max(1,bestLen/3);o.routeCells=Math.max(1,Math.min(12,bestLen/6));return o;
    }
    private static float localGray(Bitmap s,int cx,int cy,int rx,int ry){int w=s.getWidth(),h=s.getHeight(),good=0,n=0;for(int y=Math.max(0,cy-ry);y<=Math.min(h-1,cy+ry);y++)for(int x=Math.max(0,cx-rx);x<=Math.min(w-1,cx+rx);x++){int c=s.getPixel(x,y),r=Color.red(c),g=Color.green(c),b=Color.blue(c),lum=(299*r+587*g+114*b)/1000,ch=Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b));if(lum>=42&&lum<185&&ch<=65)good++;n++;}return n==0?0f:good/(float)n;}
    private static boolean localDark(Bitmap s,int cx,int cy,int rad){int w=s.getWidth(),h=s.getHeight(),dark=0,n=0;for(int y=Math.max(0,cy-rad);y<=Math.min(h-1,cy+rad);y++)for(int x=Math.max(0,cx-rad);x<=Math.min(w-1,cx+rad);x++){int c=s.getPixel(x,y),r=Color.red(c),g=Color.green(c),b=Color.blue(c),lum=(299*r+587*g+114*b)/1000;if(lum<38)dark++;n++;}return n>0&&dark>n*.55f;}
    private static int[] findPlayer(Bitmap s,int w,int h){boolean[] mask=new boolean[w*h],seen=new boolean[w*h];for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){int c=s.getPixel(x,y),r=Color.red(c),g=Color.green(c),b=Color.blue(c);mask[y*w+x]=g>=65&&g>=r+6&&g>=b+3;}int bx=-1,by=-1,best=-1,bn=0;for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++)if(mask[y*w+x]&&!seen[y*w+x]){ArrayDeque<Integer>q=new ArrayDeque<>();q.add(y*w+x);seen[y*w+x]=true;int n=0,sx=0,sy=0,minx=x,maxx=x,miny=y,maxy=y;while(!q.isEmpty()){int pp=q.removeFirst(),px=pp%w,py=pp/w;n++;sx+=px;sy+=py;minx=Math.min(minx,px);maxx=Math.max(maxx,px);miny=Math.min(miny,py);maxy=Math.max(maxy,py);for(int k=0;k<4;k++){int nx=px+(k==0?1:k==1?-1:0),ny=py+(k==2?1:k==3?-1:0);if(nx<1||nx>=w-1||ny<1||ny>=h-1)continue;int id=ny*w+nx;if(mask[id]&&!seen[id]){seen[id]=true;q.addLast(id);}}}int bw=maxx-minx+1,bh=maxy-miny+1,area=bw*bh;if(n<20||area>5000)continue;int score=n*3-Math.abs(bw-bh)*2;if(score>best){best=score;bn=n;bx=sx/n;by=sy/n;}}return new int[]{bx,by,bn};}
}
