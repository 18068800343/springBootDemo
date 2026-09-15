package com.example.autobrush31;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Visual minimap planner. It deliberately does not choose random directions:
 * every command comes from the currently visible map image.
 */
public final class MapNavigator {
    private MapNavigator() {}

    public static final class Result {
        public boolean foundPlayer;
        public int dx, dy;
        public float confidence;
        public int playerX, playerY, targetX, targetY, pathLength;
        public int routeCells;
    }

    public static Result analyze(Bitmap src) {
        Result o = new Result();
        if (src == null || src.isRecycled()) return o;
        final int w=src.getWidth(), h=src.getHeight();
        if(w<30 || h<30) return o;

        // 1) Locate the green player marker. Use the largest local green blob
        // rather than the centroid of every green pixel in the whole map.
        int bestX=-1,bestY=-1,bestN=0;
        boolean[] green=new boolean[w*h];
        for(int y=1;y<h-1;y++) for(int x=1;x<w-1;x++) {
            boolean v=isPlayer(src.getPixel(x,y));
            green[y*w+x]=v;
        }
        boolean[] seen=new boolean[w*h];
        for(int y=1;y<h-1;y++) for(int x=1;x<w-1;x++) {
            int id=y*w+x;
            if(!green[id]||seen[id]) continue;
            int minX=x,maxX=x,minY=y,maxY=y,n=0,sx=0,sy=0;
            ArrayDeque<Integer> qq=new ArrayDeque<>(); qq.add(id); seen[id]=true;
            while(!qq.isEmpty()) {
                int p=qq.removeFirst(), px=p%w, py=p/w;
                n++; sx+=px; sy+=py;
                minX=Math.min(minX,px); maxX=Math.max(maxX,px);
                minY=Math.min(minY,py); maxY=Math.max(maxY,py);
                for(int k=0;k<4;k++) {
                    int nx=px+(k==0?1:k==1?-1:0), ny=py+(k==2?1:k==3?-1:0);
                    if(nx<1||nx>=w-1||ny<1||ny>=h-1) continue;
                    int ni=ny*w+nx;
                    if(green[ni]&&!seen[ni]) {seen[ni]=true;qq.addLast(ni);}
                }
            }
            int area=(maxX-minX+1)*(maxY-minY+1);
            if(n>=3 && n>bestN && area<=Math.max(400,n*35)) {bestN=n;bestX=sx/n;bestY=sy/n;}
        }
        if(bestN<3) return o;
        o.foundPlayer=true; o.playerX=bestX; o.playerY=bestY;
        o.confidence=Math.min(1f,bestN/12f);

        // 2) Work on a coarse image grid. A cell is open only when its local
        // patch looks like terrain/road. Several thresholds are tested and
        // the mask with the strongest useful connected area wins.
        final int step=Math.max(2,Math.min(3,Math.min(w,h)/70));
        final int gw=Math.max(1,(w+step-1)/step), gh=Math.max(1,(h+step-1)/step), total=gw*gh;
        boolean[] free=buildBestMask(src,w,h,step,gw,gh,bestX,bestY);
        int pgx=clamp(bestX/step,0,gw-1), pgy=clamp(bestY/step,0,gh-1);

        // The player sprite occupies the road. Open a small local area around
        // it so anti-aliased green pixels cannot disconnect the graph.
        for(int yy=pgy-2;yy<=pgy+2;yy++) for(int xx=pgx-2;xx<=pgx+2;xx++)
            if(xx>=0&&xx<gw&&yy>=0&&yy<gh) free[yy*gw+xx]=true;

        // 3) Compute distance-to-wall. This is the key change: route targets
        // are biased toward the medial axis of the visible road, not its edge.
        int[] clearance=distanceFromWalls(free,gw,gh);

        // 4) BFS over the visible map. Among reachable cells choose a forward
        // frontier with good distance from walls. Penalize borders and reward
        // cells that are farther from the player. This produces an actual
        // image-derived route instead of a fixed/random direction.
        int[] prev=new int[total], dist=new int[total];
        Arrays.fill(prev,-1); Arrays.fill(dist,-1);
        ArrayDeque<Integer> q=new ArrayDeque<>();
        int start=pgy*gw+pgx; dist[start]=0; q.add(start);
        int best=start,bestScore=Integer.MIN_VALUE;
        while(!q.isEmpty()) {
            int cur=q.removeFirst(), x=cur%gw, y=cur/gw, d=dist[cur];
            if(d>=3) {
                int edge=Math.min(Math.min(x,y),Math.min(gw-1-x,gh-1-y));
                int cl=clearance[cur];
                int score=d*10 + Math.min(cl,8)*24 - Math.max(0,3-edge)*45;
                // Avoid selecting a point that is only one cell wide at a map
                // corner; centerline points are much more stable for joystick.
                if(cl>=1 && score>bestScore) {bestScore=score;best=cur;}
            }
            // 8-neighbour expansion gives diagonal corridors a real route.
            visit(x-1,y,cur,gw,gh,free,prev,dist,q);
            visit(x+1,y,cur,gw,gh,free,prev,dist,q);
            visit(x,y-1,cur,gw,gh,free,prev,dist,q);
            visit(x,y+1,cur,gw,gh,free,prev,dist,q);
            visit(x-1,y-1,cur,gw,gh,free,prev,dist,q);
            visit(x+1,y-1,cur,gw,gh,free,prev,dist,q);
            visit(x-1,y+1,cur,gw,gh,free,prev,dist,q);
            visit(x+1,y+1,cur,gw,gh,free,prev,dist,q);
        }

        if(best==start) {
            // If the visible map is tiny/ambiguous, only move to a directly
            // connected neighbour. Never invent a direction.
            int bx=pgx,by=pgy,score=-1;
            for(int yy=pgy-1;yy<=pgy+1;yy++) for(int xx=pgx-1;xx<=pgx+1;xx++) {
                if(xx==pgx&&yy==pgy) continue;
                if(xx<0||xx>=gw||yy<0||yy>=gh) continue;
                int id=yy*gw+xx;
                if(free[id] && clearance[id]>score) {score=clearance[id];bx=xx;by=yy;}
            }
            if(score>=0){o.dx=Integer.compare(bx,pgx);o.dy=Integer.compare(by,pgy);o.pathLength=1;o.routeCells=1;}
            return o;
        }

        // Follow the computed route to its first few cells, not directly to
        // the far target. This prevents long joystick holds from crossing a
        // turn or carrying the character into a wall.
        int first=best, guard=0;
        while(prev[first]!=-1 && prev[first]!=start && guard++<total) first=prev[first];
        int tx=first%gw, ty=first/gw;
        o.targetX=tx*step+step/2; o.targetY=ty*step+step/2;
        o.pathLength=dist[best]; o.routeCells=Math.max(1,dist[first]);
        o.dx=Integer.compare(tx,pgx); o.dy=Integer.compare(ty,pgy);
        return o;
    }

