package com.paymu.telematics.service;

import com.paymu.telematics.model.GpsCoordinates;
import com.paymu.telematics.model.UsageEvent;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Generates realistic synthetic telematics data for five sample vehicles.
 * Designed to make the service demoable without real OBD-II hardware.
 *
 * <p>Each vehicle gets 2–3 trips with varying profiles:</p>
 * <ul>
 *   <li>Vehicles 1–2: City driving (30–60 km/h, frequent braking)</li>
 *   <li>Vehicles 3–4: Highway driving (80–120 km/h, rare braking)</li>
 *   <li>Vehicle 5:    Mixed (city → highway → city)</li>
 * </ul>
 *
 * <p>GPS traces simulate smooth paths originating near Bengaluru, India
 * (12.97°N, 77.59°E) with realistic positional drift consistent with
 * the reported speed.</p>
 */
@Service
public class SyntheticDataGenerator {

    // Five fixed vehicle IDs for deterministic demos
    static final String[] VEHICLE_IDS = {
            "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
            "b2c3d4e5-f6a7-8901-bcde-f12345678901",
            "c3d4e5f6-a7b8-9012-cdef-123456789012",
            "d4e5f6a7-b8c9-0123-defa-234567890123",
            "e5f6a7b8-c9d0-1234-efab-345678901234"
    };

    // Starting coordinates (Bengaluru area)
    private static final double BASE_LAT = 12.9716;
    private static final double BASE_LON = 77.5946;

    // Approximate metres-per-degree at this latitude
    private static final double METRES_PER_DEG_LAT = 111_320.0;
    private static final double METRES_PER_DEG_LON = 111_320.0 * Math.cos(Math.toRadians(BASE_LAT));

    private final Random rng = new Random(42); // deterministic seed for reproducibility

    /**
     * Generates all synthetic events for 5 vehicles.
     *
     * @return a flat list of usage events (no trip_id set — lets the
     * aggregation service exercise its auto-segmentation logic)
     */
    public List<UsageEvent> generate() {
        Instant baseTime = Instant.now().minus(1, ChronoUnit.DAYS);
        List<UsageEvent> allEvents = new ArrayList<>();

        for (int v = 0; v < VEHICLE_IDS.length; v++) {
            String vehicleId = VEHICLE_IDS[v];
            double startLat = BASE_LAT + (v * 0.01);  // slightly different start per vehicle
            double startLon = BASE_LON + (v * 0.008);
            Instant vehicleTime = baseTime.plus(v * 10L, ChronoUnit.MINUTES);

            switch (v) {
                case 0, 1 -> allEvents.addAll(generateCityTrips(vehicleId, vehicleTime, startLat, startLon));
                case 2, 3 -> allEvents.addAll(generateHighwayTrips(vehicleId, vehicleTime, startLat, startLon));
                case 4 -> allEvents.addAll(generateMixedTrips(vehicleId, vehicleTime, startLat, startLon));
                default -> throw new IllegalStateException();
            }
        }

        return allEvents;
    }

    // ── City driving: 2 trips, 30–60 km/h, ~15% braking rate ───

    private List<UsageEvent> generateCityTrips(String vehicleId, Instant start,
                                                double lat, double lon) {
        List<UsageEvent> events = new ArrayList<>();
        // Morning commute
        events.addAll(generateTrip(vehicleId, start, lat, lon,
                30, 60, 25, 40, 0.15, 0.3));
        // Evening commute — 8 hours later (big gap → auto-segments into new trip)
        events.addAll(generateTrip(vehicleId, start.plus(8, ChronoUnit.HOURS),
                lat + 0.02, lon + 0.015, 30, 60, 20, 35, 0.18, -0.2));
        return events;
    }

    // ── Highway driving: 2 trips, 80–120 km/h, ~3% braking rate ─

    private List<UsageEvent> generateHighwayTrips(String vehicleId, Instant start,
                                                   double lat, double lon) {
        List<UsageEvent> events = new ArrayList<>();
        events.addAll(generateTrip(vehicleId, start, lat, lon,
                80, 120, 40, 60, 0.03, 0.8));
        events.addAll(generateTrip(vehicleId, start.plus(6, ChronoUnit.HOURS),
                lat + 0.05, lon + 0.04, 80, 120, 35, 55, 0.04, -0.7));
        return events;
    }

    // ── Mixed: 3 trips — city, highway, city ────────────────────

    private List<UsageEvent> generateMixedTrips(String vehicleId, Instant start,
                                                 double lat, double lon) {
        List<UsageEvent> events = new ArrayList<>();
        events.addAll(generateTrip(vehicleId, start, lat, lon,
                30, 55, 15, 25, 0.12, 0.2));
        events.addAll(generateTrip(vehicleId, start.plus(3, ChronoUnit.HOURS),
                lat + 0.01, lon + 0.01, 85, 115, 30, 50, 0.04, 0.6));
        events.addAll(generateTrip(vehicleId, start.plus(10, ChronoUnit.HOURS),
                lat + 0.03, lon - 0.01, 25, 50, 15, 30, 0.20, -0.3));
        return events;
    }

    // ── Core trip generator ─────────────────────────────────────

    private List<UsageEvent> generateTrip(String vehicleId, Instant startTime,
                                           double startLat, double startLon,
                                           double minSpeed, double maxSpeed,
                                           int minEvents, int maxEvents,
                                           double brakingProbability,
                                           double bearingRadians) {
        int eventCount = minEvents + rng.nextInt(maxEvents - minEvents + 1);
        List<UsageEvent> events = new ArrayList<>(eventCount);

        double lat = startLat;
        double lon = startLon;
        double speed = minSpeed + rng.nextDouble() * (maxSpeed - minSpeed) * 0.3; // start slow-ish

        for (int i = 0; i < eventCount; i++) {
            Instant timestamp = startTime.plusSeconds(i * (2 + rng.nextInt(3))); // 2–4s between points

            // Smooth speed changes
            double targetSpeed = minSpeed + rng.nextDouble() * (maxSpeed - minSpeed);
            speed += (targetSpeed - speed) * 0.2; // ease toward target
            speed = Math.max(minSpeed * 0.5, Math.min(maxSpeed * 1.1, speed));

            boolean braking = rng.nextDouble() < brakingProbability;
            double acceleration = braking
                    ? -(3.0 + rng.nextDouble() * 5.0) // harsh brake: -3 to -8 m/s²
                    : -1.0 + rng.nextDouble() * 3.0;   // normal: -1 to +2 m/s²

            // Move GPS position consistent with speed
            double distanceM = (speed / 3.6) * (2 + rng.nextInt(3)); // metres this interval
            double jitter = (rng.nextDouble() - 0.5) * 0.3; // slight direction variance
            lat += (distanceM * Math.cos(bearingRadians + jitter)) / METRES_PER_DEG_LAT;
            lon += (distanceM * Math.sin(bearingRadians + jitter)) / METRES_PER_DEG_LON;

            events.add(new UsageEvent(
                    UUID.randomUUID().toString(),
                    vehicleId,
                    null, // trip_id intentionally null → exercises auto-segmentation
                    timestamp.toString(),
                    new GpsCoordinates(round6(lat), round6(lon), null),
                    round2(speed),
                    round2(acceleration),
                    braking));
        }

        return events;
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    private static double round6(double v) { return Math.round(v * 1_000_000.0) / 1_000_000.0; }
}
