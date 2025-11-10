package com.cosmoscout.places;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public interface PlacesRepository {

    void list(@NonNull ListCallback callback);

    void watch(@NonNull LiveCallback callback);

    void add(@NonNull String name,
             double lat,
             double lon,
             @Nullable Integer bortle,
             @Nullable String notes,
             @NonNull Callback callback);

    void remove(@NonNull String placeId, @NonNull Callback callback);

    interface Callback {
        void onComplete(@Nullable Throwable err);
    }

    interface ListCallback {
        void onResult(@NonNull List<Place> items, @Nullable Throwable err);
    }

    interface LiveCallback {
        void onChanged(@NonNull List<Place> items, @Nullable Throwable err);
    }
}
