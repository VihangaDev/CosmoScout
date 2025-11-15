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

    void updateComputedFields(@NonNull String placeId,
                              @NonNull ComputedFields fields,
                              @NonNull Callback callback);

    void readComputedFields(@NonNull String placeId,
                            @NonNull ComputedFieldsCallback callback);

    @Nullable
    default ComputedFields getCachedComputedFields(@NonNull String placeId) {
        return null;
    }

    interface Callback {
        void onComplete(@Nullable Throwable err);
    }

    interface ListCallback {
        void onResult(@NonNull List<Place> items, @Nullable Throwable err);
    }

    interface LiveCallback {
        void onChanged(@NonNull List<Place> items, @Nullable Throwable err);
    }

    interface ComputedFieldsCallback {
        void onResult(@Nullable ComputedFields fields, @Nullable Throwable err);
    }

    final class ComputedFields {
        public final int score;
        public final long windowStart;
        public final long windowEnd;
        public final int clearPct;
        public final int moonPct;
        public final long updatedAt;

        public ComputedFields(int score,
                              long windowStart,
                              long windowEnd,
                              int clearPct,
                              int moonPct,
                              long updatedAt) {
            this.score = score;
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
            this.clearPct = clearPct;
            this.moonPct = moonPct;
            this.updatedAt = updatedAt;
        }

        public boolean isValid() {
            return updatedAt > 0L;
        }
    }
}
