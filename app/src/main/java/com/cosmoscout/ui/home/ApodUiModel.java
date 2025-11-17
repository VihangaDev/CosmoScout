package com.cosmoscout.ui.home;

import androidx.annotation.Nullable;
public class ApodUiModel {

    private final String title;
    private final String subtitle;
    private final String dateText;
    private final String footer;
    private final String description;
    @Nullable
    private final String imageUrl;
    @Nullable
    private final String detailUrl;
    private final boolean image;
    private final String mediaLabel;

    public ApodUiModel(String title,
                       String subtitle,
                       String dateText,
                       String footer,
                       String description,
                       @Nullable String imageUrl,
                       @Nullable String detailUrl,
                       boolean image,
                       String mediaLabel) {
        this.title = title;
        this.subtitle = subtitle;
        this.dateText = dateText;
        this.footer = footer;
        this.description = description;
        this.imageUrl = imageUrl;
        this.detailUrl = detailUrl;
        this.image = image;
        this.mediaLabel = mediaLabel;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getDateText() {
        return dateText;
    }

    public String getFooter() {
        return footer;
    }

    public String getDescription() {
        return description;
    }

    @Nullable
    public String getImageUrl() {
        return imageUrl;
    }

    @Nullable
    public String getDetailUrl() {
        return detailUrl;
    }

    public boolean isImage() {
        return image;
    }

    public String getMediaLabel() {
        return mediaLabel;
    }
}
