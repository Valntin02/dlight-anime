package com.dlight.util;

import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.dlight.R;

public final class ImageLoader {
    private ImageLoader() {
    }

    public static void loadCover(ImageView view, String rawUrl) {
        Glide.with(view)
            .load(ImageUrlResolver.resolve(rawUrl))
            .placeholder(R.drawable.placeholder)
            .error(R.drawable.error_placeholder)
            .centerCrop()
            .into(view);
    }

    public static void loadAvatar(ImageView view, String rawUrl) {
        Glide.with(view)
            .load(ImageUrlResolver.resolve(rawUrl))
            .placeholder(R.drawable.placeholder)
            .error(R.drawable.error_placeholder)
            .circleCrop()
            .into(view);
    }

    public static void loadPlayerThumb(ImageView view, String rawUrl) {
        Glide.with(view)
            .load(ImageUrlResolver.resolve(rawUrl))
            .placeholder(R.drawable.placeholder)
            .error(R.drawable.error_placeholder)
            .centerCrop()
            .into(view);
    }
}
