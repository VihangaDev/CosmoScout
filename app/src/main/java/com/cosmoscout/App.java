package com.cosmoscout;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.cosmoscout.core.ThemeToggle;
import com.google.firebase.FirebaseApp;

public final class App extends Application {
    private static App instance;

    public static App get() { return instance; }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        try { FirebaseApp.initializeApp(this); } catch (Throwable ignore) {}

        ThemeToggle.init(this);
        boolean dark = ThemeToggle.isDark(this);
        AppCompatDelegate.setDefaultNightMode(
                dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );

    }

    public static void applyNightMode(boolean dark) {
        AppCompatDelegate.setDefaultNightMode(
                dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
    }
}
