package com.cosmoscout.ui.binding;

import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.databinding.BindingAdapter;

public final class ImageViewBindingAdapters {

    private ImageViewBindingAdapters() {
    }

    @BindingAdapter("imageSrc")
    public static void setImageSrc(ImageView imageView, @DrawableRes int resId) {
        if (resId == 0) {
            imageView.setImageDrawable(null);
            return;
        }
        Drawable drawable = AppCompatResources.getDrawable(imageView.getContext(), resId);
        imageView.setImageDrawable(drawable);
    }
}
