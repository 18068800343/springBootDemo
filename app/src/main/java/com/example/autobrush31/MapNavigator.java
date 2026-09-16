package com.example.autobrush31;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.ArrayDeque;

/**
 * Minimap navigation.
 * Gray is confirmed floor. Black is unknown/outside the currently revealed map.
 * A straight black strip at the minimap border is treated as viewport clipping,
 * not as an exit. Direction is selected from the actual gray corridor in front
 * of the player, with hysteresis to avoid walking back into the already revealed
 * corridor when the player marker hides part of the road.
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

        // Clockwise: right, up-right, up, up-left, left, down-left, down, down-right.
        final int[][] dirs = {
                {1,0},{1,-1},{0,-1},{-1,-1},
                {-1,0},{-1,1},{0,1},{1,1}
        };

        int bestDir = -1;
        int bestScore = Integer.MIN_VALUE;
        int bestReach = 0;
        int prevIndex = dirIndex(prevDx, prevDy);
        int prevReach = prevIndex < 0 ? 0 : corridorReach(src, o.playerX, o.playerY,
                dirs[prevIndex][0], dirs[prevIndex][1]);

        for (int i = 0; i < dirs.length; i++) {
            int dx = dirs[i][0], dy = dirs[i][1];
            int reach = corridorReach(src, o.playerX, o.playerY, dx, dy);
            if (reach < 2) continue;

            // Score the continuous gray corridor, not the black area beyond it.
            int score = reach * 24;
            score += immediateRoad(src, o.playerX, o.playerY, dx, dy) ? 180 : 0;

            if (prevIndex >= 0) {
                int dot = dx * prevDx + dy * prevDy;
                if (i == prevIndex) score += 420;
                else if (i == opposite(prevIndex)) score -= 1100;
                else if (dot > 0) score += 100;
            }

            // If another direction has a substantially longer visible corridor,
            // allow a turn even when direction memory prefers the old heading.
            // This is important when the player reaches a junction or the map scrolls.
            if (prevIndex >= 0 && i != prevIndex && reach >= Math.max(18, prevReach + 10)) {
                score += 520;
            }

            // Prefer a broad corridor over a one-pixel diagonal glimpse.
            score += corridorWidthBonus(src, o.playerX, o.playerY, dx, dy);

            if (score > bestScore) {
                bestScore = score;
                bestDir = i;
                bestReach = reach;
            }
        }

        if (bestDir < 0) return o;
        int dx = dirs[bestDir][0], dy = dirs[bestDir][1];

        // Hard gate: the first segment must contain real gray floor.
        if (!hasRoadAhead(src, o.playerX, o.playerY, dx, dy)) return o;

        o.roadDirection = true;
        o.dx = dx;
        o.dy = dy;
        int len = Math.max(16, Math.min(44, bestReach));
        o.targetX = o.playerX + dx * len;
        o.targetY = o.playerY + dy * len;
        o.pathLength = Math.max(1, bestReach / 3);
        o.routeCells = Math.max(1, Math.min(12, bestReach / 6));
        return o;
    }

    private static int dirIndex(int dx, int dy) {
        if (dx == 0 && dy == 0) return -1;
        if (dx > 0 && dy == 0) return 0;
        if (dx > 0 && dy < 0) return 1;
        if (dx == 0 && dy < 0) return 2;
        if (dx < 0 && dy < 0) return 3;
        if (dx < 0 && dy == 0) return 4;
        if (dx < 0 && dy > 0) return 5;
        if (dx == 0 && dy > 0) return 6;
        return 7;
    }

    private static int opposite(int i) { return (i + 4) % 8; }

    /** Longest continuous gray corridor in the direction, tolerant of the player marker. */
    private static int corridorReach(Bitmap s, int px, int py, int dx, int dy) {
        int reach = 0;
        int gaps = 0;
        for (int d = 8; d <= 105; d += 3) {
            int cx = px + dx * d;
            int cy = py + dy * d;
            if (cx < 2 || cy < 2 || cx >= s.getWidth()-2 || cy >= s.getHeight()-2) break;
            float g = localGray(s, cx, cy, 7, 7);
            if (g >= 0.18f) {
                reach = d;
                gaps = 0;
            } else if (g < 0.08f && gaps < 2 && d < 32) {
                // The green player marker can hide the first part of a corridor.
                gaps++;
            } else {
                break;
            }
        }
        return reach;
    }

    private static boolean immediateRoad(Bitmap s, int px, int py, int dx, int dy) {
        for (int d = 8; d <= 24; d += 4) {
            if (localGray(s, px + dx*d, py + dy*d, 8, 8) >= 0.16f) return true;
        }
        return false;
    }

    private static boolean hasRoadAhead(Bitmap s, int px, int py, int dx, int dy) {
        int good = 0;
        for (int d = 7; d <= 27; d += 4) {
            if (localGray(s, px + dx*d, py + dy*d, 8, 8) >= 0.15f) good++;
        }
        return good >= 2;
    }

    private static int corridorWidthBonus(Bitmap s, int px, int py, int dx, int dy) {
        int sx = -dy, sy = dx;
        int widest = 0;
        for (int d = 15; d <= 75; d += 10) {
            int width = 0;
            for (int side = -18; side <= 18; side += 3) {
                int x = px + dx*d + sx*side;
                int y = py + dy*d + sy*side;
                if (localGray(s, x, y, 3, 3) >= 0.18f) width++;
            }
            widest = Math.max(widest, width);
        }
        return Math.min(140, widest * 8);
    }

    private static float localGray(Bitmap s, int cx, int cy, int rx, int ry) {
        int w = s.getWidth(), h = s.getHeight(), good = 0, n = 0;
        for (int y = Math.max(0, cy-ry); y <= Math.min(h-1, cy+ry); y++) {
            for (int x = Math.max(0, cx-rx); x <= Math.min(w-1, cx+rx); x++) {
                int c = s.getPixel(x, y);
                int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
                int lum = (299*r + 587*g + 114*b) / 1000;
                int ch = Math.max(r, Math.max(g,b)) - Math.min(r, Math.min(g,b));
                if (lum >= 42 && lum < 185 && ch <= 65) good++;
                n++;
            }
        }
        return n == 0 ? 0f : good / (float)n;
    }

    private static int[] findPlayer(Bitmap s, int w, int h) {
        boolean[] mask = new boolean[w*h], seen = new boolean[w*h];
        for (int y=1; y<h-1; y++) for (int x=1; x<w-1; x++) {
            int c=s.getPixel(x,y), r=Color.red(c), g=Color.green(c), b=Color.blue(c);
            mask[y*w+x]=g>=65 && g>=r+6 && g>=b+3;
        }
        int bx=-1,by=-1,best=-1,bn=0;
        for (int y=1; y<h-1; y++) for (int x=1; x<w-1; x++) if(mask[y*w+x]&&!seen[y*w+x]){
            ArrayDeque<Integer> q=new ArrayDeque<>(); q.add(y*w+x); seen[y*w+x]=true;
            int n=0,sx=0,sy=0,minx=x,maxx=x,miny=y,maxy=y;
            while(!q.isEmpty()){
                int pp=q.removeFirst(),px=pp%w,py=pp/w; n++;sx+=px;sy+=py;
                minx=Math.min(minx,px);maxx=Math.max(maxx,px);miny=Math.min(miny,py);maxy=Math.max(maxy,py);
                for(int k=0;k<4;k++){
                    int nx=px+(k==0?1:k==1?-1:0),ny=py+(k==2?1:k==3?-1:0);
                    if(nx<1||nx>=w-1||ny<1||ny>=h-1)continue;
                    int id=ny*w+nx;
                    if(mask[id]&&!seen[id]){seen[id]=true;q.addLast(id);}
                }
            }
            int bw=maxx-minx+1,bh=maxy-miny+1,area=bw*bh;
            if(n<20||area>5000)continue;
            int score=n*3-Math.abs(bw-bh)*2;
            if(score>best){best=score;bn=n;bx=sx/n;by=sy/n;}
        }
        return new int[]{bx,by,bn};
    }
}
