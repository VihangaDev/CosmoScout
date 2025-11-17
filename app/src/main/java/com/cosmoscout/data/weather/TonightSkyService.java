package com.cosmoscout.data.weather;

import androidx.annotation.NonNull;

import com.cosmoscout.data.places.PlacesScoring;
import com.cosmoscout.data.places.PlacesService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Small helper that fetches Open-Meteo data and derives 60-minute viewing windows.
 */
public class TonightSkyService {

    private final PlacesService placesService = new PlacesService();

    public Result fetchTonight(double lat, double lon) throws IOException {
        PlacesService.ForecastResponse response = placesService.fetchForecast(lat, lon);
        long now = System.currentTimeMillis();
        long horizon = now + TimeUnit.HOURS.toMillis(24);

        List<Window> windows = new ArrayList<>();
        for (PlacesService.ForecastHour hour : response.hours) {
            if (hour.timeMillis < now || hour.timeMillis > horizon) {
                continue;
            }
            Window window = toWindow(hour, response.moonPctByDay);
            windows.add(window);
        }
        Collections.sort(windows, (a, b) -> Double.compare(b.score, a.score));
        return new Result(response.timezone, Collections.unmodifiableList(windows));
    }

    @NonNull
    private Window toWindow(@NonNull PlacesService.ForecastHour hour,
                            @NonNull Map<Long, Integer> moonPctByDay) {
        int clearPct = clampPercent((int) Math.round(100d - hour.cloudCover));
        int moonPct = clampPercent(moonPctByDay.getOrDefault(hour.dayKey, 0));
        double visibility = Double.isNaN(hour.visibilityKm) ? 0d : hour.visibilityKm;
        double visibilityScore = Math.min(1d, visibility / 40d) * 100d;
        double moonScore = (100d - moonPct);
        double windPenalty = Math.min(hour.windSpeed, 20d) * 2d;
        double precipPenalty = hour.precipitation > 0.05d ? 25d : 0d;
        double score = clearPct * 0.6d + visibilityScore * 0.15d + moonScore * 0.15d;
        score -= windPenalty;
        score -= precipPenalty;
        score = Math.max(0d, Math.min(100d, score));

        PlacesScoring.SkyStatus status = PlacesScoring.toStatus(score);
        return new Window(
                hour.timeMillis,
                hour.timeMillis + TimeUnit.HOURS.toMillis(1),
                clearPct,
                moonPct,
                round1(visibility),
                round1(hour.windSpeed),
                hour.precipitation,
                score,
                status
        );
    }

    private int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private double round1(double value) {
        return Math.round(value * 10d) / 10d;
    }

    public static final class Result {
        public final TimeZone timezone;
        public final List<Window> windows;

        Result(@NonNull TimeZone timezone, @NonNull List<Window> windows) {
            this.timezone = timezone;
            this.windows = windows;
        }
    }

    public static final class Window {
        public final long startMillis;
        public final long endMillis;
        public final int clearPercent;
        public final int moonPercent;
        public final double visibilityKm;
        public final double windSpeed;
        public final double precipitationMm;
        public final double score;
        public final PlacesScoring.SkyStatus status;

        Window(long startMillis,
               long endMillis,
               int clearPercent,
               int moonPercent,
               double visibilityKm,
               double windSpeed,
               double precipitationMm,
               double score,
               PlacesScoring.SkyStatus status) {
            this.startMillis = startMillis;
            this.endMillis = endMillis;
            this.clearPercent = clearPercent;
            this.moonPercent = moonPercent;
            this.visibilityKm = visibilityKm;
            this.windSpeed = windSpeed;
            this.precipitationMm = precipitationMm;
            this.score = score;
            this.status = status;
        }
    }
}

