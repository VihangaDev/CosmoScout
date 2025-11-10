package com.cosmoscout.core;

import android.content.Context;
import android.provider.Settings;

public final class DeviceId {
    private DeviceId() {}

    public static String get(Context ctx) {
        try {
            String id = Settings.Secure.getString(
                    ctx.getContentResolver(),
                    Settings.Secure.ANDROID_ID
            );
            return id != null ? id : "unknown-device";
        } catch (Throwable t) {
            return "unknown-device";
        }
    }
}
