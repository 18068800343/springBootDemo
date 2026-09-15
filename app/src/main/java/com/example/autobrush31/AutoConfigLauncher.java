package com.example.autobrush31;

import android.content.Context;
import android.content.Intent;

public final class AutoConfigLauncher {
    private AutoConfigLauncher() {}
    public static void launch(Context c) {
        try {
            Intent i = c.getPackageManager().getLaunchIntentForPackage(AutoConfig.GAME_PACKAGE);
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                c.startActivity(i);
            }
        } catch (Throwable ignored) {}
    }
}
