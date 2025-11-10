package com.cosmoscout.core;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public final class Perms {
    private Perms() {}

    public static boolean hasCoarseLocation(Context ctx) {
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestCoarseLocation(Activity act, int reqCode) {
        ActivityCompat.requestPermissions(
                act,
                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                reqCode
        );
    }
}
