package com.example.autobrush31;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Adaptive minimap route planner. It finds the green player marker, builds a
 * walkable grid from the minimap pixels, then uses BFS to select a distant
 * reachable waypoint instead of simply choosing the most occupied direction.
 */
public final class MapNavigator {
    private MapNavigator() {}

    public static final class Result {
        public boolean foundPlayer;
        public int dx, dy;
        public float confidence;
        public int playerX, playerY;
        public int targetX, targetY;
        public int pathLength;
    }

    public static Result analyze(Bitmap src) {
        Result out = new Result();
        if (src == null || src.isRecycled()) return out;
        final int w = src.getWidth(), h = src.getHeight();
        if (w < 20 || h < 20) return out;

        // 1) Detect the bright-green player marker by centroid.
        long sx = 0, sy = 0, n = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = src.getPixel(x, y);
                int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
                if (g >= 120 && g > r * 1.28f && g > b * 1.12f && g - r >= 30) {
                    sx += x; sy += y; n++;
                }
            }
        }
        if (n < 2) return out;
        out.foundPlayer = true;
        out.playerX = clamp((int)(sx / n), 0, w - 1);
        out.playerY = clamp((int)(sy / n), 0, h - 1);
        out.confidence = Math.min(1f, n / 18f);

        // Work on a small grid. This makes planning stable and cheap enough
        // for repeated Android screenshots.
        final int step = Math.max(2, Math.min(4, Math.min(w, h) / 55));
        final int gw = (w + step - 1) / step;
        final int gh = (h + step - 1) / step;
        boolean[] free = new boolean[gw * gh];

        for (int gy = 0; gy < gh; gy++) {
            for (int gx = 0; gx < gw; gx++) {
                int px = Math.min(w - 1, gx * step + step / 2);
                int py = Math.min(h - 1, gy * step + step / 2);
                free[gy * gw + gx] = isWalkable(src.getPixel(px, py));
            }
        }

        int pgx = clamp(out.playerX / step, 0, gw - 1);
        int pgy = clamp(out.playerY / step, 0, gh - 1);
        // Always allow the player cell and its immediate neighborhood.
        for (int yy = pgy - 1; yy <= pgy + 1; yy++)
            for (int xx = pgx - 1; xx <= pgx + 1; xx++)
                if (xx >= 0 && xx < gw && yy >= 0 && yy < gh) free[yy * gw + xx] = true;

        // 2) BFS from player through the visible walkable map.
        int total = gw * gh;
        int[] prev = new int[total];
        int[] dist = new int[total];
        Arrays.fill(prev, -1);
        Arrays.fill(dist, -1);
        ArrayDeque<Integer> q = new ArrayDeque<>();
        int start = pgy * gw + pgx;
        dist[start] = 0;
        q.add(start);

        int best = start;
        int bestScore = -1;
        final int margin = 2;
        while (!q.isEmpty()) {
            int cur = q.removeFirst();
            int cx = cur % gw, cy = cur / gw;
            int d = dist[cur];
            if (d >= 2) {
                // Prefer a point far from the player and close to the edge of
                // the visible map: this behaves like frontier exploration.
                int edge = Math.min(Math.min(cx, cy), Math.min(gw - 1 - cx, gh - 1 - cy));
                int edgeBonus = Math.max(0, margin + 2 - edge);
                int score = d * 10 - edgeBonus * 3;
                if (score > bestScore) { bestScore = score; best = cur; }
            }
            int x = cx, y = cy;
            if (x > 0) enqueue(x - 1, y, cur, gw, free, prev, dist, q);
            if (x + 1 < gw) enqueue(x + 1, y, cur, gw, free, prev, dist, q);
            if (y > 0) enqueue(x, y - 1, cur, gw, free, prev, dist, q);
            if (y + 1 < gh) enqueue(x, y + 1, cur, gw, free, prev, dist, q);
        }

        // 3) Recover only the first few cells of the route. We intentionally
        // use a short horizon because the minimap moves as the character moves.
        int target = best;
        int guard = 0;
        int first = target;
        while (prev[first] != -1 && prev[first] != start && guard++ < total) first = prev[first];
        int tx = first % gw, ty = first / gw;
        out.targetX = tx * step + step / 2;
        out.targetY = ty * step + step / 2;
        out.pathLength = dist[target] < 0 ? 0 : dist[target];

        int ddx = Integer.compare(tx, pgx);
        int ddy = Integer.compare(ty, pgy);
        if (ddx == 0 && ddy == 0) {
            // If the route collapsed, take the best reachable neighboring cell.
            int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
            int bx = pgx, by = pgy, bd = -1;
            for (int[] d : dirs) {
                int xx = pgx + d[0], yy = pgy + d[1];
                if (xx >= 0 && xx < gw && yy >= 0 && yy < gh) {
                    int id = yy * gw + xx;
                    if (free[id] && dist[id] > bd) { bd = dist[id]; bx = xx; by = yy; }
                }
            }
            ddx = Integer.compare(bx, pgx);
            ddy = Integer.compare(by, pgy);
        }
        out.dx = ddx;
        out.dy = ddy;
        return out;
    }

    private static void enqueue(int x, int y, int cur, int gw, boolean[] free,
                                int[] prev, int[] dist, ArrayDeque<Integer> q) {
        int id = y * gw + x;
        if (!free[id] || dist[id] >= 0) return;
        dist[id] = dist[cur] + 1;
        prev[id] = cur;
        q.addLast(id);
    }

    private static boolean isWalkable(int c) {
        int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
        // Player marker itself is walkable.
        if (g >= 120 && g > r * 1.28f && g > b * 1.12f && g - r >= 30) return true;
        // Dark neutral/gray blocks are treated as walls. Colored/bright map
        // pixels remain traversable, which is more useful than counting walls.
        boolean neutral = Math.abs(r - g) <= 22 && Math.abs(g - b) <= 22;
        if (neutral && r < 75) return false;
        if (neutral && r >= 75 && r <= 175) return false;
        return r + g + b > 85;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
