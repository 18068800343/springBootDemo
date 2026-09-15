package com.example.autobrush31;

import android.graphics.Bitmap;

public final class MapNavigator {
    private MapNavigator() {}
    public static final class Result {
        public boolean foundPlayer;
        public int dx;
        public int dy;
        public float confidence;
        public int playerX;
        public int playerY;
    }
    public static Result analyze(Bitmap bitmap) {
        return new Result();
    }
}
