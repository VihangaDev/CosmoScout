package com.cosmoscout.core;

import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.cosmoscout.R;

public final class ImageLoader {
    private ImageLoader() {}

    public static void into(ImageView iv, String url) {
        if (iv == null) return;
        Glide.with(iv.getContext())
                .load(url)
                .placeholder(R.color.image_placeholder)
                .error(R.color.image_placeholder)
                .centerCrop()
                .into(iv);
    }
}
