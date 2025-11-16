package com.cosmoscout.data.places;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cosmoscout.R;
import com.cosmoscout.core.DeviceId;
import com.cosmoscout.core.Net;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

final class FirestoreRestClient {

    private static final String BASE_URL = "https://firestore.googleapis.com/v1/projects/";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    interface RestCallback<T> {
        void onSuccess(@Nullable T data);
        void onError(@NonNull Throwable error);
    }

    private final String deviceId;
    private final String apiKey;
    private final String projectId;

    FirestoreRestClient(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        this.deviceId = DeviceId.get(appContext);
        this.apiKey = appContext.getString(R.string.firebase_api_key);
        this.projectId = appContext.getString(R.string.firebase_project_id);
    }

    static final class RestPlace {
        final Place place;
        @Nullable final PlacesRepository.ComputedFields computedFields;

        RestPlace(@NonNull Place place, @Nullable PlacesRepository.ComputedFields computedFields) {
            this.place = place;
            this.computedFields = computedFields;
        }
    }

    void list(@NonNull RestCallback<List<RestPlace>> callback) {
        HttpUrl url = baseDocumentUrl("users", deviceId, "places")
                .addQueryParameter("orderBy", "fields.createdAt desc")
                .build();
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        Net.client().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.code() == 404) {
                    callback.onSuccess(Collections.emptyList());
                    return;
                }
                if (!response.isSuccessful()) {
                    callback.onError(new IOException("HTTP " + response.code()));
                    return;
                }
                String body = response.body() != null ? response.body().string() : "";
                try {
                    callback.onSuccess(parsePlaces(body));
                } catch (JSONException e) {
                    callback.onError(e);
                }
            }
        });
    }

    void add(@NonNull String placeId,
             @NonNull String name,
             double lat,
             double lon,
             @Nullable Integer bortle,
             @Nullable String notes,
             long createdAt,
             @NonNull RestCallback<Void> callback) {
        HttpUrl url = baseDocumentUrl("users", deviceId, "places")
                .addQueryParameter("documentId", placeId)
                .build();
        JSONObject payload = new JSONObject();
        try {
            payload.put("fields", buildPlaceFields(placeId, name, lat, lon, bortle, notes, createdAt));
        } catch (JSONException e) {
            callback.onError(e);
            return;
        }
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(JSON, payload.toString()))
                .build();
        executeVoid(request, callback);
    }

    void remove(@NonNull String placeId, @NonNull RestCallback<Void> callback) {
        HttpUrl url = baseDocumentUrl("users", deviceId, "places", placeId)
                .build();
        Request request = new Request.Builder()
                .url(url)
                .delete()
                .build();
        executeVoid(request, callback);
    }

    void updateComputedFields(@NonNull String placeId,
                              @NonNull PlacesRepository.ComputedFields fields,
                              @NonNull RestCallback<Void> callback) {
        HttpUrl.Builder urlBuilder = baseDocumentUrl("users", deviceId, "places", placeId);
        urlBuilder.addQueryParameter("updateMask.fieldPaths", "lastSkyScore");
        urlBuilder.addQueryParameter("updateMask.fieldPaths", "lastWindowStart");
        urlBuilder.addQueryParameter("updateMask.fieldPaths", "lastWindowEnd");
        urlBuilder.addQueryParameter("updateMask.fieldPaths", "lastClearPct");
        urlBuilder.addQueryParameter("updateMask.fieldPaths", "lastMoonPct");
        urlBuilder.addQueryParameter("updateMask.fieldPaths", "lastUpdated");
        JSONObject fieldsJson = new JSONObject();
        try {
            fieldsJson.put("lastSkyScore", integerValue(fields.score));
            fieldsJson.put("lastWindowStart", integerValue(fields.windowStart));
            fieldsJson.put("lastWindowEnd", integerValue(fields.windowEnd));
            fieldsJson.put("lastClearPct", integerValue(fields.clearPct));
            fieldsJson.put("lastMoonPct", integerValue(fields.moonPct));
            fieldsJson.put("lastUpdated", integerValue(fields.updatedAt));
        } catch (JSONException e) {
            callback.onError(e);
            return;
        }
        JSONObject payload = new JSONObject();
        try {
            payload.put("fields", fieldsJson);
        } catch (JSONException e) {
            callback.onError(e);
            return;
        }
        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .patch(RequestBody.create(JSON, payload.toString()))
                .build();
        executeVoid(request, callback);
    }

    void readComputedFields(@NonNull String placeId, @NonNull RestCallback<PlacesRepository.ComputedFields> callback) {
        HttpUrl url = baseDocumentUrl("users", deviceId, "places", placeId).build();
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        Net.client().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.code() == 404) {
                    callback.onSuccess(null);
                    return;
                }
                if (!response.isSuccessful()) {
                    callback.onError(new IOException("HTTP " + response.code()));
                    return;
                }
                String body = response.body() != null ? response.body().string() : "";
                try {
                    callback.onSuccess(parseComputedFields(body));
                } catch (JSONException e) {
                    callback.onError(e);
                }
            }
        });
    }

    private void executeVoid(@NonNull Request request, @NonNull RestCallback<Void> callback) {
        Net.client().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError(new IOException("HTTP " + response.code()));
                } else {
                    callback.onSuccess(null);
                }
            }
        });
    }

    private HttpUrl.Builder baseDocumentUrl(String... segments) {
        HttpUrl.Builder builder = HttpUrl.parse(BASE_URL + projectId + "/databases/(default)/documents").newBuilder();
        for (String segment : segments) {
            builder.addPathSegment(segment);
        }
        builder.addQueryParameter("key", apiKey);
        return builder;
    }

    private JSONObject buildPlaceFields(@NonNull String placeId,
                                        @NonNull String name,
                                        double lat,
                                        double lon,
                                        @Nullable Integer bortle,
                                        @Nullable String notes,
                                        long createdAt) throws JSONException {
        JSONObject fields = new JSONObject();
        fields.put("id", stringValue(placeId));
        fields.put("name", stringValue(name));
        fields.put("lat", doubleValue(lat));
        fields.put("lon", doubleValue(lon));
        fields.put("createdAt", integerValue(createdAt));
        fields.put("deviceId", stringValue(deviceId));
        if (bortle != null) {
            fields.put("bortle", integerValue(bortle));
        }
        if (notes != null && !notes.trim().isEmpty()) {
            fields.put("notes", stringValue(notes));
        }
        return fields;
    }

    @NonNull
    private List<RestPlace> parsePlaces(@NonNull String body) throws JSONException {
        JSONObject root = new JSONObject(body);
        JSONArray docs = root.optJSONArray("documents");
        if (docs == null) {
            return Collections.emptyList();
        }
        List<RestPlace> places = new ArrayList<>();
        for (int i = 0; i < docs.length(); i++) {
            JSONObject doc = docs.optJSONObject(i);
            RestPlace place = parsePlace(doc);
            if (place != null) {
                places.add(place);
            }
        }
        return Collections.unmodifiableList(places);
    }

    @Nullable
    private RestPlace parsePlace(@Nullable JSONObject doc) throws JSONException {
        if (doc == null) {
            return null;
        }
        JSONObject fields = doc.optJSONObject("fields");
        if (fields == null) {
            return null;
        }
        String documentName = doc.optString("name", "");
        String[] parts = documentName.split("/");
        String placeId = parts.length > 0 ? parts[parts.length - 1] : "";
        String name = extractString(fields, "name");
        double lat = extractDouble(fields, "lat");
        double lon = extractDouble(fields, "lon");
        Integer bortle = extractInteger(fields, "bortle");
        String notes = normalizeOptionalString(extractString(fields, "notes"));
        long createdAt = extractLong(fields, "createdAt");
        Place place = new Place(placeId, name, lat, lon, bortle, notes, createdAt, extractString(fields, "deviceId"));
        PlacesRepository.ComputedFields computed = extractComputedFields(fields);
        return new RestPlace(place, computed);
    }

    @Nullable
    private PlacesRepository.ComputedFields parseComputedFields(@NonNull String body) throws JSONException {
        JSONObject doc = new JSONObject(body);
        JSONObject fields = doc.optJSONObject("fields");
        if (fields == null) {
            return null;
        }
        return extractComputedFields(fields);
    }

    private JSONObject stringValue(@NonNull String value) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("stringValue", value);
        return obj;
    }

    private JSONObject doubleValue(double value) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("doubleValue", value);
        return obj;
    }

    private JSONObject integerValue(long value) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("integerValue", String.valueOf(value));
        return obj;
    }

    @Nullable
    private Integer extractInteger(@NonNull JSONObject fields, @NonNull String key) throws JSONException {
        JSONObject obj = fields.optJSONObject(key);
        if (obj == null) {
            return null;
        }
        if (obj.has("integerValue")) {
            return (int) Long.parseLong(obj.getString("integerValue"));
        }
        if (obj.has("doubleValue")) {
            return (int) Math.round(obj.getDouble("doubleValue"));
        }
        return null;
    }

    private long extractLong(@NonNull JSONObject fields, @NonNull String key) throws JSONException {
        JSONObject obj = fields.optJSONObject(key);
        if (obj == null) {
            return 0L;
        }
        if (obj.has("integerValue")) {
            return Long.parseLong(obj.getString("integerValue"));
        }
        if (obj.has("doubleValue")) {
            return (long) obj.getDouble("doubleValue");
        }
        return 0L;
    }

    private double extractDouble(@NonNull JSONObject fields, @NonNull String key) throws JSONException {
        JSONObject obj = fields.optJSONObject(key);
        if (obj == null) {
            return 0d;
        }
        if (obj.has("doubleValue")) {
            return obj.optDouble("doubleValue", 0d);
        }
        if (obj.has("integerValue")) {
            return Double.parseDouble(obj.optString("integerValue", "0"));
        }
        return 0d;
    }

    @NonNull
    private String extractString(@NonNull JSONObject fields, @NonNull String key) {
        JSONObject obj = fields.optJSONObject(key);
        if (obj == null) {
            return "";
        }
        if (obj.has("stringValue")) {
            return obj.optString("stringValue", "");
        }
        if (obj.has("integerValue")) {
            return obj.optString("integerValue", "");
        }
        if (obj.has("doubleValue")) {
            return obj.optString("doubleValue", "");
        }
        return "";
    }

    @Nullable
    private String normalizeOptionalString(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Nullable
    private PlacesRepository.ComputedFields extractComputedFields(@NonNull JSONObject fields) throws JSONException {
        Integer score = extractInteger(fields, "lastSkyScore");
        Integer clear = extractInteger(fields, "lastClearPct");
        Integer moon = extractInteger(fields, "lastMoonPct");
        long start = extractLong(fields, "lastWindowStart");
        long end = extractLong(fields, "lastWindowEnd");
        long updated = extractLong(fields, "lastUpdated");
        if (score == null || clear == null || moon == null || updated == 0L) {
            return null;
        }
        return new PlacesRepository.ComputedFields(
                score,
                start,
                end,
                clear,
                moon,
                updated
        );
    }
}
