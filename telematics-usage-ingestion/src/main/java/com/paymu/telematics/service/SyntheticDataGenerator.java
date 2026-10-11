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
 * Generates realistic synthetic telematics data for five distinct sample vehicles.
 *
 * <p>Profiles:</p>
 * <ol>
 *   <li><b>NORMAL</b> (a1b2c3d4...): ~12 trips in last 30 days, careful driving (0 harsh brakes).</li>
 *   <li><b>POOR</b> (b2c3d4e5...): aggressive driving, harsh braking and hard acceleration.</li>
 *   <li><b>CONFLICTING</b> (c3d4e5f6...): harsh braking deduction fires, hard acceleration doesn't.</li>
 *   <li><b>UNVERIFIED</b> (d4e5f6a7...): careful driving, 0 harsh brakes.</li>
 *   <li><b>INSUFFICIENT</b> (e5f6a7b8...): ZERO trips.</li>
 * </ol>
 */
@Service
public class SyntheticDataGenerator {

    public static final String[] VEHICLE_IDS = {
            "a1b2c3d4-e5f6-7890-abcd-ef1234567890", // NORMAL
            "b2c3d4e5-f6a7-8901-bcde-f12345678901", // POOR
            "c3d4e5f6-a7b8-9012-cdef-123456789012", // CONFLICTING
            "d4e5f6a7-b8c9-0123-defa-234567890123", // UNVERIFIED
            "e5f6a7b8-c9d0-1234-efab-345678901234"  // INSUFFICIENT
    };

    // Starting coordinates (Bengaluru area)
    private static final double BASE_LAT = 12.9716;
    private static final double BASE_LON = 77.5946;

    private static final double METRES_PER_DEG_LAT = 111_320.0;
    private static final double METRES_PER_DEG_LON = 111_320.0 * Math.cos(Math.toRadians(BASE_LAT));

    private final Random rng = new Random(42);

    /**
     * Generates all synthetic events for the 5 demo vehicles.
     */
    public List<UsageEvent> generate() {
        Instant now = Instant.now();
        List<UsageEvent> allEvents = new ArrayList<>();

        // 1. NORMAL: ~12 trips in the last 30 days, careful driving
        allEvents.addAll(generateNormalTrips(VEHICLE_IDS[0], now));

        // 2. POOR: aggressive driving, harsh braking fires
        allEvents.addAll(generatePoorTrips(VEHICLE_IDS[1], now));

        // 3. CONFLICTING: harsh-braking fires (>0.5), hard acceleration doesn't
        allEvents.addAll(generateConflictingTrips(VEHICLE_IDS[2], now));

        // 4. UNVERIFIED: careful driving
        allEvents.addAll(generateUnverifiedTrips(VEHICLE_IDS[3], now));

        // 5. INSUFFICIENT: ZERO trips
        // (no events added for VEHICLE_IDS[4])

        return allEvents;
    }

