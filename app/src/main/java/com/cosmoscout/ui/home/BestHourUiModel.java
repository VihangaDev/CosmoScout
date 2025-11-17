package com.cosmoscout.ui.home;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
public class BestHourUiModel {

    private final String statusLabel;
    private final String timeRange;
    private final String conditions;
    private final String locationName;
    private final String footer;
    @DrawableRes
    private final int statusBackgroundRes;
    @ColorRes
    private final int statusTextColorRes;

    public BestHourUiModel(String statusLabel,
                           String timeRange,
                           String conditions,
                           String locationName,
                           String footer,
                           @DrawableRes int statusBackgroundRes,
                           @ColorRes int statusTextColorRes) {
        this.statusLabel = statusLabel;
        this.timeRange = timeRange;
        this.conditions = conditions;
        this.locationName = locationName;
        this.footer = footer;
        this.statusBackgroundRes = statusBackgroundRes;
        this.statusTextColorRes = statusTextColorRes;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public String getTimeRange() {
        return timeRange;
    }

    public String getConditions() {
        return conditions;
    }

    public String getLocationName() {
        return locationName;
    }

    public String getFooter() {
        return footer;
    }

    @DrawableRes
    public int getStatusBackgroundRes() {
        return statusBackgroundRes;
    }

    @ColorRes
    public int getStatusTextColorRes() {
        return statusTextColorRes;
    }
}
