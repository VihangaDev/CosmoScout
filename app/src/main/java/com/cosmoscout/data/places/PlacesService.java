package com.cosmoscout.data.places;

import androidx.annotation.NonNull;

import android.util.Log;

import com.cosmoscout.core.Net;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
public final class PlacesService {

    private static final String ENDPOINT = "https://api.open-meteo.com/v1/forecast"; // API ref: https://open-meteo.com/

    @NonNull
    public ForecastResponse fetchForecast(double lat, double lon) throws IOException {
        Log.d("PlacesService", "Fetching forecast for lat=" + lat + ", lon=" + lon);

        HttpUrl url = HttpUrl.parse(ENDPOINT)
                .newBuilder()
                .addQueryParameter("latitude", format(lat))
                .addQueryParameter("longitude", format(lon))
                .addQueryParameter("hourly", "cloud_cover,precipitation,wind_speed_10m,visibility")
                .addQueryParameter("timezone", "auto")
                .addQueryParameter("forecast_days", "2")
                .build();

        Log.d("PlacesService", "Request URL: " + url);

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "CosmoScout/1.0 (Android)")
                .build();

        try (Response response = Net.client().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                Log.e("PlacesService", "HTTP " + response.code() + " response: " + body);
                throw new IOException("HTTP " + response.code());
            }
            String body = response.body() != null ? response.body().string() : "";
            ForecastResponse parsed = parseForecast(body);
            Map<Long, Integer> moonMap = fetchMoonPhases(lat, lon, parsed.timezone);
            if (!moonMap.isEmpty()) {
                return new ForecastResponse(
                        parsed.timezone,
                        parsed.hours,
                        Collections.unmodifiableMap(moonMap)
                );
            }
            return parsed;
        }
    }

    @NonNull
    private ForecastResponse parseForecast(@NonNull String body) throws IOException {
        try {
            JSONObject root = new JSONObject(body);
            String timezoneId = root.optString("timezone", "UTC");
            TimeZone timezone = TimeZone.getTimeZone(timezoneId);

            JSONObject hourly = root.optJSONObject("hourly");
            JSONArray times = hourly != null ? hourly.optJSONArray("time") : null;
            JSONArray clouds = hourly != null ? hourly.optJSONArray("cloud_cover") : null;
            JSONArray precip = hourly != null ? hourly.optJSONArray("precipitation") : null;
            JSONArray wind = hourly != null ? hourly.optJSONArray("wind_speed_10m") : null;
            JSONArray visibility = hourly != null ? hourly.optJSONArray("visibility") : null;

            if (times == null || clouds == null || precip == null || wind == null || visibility == null) {
                throw new IOException("Missing hourly data");
            }

            SimpleDateFormat hourFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
            hourFormat.setTimeZone(timezone);
            List<ForecastHour> hours = new ArrayList<>();
            for (int i = 0; i < times.length(); i++) {
                String stamp = times.optString(i);
                long timeMillis = parseTime(hourFormat, stamp);
                long dayKey = startOfDay(timeMillis, timezone);
                double cloudVal = clouds.optDouble(i, 0d);
                double precipVal = precip.optDouble(i, 0d);
                double windVal = wind.optDouble(i, 0d);
                double visibilityMeters = visibility.optDouble(i, Double.NaN);
                double visibilityKm = Double.isNaN(visibilityMeters) ? Double.NaN : visibilityMeters / 1000d;
                hours.add(new ForecastHour(timeMillis, dayKey, cloudVal, precipVal, windVal, visibilityKm));
            }

            return new ForecastResponse(
                    timezone,
                    Collections.unmodifiableList(hours),
                    Collections.emptyMap()
            );
        } catch (ParseException | JSONException | RuntimeException e) {
            throw new IOException("Failed to parse forecast", e);
        }
    }

    private Map<Long, Integer> fetchMoonPhases(double lat,
                                               double lon,
                                               @NonNull TimeZone timezone) {
        Calendar startCal = Calendar.getInstance(timezone);
        Calendar endCal = (Calendar) startCal.clone();
        endCal.add(Calendar.DAY_OF_YEAR, 2);
        long startMillis = startOfDay(startCal.getTimeInMillis(), timezone);
        long endMillis = startOfDay(endCal.getTimeInMillis(), timezone);

        // Open-Meteo does not support moon phases in the free API anymore,
        // so we use a local approximation which is sufficient for our needs.
        return approximateMoonPhases(startMillis, endMillis, timezone);
    }

    private long parseTime(@NonNull SimpleDateFormat format, @NonNull String value) throws ParseException {
        return format.parse(value).getTime();
    }

    private long parseDay(@NonNull SimpleDateFormat format, @NonNull String value) throws ParseException {
        return startOfDay(format.parse(value).getTime(), format.getTimeZone());
    }

    private long startOfDay(long timeMillis, @NonNull TimeZone tz) {
        long offset = tz.getOffset(timeMillis);
        long local = timeMillis + offset;
        long days = local / 86_400_000L;
        long startLocal = days * 86_400_000L;
        return startLocal - offset;
    }

    private String format(double value) {
        return String.format(Locale.US, "%.5f", value);
    }

    @NonNull
    private Map<Long, Integer> approximateMoonPhases(long startMillis,
                                                     long endMillis,
                                                     @NonNull TimeZone timezone) {
        Map<Long, Integer> map = new HashMap<>();
        Calendar cursor = Calendar.getInstance(timezone);
        cursor.setTimeInMillis(startMillis);
        while (cursor.getTimeInMillis() <= endMillis) {
            long dayKey = startOfDay(cursor.getTimeInMillis(), timezone);
            int pct = approximateMoonPercent(cursor.getTimeInMillis());
            map.put(dayKey, pct);
            cursor.add(Calendar.DAY_OF_YEAR, 1);
        }
        return map;
    }

    private int approximateMoonPercent(long timeMillis) {
        double jd = timeMillis / 86_400_000d + 2440587.5d;
        double daysSinceNew = jd - 2451549.5d;
        double cycle = 29.530588853d;
        double phase = daysSinceNew / cycle;
        phase -= Math.floor(phase);
        if (phase < 0d) {
            phase += 1d;
        }
        return PlacesScoring.moonIlluminationPercent(phase);
    }

    public static final class ForecastHour {
        public final long timeMillis;
        public final long dayKey;
        public final double cloudCover;
        public final double precipitation;
        public final double windSpeed;
        public final double visibilityKm;

        ForecastHour(long timeMillis,
                     long dayKey,
                     double cloudCover,
                     double precipitation,
                     double windSpeed,
                     double visibilityKm) {
            this.timeMillis = timeMillis;
            this.dayKey = dayKey;
            this.cloudCover = cloudCover;
            this.precipitation = precipitation;
            this.windSpeed = windSpeed;
            this.visibilityKm = visibilityKm;
        }
    }

    public static final class ForecastResponse {
        public final TimeZone timezone;
        public final List<ForecastHour> hours;
        public final Map<Long, Integer> moonPctByDay;

        ForecastResponse(@NonNull TimeZone timezone,
                         @NonNull List<ForecastHour> hours,
                         @NonNull Map<Long, Integer> moonPctByDay) {
            this.timezone = timezone;
            this.hours = hours;
            this.moonPctByDay = moonPctByDay;
        }
    }
}
