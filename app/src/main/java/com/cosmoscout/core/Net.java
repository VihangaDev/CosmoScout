package com.cosmoscout.core;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

public final class Net {
    private static volatile OkHttpClient client;

    private Net() {}

    public static OkHttpClient client() {
        if (client == null) {
            synchronized (Net.class) {
                if (client == null) {
                    client = new OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(20, TimeUnit.SECONDS)
                            .writeTimeout(20, TimeUnit.SECONDS)
                            .retryOnConnectionFailure(true)
                            .build();
                }
            }
        }
        return client;
    }
}
