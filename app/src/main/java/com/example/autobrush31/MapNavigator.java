package com.example.autobrush31;

import android.graphics.Bitmap;
import android.graphics.Color;

/** Lightweight minimap analyzer; no OpenCV/ML dependency. */
public final class MapNavigator {
    private MapNavigator() {}
    public static final class Result {
        public boolean foundPlayer;
        public int dx, dy;
        public float confidence;
        public int playerX, playerY;
    }

    public static Result analyze(Bitmap src) {
        Result r = new Result();
        if (src == null || src.isRecycled()) return r;
        int w = src.getWidth(), h = src.getHeight();
        // Look for the common bright-green player marker in the supplied minimap.
        long sx=0, sy=0, n=0;
        for (int y=0; y<h; y++) {
            for (int x=0; x<w; x++) {
                int c=src.getPixel(x,y);
                int red=Color.red(c), green=Color.green(c), blue=Color.blue(c);
                if (green > 145 && green > red*1.35f && green > blue*1.20f && (green-red)>45) {
                    sx+=x; sy+=y; n++;
                }
            }
        }
        if (n < 3) return r;
        r.foundPlayer=true; r.playerX=(int)(sx/n); r.playerY=(int)(sy/n);
        r.confidence=Math.min(1f,n/25f);

        // Pick a direction toward the least-occupied side of the minimap.
        int left=0,right=0,top=0,bottom=0;
        int radius=Math.max(10, Math.min(w,h)/8);
        for(int y=0;y<h;y+=2) for(int x=0;x<w;x+=2){
            int c=src.getPixel(x,y); int rr=Color.red(c), gg=Color.green(c), bb=Color.blue(c);
            boolean obstacle=(Math.abs(rr-gg)<18 && Math.abs(gg-bb)<18 && rr<170) || (rr<70&&gg<70&&bb<70);
            if(!obstacle) continue;
            if(x<r.playerX-radius) left++; else if(x>r.playerX+radius) right++;
            if(y<r.playerY-radius) top++; else if(y>r.playerY+radius) bottom++;
        }
        int max=0; r.dx=1; r.dy=0;
        if(left>max){max=left;r.dx=-1;r.dy=0;}
        if(right>max){max=right;r.dx=1;r.dy=0;}
        if(top>max){max=top;r.dx=0;r.dy=-1;}
        if(bottom>max){max=bottom;r.dx=0;r.dy=1;}
        // If the chosen side looks blocked, prefer a diagonal/open direction.
        if(max==0){ r.dx=1; r.dy=0; }
        return r;
    }
}
