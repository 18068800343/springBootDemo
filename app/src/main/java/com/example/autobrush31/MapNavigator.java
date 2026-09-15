package com.example.autobrush31;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Minimap planner for MWDL.
 * Rules: gray/gray-white road is walkable; bright boundary lines are not a
 * destination; black ahead is unknown and is considered only when a road
 * opening touches it. The character is kept near the road center.
 */
public final class MapNavigator {
    private MapNavigator() {}
    public static final class Result {
        public boolean foundPlayer;
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
        o.foundPlayer=true;o.playerX=p[0];o.playerY=p[1];o.confidence=Math.min(1f,p[2]/12f);

        int step=Math.max(2,Math.min(3,Math.min(w,h)/70));
        int gw=(w+step-1)/step,gh=(h+step-1)/step,total=gw*gh;
        boolean[] road=new boolean[total];
        boolean[] unknown=new boolean[total];
        for(int gy=0;gy<gh;gy++)for(int gx=0;gx<gw;gx++){
            int x=Math.min(w-1,gx*step+step/2),y=Math.min(h-1,gy*step+step/2);
            int sum=0,n=0,bright=0,dark=0;
            for(int yy=Math.max(0,y-step);yy<=Math.min(h-1,y+step);yy++)for(int xx=Math.max(0,x-step);xx<=Math.min(w-1,x+step);xx++){
                int c=src.getPixel(xx,yy),r=Color.red(c),g=Color.green(c),b=Color.blue(c);
                int lum=(299*r+587*g+114*b)/1000;sum+=lum;n++;
                if(lum>=205)bright++; if(lum<55)dark++;
            }
            int avg=sum/Math.max(1,n);
            // Road: mid-gray/gray-white, but reject cells dominated by very
            // bright boundary pixels. Preserve mildly bright road interiors.
            road[gy*gw+gx]=(avg>=58&&avg<205&&bright<n*0.55) || (avg>=90&&avg<225&&bright<n*0.30);
            unknown[gy*gw+gx]=(dark>n*0.55);
        }

        int pgx=clamp(o.playerX/step,0,gw-1),pgy=clamp(o.playerY/step,0,gh-1);
        // Always make a small road patch around the player, since the green
        // marker itself can hide the road pixels.
        for(int y=pgy-2;y<=pgy+2;y++)for(int x=pgx-2;x<=pgx+2;x++)if(in(x,y,gw,gh))road[y*gw+x]=true;

