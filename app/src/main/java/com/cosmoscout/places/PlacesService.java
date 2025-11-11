package com.cosmoscout.places;

import androidx.annotation.NonNull;

import com.cosmoscout.core.Net;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Lightweight client for Open-Meteo that returns parsed hourly + moon data for Places.
 */
public final class PlacesService {

    private static final String ENDPOINT = "https://api.open-meteo.com/v1/forecast";

    @NonNull
    public ForecastResponse fetchForecast(double lat, double lon) throws IOException {
        HttpUrl url = HttpUrl.parse(ENDPOINT)
                .newBuilder()
                .addQueryParameter("latitude", format(lat))
                .addQueryParameter("longitude", format(lon))
                .addQueryParameter("hourly", "cloud_cover,precipitation,wind_speed_10m,visibility")
                .addQueryParameter("daily", "moon_phase")
                .addQueryParameter("timezone", "auto")
                .addQueryParameter("forecast_days", "2")
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
            return parseForecast(body);
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

            if (times == null || clouds == null || precip == null || wind == null) {
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
                hours.add(new ForecastHour(timeMillis, dayKey, cloudVal, precipVal, windVal));
            }

            JSONObject daily = root.optJSONObject("daily");
            JSONArray dayTimes = daily != null ? daily.optJSONArray("time") : null;
            JSONArray moon = daily != null ? daily.optJSONArray("moon_phase") : null;

            Map<Long, Integer> moonPctByDay = new HashMap<>();
            if (dayTimes != null && moon != null) {
                SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                dayFormat.setTimeZone(timezone);
                for (int i = 0; i < dayTimes.length(); i++) {
                    String dayStamp = dayTimes.optString(i);
                    long dayKey = parseDay(dayFormat, dayStamp);
                    double phase = moon.optDouble(i, 0d);
                    int pct = PlacesScoring.moonIlluminationPercent(phase);
                    moonPctByDay.put(dayKey, pct);
                }
            }
            return new ForecastResponse(
                    timezone,
                    Collections.unmodifiableList(hours),
                    Collections.unmodifiableMap(moonPctByDay)
            );
        } catch (ParseException | JSONException | RuntimeException e) {
            throw new IOException("Failed to parse forecast", e);
        }
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

    public static final class ForecastHour {
        public final long timeMillis;
        public final long dayKey;
        public final double cloudCover;
        public final double precipitation;
        public final double windSpeed;

        ForecastHour(long timeMillis,
                     long dayKey,
                     double cloudCover,
                     double precipitation,
                     double windSpeed) {
            this.timeMillis = timeMillis;
            this.dayKey = dayKey;
            this.cloudCover = cloudCover;
            this.precipitation = precipitation;
            this.windSpeed = windSpeed;
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
