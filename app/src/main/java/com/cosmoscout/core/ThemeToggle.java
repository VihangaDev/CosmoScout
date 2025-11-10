package com.cosmoscout.core;

import android.content.Context;
import android.content.SharedPreferences;

import com.cosmoscout.App;

public final class ThemeToggle {
    private static final String PREFS = "cosmoscout.prefs";
    private static final String KEY_DARK = "dark_mode";
    private static SharedPreferences sp;

    private ThemeToggle() {}

    public static void init(Context ctx) {
        if (sp == null) sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isDark(Context ctx) {
        init(ctx);
        return sp.getBoolean(KEY_DARK, false);
    }

    public static void setDark(Context ctx, boolean dark) {
        init(ctx);
        sp.edit().putBoolean(KEY_DARK, dark).apply();
        App.applyNightMode(dark);
    }
}
