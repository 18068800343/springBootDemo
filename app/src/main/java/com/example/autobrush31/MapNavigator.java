package com.example.autobrush31;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.ArrayDeque;

/**
 * Conservative minimap navigator.
 *
 * Important game behavior: the minimap is a moving viewport.  A previously
 * explored gray road can reach the viewport edge and then appear to end in a
 * straight black strip.  That black strip is NOT an unexplored exit.
 *
 * Therefore direction selection is based on:
 *  1) confirmed gray road immediately in front of the player;
 *  2) length of the continuous road corridor;
 *  3) an interior gray->black frontier (newly unexplored area);
 *  4) penalty for a corridor which simply disappears at the viewport edge;
 *  5) previous direction hysteresis, but never at the cost of a clearly longer
 *     forward corridor.
 *
 * Cardinal directions are evaluated first. Diagonals are used only when no
 * cardinal direction has enough confirmed road.
 */
public final class MapNavigator {
    private MapNavigator() {}

    public static final class Result {
        public boolean foundPlayer, roadDirection;
        public int dx, dy, playerX, playerY, targetX, targetY, pathLength, routeCells;
        public float confidence;
    }

    public static Result analyze(Bitmap src) { return analyze(src, 0, 0); }

    public static Result analyze(Bitmap src, int prevDx, int prevDy) {
        Result o = new Result();
        if (src == null || src.isRecycled()) return o;
        int w = src.getWidth(), h = src.getHeight();
        if (w < 30 || h < 30) return o;

        int[] p = findPlayer(src, w, h);
        if (p[0] < 0) return o;
        o.foundPlayer = true;
        o.playerX = p[0];
        o.playerY = p[1];
        o.confidence = Math.min(1f, p[2] / 60f);

        // First evaluate cardinal directions. This avoids choosing a diagonal
        // merely because it touches a wider-looking corner of the road.
        final int[][] card = {{1,0},{0,1},{-1,0},{0,-1}}; // right, down, left, up
        int prev = dirIndex(prevDx, prevDy);
        int prevReach = prev < 4 ? corridorReach(src, o.playerX, o.playerY,
                card[prev][0], card[prev][1]) : 0;

        int best = -1, bestScore = Integer.MIN_VALUE, bestReach = 0;
        boolean anyCardinal = false;
        for (int i = 0; i < card.length; i++) {
            int dx = card[i][0], dy = card[i][1];
            int reach = corridorReach(src, o.playerX, o.playerY, dx, dy);
            if (reach < 10 || !hasRoadAhead(src, o.playerX, o.playerY, dx, dy)) continue;
            anyCardinal = true;

            int score = corridorScore(src, o.playerX, o.playerY, dx, dy, reach);

            if (prev >= 0) {
                if (i == prev) score += 360;
                if (i == opposite(prev)) {
                    // Do not immediately turn around. But a substantially longer
                    // corridor is allowed to override this, which is important
                    // when the viewport has just scrolled.
                    if (reach < prevReach + 18) score -= 900;
                }
                if (i != prev && sameGeneralHeading(dx,dy,prevDx,prevDy)) score += 100;
            }

            if (score > bestScore) {
                bestScore = score;
                best = i;
                bestReach = reach;
            }
        }

        // If there is no usable cardinal corridor, try diagonals.
        if (!anyCardinal) {
            final int[][] diag = {{1,-1},{-1,-1},{-1,1},{1,1}};
            for (int i = 0; i < diag.length; i++) {
                int dx = diag[i][0], dy = diag[i][1];
                int reach = corridorReach(src, o.playerX, o.playerY, dx, dy);
                if (reach < 12 || !hasRoadAhead(src, o.playerX, o.playerY, dx, dy)) continue;
                int score = corridorScore(src, o.playerX, o.playerY, dx, dy, reach) - 30;
                if (prev >= 0) {
                    if (dx == prevDx && dy == prevDy) score += 320;
                    if (dx == -prevDx && dy == -prevDy && reach < prevReach + 18) score -= 850;
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = 4 + i;
                    bestReach = reach;
                }
            }
            if (best >= 4) {
                int[][] diag = {{1,-1},{-1,-1},{-1,1},{1,1}};
                o.dx = diag[best-4][0]; o.dy = diag[best-4][1];
            }
        } else if (best >= 0) {
            o.dx = card[best][0]; o.dy = card[best][1];
        }

        if (best < 0 || !hasRoadAhead(src, o.playerX, o.playerY, o.dx, o.dy)) return o;

        o.roadDirection = true;
        int len = Math.max(18, Math.min(50, bestReach));
        o.targetX = o.playerX + o.dx * len;
        o.targetY = o.playerY + o.dy * len;
        o.pathLength = Math.max(1, bestReach / 3);
        o.routeCells = Math.max(1, Math.min(16, bestReach / 5));
        return o;
    }

    /** Score a corridor by usable length and an interior unexplored frontier. */
    private static int corridorScore(Bitmap s, int px, int py, int dx, int dy, int reach) {
        int score = reach * 20;
        int frontier = findFrontier(s, px, py, dx, dy, reach);
        if (frontier > 0) {
            // A gray road ending in black inside the viewport is a strong
            // unexplored-frontier signal.
            score += 300;
            score += Math.max(0, 120 - frontier);
        }

        // If the road reaches the minimap border, its black continuation is
        // very likely viewport clipping. Do not treat that as a discovery goal.
        if (reachesViewportEdge(s, px, py, dx, dy, reach)) score -= 520;

        // Prefer a reasonably straight, stable corridor. Width is deliberately
        // a small tie-breaker; it must not overpower corridor length.
        score += straightnessBonus(s, px, py, dx, dy, reach);
        return score;
    }

