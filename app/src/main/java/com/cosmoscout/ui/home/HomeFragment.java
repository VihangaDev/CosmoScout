package com.cosmoscout.ui.home;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.cosmoscout.R;
import com.cosmoscout.core.ImageLoader;
import com.cosmoscout.core.Net;
import com.cosmoscout.core.Ui;
import com.cosmoscout.data.ApiKeys;
import com.cosmoscout.data.weather.TonightSkyService;
import com.cosmoscout.databinding.FragmentHomeBinding;
import com.cosmoscout.data.places.PlacesScoring;
import com.cosmoscout.ui.RefreshableFragment;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
public class HomeFragment extends RefreshableFragment {

    private static final double DEFAULT_LAT = 37.773972d;
    private static final double DEFAULT_LON = -122.431297d;
    private static final String APOD_ENDPOINT = "https://api.nasa.gov/planetary/apod"; // API ref: https://api.nasa.gov/

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final TonightSkyService skyService = new TonightSkyService();

    private FragmentHomeBinding binding;
    @Nullable
    private String apodDetailUrl;

    public HomeFragment() {
        super(R.layout.fragment_home);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentHomeBinding.bind(view);
        binding.setLifecycleOwner(getViewLifecycleOwner());
        binding.apodViewButton.setOnClickListener(v -> openApodLink());
        binding.apodImage.setOnClickListener(v -> openApodLink());
        resetState();
        refreshHomeContent(null);
    }

    private void resetState() {
        if (binding == null) {
            return;
        }
        apodDetailUrl = null;
        binding.setBestHour(null);
        binding.setBestHourLoading(true);
        binding.setBestHourError(null);
        binding.setApod(null);
        binding.setApodLoading(true);
        binding.setApodError(null);
        binding.apodImage.setImageResource(R.color.image_placeholder);
        binding.apodViewButton.setEnabled(false);
    }

    @Override
    protected void performRefresh(@NonNull Runnable onComplete) {
        refreshHomeContent(onComplete);
    }

    private void refreshHomeContent(@Nullable Runnable onComplete) {
        if (binding == null) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        resetState();
        loadApod();
        loadBestHour(onComplete);
    }

    private void loadApod() {
        executor.execute(() -> {
            try {
                ApodUiModel apod = fetchApod();
                mainHandler.post(() -> applyApod(apod));
            } catch (Exception e) {
                mainHandler.post(this::showApodError);
            }
        });
    }

    private void loadBestHour(@Nullable Runnable onComplete) {
        executor.execute(() -> {
            Location location = resolveLocation();
            double lat = location != null ? location.getLatitude() : DEFAULT_LAT;
            double lon = location != null ? location.getLongitude() : DEFAULT_LON;
            try {
                TonightSkyService.Result result = skyService.fetchTonight(lat, lon);
                TonightSkyService.Window best = result.windows.isEmpty() ? null : result.windows.get(0);
                mainHandler.post(() -> applyBestHour(best, result.timezone, onComplete));
            } catch (Exception e) {
                mainHandler.post(() -> showBestHourError(onComplete));
            }
        });
    }

    private void applyBestHour(@Nullable TonightSkyService.Window window,
                               @NonNull TimeZone timezone,
                               @Nullable Runnable onComplete) {
        if (binding == null) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        if (window == null) {
            binding.setBestHour(null);
            binding.setBestHourLoading(false);
            binding.setBestHourError(getString(R.string.home_best_error));
        } else {
            BestHourUiModel model = buildBestHourModel(window, timezone);
            binding.setBestHour(model);
            binding.setBestHourLoading(false);
            binding.setBestHourError(null);
            binding.bestStatusPill.setBackgroundResource(model.getStatusBackgroundRes());
            binding.bestStatusPill.setTextColor(
                    ContextCompat.getColor(requireContext(), model.getStatusTextColorRes()));
        }
        if (onComplete != null) {
            onComplete.run();
        }
    }

    private void showBestHourError(@Nullable Runnable onComplete) {
        if (binding != null) {
            binding.setBestHour(null);
            binding.setBestHourLoading(false);
            binding.setBestHourError(getString(R.string.home_best_error));
        }
        if (onComplete != null) {
            onComplete.run();
        }
    }

    private BestHourUiModel buildBestHourModel(@NonNull TonightSkyService.Window window,
                                               @NonNull TimeZone timezone) {
        DateFormat formatter = android.text.format.DateFormat.getTimeFormat(requireContext());
        formatter.setTimeZone(timezone);
        String range = formatRange(formatter, window.startMillis, window.endMillis);
        String conditions = getString(R.string.home_best_conditions_format, window.clearPercent, window.moonPercent);
        String location = getString(R.string.home_best_location_current);
        String footer = getString(R.string.home_best_footer_default);
        String label;
        int backgroundRes;
        int textColorRes;
        if (window.status == PlacesScoring.SkyStatus.GOOD) {
            label = getString(R.string.home_status_clear);
            backgroundRes = R.drawable.bg_status_good;
            textColorRes = R.color.colorSecondary;
        } else if (window.status == PlacesScoring.SkyStatus.OK) {
            label = getString(R.string.home_status_ok);
            backgroundRes = R.drawable.bg_status_ok;
            textColorRes = R.color.colorPrimary;
        } else {
            label = getString(R.string.home_status_poor);
            backgroundRes = R.drawable.bg_status_poor;
            textColorRes = R.color.status_poor_text;
        }
        return new BestHourUiModel(
                label,
                range,
                conditions,
                location,
                footer,
                backgroundRes,
                textColorRes
        );
    }

