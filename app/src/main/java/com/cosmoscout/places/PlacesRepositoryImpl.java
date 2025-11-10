package com.cosmoscout.places;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cosmoscout.core.DeviceId;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlacesRepositoryImpl implements PlacesRepository {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final CollectionReference collection;
    private final String deviceId;
    @Nullable private ListenerRegistration liveRegistration;

    public PlacesRepositoryImpl(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        deviceId = DeviceId.get(appContext);
        collection = firestore.collection("users")
                .document(deviceId)
                .collection("places");
    }

    @Override
    public void list(@NonNull ListCallback callback) {
        collection.orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        List<Place> places = toPlaces(task.getResult());
                        dispatch(() -> callback.onResult(places, null));
                    } else {
                        dispatch(() -> callback.onResult(Collections.emptyList(), task.getException()));
                    }
                });
    }

    @Override
    public void watch(@NonNull LiveCallback callback) {
        if (liveRegistration != null) {
            liveRegistration.remove();
            liveRegistration = null;
        }
        liveRegistration = collection.orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (snapshots != null) {
                        List<Place> places = toPlaces(snapshots);
                        dispatch(() -> callback.onChanged(places, null));
                    } else {
                        dispatch(() -> callback.onChanged(Collections.emptyList(), error));
                    }
                });
    }

    @Override
    public void add(@NonNull String name,
                    double lat,
                    double lon,
                    @Nullable Integer bortle,
                    @Nullable String notes,
                    @NonNull Callback callback) {
        String placeId = UUID.randomUUID().toString();
        long createdAt = System.currentTimeMillis();

        Map<String, Object> payload = new HashMap<>();
        payload.put("name", name);
        payload.put("lat", lat);
        payload.put("lon", lon);
        payload.put("createdAt", createdAt);
        payload.put("deviceId", deviceId);
        if (bortle != null) {
            payload.put("bortle", bortle);
        }
        if (notes != null && !notes.trim().isEmpty()) {
            payload.put("notes", notes.trim());
        }

        collection.document(placeId)
                .set(payload)
                .addOnCompleteListener(task ->
                        dispatch(() -> callback.onComplete(task.isSuccessful() ? null : task.getException())));
    }

    @Override
    public void remove(@NonNull String placeId, @NonNull Callback callback) {
        collection.document(placeId)
                .delete()
                .addOnCompleteListener(task ->
                        dispatch(() -> callback.onComplete(task.isSuccessful() ? null : task.getException())));
    }

    private void dispatch(@NonNull Runnable runnable) {
        if (Looper.myLooper() == mainHandler.getLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }

    @NonNull
    private List<Place> toPlaces(@NonNull QuerySnapshot snapshots) {
        List<Place> items = new ArrayList<>();
        for (DocumentSnapshot doc : snapshots.getDocuments()) {
            Place place = toPlace(doc);
            if (place != null) {
                items.add(place);
            }
        }
        return Collections.unmodifiableList(items);
    }

    @Nullable
    private Place toPlace(@NonNull DocumentSnapshot doc) {
        if (!doc.exists()) {
            return null;
        }
        String id = doc.getId();
        String name = safeString(doc.getString("name"));
        double lat = safeDouble(doc.get("lat"));
        double lon = safeDouble(doc.get("lon"));
        Integer bortle = safeInteger(doc.get("bortle"));
        String notes = trimToNull(doc.getString("notes"));
        long createdAt = safeLong(doc.get("createdAt"));
        String deviceId = doc.getString("deviceId");
        return new Place(id, name, lat, lon, bortle, notes, createdAt, deviceId);
    }

    @NonNull
    private String safeString(@Nullable String value) {
        return value == null ? "" : value;
    }

    private double safeDouble(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0d;
    }

    @Nullable
    private Integer safeInteger(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    private long safeLong(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        } else if (value instanceof Timestamp) {
            return ((Timestamp) value).toDate().getTime();
        }
        return 0L;
    }

    @Nullable
    private String trimToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
