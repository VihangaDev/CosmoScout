package com.cosmoscout.ui.places;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cosmoscout.data.places.Place;
import com.cosmoscout.data.places.PlacesRepository;
import com.cosmoscout.data.places.PlacesScoring;
import com.cosmoscout.data.places.PlacesService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
public final class PlacesController {

    private static final String PREFS_NAME = "dark_sky_planner";
    private static final String PREF_WINDOW_START = "night_window_start";
    private static final String PREF_WINDOW_END = "night_window_end";
    private static final String PREF_WIND_CAP = "night_wind_cap";
    private static final String PREF_WEIGHT_CLOUD = "night_weight_cloud";
    private static final String PREF_WEIGHT_PRECIP = "night_weight_precip";
    private static final String PREF_WEIGHT_WIND = "night_weight_wind";
    private static final String PREF_WEIGHT_MOON = "night_weight_moon";
    private static final String PREF_SORT = "places_sort";
    private static final String PREF_FILTER = "places_filter";
    private static final String PREF_PRIMARY = "places_primary";

    private static final long CACHE_WINDOW_MS = TimeUnit.MINUTES.toMillis(30);
    private static final int TIMELINE_SEGMENTS = 8;

    public interface Listener {
        void onPlacesUpdated(@NonNull List<UiPlace> places);
        void onLoadingStateChanged(boolean loading);
        void onError(@NonNull Throwable throwable);
    }

    public enum Filter {
        ALL, GOOD, OK, POOR
    }

    public enum Sort {
        SCORE, DISTANCE, NAME
    }

    public static final class UiPlace {
        public final Place place;
        @Nullable public final PlaceSkyState sky;
        @Nullable public final Double distanceKm;
        public final boolean isPrimary;

        UiPlace(@NonNull Place place,
                @Nullable PlaceSkyState sky,
                @Nullable Double distanceKm,
                boolean isPrimary) {
            this.place = place;
            this.sky = sky;
            this.distanceKm = distanceKm;
            this.isPrimary = isPrimary;
        }
    }

    public static final class NightSettings {
        public final int windowStartMinutes;
        public final int windowEndMinutes;
        public final double windCap;
        public final double weightCloud;
        public final double weightPrecip;
        public final double weightWind;
        public final double weightMoon;

        public NightSettings(int windowStartMinutes,
                             int windowEndMinutes,
                             double windCap,
                             double weightCloud,
                             double weightPrecip,
                             double weightWind,
                             double weightMoon) {
            this.windowStartMinutes = windowStartMinutes;
            this.windowEndMinutes = windowEndMinutes;
            this.windCap = windCap;
            this.weightCloud = weightCloud;
            this.weightPrecip = weightPrecip;
            this.weightWind = weightWind;
            this.weightMoon = weightMoon;
        }
    }

    public static final class HourSample {
        public final long timeMillis;
        public final int cloudPct;
        public final double precipitation;
        public final double windSpeed;

        public HourSample(long timeMillis, int cloudPct, double precipitation, double windSpeed) {
            this.timeMillis = timeMillis;
            this.cloudPct = cloudPct;
            this.precipitation = precipitation;
            this.windSpeed = windSpeed;
        }
    }

    static final class PlaceSkyState {
        final int score;
        final PlacesScoring.SkyStatus status;
        final long windowStart;
        final long windowEnd;
        final int clearPct;
        final int moonPct;
        final long updatedAt;
        final boolean fromCache;
        final List<Integer> timeline;
        final List<HourSample> hourSamples;
        final double avgCloud;
        final double avgWind;
        final boolean precipFree;
        final TimeZone timezone;

        PlaceSkyState(int score,
                      PlacesScoring.SkyStatus status,
                      long windowStart,
                      long windowEnd,
                      int clearPct,
                      int moonPct,
                      long updatedAt,
                      boolean fromCache,
                      @NonNull List<Integer> timeline,
                      @NonNull List<HourSample> hourSamples,
                      double avgCloud,
                      double avgWind,
                      boolean precipFree,
                      @NonNull TimeZone timezone) {
            this.score = score;
            this.status = status;
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
            this.clearPct = clearPct;
            this.moonPct = moonPct;
            this.updatedAt = updatedAt;
            this.fromCache = fromCache;
            this.timeline = Collections.unmodifiableList(new ArrayList<>(timeline));
            this.hourSamples = Collections.unmodifiableList(new ArrayList<>(hourSamples));
            this.avgCloud = avgCloud;
            this.avgWind = avgWind;
            this.precipFree = precipFree;
            this.timezone = timezone;
        }

