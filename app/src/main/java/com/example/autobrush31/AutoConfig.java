package com.example.autobrush31;

public final class AutoConfig {
    private AutoConfig() {}
    public static final String GAME_PACKAGE = "com.hortor.mwdl.gf";
    public static final int REF_W = 691, REF_H = 1536;
    public static final int SECRET_X = 620, SECRET_Y = 1090;
    // 31 与 33 垂直相邻，原 895 会落到 33；上移到 830，并由点击前后状态检查保护。
    public static final int HELL31_X = 335, HELL31_Y = 830;
    public static final int ENTER_X = 345, ENTER_Y = 1270;
    public static final int JOY_X = 355, JOY_Y = 1125;
    public static final int MAP_L = 25, MAP_T = 105, MAP_R = 285, MAP_B = 270;
    public static final int PLAYER_G_MIN = 100, PLAYER_RG_DIFF = 35, PLAYER_BG_DIFF = 20;
    public static final int MAP_GRAY_MIN = 45, MAP_GRAY_MAX = 205, MAP_COLOR_DIFF_MAX = 35;
    public static final long ENTER_WAIT = 3000, TAP_WAIT = 900, MOVE_HOLD = 700, LOOP_WAIT = 220;
    public static final int JOY_RADIUS = 145;
    public static final int LOST_MAP_LIMIT = 8;
    public static final boolean BOSS_BAR_ENABLED = false;
    public static final int SUCCESS_L = 170, SUCCESS_T = 350, SUCCESS_R = 560, SUCCESS_B = 620;
}