        // Remove isolated noise from road mask with a local neighbor rule.
        boolean[] clean=road.clone();
        for(int y=1;y<gh-1;y++)for(int x=1;x<gw-1;x++){
            int id=y*gw+x,c=0;for(int k=0;k<8;k++){int nx=x+(k==0||k==4?1:k==1||k==5?-1:0),ny=y+(k==2||k==3?1:k==6||k==7?-1:0);if(in(nx,ny,gw,gh)&&road[ny*gw+nx])c++;}
            if(road[id]&&c<2&&Math.abs(x-pgx)>2&&Math.abs(y-pgy)>2)clean[id]=false;
        }
        road=clean;

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
                // Prefer unexplored black openings, then long centerline routes.
                int score=(frontier?10000:0)+d*5+Math.min(cl,10)*180+edge*4;
                if(cl>=1&&score>bestScore){bestScore=score;best=cur;}
            }
            visit(x+1,y,cur,gw,gh,road,prev,dist,q);visit(x-1,y,cur,gw,gh,road,prev,dist,q);
            visit(x,y+1,cur,gw,gh,road,prev,dist,q);visit(x,y-1,cur,gw,gh,road,prev,dist,q);
        }

        if(best<0){
            // No road route: do not invent a direction. Check only immediate
            // road neighbors with the greatest clearance.
            int bx=pgx,by=pgy,sc=0;
            int[][] ds={{1,0},{-1,0},{0,1},{0,-1}};
            for(int[] z:ds){int x=pgx+z[0],y=pgy+z[1];if(in(x,y,gw,gh)&&road[y*gw+x]&&clearance[y*gw+x]>sc){sc=clearance[y*gw+x];bx=x;by=y;}}
            if(sc>0){o.dx=Integer.compare(bx,pgx);o.dy=Integer.compare(by,pgy);o.routeCells=1;o.pathLength=1;}
            return o;
        }

        // Backtrack only a short segment. Re-read the minimap frequently so
        // turns are handled by a fresh plan instead of a long blind joystick.
        int first=best,guard=0;
        while(prev[first]!=-1&&prev[first]!=start&&guard++<total)first=prev[first];
        int tx=first%gw,ty=first/gw;
        o.targetX=tx*step+step/2;o.targetY=ty*step+step/2;
        o.dx=Integer.compare(tx,pgx);o.dy=Integer.compare(ty,pgy);
        o.pathLength=dist[best];o.routeCells=Math.max(1,dist[first]);
        return o;
    }

    private static boolean touchesUnknown(int x,int y,boolean[] unknown,boolean[] road,int gw,int gh){
        for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
            if(Math.abs(dx)+Math.abs(dy)!=1)continue;int nx=x+dx,ny=y+dy;
            if(in(nx,ny,gw,gh)&&unknown[ny*gw+nx]&&!road[ny*gw+nx])return true;
        }return false;
    }

    private static int[] distanceToNonRoad(boolean[] road,int gw,int gh){
        int[] d=new int[road.length];Arrays.fill(d,999);ArrayDeque<Integer>q=new ArrayDeque<>();
        for(int y=0;y<gh;y++)for(int x=0;x<gw;x++){int id=y*gw+x;if(!road[id]){d[id]=0;q.add(id);}}
        while(!q.isEmpty()){int p=q.removeFirst(),x=p%gw,y=p/gw;for(int k=0;k<4;k++){int nx=x+(k==0?1:k==1?-1:0),ny=y+(k==2?1:k==3?-1:0);if(!in(nx,ny,gw,gh))continue;int id=ny*gw+nx;if(d[id]>d[p]+1){d[id]=d[p]+1;q.add(id);}}}return d;
    }

    private static void visit(int x,int y,int cur,int gw,int gh,boolean[] road,int[] prev,int[]dist,ArrayDeque<Integer>q){
        if(!in(x,y,gw,gh))return;int id=y*gw+x;if(!road[id]||dist[id]>=0)return;dist[id]=dist[cur]+1;prev[id]=cur;q.addLast(id);
    }

    private static int[] findPlayer(Bitmap s,int w,int h){
        boolean[] g=new boolean[w*h],seen=new boolean[w*h];int bx=-1,by=-1,bn=0;
        for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){int c=s.getPixel(x,y),r=Color.red(c),gg=Color.green(c),b=Color.blue(c);g[y*w+x]=gg>=120&&gg-r>=30&&gg-b>=16&&gg>=b*1.10f;}
        for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){int id=y*w+x;if(!g[id]||seen[id])continue;ArrayDeque<Integer>q=new ArrayDeque<>();q.add(id);seen[id]=true;int n=0,sx=0,sy=0,minx=x,maxx=x,miny=y,maxy=y;while(!q.isEmpty()){int p=q.removeFirst(),px=p%w,py=p/w;n++;sx+=px;sy+=py;minx=Math.min(minx,px);maxx=Math.max(maxx,px);miny=Math.min(miny,py);maxy=Math.max(maxy,py);for(int k=0;k<4;k++){int nx=px+(k==0?1:k==1?-1:0),ny=py+(k==2?1:k==3?-1:0);if(nx<1||nx>=w-1||ny<1||ny>=h-1)continue;int ni=ny*w+nx;if(g[ni]&&!seen[ni]){seen[ni]=true;q.addLast(ni);}}}int area=(maxx-minx+1)*(maxy-miny+1);if(n>=3&&n>bn&&area<=Math.max(500,n*45)){bn=n;bx=sx/n;by=sy/n;}}
        return new int[]{bx,by,bn};
    }
    private static boolean in(int x,int y,int w,int h){return x>=0&&x<w&&y>=0&&y<h;}
    private static int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}
}
