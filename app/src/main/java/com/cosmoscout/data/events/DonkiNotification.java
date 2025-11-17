package com.cosmoscout.data.events;

import androidx.annotation.Nullable;

/**
 * Light-weight UI model for a NASA DONKI notification entry.
 */
public class DonkiNotification {
//
    private final String id;
    private final String type;
    private final String classification;
    @Nullable
    private final String detailLine;
    private final long timestampMillis;
    private final String source;

    public DonkiNotification(String id,
                             String type,
                             String classification,
                             @Nullable String detailLine,
                             long timestampMillis,
                             String source) {
        this.id = id;
        this.type = type;
        this.classification = classification;
        this.detailLine = detailLine;
        this.timestampMillis = timestampMillis;
        this.source = source;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getClassification() {
        return classification;
    }

    @Nullable
    public String getDetailLine() {
        return detailLine;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }

    public String getSource() {
        return source;
    }

    public boolean matchesType(@Nullable String desiredType) {
        if (desiredType == null || desiredType.isEmpty()) {
            return true;
        }
        return type.equalsIgnoreCase(desiredType);
    }
}
