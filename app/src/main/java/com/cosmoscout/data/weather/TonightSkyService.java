package com.cosmoscout.data.weather;

import androidx.annotation.NonNull;

import com.cosmoscout.core.Net;
import com.cosmoscout.data.places.PlacesScoring;
import com.cosmoscout.data.places.PlacesService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import okhttp3.Request;
import okhttp3.Response;

public class TonightSkyService {

    private final PlacesService placesService = new PlacesService();

    private static final String STAR_CATALOG_URL =
            "https://raw.githubusercontent.com/astronexus/hyg-database/main/hyg/v3/hyg_v37.csv.gz";
    private static volatile List<StarEntry> cachedCatalog;

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
        List<VisibleObject> objects = buildVisibleObjects(lat, lon, windows);
        return new Result(response.timezone, Collections.unmodifiableList(windows), objects);
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

    private List<VisibleObject> buildVisibleObjects(double lat,
                                                    double lon,
                                                    @NonNull List<Window> windows) {
        if (windows.isEmpty()) {
            return Collections.emptyList();
        }
        Window best = windows.get(0);
        if (best.clearPercent < 30) {
            return Collections.emptyList();
        }

        List<VisibleObject> list = new ArrayList<>();
        long time = best.startMillis;

        // Saturn (Approx RA/Dec for late 2025)
        addCustomObject(list, "Saturn", 23.8, -3.0, "planet", lat, lon, time);
        // Jupiter (Approx RA/Dec for late 2025)
        addCustomObject(list, "Jupiter", 7.2, 22.5, "planet", lat, lon, time);
        // Orion Nebula
        addCustomObject(list, "Orion Nebula", 5.59, -5.39, "nebula", lat, lon, time);

        return Collections.unmodifiableList(list);
    }

    private void addCustomObject(List<VisibleObject> list, String name, double ra, double dec, String type, double lat, double lon, long time) {
        double[] altAz = computeAltAz(lat, lon, ra * 15d, dec, time);
        if (altAz != null) {
            double altitude = altAz[0];
            if (altitude >= 10d) {
                String direction = buildDirectionLabel(altAz[1], (int) Math.round(altitude));
                String altitudeLabel = String.format(Locale.getDefault(), "%d° above horizon",
                        (int) Math.round(altitude));
                list.add(new VisibleObject(name, direction, altitudeLabel, type));
            }
        }
    }

    private double normalizeAzimuth(double value) {
        double result = value % 360d;
        if (result < 0d) {
            result += 360d;
        }
        return result;
    }

    private String buildDirectionLabel(double azimuth, int altitude) {
        String[] directions = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
        int index = (int) Math.round(azimuth / 22.5) % directions.length;
        return String.format(Locale.getDefault(), "%s · %d°", directions[index], altitude);
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
        public final List<VisibleObject> objects;

        Result(@NonNull TimeZone timezone,
               @NonNull List<Window> windows,
               @NonNull List<VisibleObject> objects) {
            this.timezone = timezone;
            this.windows = windows;
            this.objects = objects;
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

    public static final class VisibleObject {
        public final String name;
        public final String directionLabel;
        public final String altitudeLabel;
        public final String type;

        VisibleObject(@NonNull String name,
                      @NonNull String directionLabel,
                      @NonNull String altitudeLabel,
                      @NonNull String type) {
            this.name = name;
            this.directionLabel = directionLabel;
            this.altitudeLabel = altitudeLabel;
            this.type = type;
        }
    }

    private VisibleObject toVisibleObject(@NonNull StarEntry entry,
                                          double lat,
                                          double lon,
                                          long timestamp) {
        double[] altAz = computeAltAz(lat, lon, entry.raHours * 15d, entry.decDegrees, timestamp);
        if (altAz == null) {
            return null;
        }
        double altitude = altAz[0];
        if (altitude < 10d) {
            return null;
        }
        String direction = buildDirectionLabel(altAz[1], (int) Math.round(altitude));
        String altitudeLabel = String.format(Locale.getDefault(), "%d° above horizon",
                (int) Math.round(altitude));
        return new VisibleObject(entry.displayName, direction, altitudeLabel, "star");
    }

    private double[] computeAltAz(double latDeg,
                                  double lonDeg,
                                  double raDeg,
                                  double decDeg,
                                  long timestamp) {
        double jd = timestamp / 86_400_000d + 2440587.5d;
        double d = jd - 2451545.0d;
        double gmst = 280.46061837 + 360.98564736629 * d;
        double lst = (gmst + lonDeg) % 360d;
        if (lst < 0d) {
            lst += 360d;
        }
        double ha = lst - raDeg;
        if (ha < 0d) {
            ha += 360d;
        }
        double latRad = Math.toRadians(latDeg);
        double decRad = Math.toRadians(decDeg);
        double haRad = Math.toRadians(ha);

        double sinAlt = Math.sin(decRad) * Math.sin(latRad)
                + Math.cos(decRad) * Math.cos(latRad) * Math.cos(haRad);
        double alt = Math.asin(sinAlt);

        double cosAz = (Math.sin(decRad) - Math.sin(alt) * Math.sin(latRad))
                / (Math.cos(alt) * Math.cos(latRad));
        cosAz = Math.max(-1d, Math.min(1d, cosAz));
        double az = Math.acos(cosAz);
        if (Math.sin(haRad) > 0d) {
            az = (2 * Math.PI) - az;
        }

        return new double[]{Math.toDegrees(alt), Math.toDegrees(az)};
    }

    private List<StarEntry> loadCatalog() {
        if (cachedCatalog != null) {
            return cachedCatalog;
        }
        synchronized (TonightSkyService.class) {
            if (cachedCatalog != null) {
                return cachedCatalog;
            }
            List<StarEntry> entries = new ArrayList<>();
            try {
                Request request = new Request.Builder()
                        .url(STAR_CATALOG_URL)
                        .build();
                try (Response response = Net.client().newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        return Collections.emptyList();
                    }

                    InputStream is = response.body().byteStream();
                    GZIPInputStream gzip = new GZIPInputStream(is);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(gzip));

                    String line;
                    reader.readLine(); // header
                    while ((line = reader.readLine()) != null) {
                        StarEntry entry = parseStarLine(line);
                        if (entry != null) {
                            entries.add(entry);
                        }
                        if (entries.size() >= 200) {
                            break;
                        }
                    }
                }
            } catch (IOException ignored) {
            }
            cachedCatalog = Collections.unmodifiableList(entries);
            return cachedCatalog;
        }
    }

    private StarEntry parseStarLine(@NonNull String line) {
        String[] parts = line.split(",", -1);
        if (parts.length < 15) {
            return null;
        }
        try {
            double ra = parse(parts[7]);
            double dec = parse(parts[8]);
            double mag = parse(parts[13]);
            if (Double.isNaN(ra) || Double.isNaN(dec) || Double.isNaN(mag) || mag > 2.5d) {
                return null;
            }
            String name = parts[6].isEmpty() ? parts[5] : parts[6];
            if (name == null || name.trim().isEmpty()) {
                return null;
            }
            return new StarEntry(name.trim(), ra, dec);
        } catch (Exception e) {
            return null;
        }
    }

    private double parse(String text) {
        if (text == null || text.isEmpty()) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static final class StarEntry {
        final String displayName;
        final double raHours;
        final double decDegrees;

        StarEntry(String displayName, double raHours, double decDegrees) {
            this.displayName = displayName;
            this.raHours = raHours;
            this.decDegrees = decDegrees;
        }
    }
}