    private static boolean[] buildBestMask(Bitmap src,int w,int h,int step,int gw,int gh,int px,int py){
        int[][] th={{45,58},{58,70},{70,84},{84,100}};
        boolean[] best=null; int bestReach=-1;
        int pgx=clamp(px/step,0,gw-1),pgy=clamp(py/step,0,gh-1);
        for(int[] t:th){
            boolean[] m=new boolean[gw*gh];
            for(int gy=0;gy<gh;gy++) for(int gx=0;gx<gw;gx++) {
                int x=Math.min(w-1,gx*step+step/2), y=Math.min(h-1,gy*step+step/2);
                int sum=0,n=0,max=0,min=255;
                for(int yy=Math.max(0,y-step);yy<=Math.min(h-1,y+step);yy++) for(int xx=Math.max(0,x-step);xx<=Math.min(w-1,x+step);xx++) {
                    int c=src.getPixel(xx,yy); int r=Color.red(c),g=Color.green(c),b=Color.blue(c);
                    int lum=(299*r+587*g+114*b)/1000; sum+=lum;n++;max=Math.max(max,lum);min=Math.min(min,lum);
                }
                int avg=sum/Math.max(1,n);
                // Colored map elements are valid terrain too; very dark flat
                // regions are treated as outside/wall.
                m[gy*gw+gx]=(avg>=t[0] || (max-min>=24 && avg>=t[1]));
            }
            // Count connected cells from player for mask quality selection.
            if(!m[pgy*gw+pgx]){
                for(int yy=pgy-1;yy<=pgy+1;yy++) for(int xx=pgx-1;xx<=pgx+1;xx++)
                    if(xx>=0&&xx<gw&&yy>=0&&yy<gh)m[yy*gw+xx]=true;
            }
            int reach=reachableCount(m,gw,gh,pgx,pgy);
            if(reach>bestReach){bestReach=reach;best=m;}
        }
        return best==null?new boolean[gw*gh]:best;
    }

    private static int reachableCount(boolean[] m,int gw,int gh,int sx,int sy){
        if(!m[sy*gw+sx])return 0;
        boolean[] seen=new boolean[m.length]; ArrayDeque<Integer> q=new ArrayDeque<>();
        int st=sy*gw+sx;seen[st]=true;q.add(st);int n=0;
        while(!q.isEmpty()){int p=q.removeFirst(),x=p%gw,y=p/gw;n++;for(int k=0;k<4;k++){int nx=x+(k==0?1:k==1?-1:0),ny=y+(k==2?1:k==3?-1:0);if(nx>=0&&nx<gw&&ny>=0&&ny<gh){int id=ny*gw+nx;if(m[id]&&!seen[id]){seen[id]=true;q.addLast(id);}}}}return n;
    }

    private static int[] distanceFromWalls(boolean[] free,int gw,int gh){
        int[] d=new int[free.length]; Arrays.fill(d,999);
        ArrayDeque<Integer> q=new ArrayDeque<>();
        for(int y=0;y<gh;y++) for(int x=0;x<gw;x++){
            int id=y*gw+x;
            if(!free[id]){d[id]=0;q.addLast(id);}
            else if(x==0||y==0||x==gw-1||y==gh-1){d[id]=1;q.addLast(id);}
        }
        while(!q.isEmpty()){
            int p=q.removeFirst(),x=p%gw,y=p/gw;
            for(int k=0;k<4;k++){int nx=x+(k==0?1:k==1?-1:0),ny=y+(k==2?1:k==3?-1:0);if(nx<0||nx>=gw||ny<0||ny>=gh)continue;int id=ny*gw+nx;if(d[id]>d[p]+1){d[id]=d[p]+1;q.addLast(id);}}
        }
        return d;
    }

    private static void visit(int x,int y,int cur,int gw,int gh,boolean[] free,int[] prev,int[] dist,ArrayDeque<Integer> q){
        if(x<0||x>=gw||y<0||y>=gh)return;int id=y*gw+x;if(!free[id]||dist[id]>=0)return;dist[id]=dist[cur]+1;prev[id]=cur;q.addLast(id);
    }

    private static boolean isPlayer(int c){
        int r=Color.red(c),g=Color.green(c),b=Color.blue(c);
        return g>=125 && g-r>=32 && g-b>=18 && g>=b*1.12f;
    }

    private static int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}
}
