package com.cosmoscout.data.places;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class PlacesLocalStore {

    private static final String PREFS = "places_local_cache";
    private static final String KEY_PLACES = "places";

    private final SharedPreferences prefs;

    PlacesLocalStore(@NonNull Context context) {
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    void save(@NonNull List<Place> places) {
        JSONArray array = new JSONArray();
        for (Place place : places) {
            array.put(toJson(place));
        }
        prefs.edit().putString(KEY_PLACES, array.toString()).apply();
    }

    @NonNull
    List<Place> load() {
        String raw = prefs.getString(KEY_PLACES, null);
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            JSONArray array = new JSONArray(raw);
            List<Place> places = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                Place place = fromJson(obj);
                if (place != null) {
                    places.add(place);
                }
            }
            return Collections.unmodifiableList(places);
        } catch (JSONException e) {
            return Collections.emptyList();
        }
    }

    private JSONObject toJson(@NonNull Place place) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", place.getId());
            obj.put("name", place.getName());
            obj.put("lat", place.getLat());
            obj.put("lon", place.getLon());
            obj.put("createdAt", place.getCreatedAt());
            obj.put("deviceId", place.getDeviceId());
            if (place.getBortle() != null) {
                obj.put("bortle", place.getBortle());
            }
            if (place.getNotes() != null) {
                obj.put("notes", place.getNotes());
            }
        } catch (JSONException ignored) {
        }
        return obj;
    }

    @Nullable
    private Place fromJson(@Nullable JSONObject obj) {
        if (obj == null) {
            return null;
        }
        String id = obj.optString("id", "");
        String name = obj.optString("name", "");
        double lat = obj.optDouble("lat", 0d);
        double lon = obj.optDouble("lon", 0d);
        long createdAt = obj.optLong("createdAt", 0L);
        String deviceId = obj.optString("deviceId", null);
        Integer bortle = obj.has("bortle") ? obj.optInt("bortle") : null;
        String notes = obj.optString("notes", null);
        if (notes != null) {
            notes = notes.trim();
            if (notes.isEmpty()) {
                notes = null;
            }
        }
        return new Place(id, name, lat, lon, bortle, notes, createdAt, deviceId);
    }
}