    private static int findFrontier(Bitmap s, int px, int py, int dx, int dy, int reach) {
        for (int d = 12; d <= Math.min(110, reach + 12); d += 4) {
            int x = px + dx*d, y = py + dy*d;
            if (!inside(s,x,y,3)) break;
            float g = localGray(s,x,y,6,6);
            if (g < 0.09f) {
                // Ignore a dark area that is essentially touching the viewport
                // border; that is clipping rather than an unexplored exit.
                if (distanceToEdge(s,x,y) > 12) return d;
                return 0;
            }
        }
        return 0;
    }

    private static boolean reachesViewportEdge(Bitmap s, int px, int py, int dx, int dy, int reach) {
        int x = px + dx * Math.min(105, reach + 8);
        int y = py + dy * Math.min(105, reach + 8);
        return distanceToEdge(s,x,y) <= 10;
    }

    private static int distanceToEdge(Bitmap s, int x, int y) {
        return Math.min(Math.min(x, y), Math.min(s.getWidth()-1-x, s.getHeight()-1-y));
    }

    private static int straightnessBonus(Bitmap s, int px, int py, int dx, int dy, int reach) {
        int sx = -dy, sy = dx;
        int bonus = 0;
        int samples = 0;
        for (int d = 16; d <= Math.min(75, reach); d += 10) {
            int x = px + dx*d, y = py + dy*d;
            float center = localGray(s,x,y,5,5);
            float side1 = localGray(s,x + sx*10,y + sy*10,4,4);
            float side2 = localGray(s,x - sx*10,y - sy*10,4,4);
            if (center >= 0.18f) bonus += 8;
            if (side1 < 0.12f && side2 < 0.12f) bonus += 5;
            samples++;
        }
        return Math.min(90, bonus + samples*2);
    }

    private static boolean sameGeneralHeading(int dx,int dy,int px,int py) {
        if (px==0 && py==0) return false;
        return dx*px + dy*py > 0;
    }

    private static int dirIndex(int dx,int dy) {
        if (dx>0 && dy==0) return 0;
        if (dx==0 && dy>0) return 1;
        if (dx<0 && dy==0) return 2;
        if (dx==0 && dy<0) return 3;
        return -1;
    }

    private static int opposite(int i) { return (i + 2) % 4; }

    private static boolean hasRoadAhead(Bitmap s,int px,int py,int dx,int dy) {
        int good=0;
        for(int d=7;d<=31;d+=4){
            if(localGray(s,px+dx*d,py+dy*d,7,7)>=0.15f)good++;
        }
        return good>=3;
    }

    private static int corridorReach(Bitmap s,int px,int py,int dx,int dy) {
        int reach=0, gaps=0;
        for(int d=7;d<=105;d+=3){
            int x=px+dx*d,y=py+dy*d;
            if(!inside(s,x,y,4))break;
            float g=localGray(s,x,y,6,6);
            if(g>=0.16f){reach=d;gaps=0;}
            else if(g<0.10f && d<=30 && gaps<2){
                // The green player marker hides the road near the start.
                gaps++;
            } else break;
        }
        return reach;
    }

    private static boolean inside(Bitmap s,int x,int y,int margin){
        return x>=margin && y>=margin && x<s.getWidth()-margin && y<s.getHeight()-margin;
    }

    private static float localGray(Bitmap s,int cx,int cy,int rx,int ry){
        int w=s.getWidth(),h=s.getHeight(),good=0,n=0;
        for(int y=Math.max(0,cy-ry);y<=Math.min(h-1,cy+ry);y++){
            for(int x=Math.max(0,cx-rx);x<=Math.min(w-1,cx+rx);x++){
                int c=s.getPixel(x,y);
                int r=Color.red(c),g=Color.green(c),b=Color.blue(c);
                int lum=(299*r+587*g+114*b)/1000;
                int ch=Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b));
                if(lum>=42&&lum<190&&ch<=70)good++;
                n++;
            }
        }
        return n==0?0f:good/(float)n;
    }

    private static int[] findPlayer(Bitmap s,int w,int h){
        boolean[] mask=new boolean[w*h],seen=new boolean[w*h];
        for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){
            int c=s.getPixel(x,y),r=Color.red(c),g=Color.green(c),b=Color.blue(c);
            mask[y*w+x]=g>=65&&g>=r+6&&g>=b+3;
        }
        int bx=-1,by=-1,best=-1;
        for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++)if(mask[y*w+x]&&!seen[y*w+x]){
            ArrayDeque<Integer>q=new ArrayDeque<>();q.add(y*w+x);seen[y*w+x]=true;
            int n=0,sx=0,sy=0,minx=x,maxx=x,miny=y,maxy=y;
            while(!q.isEmpty()){
                int pp=q.removeFirst(),cx=pp%w,cy=pp/w;n++;sx+=cx;sy+=cy;
                minx=Math.min(minx,cx);maxx=Math.max(maxx,cx);miny=Math.min(miny,cy);maxy=Math.max(maxy,cy);
                for(int k=0;k<4;k++){
                    int nx=cx+(k==0?1:k==1?-1:0),ny=cy+(k==2?1:k==3?-1:0);
                    if(nx<1||nx>=w-1||ny<1||ny>=h-1)continue;
                    int id=ny*w+nx;
                    if(mask[id]&&!seen[id]){seen[id]=true;q.addLast(id);}
                }
            }
            int bw=maxx-minx+1,bh=maxy-miny+1,area=bw*bh;
            if(n<20||area>5000)continue;
            int score=n*3-Math.abs(bw-bh)*2;
            if(score>best){best=score;bx=sx/n;by=sy/n;}
        }
        return new int[]{bx,by,Math.max(0,best/3)};
    }
}
