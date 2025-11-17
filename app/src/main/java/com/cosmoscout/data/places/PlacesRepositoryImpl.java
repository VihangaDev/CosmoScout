package com.cosmoscout.data.places;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

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
import java.util.concurrent.ConcurrentHashMap;

public final class PlacesRepositoryImpl implements PlacesRepository {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final CollectionReference collection;
    private final String deviceId;
    private final Map<String, ComputedFields> computedCache = new ConcurrentHashMap<>();
    private final FirestoreRestClient restClient;
    private final PlacesLocalStore localStore;
    @Nullable private ListenerRegistration liveRegistration;

    public PlacesRepositoryImpl(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        deviceId = DeviceId.get(appContext);
        collection = firestore.collection("users")
                .document(deviceId)
                .collection("places");
        restClient = new FirestoreRestClient(appContext);
        localStore = new PlacesLocalStore(appContext);
    }

    @Override
    public void list(@NonNull ListCallback callback) {
        collection.orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        List<Place> places = toPlaces(task.getResult());
                        Log.d("PlacesRepositoryImpl", "Firestore query successful, loaded " + places.size() + " places");
                        localStore.save(places);
                        dispatch(() -> callback.onResult(places, null));
                    } else {
                        Log.w("PlacesRepositoryImpl", "Firestore query failed, attempting REST fallback", task.getException());
                        fetchViaRest(callback, task.getException());
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
        payload.put("id", placeId);
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
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        dispatch(() -> callback.onComplete(null));
                    } else {
                        restClient.add(placeId, name, lat, lon, bortle, notes, createdAt, new FirestoreRestClient.RestCallback<Void>() {
                            @Override
                            public void onSuccess(@Nullable Void data) {
                                dispatch(() -> callback.onComplete(null));
                            }

                            @Override
                            public void onError(@NonNull Throwable error) {
                                dispatch(() -> callback.onComplete(task.getException() != null ? task.getException() : error));
                            }
                        });
                    }
                });
    }

    @Override
    public void remove(@NonNull String placeId, @NonNull Callback callback) {
        collection.document(placeId)
                .delete()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        dispatch(() -> callback.onComplete(null));
                    } else {
                        restClient.remove(placeId, new FirestoreRestClient.RestCallback<Void>() {
                            @Override
                            public void onSuccess(@Nullable Void data) {
                                dispatch(() -> callback.onComplete(null));
                            }

                            @Override
                            public void onError(@NonNull Throwable error) {
                                dispatch(() -> callback.onComplete(task.getException() != null ? task.getException() : error));
                            }
                        });
                    }
                });
    }

    @Override
    public void updateComputedFields(@NonNull String placeId,
                                     @NonNull ComputedFields fields,
                                     @NonNull Callback callback) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("lastSkyScore", fields.score);
        payload.put("lastWindowStart", fields.windowStart);
        payload.put("lastWindowEnd", fields.windowEnd);
        payload.put("lastClearPct", fields.clearPct);
        payload.put("lastMoonPct", fields.moonPct);
        payload.put("lastUpdated", fields.updatedAt);

        collection.document(placeId)
                .update(payload)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        computedCache.put(placeId, fields);
                        dispatch(() -> callback.onComplete(null));
                    } else {
                        restClient.updateComputedFields(placeId, fields, new FirestoreRestClient.RestCallback<Void>() {
                            @Override
                            public void onSuccess(@Nullable Void data) {
                                computedCache.put(placeId, fields);
                                dispatch(() -> callback.onComplete(null));
                            }

                            @Override
                            public void onError(@NonNull Throwable error) {
                                dispatch(() -> callback.onComplete(task.getException() != null ? task.getException() : error));
                            }
                        });
                    }
                });
    }

    @Override
    public void readComputedFields(@NonNull String placeId,
                                   @NonNull ComputedFieldsCallback callback) {
        collection.document(placeId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        DocumentSnapshot doc = task.getResult();
                        ComputedFields fields = doc.exists() ? parseComputedFields(doc) : null;
                        if (fields != null && fields.isValid()) {
                            computedCache.put(placeId, fields);
                        }
                        dispatch(() -> callback.onResult(fields, null));
                    } else {
                        restClient.readComputedFields(placeId, new FirestoreRestClient.RestCallback<ComputedFields>() {
                            @Override
                            public void onSuccess(@Nullable ComputedFields data) {
                                if (data != null && data.isValid()) {
                                    computedCache.put(placeId, data);
                                }
                                dispatch(() -> callback.onResult(data, null));
                            }

                            @Override
                            public void onError(@NonNull Throwable error) {
                                dispatch(() -> callback.onResult(null, task.getException() != null ? task.getException() : error));
                            }
                        });
                    }
                });
    }

    @Nullable
    @Override
    public ComputedFields getCachedComputedFields(@NonNull String placeId) {
        return computedCache.get(placeId);
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
        cacheComputedFields(doc);
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

    private void fetchViaRest(@NonNull ListCallback callback, @Nullable Throwable originalError) {
        Log.d("PlacesRepositoryImpl", "Attempting REST fallback...");
        restClient.list(new FirestoreRestClient.RestCallback<List<FirestoreRestClient.RestPlace>>() {
            @Override
            public void onSuccess(@Nullable List<FirestoreRestClient.RestPlace> data) {
                List<Place> result;
                if (data == null || data.isEmpty()) {
                    result = Collections.emptyList();
                } else {
                    List<Place> list = new ArrayList<>(data.size());
                    for (FirestoreRestClient.RestPlace restPlace : data) {
                        list.add(restPlace.place);
                        if (restPlace.computedFields != null && restPlace.computedFields.isValid()) {
                            computedCache.put(restPlace.place.getId(), restPlace.computedFields);
                        }
                    }
                    result = Collections.unmodifiableList(list);
                }
                if (!result.isEmpty()) {
                    localStore.save(result);
                }
                Log.d("PlacesRepositoryImpl", "REST call successful, loaded " + result.size() + " places");
                dispatch(() -> callback.onResult(result, null));
            }

            @Override
            public void onError(@NonNull Throwable error) {
                Log.e("PlacesRepositoryImpl", "REST call failed", error);
                List<Place> cached = localStore.load();
                if (!cached.isEmpty()) {
                    Log.d("PlacesRepositoryImpl", "Using cached data, loaded " + cached.size() + " places");
                    dispatch(() -> callback.onResult(cached, null));
                } else {
                    Throwable toReport = originalError != null ? originalError : error;
                    Log.e("PlacesRepositoryImpl", "No cached data available, reporting error", toReport);
                    dispatch(() -> callback.onResult(Collections.emptyList(), toReport));
                }
            }
        });
    }

    private void cacheComputedFields(@NonNull DocumentSnapshot doc) {
        ComputedFields fields = parseComputedFields(doc);
        if (fields != null && fields.isValid()) {
            computedCache.put(doc.getId(), fields);
        } else {
            computedCache.remove(doc.getId());
        }
    }

    @Nullable
    private ComputedFields parseComputedFields(@NonNull DocumentSnapshot doc) {
        long updatedAt = safeLong(doc.get("lastUpdated"));
        if (updatedAt <= 0L) {
            return null;
        }
        Integer score = safeInteger(doc.get("lastSkyScore"));
        long windowStart = safeLong(doc.get("lastWindowStart"));
        long windowEnd = safeLong(doc.get("lastWindowEnd"));
        Integer clear = safeInteger(doc.get("lastClearPct"));
        Integer moon = safeInteger(doc.get("lastMoonPct"));
        return new ComputedFields(
                score != null ? score : 0,
                windowStart,
                windowEnd,
                clear != null ? clear : 0,
                moon != null ? moon : 0,
                updatedAt
        );
    }
}
