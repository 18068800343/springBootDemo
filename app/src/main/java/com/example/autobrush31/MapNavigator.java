package com.example.autobrush31;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Conservative minimap planner.
 *
 * Important map rules for MWDL:
 *  - gray/gray-white ROAD SURFACE is walkable;
 *  - thin bright gray/white EDGE LINES are walls and are never walkable;
 *  - black is unexplored/unknown, never a walk target;
 *  - a black area can only be treated as an exit candidate when a confirmed
 *    road corridor leads to its edge. The joystick itself always receives the
 *    first confirmed ROAD cell direction, never the black direction.
 */
public final class MapNavigator {
    private MapNavigator() {}
    public static final class Result {
        public boolean foundPlayer;
        public boolean roadDirection;
        public int dx,dy,playerX,playerY,targetX,targetY,pathLength,routeCells;
        public float confidence;
    }

    public static Result analyze(Bitmap src){
        Result o=new Result();
        if(src==null||src.isRecycled())return o;
        int w=src.getWidth(),h=src.getHeight();
        if(w<30||h<30)return o;

        int[] p=findPlayer(src,w,h);
        if(p[0]<0)return o;
        o.foundPlayer=true;o.playerX=p[0];o.playerY=p[1];o.confidence=Math.min(1f,p[2]/10f);

        int step=Math.max(2,Math.min(3,Math.min(w,h)/70));
        int gw=(w+step-1)/step,gh=(h+step-1)/step,total=gw*gh;
        boolean[] road=new boolean[total];
        boolean[] unknown=new boolean[total];

        for(int gy=0;gy<gh;gy++)for(int gx=0;gx<gw;gx++){
            int cx=Math.min(w-1,gx*step+step/2),cy=Math.min(h-1,gy*step+step/2);
            int roadPix=0,brightPix=0,darkPix=0,n=0;
            for(int yy=Math.max(0,cy-step);yy<=Math.min(h-1,cy+step);yy++)for(int xx=Math.max(0,cx-step);xx<=Math.min(w-1,cx+step);xx++){
                int c=src.getPixel(xx,yy),r=Color.red(c),g=Color.green(c),b=Color.blue(c);
                int lum=(299*r+587*g+114*b)/1000;
                int cd=Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b));
                n++;
                if(lum>=55&&lum<190&&cd<=38)roadPix++;
                if(lum>=190)brightPix++;
                if(lum<50)darkPix++;
            }
            // A road cell needs a majority of genuine mid-gray pixels. A
            // bright-line dominated cell is explicitly rejected.
            road[gy*gw+gx]=roadPix>=Math.max(3,n*0.42f) && brightPix<n*0.35f;
            unknown[gy*gw+gx]=darkPix>=n*0.55f;
        }

        int pgx=clamp(o.playerX/step,0,gw-1),pgy=clamp(o.playerY/step,0,gh-1);
        // Only restore the single player cell. Never paint a 5x5 area as road:
        // doing that can bridge a white boundary and make the planner jump into black.
        road[pgy*gw+pgx]=true;

        // Remove thin/noisy road fragments. Do not remove the player's cell.
        boolean[] clean=road.clone();
        for(int y=0;y<gh;y++)for(int x=0;x<gw;x++){
            int id=y*gw+x;if(id==pgy*gw+pgx||!road[id])continue;
            int c4=0;
            if(in(x+1,y,gw,gh)&&road[y*gw+x+1])c4++;
            if(in(x-1,y,gw,gh)&&road[y*gw+x-1])c4++;
            if(in(x,y+1,gw,gh)&&road[(y+1)*gw+x])c4++;
            if(in(x,y-1,gw,gh)&&road[(y-1)*gw+x])c4++;
            if(c4==0)clean[id]=false;
        }
        road=clean;

        // Clearance from walls/non-road keeps the route in the center of the
        // gray corridor rather than riding its white/gray edge line.
        int[] clearance=distanceToNonRoad(road,gw,gh);
        int start=pgy*gw+pgx;
        int[] prev=new int[total],dist=new int[total];Arrays.fill(prev,-1);Arrays.fill(dist,-1);
        ArrayDeque<Integer> q=new ArrayDeque<>();dist[start]=0;q.add(start);

        int best=-1,bestScore=Integer.MIN_VALUE;
        while(!q.isEmpty()){
            int cur=q.removeFirst(),x=cur%gw,y=cur/gw,d=dist[cur];
            if(d>=2){
                boolean frontier=touchesUnknown(x,y,unknown,road,gw,gh);
                int cl=clearance[cur];
                int edge=Math.min(Math.min(x,y),Math.min(gw-1-x,gh-1-y));
                // Frontier is useful, but center clearance is more important.
                // Never score unknown/black itself as a target.
                int score=(frontier?7000:0)+d*2+Math.min(cl,8)*500+edge;
                if(cl>=1&&score>bestScore){bestScore=score;best=cur;}
            }
            visit(x+1,y,cur,gw,gh,road,prev,dist,q);
            visit(x-1,y,cur,gw,gh,road,prev,dist,q);
            visit(x,y+1,cur,gw,gh,road,prev,dist,q);
            visit(x,y-1,cur,gw,gh,road,prev,dist,q);
        }

        if(best<0)return o;

        // Backtrack to the first confirmed ROAD cell. Movement is deliberately
        // 4-directional; diagonal movement can cut across a boundary corner.
        int first=best,guard=0;
        while(prev[first]!=-1&&prev[first]!=start&&guard++<total)first=prev[first];
        int tx=first%gw,ty=first/gw;
        int ddx=Integer.compare(tx,pgx),ddy=Integer.compare(ty,pgy);
        if(Math.abs(tx-pgx)+Math.abs(ty-pgy)!=1){
            // If the reconstructed first point is not an immediate neighbor,
            // find the best immediate ROAD neighbor on the same BFS tree.
            int chosen=-1,score=Integer.MIN_VALUE;
            int[][] ds={{1,0},{-1,0},{0,1},{0,-1}};
            for(int[] z:ds){
                int nx=pgx+z[0],ny=pgy+z[1];
                if(!in(nx,ny,gw,gh)||!road[ny*gw+nx])continue;
                int id=ny*gw+nx,s=clearance[id]*1000-(dist[id]<0?999999:dist[id]);
                if(s>score){score=s;chosen=id;}
            }
            if(chosen<0)return o;
            tx=chosen%gw;ty=chosen/gw;ddx=tx-pgx;ddy=ty-pgy;
        }

        // Hard safety gate: first step must be a confirmed road cell with
        // non-trivial clearance. This prevents the observed "left-down into
        // black" failure.
        if(!in(tx,ty,gw,gh)||!road[ty*gw+tx]||clearance[ty*gw+tx]<1)return o;
        o.roadDirection=true;o.dx=ddx;o.dy=ddy;
        o.targetX=tx*step+step/2;o.targetY=ty*step+step/2;
        o.pathLength=Math.max(1,dist[best]);o.routeCells=1;
        return o;
    }

    private static boolean touchesUnknown(int x,int y,boolean[] unknown,boolean[] road,int gw,int gh){
        for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
            if(Math.abs(dx)+Math.abs(dy)!=1)continue;
            int nx=x+dx,ny=y+dy;
            if(in(nx,ny,gw,gh)&&unknown[ny*gw+nx]&&!road[ny*gw+nx])return true;
        }
        return false;
    }

    private static int[] distanceToNonRoad(boolean[] road,int gw,int gh){
        int[] d=new int[road.length];Arrays.fill(d,999);ArrayDeque<Integer>q=new ArrayDeque<>();
        for(int y=0;y<gh;y++)for(int x=0;x<gw;x++){
            int id=y*gw+x;if(!road[id]){d[id]=0;q.add(id);}
        }
        while(!q.isEmpty()){
            int p=q.removeFirst(),x=p%gw,y=p/gw;
            for(int k=0;k<4;k++){
                int nx=x+(k==0?1:k==1?-1:0),ny=y+(k==2?1:k==3?-1:0);
                if(!in(nx,ny,gw,gh))continue;
                int id=ny*gw+nx;if(d[id]>d[p]+1){d[id]=d[p]+1;q.add(id);}
            }
        }
        return d;
    }

    private static void visit(int x,int y,int cur,int gw,int gh,boolean[] road,int[] prev,int[]dist,ArrayDeque<Integer>q){
        if(!in(x,y,gw,gh))return;
        int id=y*gw+x;if(!road[id]||dist[id]>=0)return;
        dist[id]=dist[cur]+1;prev[id]=cur;q.addLast(id);
    }

    private static int[] findPlayer(Bitmap s,int w,int h){
        boolean[] g=new boolean[w*h],seen=new boolean[w*h];int bx=-1,by=-1,bn=0;
        for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){
            int c=s.getPixel(x,y),r=Color.red(c),gg=Color.green(c),b=Color.blue(c);
            g[y*w+x]=gg>=120&&gg-r>=30&&gg-b>=16&&gg>=b*1.10f;
        }
        for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){
            int id=y*w+x;if(!g[id]||seen[id])continue;
            ArrayDeque<Integer>q=new ArrayDeque<>();q.add(id);seen[id]=true;
            int n=0,sx=0,sy=0,minx=x,maxx=x,miny=y,maxy=y;
            while(!q.isEmpty()){
                int p=q.removeFirst(),px=p%w,py=p/w;n++;sx+=px;sy+=py;
                minx=Math.min(minx,px);maxx=Math.max(maxx,px);miny=Math.min(miny,py);maxy=Math.max(maxy,py);
                for(int k=0;k<4;k++){
                    int nx=px+(k==0?1:k==1?-1:0),ny=py+(k==2?1:k==3?-1:0);
                    if(nx<1||nx>=w-1||ny<1||ny>=h-1)continue;
                    int ni=ny*w+nx;if(g[ni]&&!seen[ni]){seen[ni]=true;q.addLast(ni);}
                }
            }
            int area=(maxx-minx+1)*(maxy-miny+1);
            if(n>=3&&n>bn&&area<=Math.max(500,n*45)){bn=n;bx=sx/n;by=sy/n;}
        }
        return new int[]{bx,by,bn};
    }
    private static boolean in(int x,int y,int w,int h){return x>=0&&x<w&&y>=0&&y<h;}
    private static int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}
}