        boolean isFresh(long now) {
            return now - updatedAt <= CACHE_WINDOW_MS;
        }
    }

    private final Context appContext;
    private final PlacesRepository repository;
    private final PlacesService service = new PlacesService();
    private final Listener listener;
    private final SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final List<Place> allPlaces = new ArrayList<>();
    private final Map<String, PlaceSkyState> skyStates = new HashMap<>();
    private final Map<String, Double> distanceCache = new HashMap<>();
    private final Set<String> inFlight = new HashSet<>();

    private NightSettings nightSettings;
    private Filter filter;
    private Sort sort;
    @Nullable private String primaryPlaceId;
    @Nullable private Location deviceLocation;
    private boolean loading;
    private boolean destroyed;
    private List<UiPlace> lastUi = Collections.emptyList();
    private int visibleStart = 0;
    private int visibleEnd = -1;

    public PlacesController(@NonNull Context context,
                            @NonNull PlacesRepository repository,
                            @NonNull Listener listener) {
        this.appContext = context.getApplicationContext();
        this.repository = repository;
        this.listener = listener;
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.nightSettings = readNightSettings();
        this.filter = readFilter();
        this.sort = readSort();
        this.primaryPlaceId = prefs.getString(PREF_PRIMARY, null);
    }

    public void destroy() {
        destroyed = true;
        executor.shutdownNow();
    }

    public void reload() {
        reload(null);
    }

