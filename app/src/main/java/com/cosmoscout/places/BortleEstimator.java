package com.cosmoscout.places;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cosmoscout.core.Net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Fetches a rough Bortle estimate by hitting Photon (OpenStreetMap-based) reverse geocoding.
 * We map settlement types to an approximate Bortle class so the user doesn't have to guess.
 */
final class BortleEstimator {

    private static final String ENDPOINT = "https://photon.komoot.io/reverse";

    interface EstimateCallback {
        void onComplete(@Nullable Integer bortle, @Nullable Throwable error);
    }

    Call estimate(double lat, double lon, @NonNull EstimateCallback callback) {
        HttpUrl url = HttpUrl.parse(ENDPOINT)
                .newBuilder()
                .addQueryParameter("lat", format(lat))
                .addQueryParameter("lon", format(lon))
                .addQueryParameter("limit", "5")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "CosmoScout/1.0 (Android)")
                .build();

        Call call = Net.client().newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onComplete(null, e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onComplete(null, new IOException("HTTP " + response.code()));
                    return;
                }
                String body = response.body() != null ? response.body().string() : "";
                callback.onComplete(parseBortle(body), null);
            }
        });
        return call;
    }

    @Nullable
    private Integer parseBortle(@NonNull String body) {
        try {
            JSONObject root = new JSONObject(body);
            JSONArray features = root.optJSONArray("features");
            if (features == null || features.length() == 0) {
                return null;
            }
            Integer best = null;
            for (int i = 0; i < features.length(); i++) {
                JSONObject feature = features.optJSONObject(i);
                if (feature == null) continue;
                JSONObject props = feature.optJSONObject("properties");
                if (props == null) continue;
                Integer value = mapPropertiesToBortle(props);
                if (value != null) {
                    if (best == null || value > best) {
                        best = value;
                    }
                }
            }
            return best;
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private Integer mapPropertiesToBortle(@NonNull JSONObject props) {
        String osmValue = props.optString("osm_value", "");
        String type = props.optString("type", "");
        String placeType = props.optString("osm_key", "");

        if (!props.optString("city", "").isEmpty()) return 8;
        if (!props.optString("town", "").isEmpty()) return 7;
        if (!props.optString("village", "").isEmpty()) return 5;
        if (!props.optString("hamlet", "").isEmpty()) return 4;

        String normalized = normalize(osmValue, type, placeType);
        switch (normalized) {
            case "city":
                return 8;
            case "town":
                return 7;
            case "suburb":
                return 6;
            case "village":
                return 5;
            case "hamlet":
            case "farmland":
            case "meadow":
            case "forest":
                return 4;
            case "peak":
            case "mountain_pass":
            case "wilderness":
                return 3;
            default:
                return 6;
        }
    }

    @NonNull
    private String normalize(String... values) {
        for (String value : values) {
            if (value == null) continue;
            String trimmed = value.trim().toLowerCase(Locale.US);
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return "";
    }

    private String format(double number) {
        return String.format(Locale.US, "%.6f", number);
    }
}
