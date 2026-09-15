package com.example.autobrush31;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.ArrayDeque;
import java.util.Arrays;

/** Re-plans a short route from the minimap on every screenshot. */
public final class MapNavigator {
    private MapNavigator() {}

    public static final class Result {
        public boolean foundPlayer;
        public int dx, dy;
        public float confidence;
        public int playerX, playerY, targetX, targetY, pathLength;
    }

    public static Result analyze(Bitmap src) {
        Result o = new Result();
        if (src == null || src.isRecycled()) return o;
        int w=src.getWidth(), h=src.getHeight();
        if(w<24 || h<24) return o;

        // The player marker in the game's minimap is normally green. Use a
        // connected-component-like centroid and reject tiny green noise.
        long sx=0, sy=0, n=0;
        for(int y=1;y<h-1;y++) for(int x=1;x<w-1;x++) {
            int c=src.getPixel(x,y);
            if(isPlayer(c)) { sx+=x; sy+=y; n++; }
        }
        if(n<3) return o;
        o.foundPlayer=true;
        o.playerX=clamp((int)(sx/n),0,w-1);
        o.playerY=clamp((int)(sy/n),0,h-1);
        o.confidence=Math.min(1f,n/14f);

        // 2-pixel grid gives enough detail for the ~260x165 minimap while
        // keeping BFS inexpensive on a phone.
        final int step=Math.max(2,Math.min(3,Math.min(w,h)/70));
        final int gw=(w+step-1)/step, gh=(h+step-1)/step, total=gw*gh;
        boolean[] free=new boolean[total];
        for(int gy=0;gy<gh;gy++) for(int gx=0;gx<gw;gx++) {
            int x=Math.min(w-1,gx*step+step/2), y=Math.min(h-1,gy*step+step/2);
            free[gy*gw+gx]=isMapPassable(src.getPixel(x,y));
        }
        int pgx=clamp(o.playerX/step,0,gw-1), pgy=clamp(o.playerY/step,0,gh-1);
        for(int yy=pgy-2;yy<=pgy+2;yy++) for(int xx=pgx-2;xx<=pgx+2;xx++)
            if(xx>=0&&xx<gw&&yy>=0&&yy<gh) free[yy*gw+xx]=true;

        int[] prev=new int[total], dist=new int[total];
        Arrays.fill(prev,-1); Arrays.fill(dist,-1);
        ArrayDeque<Integer> q=new ArrayDeque<>();
        int start=pgy*gw+pgx; dist[start]=0; q.add(start);
        int best=start,bestScore=Integer.MIN_VALUE;

        // Select a reachable frontier: far away, but not glued to the outer
        // border. This avoids the previous random 90-degree turns.
        while(!q.isEmpty()) {
            int cur=q.removeFirst(), x=cur%gw, y=cur/gw, d=dist[cur];
            if(d>=4) {
                int edge=Math.min(Math.min(x,y),Math.min(gw-1-x,gh-1-y));
                int score=d*12 - Math.max(0,3-edge)*20;
                if(score>bestScore) {bestScore=score;best=cur;}
            }
            visit(x-1,y,cur,gw,gh,free,prev,dist,q);
            visit(x+1,y,cur,gw,gh,free,prev,dist,q);
            visit(x,y-1,cur,gw,gh,free,prev,dist,q);
            visit(x,y+1,cur,gw,gh,free,prev,dist,q);
        }

        if(best==start) {
            // Pick the best immediate reachable direction; never invent a
            // direction when the player cannot be located on the map.
            int[][] ds={{1,0},{-1,0},{0,1},{0,-1}};
            int bx=pgx,by=pgy,bd=-1;
            for(int[] d:ds){int x=pgx+d[0],y=pgy+d[1];if(x>=0&&x<gw&&y>=0&&y<gh){int id=y*gw+x;if(free[id]&&dist[id]>=0&&dist[id]>bd){bd=dist[id];bx=x;by=y;}}}
            o.dx=Integer.compare(bx,pgx); o.dy=Integer.compare(by,pgy); o.pathLength=Math.max(0,bd); return o;
        }

        int first=best,guard=0;
        while(prev[first]!=-1 && prev[first]!=start && guard++<total) first=prev[first];
        int tx=first%gw,ty=first/gw;
        o.targetX=tx*step+step/2; o.targetY=ty*step+step/2;
        o.pathLength=dist[best];
        o.dx=Integer.compare(tx,pgx); o.dy=Integer.compare(ty,pgy);
        return o;
    }

    private static void visit(int x,int y,int cur,int gw,int gh,boolean[] free,int[] prev,int[] dist,ArrayDeque<Integer> q){
        if(x<0||x>=gw||y<0||y>=gh)return;
        int id=y*gw+x;
        if(!free[id]||dist[id]>=0)return;
        dist[id]=dist[cur]+1;prev[id]=cur;q.addLast(id);
    }

    private static boolean isPlayer(int c){
        int r=Color.red(c),g=Color.green(c),b=Color.blue(c);
        return g>=125 && g-r>=32 && g-b>=18 && g>=b*1.12f;
    }

    private static boolean isMapPassable(int c){
        int r=Color.red(c),g=Color.green(c),b=Color.blue(c);
        if(isPlayer(c)) return true;
        int max=Math.max(r,Math.max(g,b)), min=Math.min(r,Math.min(g,b));
        // Keep colored map features and medium/bright terrain. Only very dark
        // pixels are walls/background. The old implementation incorrectly
        // rejected most gray terrain, which made BFS collapse and caused
        // apparently random movement.
        if(max-min>38) return max>=70;
        return (r+g+b)/3>=62;
    }

    private static int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}
}