    public void reload(@Nullable Runnable onComplete) {
        if (destroyed) return;
        setLoading(true);
        repository.list((items, err) -> {
            if (destroyed) {
                if (onComplete != null) {
                    onComplete.run();
                }
                return;
            }
            setLoading(false);
            if (err != null) {
                listener.onError(err);
                if (onComplete != null) {
                    onComplete.run();
                }
                return;
            }
            allPlaces.clear();
            allPlaces.addAll(items);
            hydrateCaches();
            computeDistances();
            emitUi();
            refreshVisible(false);
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    public void onPlaceRemoved(@NonNull String placeId) {
        skyStates.remove(placeId);
        distanceCache.remove(placeId);
        clearLocalSnapshot(placeId);
        if (placeId.equals(primaryPlaceId)) {
            setPrimaryPlace(null);
        } else {
            emitUi();
        }
    }

    public void setDeviceLocation(@Nullable Location location) {
        this.deviceLocation = location;
        computeDistances();
        emitUi();
    }

    public void onVisibleRangeChanged(int first, int last) {
        visibleStart = Math.max(0, first);
        visibleEnd = Math.max(visibleStart, last);
        scheduleRangeFetch(false);
    }

    public void refreshVisible(boolean force) {
        scheduleRangeFetch(force);
    }

    public void setFilter(@NonNull Filter next) {
        if (filter == next) return;
        filter = next;
        prefs.edit().putString(PREF_FILTER, next.name()).apply();
        emitUi();
    }

    public Filter getFilter() {
        return filter;
    }

    public void setSort(@NonNull Sort next) {
        if (sort == next) return;
        sort = next;
        prefs.edit().putString(PREF_SORT, next.name()).apply();
        emitUi();
    }

    public Sort getSort() {
        return sort;
    }

    public void setPrimaryPlace(@Nullable String placeId) {
        if (Objects.equals(primaryPlaceId, placeId)) {
            return;
        }
        primaryPlaceId = placeId;
        if (placeId == null) {
            prefs.edit().remove(PREF_PRIMARY).apply();
        } else {
            prefs.edit().putString(PREF_PRIMARY, placeId).apply();
        }
        emitUi();
    }

    @Nullable
    public String getPrimaryPlaceId() {
        return primaryPlaceId;
    }

    public NightSettings getNightSettings() {
        return nightSettings;
    }

    public void updateNightSettings(@NonNull NightSettings settings) {
        nightSettings = settings;
        persistNightSettings(settings);
        refreshVisible(true);
    }

    @Nullable
    public PlaceSkyState findSkyState(@NonNull String placeId) {
        return skyStates.get(placeId);
    }

    @NonNull
    public List<HourSample> getHourSamples(@NonNull String placeId) {
        PlaceSkyState state = skyStates.get(placeId);
        if (state != null) {
            return state.hourSamples;
        }
        return Collections.emptyList();
    }

    @NonNull
    public TimeZone getTimezone(@NonNull String placeId) {
        PlaceSkyState state = skyStates.get(placeId);
        return state != null ? state.timezone : TimeZone.getDefault();
    }

    private void setLoading(boolean next) {
        if (loading == next) {
            return;
        }
        loading = next;
        listener.onLoadingStateChanged(next);
    }

    private void hydrateCaches() {
        for (Place place : allPlaces) {
            String id = place.getId();
            if (skyStates.containsKey(id)) {
                continue;
            }
            PlaceSkyState cached = buildStateFromCache(place);
            if (cached != null) {
                skyStates.put(id, cached);
            }
        }
    }

    private void emitUi() {
        List<UiPlace> models = new ArrayList<>();
        for (Place place : allPlaces) {
            PlaceSkyState state = skyStates.get(place.getId());
            if (!passesFilter(state)) {
                continue;
            }
            models.add(new UiPlace(
                    place,
                    state,
                    distanceCache.get(place.getId()),
                    place.getId().equals(primaryPlaceId)
            ));
        }
        sort(models);
        lastUi = Collections.unmodifiableList(models);
        listener.onPlacesUpdated(lastUi);
    }

    private boolean passesFilter(@Nullable PlaceSkyState state) {
        if (filter == Filter.ALL) {
            return true;
        }
        if (state == null) {
            return false;
        }
        switch (filter) {
            case GOOD:
                return state.status == PlacesScoring.SkyStatus.GOOD;
            case OK:
                return state.status == PlacesScoring.SkyStatus.OK;
            case POOR:
                return state.status == PlacesScoring.SkyStatus.POOR;
            default:
                return true;
        }
    }

    private void sort(@NonNull List<UiPlace> models) {
        Comparator<UiPlace> comparator;
        if (sort == Sort.DISTANCE) {
            comparator = Comparator.comparingDouble(
                    item -> item.distanceKm != null ? item.distanceKm : Double.MAX_VALUE
            );
        } else if (sort == Sort.NAME) {
            comparator = Comparator.comparing(item -> item.place.getName(), String.CASE_INSENSITIVE_ORDER);
        } else {
            comparator = (a, b) -> {
                int scoreA = a.sky != null ? a.sky.score : -1;
                int scoreB = b.sky != null ? b.sky.score : -1;
                if (scoreA == scoreB) {
                    return a.place.getName().compareToIgnoreCase(b.place.getName());
                }
                return Integer.compare(scoreB, scoreA);
            };
        }
        Collections.sort(models, comparator);
    }

    private void scheduleRangeFetch(boolean force) {
        if (lastUi.isEmpty()) {
            return;
        }
        int start = Math.max(0, Math.min(visibleStart, lastUi.size() - 1));
        int end = Math.max(start, Math.min(visibleEnd, lastUi.size() - 1));
        for (int i = start; i <= end; i++) {
            UiPlace ui = lastUi.get(i);
            requestFetch(ui.place, force);
        }
    }

    private void requestFetch(@NonNull Place place, boolean force) {
        String id = place.getId();
        PlaceSkyState current = skyStates.get(id);
        if (!force && current != null && current.isFresh(System.currentTimeMillis())) {
            return;
        }
        if (inFlight.contains(id)) {
            return;
        }
        inFlight.add(id);
        executor.execute(() -> performFetch(place, force));
    }

    private void performFetch(@NonNull Place place, boolean force) {
        try {
            NightSettings settings = nightSettings;
            PlacesService.ForecastResponse response = service.fetchForecast(place.getLat(), place.getLon());
            long[] window = resolveWindow(response.timezone, settings);
            List<PlacesService.ForecastHour> hours = sliceHours(response.hours, window[0], window[1]);
            if (hours.isEmpty()) {
                throw new IOException("No forecast hours");
            }
            PlacesScoring.Weights weights = new PlacesScoring.Weights(
                    settings.weightCloud,
                    settings.weightPrecip,
                    settings.weightWind,
                    settings.weightMoon
            );
            PlacesScoring.ScoreResult best = PlacesScoring.findBestWindow(
                    hours,
                    response.moonPctByDay,
                    weights,
                    settings.windCap
            );
            if (best == null) {
                throw new IOException("Unable to score window");
            }
            int score = clampScore((int) Math.round(best.score));
            List<Integer> timeline = PlacesScoring.buildTimeline(hours, TIMELINE_SEGMENTS);
            List<HourSample> samples = toHourSamples(hours);
            PlaceSkyState state = new PlaceSkyState(
                    score,
                    best.status,
                    best.windowStart,
                    best.windowEnd,
                    best.clearPct,
                    best.moonPct,
                    System.currentTimeMillis(),
                    false,
                    timeline,
                    samples,
                    best.avgCloud,
                    best.avgWind,
                    best.precipFree,
                    response.timezone
            );
            PlacesRepository.ComputedFields fields = new PlacesRepository.ComputedFields(
                    state.score,
                    state.windowStart,
                    state.windowEnd,
                    state.clearPct,
                    state.moonPct,
                    state.updatedAt
            );
            repository.updateComputedFields(place.getId(), fields, err -> {});
            saveLocalSnapshot(place.getId(), state);
            mainHandler.post(() -> {
                inFlight.remove(place.getId());
                if (destroyed) return;
                skyStates.put(place.getId(), state);
                emitUi();
            });
        } catch (IOException e) {
            mainHandler.post(() -> {
                inFlight.remove(place.getId());
                if (destroyed) return;
                listener.onError(e);
            });
        }
    }

    private int clampScore(int value) {
        if (value < 0) return 0;
        if (value > 100) return 100;
        return value;
    }

    private void computeDistances() {
        Location origin = deviceLocation;
        if (origin == null) {
            distanceCache.clear();
            return;
        }
        float[] results = new float[1];
        for (Place place : allPlaces) {
            Location.distanceBetween(
                    origin.getLatitude(),
                    origin.getLongitude(),
                    place.getLat(),
                    place.getLon(),
                    results
            );
            distanceCache.put(place.getId(), results[0] / 1000d);
        }
    }

    private List<PlacesService.ForecastHour> sliceHours(@NonNull List<PlacesService.ForecastHour> hours,
                                                        long windowStart,
                                                        long windowEnd) {
        List<PlacesService.ForecastHour> subset = new ArrayList<>();
        for (PlacesService.ForecastHour hour : hours) {
            if (hour.timeMillis >= windowStart && hour.timeMillis < windowEnd) {
                subset.add(hour);
            }
        }
        return subset;
    }

    private List<HourSample> toHourSamples(@NonNull List<PlacesService.ForecastHour> hours) {
        List<HourSample> samples = new ArrayList<>(hours.size());
        for (PlacesService.ForecastHour hour : hours) {
            samples.add(new HourSample(
                    hour.timeMillis,
                    clampScore((int) Math.round(hour.cloudCover)),
                    hour.precipitation,
                    hour.windSpeed
            ));
        }
        return samples;
    }

    private long[] resolveWindow(@NonNull TimeZone timezone, @NonNull NightSettings settings) {
        Calendar now = Calendar.getInstance(timezone);
        Calendar start = (Calendar) now.clone();
        start.set(Calendar.HOUR_OF_DAY, settings.windowStartMinutes / 60);
        start.set(Calendar.MINUTE, settings.windowStartMinutes % 60);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        Calendar end = (Calendar) start.clone();
        end.set(Calendar.HOUR_OF_DAY, settings.windowEndMinutes / 60);
        end.set(Calendar.MINUTE, settings.windowEndMinutes % 60);
        end.set(Calendar.SECOND, 0);
        end.set(Calendar.MILLISECOND, 0);
        if (settings.windowEndMinutes <= settings.windowStartMinutes) {
            end.add(Calendar.DATE, 1);
        }
        if (now.after(end)) {
            start.add(Calendar.DATE, 1);
            end.add(Calendar.DATE, 1);
        }
        return new long[]{start.getTimeInMillis(), end.getTimeInMillis()};
    }

    private PlaceSkyState buildStateFromCache(@NonNull Place place) {
        PlacesRepository.ComputedFields fields = repository.getCachedComputedFields(place.getId());
        if (fields != null && fields.isValid()) {
            return composeState(place.getId(), fields, true);
        }
        return restoreFromLocalOnly(place.getId());
    }

    private PlaceSkyState composeState(@NonNull String placeId,
                                       @NonNull PlacesRepository.ComputedFields fields,
                                       boolean fromCache) {
        List<Integer> timeline = readTimeline(placeId);
        if (timeline.isEmpty()) {
            timeline = fallbackTimeline(fields.clearPct);
        }
        List<HourSample> samples = readHourSamples(placeId);
        if (samples.isEmpty()) {
            samples = Collections.singletonList(new HourSample(
                    fields.windowStart,
                    fields.clearPct,
                    0d,
                    0d
            ));
        }
        TimeZone timezone = readTimezone(placeId);
        double avgCloud = readDouble(keyAvgCloud(placeId), 100d - fields.clearPct);
        double avgWind = readDouble(keyAvgWind(placeId), 0d);
        boolean precipFree = prefs.getBoolean(keyPrecip(placeId), true);
        return new PlaceSkyState(
                clampScore(fields.score),
                PlacesScoring.toStatus(fields.score),
                fields.windowStart,
                fields.windowEnd,
                fields.clearPct,
                fields.moonPct,
                fields.updatedAt,
                fromCache,
                timeline,
                samples,
                avgCloud,
                avgWind,
                precipFree,
                timezone
        );
    }

    @Nullable
    private PlaceSkyState restoreFromLocalOnly(@NonNull String placeId) {
        int score = prefs.getInt(keyLocalScore(placeId), -1);
        if (score < 0) {
            return null;
        }
        long updated = prefs.getLong(keyLocalUpdated(placeId), 0L);
        long winStart = prefs.getLong(keyLocalWindowStart(placeId), 0L);
        long winEnd = prefs.getLong(keyLocalWindowEnd(placeId), 0L);
        int clear = prefs.getInt(keyLocalClear(placeId), 0);
        int moon = prefs.getInt(keyLocalMoon(placeId), 0);
        PlacesRepository.ComputedFields fields = new PlacesRepository.ComputedFields(
                score,
                winStart,
                winEnd,
                clear,
                moon,
                updated
        );
        return composeState(placeId, fields, true);
    }

    private void saveLocalSnapshot(@NonNull String placeId, @NonNull PlaceSkyState state) {
        SharedPreferences.Editor editor = prefs.edit()
                .putString(keyTimeline(placeId), encodeTimeline(state.timeline))
                .putString(keyHours(placeId), encodeHours(state.hourSamples))
                .putString(keyTimezone(placeId), state.timezone.getID())
                .putInt(keyLocalScore(placeId), state.score)
                .putInt(keyLocalClear(placeId), state.clearPct)
                .putInt(keyLocalMoon(placeId), state.moonPct)
                .putLong(keyLocalWindowStart(placeId), state.windowStart)
                .putLong(keyLocalWindowEnd(placeId), state.windowEnd)
                .putLong(keyLocalUpdated(placeId), state.updatedAt)
                .putBoolean(keyPrecip(placeId), state.precipFree);
        writeDouble(editor, keyAvgCloud(placeId), state.avgCloud);
        writeDouble(editor, keyAvgWind(placeId), state.avgWind);
        editor.apply();
    }

    private void clearLocalSnapshot(@NonNull String placeId) {
        prefs.edit()
                .remove(keyTimeline(placeId))
                .remove(keyHours(placeId))
                .remove(keyTimezone(placeId))
                .remove(keyAvgCloud(placeId))
                .remove(keyAvgWind(placeId))
                .remove(keyPrecip(placeId))
                .remove(keyLocalScore(placeId))
                .remove(keyLocalClear(placeId))
                .remove(keyLocalMoon(placeId))
                .remove(keyLocalWindowStart(placeId))
                .remove(keyLocalWindowEnd(placeId))
                .remove(keyLocalUpdated(placeId))
                .apply();
    }

    private List<Integer> readTimeline(@NonNull String placeId) {
        String raw = prefs.getString(keyTimeline(placeId), null);
        return raw == null ? Collections.emptyList() : decodeTimeline(raw);
    }

    private List<HourSample> readHourSamples(@NonNull String placeId) {
        String raw = prefs.getString(keyHours(placeId), null);
        return raw == null ? Collections.emptyList() : decodeHours(raw);
    }

    private TimeZone readTimezone(@NonNull String placeId) {
        String id = prefs.getString(keyTimezone(placeId), null);
        if (id == null || id.isEmpty()) {
            return TimeZone.getDefault();
        }
        return TimeZone.getTimeZone(id);
    }

    private String encodeTimeline(@NonNull List<Integer> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    private List<Integer> decodeTimeline(@NonNull String raw) {
        String[] parts = raw.split(",");
        List<Integer> values = new ArrayList<>(parts.length);
        for (String part : parts) {
            try {
                values.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return values;
    }

    private String encodeHours(@NonNull List<HourSample> hours) {
        StringBuilder sb = new StringBuilder();
        for (HourSample sample : hours) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(sample.timeMillis)
                    .append('|').append(sample.cloudPct)
                    .append('|').append(String.format(Locale.US, "%.2f", sample.precipitation))
                    .append('|').append(String.format(Locale.US, "%.2f", sample.windSpeed));
        }
        return sb.toString();
    }

    private List<HourSample> decodeHours(@NonNull String raw) {
        String[] entries = raw.split(";");
        List<HourSample> samples = new ArrayList<>(entries.length);
        for (String entry : entries) {
            String[] parts = entry.split("\\|");
            if (parts.length != 4) continue;
            try {
                long time = Long.parseLong(parts[0]);
                int cloud = Integer.parseInt(parts[1]);
                double precip = Double.parseDouble(parts[2]);
                double wind = Double.parseDouble(parts[3]);
                samples.add(new HourSample(time, cloud, precip, wind));
            } catch (NumberFormatException ignored) {
            }
        }
        return samples;
    }

    private List<Integer> fallbackTimeline(int clearPct) {
        int cloud = clampScore(100 - clearPct);
        List<Integer> values = new ArrayList<>(TIMELINE_SEGMENTS);
        for (int i = 0; i < TIMELINE_SEGMENTS; i++) {
            values.add(cloud);
        }
        return values;
    }

    private void writeDouble(@NonNull SharedPreferences.Editor editor, @NonNull String key, double value) {
        editor.putLong(key, Double.doubleToRawLongBits(value));
    }

    private double readDouble(@NonNull String key, double fallback) {
        if (!prefs.contains(key)) {
            return fallback;
        }
        long bits = prefs.getLong(key, Double.doubleToRawLongBits(fallback));
        return Double.longBitsToDouble(bits);
    }

    private String keyTimeline(String id) { return "timeline_" + id; }
    private String keyHours(String id) { return "hours_" + id; }
    private String keyTimezone(String id) { return "timezone_" + id; }
    private String keyAvgCloud(String id) { return "avg_cloud_" + id; }
    private String keyAvgWind(String id) { return "avg_wind_" + id; }
    private String keyPrecip(String id) { return "precip_" + id; }
    private String keyLocalScore(String id) { return "score_" + id; }
    private String keyLocalClear(String id) { return "clear_" + id; }
    private String keyLocalMoon(String id) { return "moon_" + id; }
    private String keyLocalWindowStart(String id) { return "wstart_" + id; }
    private String keyLocalWindowEnd(String id) { return "wend_" + id; }
    private String keyLocalUpdated(String id) { return "updated_" + id; }

    private Filter readFilter() {
        String raw = prefs.getString(PREF_FILTER, Filter.ALL.name());
        try {
            return Filter.valueOf(raw);
        } catch (Exception e) {
            return Filter.ALL;
        }
    }

    private Sort readSort() {
        String raw = prefs.getString(PREF_SORT, Sort.SCORE.name());
        try {
            return Sort.valueOf(raw);
        } catch (Exception e) {
            return Sort.SCORE;
        }
    }

    private NightSettings readNightSettings() {
        int start = prefs.getInt(PREF_WINDOW_START, 19 * 60);
        int end = prefs.getInt(PREF_WINDOW_END, 3 * 60);
        double windCap = readDouble(PREF_WIND_CAP, 12d);
        double weightCloud = readDouble(PREF_WEIGHT_CLOUD, 0.6d);
        double weightPrecip = readDouble(PREF_WEIGHT_PRECIP, 0.2d);
        double weightWind = readDouble(PREF_WEIGHT_WIND, 0.1d);
        double weightMoon = readDouble(PREF_WEIGHT_MOON, 0.1d);
        return new NightSettings(start, end, windCap, weightCloud, weightPrecip, weightWind, weightMoon);
    }

    private void persistNightSettings(@NonNull NightSettings settings) {
        SharedPreferences.Editor editor = prefs.edit()
                .putInt(PREF_WINDOW_START, settings.windowStartMinutes)
                .putInt(PREF_WINDOW_END, settings.windowEndMinutes);
        writeDouble(editor, PREF_WIND_CAP, settings.windCap);
        writeDouble(editor, PREF_WEIGHT_CLOUD, settings.weightCloud);
        writeDouble(editor, PREF_WEIGHT_PRECIP, settings.weightPrecip);
        writeDouble(editor, PREF_WEIGHT_WIND, settings.weightWind);
        writeDouble(editor, PREF_WEIGHT_MOON, settings.weightMoon);
        editor.apply();
    }
}