    private void applyApod(@NonNull ApodUiModel model) {
        if (binding == null) {
            return;
        }
        binding.setApod(model);
        binding.setApodLoading(false);
        binding.setApodError(null);
        apodDetailUrl = model.getDetailUrl();
        if (!TextUtils.isEmpty(model.getImageUrl())) {
            ImageLoader.into(binding.apodImage, model.getImageUrl());
        } else {
            binding.apodImage.setImageResource(R.color.image_placeholder);
        }
        binding.apodMediaBadge.setVisibility(model.isImage() ? View.GONE : View.VISIBLE);
        if (!model.isImage()) {
            binding.apodMediaBadge.setText(model.getMediaLabel());
        }
        boolean hasLink = !TextUtils.isEmpty(apodDetailUrl);
        binding.apodViewButton.setEnabled(hasLink);
        binding.apodImage.setClickable(hasLink);
    }

    private void showApodError() {
        if (binding == null) {
            return;
        }
        binding.setApodLoading(false);
        binding.setApodError(getString(R.string.home_apod_error_body));
        binding.apodImage.setImageResource(R.color.image_placeholder);
        binding.apodViewButton.setEnabled(false);
        apodDetailUrl = null;
    }

    private void openApodLink() {
        if (!isAdded() || TextUtils.isEmpty(apodDetailUrl)) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(apodDetailUrl));
            startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    @NonNull
    private ApodUiModel fetchApod() throws IOException, JSONException {
        HttpUrl url = HttpUrl.parse(APOD_ENDPOINT)
                .newBuilder()
                .addQueryParameter("api_key", ApiKeys.NASA)
                .addQueryParameter("thumbs", "true")
                .build();
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "CosmoScout/1.0 (Android)")
                .build();
        try (Response response = Net.client().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            String body = response.body() != null ? response.body().string() : "";
            JSONObject root = new JSONObject(body);
            String title = root.optString("title", getString(R.string.home_apod_error_title));
            String date = root.optString("date", "");
            String formattedDate = formatApodDate(date);
            String description = root.optString("explanation", "");
            String mediaType = root.optString("media_type", "image");
            boolean isImage = "image".equalsIgnoreCase(mediaType);
            String imageUrl = null;
            if (isImage) {
                imageUrl = root.optString("url", null);
            } else {
                imageUrl = root.optString("thumbnail_url", null);
            }
            String detailUrl = root.optString("hdurl", root.optString("url", imageUrl));
            String footer = getString(R.string.home_apod_footer, formattedDate);
            String mediaLabel = isImage ? "" : getString(R.string.home_apod_media_video);
            return new ApodUiModel(
                title,
                getString(R.string.home_apod_card_subtitle),
                formattedDate,
                footer,
                description,
                imageUrl,
                detailUrl,
                isImage,
                mediaLabel
            );
        }
    }

    @NonNull
    private String formatApodDate(@NonNull String iso) {
        if (iso.isEmpty()) {
            return "";
        }
        SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat output = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());
        try {
            Date parsed = input.parse(iso);
            return parsed != null ? output.format(parsed) : iso;
        } catch (ParseException e) {
            return iso;
        }
    }

    private String formatRange(@NonNull DateFormat format, long start, long end) {
        return String.format(Locale.getDefault(), "%s \u2013 %s",
                format.format(new Date(start)),
                format.format(new Date(end)));
    }

    @Nullable
    private Location resolveLocation() {
        Context context = getContext();
        if (context == null) {
            return null;
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) {
            return null;
        }
        Location best = null;
        for (String provider : getProviders(manager)) {
            try {
                Location loc = manager.getLastKnownLocation(provider);
                if (loc != null && (best == null || loc.getTime() > best.getTime())) {
                    best = loc;
                }
            } catch (SecurityException ignored) {
            }
        }
        return best;
    }

    @NonNull
    private List<String> getProviders(@NonNull LocationManager manager) {
        List<String> providers = new ArrayList<>();
        if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            providers.add(LocationManager.NETWORK_PROVIDER);
        }
        if (manager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
            providers.add(LocationManager.PASSIVE_PROVIDER);
        }
        if (providers.isEmpty()) {
            providers.add(LocationManager.NETWORK_PROVIDER);
            providers.add(LocationManager.PASSIVE_PROVIDER);
        }
        return providers;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
