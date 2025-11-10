package com.cosmoscout.core;

import android.app.Activity;
import android.widget.Toast;

public final class Ui {
    private Ui() {}

    public static void runOnUi(Activity act, Runnable r) {
        if (act == null || r == null) return;
        if (act.isFinishing() || act.isDestroyed()) return;
        act.runOnUiThread(r);
    }

    public static void toast(Activity act, CharSequence msg) {
        if (act == null || msg == null) return;
        Toast.makeText(act, msg, Toast.LENGTH_SHORT).show();
    }
}