    // ── 1. NORMAL: 12 trips in last 30 days, careful driving ──────────────
    private List<UsageEvent> generateNormalTrips(String vehicleId, Instant now) {
        List<UsageEvent> events = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            Instant tripStart = now.minus(28 - (i * 2), ChronoUnit.DAYS).minus(i * 3, ChronoUnit.HOURS);
            String tripId = UUID.randomUUID().toString();
            events.addAll(generateTrip(tripId, vehicleId, tripStart,
                    BASE_LAT + (i * 0.002), BASE_LON + (i * 0.002),
                    35, 65, 12, 16, 0.0, 0.0, 0.4));
        }
        return events;
    }

    // ── 2. POOR: 4 trips in last 10 days, aggressive driving ──────────────
    private List<UsageEvent> generatePoorTrips(String vehicleId, Instant now) {
        List<UsageEvent> events = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Instant tripStart = now.minus(8 - (i * 2), ChronoUnit.DAYS).minus(i * 2, ChronoUnit.HOURS);
            String tripId = UUID.randomUUID().toString();
            // High braking probability with guaranteed harsh brakes and aggressive acceleration
            events.addAll(generateTrip(tripId, vehicleId, tripStart,
                    BASE_LAT + 0.02 + (i * 0.003), BASE_LON + 0.02,
                    45, 95, 15, 20, 0.35, 4.0, 0.8));
        }
        return events;
    }

    // ── 3. CONFLICTING: 4 trips, harsh braking fires (>0.5), no hard accel ─
    private List<UsageEvent> generateConflictingTrips(String vehicleId, Instant now) {
        List<UsageEvent> events = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Instant tripStart = now.minus(12 - (i * 3), ChronoUnit.DAYS).minus(i * 4, ChronoUnit.HOURS);
            String tripId = UUID.randomUUID().toString();
            // 3 of 4 trips have harsh braking (frequency = 0.75 > 0.5), normal acceleration
            double brakeProb = (i < 3) ? 0.25 : 0.0;
            events.addAll(generateTrip(tripId, vehicleId, tripStart,
                    BASE_LAT + 0.03 + (i * 0.002), BASE_LON + 0.03,
                    30, 60, 12, 16, brakeProb, 0.0, 0.2));
        }
        return events;
    }

    // ── 4. UNVERIFIED: 6 trips, careful driving ───────────────────────────
    private List<UsageEvent> generateUnverifiedTrips(String vehicleId, Instant now) {
        List<UsageEvent> events = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Instant tripStart = now.minus(18 - (i * 3), ChronoUnit.DAYS).minus(i * 3, ChronoUnit.HOURS);
            String tripId = UUID.randomUUID().toString();
            events.addAll(generateTrip(tripId, vehicleId, tripStart,
                    BASE_LAT + 0.04 + (i * 0.002), BASE_LON + 0.04,
                    35, 60, 12, 15, 0.0, 0.0, -0.3));
        }
        return events;
    }

    // ── Core trip generator ───────────────────────────────────────────────

    private List<UsageEvent> generateTrip(String tripId, String vehicleId, Instant startTime,
                                          double startLat, double startLon,
                                          double minSpeed, double maxSpeed,
                                          int minEvents, int maxEvents,
                                          double brakingProbability,
                                          double extraAccel,
                                          double bearingRadians) {
        int eventCount = minEvents + rng.nextInt(maxEvents - minEvents + 1);
        List<UsageEvent> events = new ArrayList<>(eventCount);

        double lat = startLat;
        double lon = startLon;
        double speed = minSpeed + rng.nextDouble() * (maxSpeed - minSpeed) * 0.3;

        boolean forcedHarshBrake = (brakingProbability > 0);

        for (int i = 0; i < eventCount; i++) {
            Instant timestamp = startTime.plusSeconds(i * (2 + rng.nextInt(3)));

            double targetSpeed = minSpeed + rng.nextDouble() * (maxSpeed - minSpeed);
            speed += (targetSpeed - speed) * 0.2;
            speed = Math.max(minSpeed * 0.5, Math.min(maxSpeed * 1.1, speed));

            boolean braking = (rng.nextDouble() < brakingProbability) || (forcedHarshBrake && i == eventCount / 2);
            double acceleration;
            if (braking) {
                acceleration = -(3.5 + rng.nextDouble() * 4.0); // harsh brake: <= -3.5 m/s²
                forcedHarshBrake = false; // already triggered
            } else if (extraAccel > 0) {
                acceleration = 1.0 + rng.nextDouble() * extraAccel; // hard acceleration
            } else {
                acceleration = -0.8 + rng.nextDouble() * 2.0; // normal acceleration
            }

            double distanceM = (speed / 3.6) * (2 + rng.nextInt(3));
            double jitter = (rng.nextDouble() - 0.5) * 0.3;
            lat += (distanceM * Math.cos(bearingRadians + jitter)) / METRES_PER_DEG_LAT;
            lon += (distanceM * Math.sin(bearingRadians + jitter)) / METRES_PER_DEG_LON;

            events.add(new UsageEvent(
                    UUID.randomUUID().toString(),
                    vehicleId,
                    tripId, // explicit trip_id
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
