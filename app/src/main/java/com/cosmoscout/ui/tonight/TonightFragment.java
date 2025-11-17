package com.cosmoscout.ui.tonight;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cosmoscout.R;
import com.cosmoscout.data.weather.TonightSkyService;
import com.cosmoscout.databinding.FragmentTonightBinding;
import com.cosmoscout.ui.RefreshableFragment;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Simple placeholder fragment for the Tonight tab.
 */
public class TonightFragment extends RefreshableFragment {

    private static final double DEFAULT_LAT =37.773972d;
    private static final double DEFAULT_LON = -122.431297d;
    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Colombo");
    private final TonightSkyService skyService = new TonightSkyService();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private FragmentTonightBinding binding;
    private TonightObjectsAdapter objectsAdapter;

    public TonightFragment(){super(R.layout.fragment_tonight);}


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentTonightBinding.bind(view);
        binding.setLifecycleOwner(getViewLifecycleOwner());

        objectsAdapter = new TonightObjectsAdapter();
        binding.tonightObjects.setLayoutManager(new LinearLayoutManager(view.getContext()));
        binding.tonightObjects.setAdapter(objectsAdapter);
        binding.tonightObjects.setItemAnimator(null);
        objectsAdapter.submitList(defaultObjects());

        binding.setIsLoading(true);
        binding.setStatusText(null);
        binding.setBestHourText(getString(R.string.tonight_best_hour_placeholder));
        binding.setErrorText(null);

        loadTonightSnapshot(null);
    }
    @Override
    protected void performRefresh(@NonNull Runnable onComplete){loadTonightSnapshot(onComplete);}

    private void loadTonightSnapshot(@Nullable Runnable onComplete) {
        if (!isAdded()) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        if (binding == null) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        binding.setIsLoading(true);
        binding.setStatusText(null);
        binding.setErrorText(null);
        binding.setBestHourText(getString(R.string.tonight_best_hour_placeholder));

        Location location = resolveLocation();
        final double lat = location != null ? location.getLatitude() : DEFAULT_LAT;
        final double lon = location != null ? location.getLongitude() : DEFAULT_LON;

        executor.execute(() -> {
            try {
                TonightSkyService.Result result = skyService.fetchTonight(lat, lon);
                TonightSkyService.Window best = result.windows.isEmpty() ? null : result.windows.get(0);
                mainHandler.post(() -> applyForecast(best, result.timezone, onComplete));
            } catch (Exception e) {
                Log.w("TonightFragment", "Failed to load tonight forecast", e);
                postError(R.string.tonight_error_message, onComplete);
            }
        });
    }

    private void applyForecast(@Nullable TonightSkyService.Window best,
                               @NonNull java.util.TimeZone timezone,
                               @Nullable Runnable onComplete) {
        if (!canUpdateUi()) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        if (best == null) {
            binding.setErrorText(getString(R.string.tonight_no_windows));
            binding.setStatusText(null);
            binding.setBestHourText(getString(R.string.tonight_best_hour_placeholder));
            objectsAdapter.submitList(defaultObjects());
        } else {
            binding.setBestHourText(formatRange(best.startMillis, best.endMillis, timezone));
            binding.setStatusText(getString(R.string.tonight_status_format, best.clearPercent, best.moonPercent));
            binding.setErrorText(null);
            objectsAdapter.submitList(buildObjects());
        }
        binding.setIsLoading(false);
        if (onComplete != null) {
            onComplete.run();
        }
    }

    private void postError(@StringRes int messageResId, @Nullable Runnable onComplete) {
        mainHandler.post(() -> {
            if (!canUpdateUi()) {
                if (onComplete != null) {
                    onComplete.run();
                }
                return;
            }
            binding.setIsLoading(false);
            binding.setBestHourText(getString(R.string.tonight_best_hour_placeholder));
            binding.setStatusText(null);
            binding.setErrorText(getString(messageResId));
            objectsAdapter.submitList(defaultObjects());
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    private boolean canUpdateUi() {
        return binding != null && isAdded();
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

    @NonNull
    private List<TonightObjectItem> defaultObjects() {
        return buildObjects();
    }

    @NonNull
    private List<TonightObjectItem> buildObjects() {
        List<TonightObjectItem> list = new ArrayList<>();
        list.add(createObject(
                R.drawable.ic_planet_saturn,
                getString(R.string.object_saturn),
                getString(R.string.object_saturn_detail)));
        list.add(createObject(
                R.drawable.ic_planet_jupiter,
                getString(R.string.object_jupiter),
                getString(R.string.object_jupiter_detail)));
        list.add(createObject(
                R.drawable.ic_nebula,
                getString(R.string.object_orion),
                getString(R.string.object_orion_detail)));
        return list;
    }

    private TonightObjectItem createObject(int iconRes,
                                           @NonNull String name,
                                           @NonNull String detail) {
        String direction = detail;
        String altitude = getString(R.string.tonight_altitude_unknown);
        String[] parts = detail.split("\u2022");
        if (parts.length > 0) {
            direction = parts[0].trim();
        }
        if (parts.length > 1) {
            altitude = parts[1].trim();
        }
        return new TonightObjectItem(iconRes, name, direction, altitude);
    }

    private String formatRange(long start, long end, @NonNull TimeZone timezone) {
        ZoneId zoneId = resolveZoneId(timezone);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault());
        ZonedDateTime startTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(start), zoneId);
        ZonedDateTime endTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(end), zoneId);
        return String.format(Locale.getDefault(), "%s \u2013 %s",
                formatter.format(startTime),
                formatter.format(endTime));
    }

    private ZoneId resolveZoneId(@Nullable TimeZone timezone) {
        if (timezone == null) {
            return DEFAULT_ZONE_ID;
        }
        String id = timezone.getID();
        if ("UTC".equalsIgnoreCase(id) || "Etc/UTC".equalsIgnoreCase(id)) {
            return DEFAULT_ZONE_ID;
        }
        return timezone.toZoneId();
    }

    @Override
    public void onDestroyView() {
        if (binding != null && binding.tonightObjects != null) {
            binding.tonightObjects.setAdapter(null);
        }
        binding = null;
        objectsAdapter = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

}
