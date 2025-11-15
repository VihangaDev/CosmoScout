package com.cosmoscout.places;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Simple model for a saved stargazing spot.
 */
public final class Place {

    @NonNull private final String id;
    @NonNull private final String name;
    private final double lat;
    private final double lon;
    @Nullable private final Integer bortle;
    @Nullable private final String notes;
    private final long createdAt;
    @Nullable private final String deviceId;

    public Place(@NonNull String id,
                 @NonNull String name,
                 double lat,
                 double lon,
                 @Nullable Integer bortle,
                 @Nullable String notes,
                 long createdAt,
                 @Nullable String deviceId) {
        this.id = id;
        this.name = name;
        this.lat = lat;
        this.lon = lon;
        this.bortle = bortle;
        this.notes = notes;
        this.createdAt = createdAt;
        this.deviceId = deviceId;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    @Nullable
    public Integer getBortle() {
        return bortle;
    }

    @Nullable
    public String getNotes() {
        return notes;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    @Nullable
    public String getDeviceId() {
        return deviceId;
    }

    public long getStableId() {
        return id.hashCode() & 0xffffffffL;
    }

    public boolean hasNotes() {
        return notes != null && !notes.trim().isEmpty();
    }
}
