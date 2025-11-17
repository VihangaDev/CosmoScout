package com.cosmoscout.data.places;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Pure helpers to evaluate observation windows and derive compact stats for UI layers.
 */
public final class PlacesScoring {

    private PlacesScoring() {
    }

    public enum SkyStatus {
        GOOD,
        OK,
        POOR
    }

    public static final class Weights {
        public final double cloud;
        public final double precip;
        public final double wind;
        public final double moon;

        public Weights(double cloud, double precip, double wind, double moon) {
            this.cloud = cloud;
            this.precip = precip;
            this.wind = wind;
            this.moon = moon;
        }
    }

    public static final class ScoreResult {
        public final double score;
        public final long windowStart;
        public final long windowEnd;
        public final int clearPct;
        public final int moonPct;
        public final double avgCloud;
        public final double avgWind;
        public final boolean precipFree;
        public final SkyStatus status;

        public ScoreResult(double score,
                           long windowStart,
                           long windowEnd,
                           int clearPct,
                           int moonPct,
                           double avgCloud,
                           double avgWind,
                           boolean precipFree,
                           SkyStatus status) {
            this.score = score;
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
            this.clearPct = clearPct;
            this.moonPct = moonPct;
            this.avgCloud = avgCloud;
            this.avgWind = avgWind;
            this.precipFree = precipFree;
            this.status = status;
        }
    }

    /**
     * Converts Open-Meteo moon phase value (0 new, 0.5 full) into illumination percent.
     */
    public static int moonIlluminationPercent(double phase) {
        double normalized = 1d - Math.abs(0.5d - phase) * 2d;
        return clampToPercent((int) Math.round(normalized * 100d));
    }

    @Nullable
    public static ScoreResult findBestWindow(@NonNull List<PlacesService.ForecastHour> points,
                                             @NonNull Map<Long, Integer> moonPctByDay,
                                             @NonNull Weights weights,
                                             double windCapMetersPerSecond) {
        if (points.isEmpty()) {
            return null;
        }

        ScoreResult best = null;
        for (PlacesService.ForecastHour point : points) {
            int moonPct = clampToPercent(moonPctByDay.getOrDefault(point.dayKey, 0));
            double cloudComponent = weights.cloud * (100d - point.cloudCover);
            double precipComponent = weights.precip * (point.precipitation <= 0d ? 20d : -100d);
            double windComponent = weights.wind * Math.max(0d, windCapMetersPerSecond - point.windSpeed);
            double moonComponent = weights.moon * (100d - moonPct);
            double score = cloudComponent + precipComponent + windComponent + moonComponent;

            if (best == null || score > best.score) {
                double avgCloud = point.cloudCover;
                double avgWind = point.windSpeed;
                boolean precipFree = point.precipitation <= 0d;
                best = new ScoreResult(
                        score,
                        point.timeMillis,
                        point.timeMillis + 3600_000L,
                        clampToPercent((int) Math.round(100d - avgCloud)),
                        moonPct,
                        avgCloud,
                        avgWind,
                        precipFree,
                        toStatus(score)
                );
            }
        }
        return best;
    }

    @NonNull
    public static List<Integer> buildTimeline(@NonNull List<PlacesService.ForecastHour> points,
                                              int segments) {
        if (segments <= 0) {
            return Collections.emptyList();
        }
        List<Integer> bars = new ArrayList<>(segments);
        if (points.isEmpty()) {
            for (int i = 0; i < segments; i++) {
                bars.add(0);
            }
            return bars;
        }

        if (points.size() <= segments) {
            for (PlacesService.ForecastHour point : points) {
                bars.add(clampToPercent((int) Math.round(point.cloudCover)));
            }
            int last = bars.get(bars.size() - 1);
            while (bars.size() < segments) {
                bars.add(last);
            }
            return bars;
        }

        double step = (points.size() - 1) / (double) (segments - 1);
        for (int i = 0; i < segments; i++) {
            int index = (int) Math.round(i * step);
            index = Math.max(0, Math.min(points.size() - 1, index));
            bars.add(clampToPercent((int) Math.round(points.get(index).cloudCover)));
        }
        return bars;
    }

    public static SkyStatus toStatus(double score) {
        if (score >= 80d) {
            return SkyStatus.GOOD;
        } else if (score >= 60d) {
            return SkyStatus.OK;
        }
        return SkyStatus.POOR;
    }

    private static int clampToPercent(int value) {
        if (value < 0) return 0;
        if (value > 100) return 100;
        return value;
    }
}
