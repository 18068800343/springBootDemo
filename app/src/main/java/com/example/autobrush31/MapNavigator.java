package com.example.autobrush31;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.ArrayDeque;
import java.util.Arrays;

public final class MapNavigator {
    private MapNavigator() {}
    public static final class Result {
        public boolean foundPlayer,roadDirection;
        public int dx,dy,playerX,playerY,targetX,targetY,pathLength,routeCells;
        public float confidence;
    }
    public static Result analyze(Bitmap src){
        Result o=new Result();
        if(src==null||src.isRecycled())return o;
        int w=src.getWidth(),h=src.getHeight(); if(w<30||h<30)return o;
        int[] p=findPlayer(src,w,h); if(p[0]<0)return o;
        o.foundPlayer=true;o.playerX=p[0];o.playerY=p[1];o.confidence=Math.min(1f,p[2]/10f);
        int step=Math.max(2,Math.min(3,Math.min(w,h)/70));
        int gw=(w+step-1)/step,gh=(h+step-1)/step,total=gw*gh;
        boolean[] road=new boolean[total],unknown=new boolean[total];
        for(int gy=0;gy<gh;gy++)for(int gx=0;gx<gw;gx++){
            int cx=Math.min(w-1,gx*step+step/2),cy=Math.min(h-1,gy*step+step/2),rp=0,bp=0,dp=0,n=0;
            for(int yy=Math.max(0,cy-step);yy<=Math.min(h-1,cy+step);yy++)for(int xx=Math.max(0,cx-step);xx<=Math.min(w-1,cx+step);xx++){
                int c=src.getPixel(xx,yy),r=Color.red(c),g=Color.green(c),b=Color.blue(c),lum=(299*r+587*g+114*b)/1000,cd=Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b));n++;
                if(lum>=55&&lum<190&&cd<=38)rp++; if(lum>=190)bp++; if(lum<50)dp++;
            }
            road[gy*gw+gx]=rp>=Math.max(3,n*.42f)&&bp<n*.35f; unknown[gy*gw+gx]=dp>=n*.55f;
        }
        int pgx=clamp(o.playerX/step,0,gw-1),pgy=clamp(o.playerY/step,0,gh-1);road[pgy*gw+pgx]=true;
        boolean[] clean=road.clone();
        for(int y=0;y<gh;y++)for(int x=0;x<gw;x++){int id=y*gw+x;if(id==pgy*gw+pgx||!road[id])continue;int c=0;
            if(in(x+1,y,gw,gh)&&road[y*gw+x+1])c++;if(in(x-1,y,gw,gh)&&road[y*gw+x-1])c++;if(in(x,y+1,gw,gh)&&road[(y+1)*gw+x])c++;if(in(x,y-1,gw,gh)&&road[(y-1)*gw+x])c++;if(c==0)clean[id]=false;}
        road=clean;int[] clearance=distanceToNonRoad(road,gw,gh),prev=new int[total],dist=new int[total];Arrays.fill(prev,-1);Arrays.fill(dist,-1);
        int start=pgy*gw+pgx;ArrayDeque<Integer> q=new ArrayDeque<>();dist[start]=0;q.add(start);int best=-1,bestScore=Integer.MIN_VALUE;
        while(!q.isEmpty()){int cur=q.removeFirst(),x=cur%gw,y=cur/gw,d=dist[cur];if(d>=2){int cl=clearance[cur];int edge=Math.min(Math.min(x,y),Math.min(gw-1-x,gh-1-y));int score=(touchesUnknown(x,y,unknown,road,gw,gh)?7000:0)+d*2+Math.min(cl,8)*500+edge;if(cl>=1&&score>bestScore){bestScore=score;best=cur;}}
            visit(x+1,y,cur,gw,gh,road,prev,dist,q);visit(x-1,y,cur,gw,gh,road,prev,dist,q);visit(x,y+1,cur,gw,gh,road,prev,dist,q);visit(x,y-1,cur,gw,gh,road,prev,dist,q);}
        if(best<0)return o;int first=best,guard=0;while(prev[first]!=-1&&prev[first]!=start&&guard++<total)first=prev[first];
        int tx=first%gw,ty=first/gw,ddx=Integer.compare(tx,pgx),ddy=Integer.compare(ty,pgy);
        if(Math.abs(tx-pgx)+Math.abs(ty-pgy)!=1){int chosen=-1,score=Integer.MIN_VALUE;int[][] ds={{1,0},{-1,0},{0,1},{0,-1}};for(int[] z:ds){int nx=pgx+z[0],ny=pgy+z[1];if(!in(nx,ny,gw,gh)||!road[ny*gw+nx])continue;int id=ny*gw+nx,s=clearance[id]*1000-(dist[id]<0?999999:dist[id]);if(s>score){score=s;chosen=id;}}if(chosen<0)return o;tx=chosen%gw;ty=chosen/gw;ddx=tx-pgx;ddy=ty-pgy;}
        if(!in(tx,ty,gw,gh)||!road[ty*gw+tx]||clearance[ty*gw+tx]<1)return o;o.roadDirection=true;o.dx=ddx;o.dy=ddy;o.targetX=tx*step+step/2;o.targetY=ty*step+step/2;o.pathLength=Math.max(1,dist[best]);o.routeCells=1;return o;
    }
    private static boolean touchesUnknown(int x,int y,boolean[] u,boolean[] r,int w,int h){for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)if(Math.abs(dx)+Math.abs(dy)==1){int nx=x+dx,ny=y+dy;if(in(nx,ny,w,h)&&u[ny*w+nx]&&!r[ny*w+nx])return true;}return false;}
    private static int[] distanceToNonRoad(boolean[] road,int w,int h){int[] d=new int[road.length];Arrays.fill(d,999);ArrayDeque<Integer>q=new ArrayDeque<>();for(int y=0;y<h;y++)for(int x=0;x<w;x++){int id=y*w+x;if(!road[id]){d[id]=0;q.add(id);}}while(!q.isEmpty()){int p=q.removeFirst(),x=p%w,y=p/w;for(int k=0;k<4;k++){int nx=x+(k==0?1:k==1?-1:0),ny=y+(k==2?1:k==3?-1:0);if(!in(nx,ny,w,h))continue;int id=ny*w+nx;if(d[id]>d[p]+1){d[id]=d[p]+1;q.add(id);}}}return d;}
    private static void visit(int x,int y,int cur,int w,int h,boolean[] road,int[] prev,int[] dist,ArrayDeque<Integer>q){if(!in(x,y,w,h))return;int id=y*w+x;if(!road[id]||dist[id]>=0)return;dist[id]=dist[cur]+1;prev[id]=cur;q.addLast(id);}

    // The marker is visibly green on the supplied 691x1536 frame. Android
    // screenshot color conversion can reduce saturation, so use a deliberately
    // tolerant green-dominance test. The search is only performed on the
    // minimap crop, which prevents normal game UI icons from being selected.
    private static int[] findPlayer(Bitmap s,int w,int h){
        boolean[] mask=new boolean[w*h],seen=new boolean[w*h];
        for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){
            int c=s.getPixel(x,y),r=Color.red(c),g=Color.green(c),b=Color.blue(c);
            mask[y*w+x]=g>=80&&g>=r+8&&g>=b+4;
        }
        int bx=-1,by=-1,bn=0,bs=-1;
        for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++)if(mask[y*w+x]&&!seen[y*w+x]){
            int seed=y*w+x;ArrayDeque<Integer>q=new ArrayDeque<>();q.add(seed);seen[seed]=true;
            int n=0,sx=0,sy=0,minx=x,maxx=x,miny=y,maxy=y;
            while(!q.isEmpty()){
                int p=q.removeFirst(),px=p%w,py=p/w;n++;sx+=px;sy+=py;minx=Math.min(minx,px);maxx=Math.max(maxx,px);miny=Math.min(miny,py);maxy=Math.max(maxy,py);
                for(int k=0;k<4;k++){int nx=px+(k==0?1:k==1?-1:0),ny=py+(k==2?1:k==3?-1:0);if(nx<1||nx>=w-1||ny<1||ny>=h-1)continue;int ni=ny*w+nx;if(mask[ni]&&!seen[ni]){seen[ni]=true;q.addLast(ni);}}
            }
            int bw=maxx-minx+1,bh=maxy-miny+1,area=bw*bh;
            if(n<5||area>3600)continue;
            float compact=n/(float)Math.max(1,area);
            float roundPenalty=Math.abs(bw-bh);
            float score=n*0.03f+compact*30f-roundPenalty*0.7f;
            if(score>bs){bs=(int)score;bn=n;bx=sx/n;by=sy/n;}
        }
        return new int[]{bx,by,bn};
    }
    private static boolean in(int x,int y,int w,int h){return x>=0&&x<w&&y>=0&&y<h;}
    private static int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}
}
