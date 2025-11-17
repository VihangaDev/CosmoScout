package com.cosmoscout.ui.events;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cosmoscout.R;
import com.cosmoscout.core.Net;
import com.cosmoscout.data.ApiKeys;
import com.cosmoscout.data.events.DonkiNotification;
import com.cosmoscout.databinding.FragmentEventsBinding;
import com.cosmoscout.ui.RefreshableFragment;
import com.google.android.material.chip.ChipGroup;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
public class EventsFragment extends RefreshableFragment {

    private static final String DONKI_ENDPOINT = "https://api.nasa.gov/DONKI/notifications"; // API ref: https://api.nasa.gov/
    private static final Pattern FLARE_PATTERN = Pattern.compile("(X|M|C)\\s*\\d+(?:\\.\\d+)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern KP_PATTERN = Pattern.compile("KP\\s*(?:INDEX)?\\s*=?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private final List<DonkiNotification> allEvents = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private FragmentEventsBinding binding;
    private EventsAdapter adapter;
    @Nullable
    private String activeFilter;

    public EventsFragment() {
        super(R.layout.fragment_events);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentEventsBinding.bind(view);
        adapter = new EventsAdapter();
        binding.eventsList.setLayoutManager(new LinearLayoutManager(view.getContext()));
        binding.eventsList.setAdapter(adapter);
        binding.eventsList.setItemAnimator(null);

        binding.setEventsLoading(true);
        binding.setHasEvents(false);
        binding.errorState.setVisibility(View.GONE);

        setupFilterGroup();
        loadEvents(null);
    }

    @Override
    protected void performRefresh(@NonNull Runnable onComplete) {
        loadEvents(onComplete);
    }

    private void setupFilterGroup() {
        ChipGroup group = binding.filterGroup;
        group.setOnCheckedStateChangeListener((g, ids) -> {
            int checkedId = ids.isEmpty() ? View.NO_ID : ids.get(0);
            String nextFilter;
            if (checkedId == binding.filterCme.getId()) {
                nextFilter = "CME";
            } else if (checkedId == binding.filterSep.getId()) {
                nextFilter = "SEP";
            } else if (checkedId == binding.filterGeo.getId()) {
                nextFilter = "GEOMAGNETIC";
            } else {
                nextFilter = null;
            }
            applyFilter(nextFilter);
        });
    }

    private void loadEvents(@Nullable Runnable onComplete) {
        if (binding == null) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        binding.setEventsLoading(true);
        binding.errorState.setVisibility(View.GONE);
        adapter.submitList(Collections.emptyList());

        executor.execute(() -> {
            try {
                List<DonkiNotification> events = fetchEvents();
                mainHandler.post(() -> {
                    if (!isAdded() || binding == null) {
                        if (onComplete != null) {
                            onComplete.run();
                        }
                        return;
                    }
                    allEvents.clear();
                    allEvents.addAll(events);
                    applyFilter(activeFilter);
                    binding.setEventsLoading(false);
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (binding == null) {
                        if (onComplete != null) {
                            onComplete.run();
                        }
                        return;
                    }
                    binding.setEventsLoading(false);
                    binding.errorState.setVisibility(View.VISIBLE);
                    binding.setHasEvents(false);
                    adapter.submitList(Collections.emptyList());
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
            }
        });
    }

    private void applyFilter(@Nullable String filter) {
        activeFilter = filter;
        if (binding == null) {
            return;
        }
        List<DonkiNotification> filtered = new ArrayList<>();
        for (DonkiNotification event : allEvents) {
            if (event.matchesType(filter)) {
                filtered.add(event);
            }
        }
        adapter.submitList(filtered);
        binding.setHasEvents(!filtered.isEmpty());
        binding.errorState.setVisibility(View.GONE);
    }

    private List<DonkiNotification> fetchEvents() throws IOException, JSONException {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String endDate = dateFormat.format(calendar.getTime());
        calendar.add(Calendar.DAY_OF_YEAR, -7);
        String startDate = dateFormat.format(calendar.getTime());

        HttpUrl url = HttpUrl.parse(DONKI_ENDPOINT)
                .newBuilder()
                .addQueryParameter("startDate", startDate)
                .addQueryParameter("endDate", endDate)
                .addQueryParameter("type", "all")
                .addQueryParameter("api_key", ApiKeys.NASA)
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
            JSONArray array = new JSONArray(body);
            List<DonkiNotification> events = new ArrayList<>();
            SimpleDateFormat issueFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US);
            issueFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) continue;
                String id = obj.optString("messageID", "event-" + i);
                String type = obj.optString("messageType", "OTHER").toUpperCase(Locale.US);
                String title = obj.optString("messageTitle", type);
                String bodyText = obj.optString("messageBody", "");
                long timestamp = parseIssueTime(obj.optString("messageIssueTime", ""), issueFormat);
                String flareClass = findFlareClass(bodyText);
                String kpIndex = findKpIndex(bodyText);
                String impactTarget = findImpactTarget(bodyText);
                String classification = deriveClassification(type, title, bodyText, flareClass, impactTarget, kpIndex);
                String detail = deriveDetail(type, flareClass, impactTarget, kpIndex);
                String source = resolveSourceLabel(obj.optString("messageSource", ""));
                events.add(new DonkiNotification(
                        id,
                        type,
                        classification,
                        detail,
                        timestamp,
                        source
                ));
            }
            Collections.sort(events, (a, b) -> Long.compare(b.getTimestampMillis(), a.getTimestampMillis()));
            return events;
        }
    }

    private long parseIssueTime(@NonNull String value, @NonNull SimpleDateFormat format) {
        if (value.isEmpty()) {
            return System.currentTimeMillis();
        }
        try {
            Date date = format.parse(value);
            if (date != null) {
                return date.getTime();
            }
        } catch (ParseException ignored) {
        }
        return System.currentTimeMillis();
    }

    @NonNull
    private String deriveClassification(@NonNull String type,
                                        @NonNull String fallbackTitle,
                                        @NonNull String body,
                                        @Nullable String flareClass,
                                        @Nullable String impactTarget,
                                        @Nullable String kpIndex) {
        String fallback = fallbackTitle.isEmpty() ? type : fallbackTitle;
        String normalizedType = type.toUpperCase(Locale.US);
        String upperBody = body.toUpperCase(Locale.US);
        if (normalizedType.contains("FLR") || normalizedType.contains("FLARE")) {
            if (flareClass != null && flareClass.length() > 0) {
                String band = flareClass.substring(0, 1).toUpperCase(Locale.US);
                return getString(R.string.events_classification_flare_band, band);
            }
            return getString(R.string.events_classification_flare_generic);
        } else if (normalizedType.contains("CME")) {
            if (upperBody.contains("HALO")) {
                if (upperBody.contains("PARTIAL")) {
                    return getString(R.string.events_classification_cme_partial);
                }
                return getString(R.string.events_classification_cme_halo);
            }
            if (impactTarget != null) {
                if ("EARTH".equals(impactTarget)) {
                    return getString(R.string.events_classification_cme_earth);
                }
                if ("MISSIONS".equals(impactTarget)) {
                    return getString(R.string.events_classification_cme_missions);
                }
            }
            return getString(R.string.events_classification_cme_generic);
        } else if (normalizedType.contains("SEP")) {
            return getString(R.string.events_classification_sep);
        } else if (normalizedType.contains("GEOMAGNETIC") || normalizedType.contains("GST")) {
            if (kpIndex != null) {
                return getString(R.string.events_classification_geomagnetic_kp, kpIndex);
            }
            return getString(R.string.events_classification_geomagnetic);
        }
        return fallback;
    }

    @Nullable
    private String deriveDetail(@NonNull String type,
                                @Nullable String flareClass,
                                @Nullable String impactTarget,
                                @Nullable String kpIndex) {
        String normalizedType = type.toUpperCase(Locale.US);
        if (normalizedType.contains("FLR") || normalizedType.contains("FLARE")) {
            if (flareClass != null) {
                return getString(R.string.events_detail_flare, flareClass);
            }
        } else if (normalizedType.contains("CME")) {
            String impactLabel = labelForImpact(impactTarget);
            if (impactLabel != null) {
                return getString(R.string.events_detail_cme, impactLabel);
            }
        } else if (normalizedType.contains("SEP")) {
            return getString(R.string.events_detail_sep);
        } else if (normalizedType.contains("GEOMAGNETIC") || normalizedType.contains("GST")) {
            if (kpIndex != null) {
                return getString(R.string.events_detail_geomagnetic, kpIndex);
            }
            return getString(R.string.events_detail_geomagnetic_generic);
        }
        return null;
    }

    @Nullable
    private String findFlareClass(@NonNull String body) {
        Matcher matcher = FLARE_PATTERN.matcher(body);
        if (matcher.find()) {
            return matcher.group(0).toUpperCase(Locale.US);
        }
        return null;
    }

    @Nullable
    private String findKpIndex(@NonNull String body) {
        Matcher matcher = KP_PATTERN.matcher(body);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    @Nullable
    private String findImpactTarget(@NonNull String body) {
        String upper = body.toUpperCase(Locale.US);
        if (upper.contains("EARTH")) {
            return "EARTH";
        }
        if (upper.contains("MARS")) {
            return "MARS";
        }
        if (upper.contains("MERCURY") || upper.contains("VENUS") || upper.contains("INNER")) {
            return "INNER";
        }
        if (upper.contains("MISSION") || upper.contains("SPACECRAFT")
                || upper.contains("STEREO") || upper.contains("SOHO")) {
            return "MISSIONS";
        }
        return null;
    }

    @Nullable
    private String labelForImpact(@Nullable String code) {
        if (code == null) {
            return null;
        }
        switch (code) {
            case "EARTH":
                return getString(R.string.events_impact_earth);
            case "MARS":
                return getString(R.string.events_impact_mars);
            case "INNER":
                return getString(R.string.events_impact_inner);
            case "MISSIONS":
                return getString(R.string.events_impact_missions);
            default:
                return null;
        }
    }

    @Override
    public void onDestroyView() {
        if (binding != null && binding.eventsList != null) {
            binding.eventsList.setAdapter(null);
        }
        adapter = null;
        binding = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    @NonNull
    private String resolveSourceLabel(@NonNull String rawSource) {
        if (rawSource.isEmpty()) {
            return getString(R.string.events_source_nasa);
        }
        if (rawSource.equalsIgnoreCase("DONKI")) {
            return getString(R.string.events_source_nasa);
        }
        return rawSource;
    }
}
