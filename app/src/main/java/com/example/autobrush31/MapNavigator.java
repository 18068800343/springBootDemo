package com.example.autobrush31;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.ArrayDeque;
import java.util.Arrays;

/** Minimap planner: walk confirmed gray road toward the nearest gray/black frontier. */
public final class MapNavigator {
    private MapNavigator() {}
    public static final class Result {
        public boolean foundPlayer, roadDirection;
        public int dx, dy, playerX, playerY, targetX, targetY, pathLength, routeCells;
        public float confidence;
    }

    public static Result analyze(Bitmap src) {
        Result o=new Result();
        if(src==null||src.isRecycled())return o;
        int w=src.getWidth(),h=src.getHeight(); if(w<30||h<30)return o;
        int[] pl=findPlayer(src,w,h); if(pl[0]<0)return o;
        o.foundPlayer=true;o.playerX=pl[0];o.playerY=pl[1];o.confidence=Math.min(1f,pl[2]/10f);

        final int step=2,gw=(w+step-1)/step,gh=(h+step-1)/step,total=gw*gh;
        boolean[] road=new boolean[total],dark=new boolean[total];
        for(int gy=0;gy<gh;gy++)for(int gx=0;gx<gw;gx++){
            int cx=Math.min(w-1,gx*step+1),cy=Math.min(h-1,gy*step+1);
            int gray=0,bright=0,black=0,n=0,radius=6;
            for(int y=Math.max(0,cy-radius);y<=Math.min(h-1,cy+radius);y++)for(int x=Math.max(0,cx-radius);x<=Math.min(w-1,cx+radius);x++){
                int c=src.getPixel(x,y),r=Color.red(c),g=Color.green(c),b=Color.blue(c);
                int lum=(299*r+587*g+114*b)/1000,chroma=Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b));n++;
                if(lum>=42&&lum<185&&chroma<=65)gray++; if(lum>=185)bright++; if(lum<38)black++;
            }
            road[gy*gw+gx]=gray>=Math.max(3,(int)(n*.08f))&&bright<n*.38f&&black<n*.96f;
            dark[gy*gw+gx]=black>=n*.55f;
        }

        int pgx=clamp(o.playerX/step,0,gw-1),pgy=clamp(o.playerY/step,0,gh-1),start=pgy*gw+pgx;
        road[start]=true;
        boolean[] connected=new boolean[total];int[] prev=new int[total],dist=new int[total];
        Arrays.fill(prev,-1);Arrays.fill(dist,-1);
        ArrayDeque<Integer>q=new ArrayDeque<>();connected[start]=true;dist[start]=0;q.add(start);
        while(!q.isEmpty()){
            int cur=q.removeFirst(),x=cur%gw,y=cur/gw;
            for(int k=0;k<4;k++){int nx=x+(k==0?1:k==1?-1:0),ny=y+(k==2?1:k==3?-1:0);if(!in(nx,ny,gw,gh))continue;int id=ny*gw+nx;if(!road[id]||connected[id])continue;connected[id]=true;dist[id]=dist[cur]+1;prev[id]=cur;q.addLast(id);}
        }

        // The green marker can cover the gray cell under it. Attach to the nearest
        // confirmed gray road cell, but never manufacture a black walk target.
        if(!hasRoadNeighbor(pgx,pgy,connected,gw,gh)){
            int near=nearestRoad(pgx,pgy,connected,gw,gh,10);
            if(near>=0){int nx=near%gw,ny=near/gw,cx=pgx,cy=pgy,guard=0;while((cx!=nx||cy!=ny)&&guard++<10){if(cx!=nx)cx+=Integer.compare(nx,cx);else cy+=Integer.compare(ny,cy);if(in(cx,cy,gw,gh))connected[cy*gw+cx]=true;}}
        }

        // Choose the closest confirmed gray cell bordering black (the unexplored
        // frontier). This is what the screenshot needs: from the green marker the
        // next frontier lies toward the upper-right, so movement starts upper-right.
        int frontier=-1,bestScore=Integer.MAX_VALUE;
        for(int y=1;y<gh-1;y++)for(int x=1;x<gw-1;x++){
            int id=y*gw+x;if(!connected[id]||dist[id]<2||!touchesDark(x,y,dark,gw,gh))continue;
            int score=dist[id]*100-localRoadCount(x,y,connected,gw,gh);
            if(score<bestScore){bestScore=score;frontier=id;}
        }
        if(frontier<0)return o;

        int first=frontier,guard=0;while(prev[first]!=-1&&prev[first]!=start&&guard++<total)first=prev[first];
        int tx=first%gw,ty=first/gw,vx=tx-pgx,vy=ty-pgy;
        int dx=Integer.compare(vx,0),dy=Integer.compare(vy,0);

        // Prefer diagonal movement when both cardinal components are confirmed road.
        if(dx!=0&&dy!=0){
            boolean xr=in(pgx+dx,pgy,gw,gh)&&connected[pgy*gw+pgx+dx];
            boolean yr=in(pgx,pgy+dy,gw,gh)&&connected[(pgy+dy)*gw+pgx];
            if(!(xr&&yr)){if(xr)dy=0;else if(yr)dx=0;else return o;}
        }
        if(dx==0&&dy==0)return o;
        int nx=pgx+dx,ny=pgy+dy;if(!in(nx,ny,gw,gh)||!connected[ny*gw+nx])return o;
        o.roadDirection=true;o.dx=dx;o.dy=dy;o.targetX=nx*step+step/2;o.targetY=ny*step+step/2;
        o.pathLength=Math.max(1,dist[frontier]);o.routeCells=Math.max(1,Math.min(8,dist[frontier]));return o;
    }

    private static boolean touchesDark(int x,int y,boolean[] dark,int w,int h){
        for(int k=0;k<4;k++){int nx=x+(k==0?1:k==1?-1:0),ny=y+(k==2?1:k==3?-1:0);if(in(nx,ny,w,h)&&dark[ny*w+nx])return true;}return false;
    }
    private static int localRoadCount(int x,int y,boolean[] road,int w,int h){int n=0;for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){int nx=x+dx,ny=y+dy;if(in(nx,ny,w,h)&&road[ny*w+nx])n++;}return n;}
    private static boolean hasRoadNeighbor(int x,int y,boolean[] road,int w,int h){return(in(x+1,y,w,h)&&road[y*w+x+1])||(in(x-1,y,w,h)&&road[y*w+x-1])||(in(x,y+1,w,h)&&road[(y+1)*w+x])||(in(x,y-1,w,h)&&road[(y-1)*w+x]);}
    private static int nearestRoad(int x,int y,boolean[] road,int w,int h,int radius){int best=-1,bd=Integer.MAX_VALUE;for(int dy=-radius;dy<=radius;dy++)for(int dx=-radius;dx<=radius;dx++){if(Math.abs(dx)+Math.abs(dy)>radius)continue;int nx=x+dx,ny=y+dy;if(!in(nx,ny,w,h)||!road[ny*w+nx]||(dx==0&&dy==0))continue;int d=Math.abs(dx)+Math.abs(dy);if(d<bd){bd=d;best=ny*w+nx;}}return best;}
    private static int[] findPlayer(Bitmap s,int w,int h){
        boolean[] mask=new boolean[w*h],seen=new boolean[w*h];
        for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){int c=s.getPixel(x,y),r=Color.red(c),g=Color.green(c),b=Color.blue(c);mask[y*w+x]=g>=65&&g>=r+6&&g>=b+3;}
        int bx=-1,by=-1,best=-1,bn=0;
        for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++)if(mask[y*w+x]&&!seen[y*w+x]){
            ArrayDeque<Integer>q=new ArrayDeque<>();q.add(y*w+x);seen[y*w+x]=true;int n=0,sx=0,sy=0,minx=x,maxx=x,miny=y,maxy=y;
            while(!q.isEmpty()){int p=q.removeFirst(),px=p%w,py=p/w;n++;sx+=px;sy+=py;minx=Math.min(minx,px);maxx=Math.max(maxx,px);miny=Math.min(miny,py);maxy=Math.max(maxy,py);for(int k=0;k<4;k++){int nx=px+(k==0?1:k==1?-1:0),ny=py+(k==2?1:k==3?-1:0);if(nx<1||nx>=w-1||ny<1||ny>=h-1)continue;int id=ny*w+nx;if(mask[id]&&!seen[id]){seen[id]=true;q.addLast(id);}}}
            int bw=maxx-minx+1,bh=maxy-miny+1,area=bw*bh;if(n<5||area>5000)continue;float compact=n/(float)Math.max(1,area);int score=Math.round(n*3f+compact*100f-Math.abs(bw-bh));if(score>best){best=score;bn=n;bx=sx/n;by=sy/n;}
        }return new int[]{bx,by,bn};
    }
    private static boolean in(int x,int y,int w,int h){return x>=0&&x<w&&y>=0&&y<h;}
    private static int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}
}
