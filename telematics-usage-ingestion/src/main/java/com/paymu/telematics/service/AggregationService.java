package com.paymu.telematics.service;

import com.paymu.telematics.model.AggregationResult;
import com.paymu.telematics.model.TripAggregate;
import com.paymu.telematics.model.UsageEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Groups telematics events into trips and computes per-trip aggregates.
 *
 * <h3>Trip segmentation</h3>
 * <ul>
 *   <li>Events that already carry a {@code trip_id} are grouped by it.</li>
 *   <li>Events without a {@code trip_id} are auto-segmented: if consecutive
 *       events (sorted by timestamp) are more than
 *       {@code telematics.trip-gap-minutes} apart, a new synthetic trip is
 *       started.  This prevents a single offline-sync batch from producing
 *       a nonsense aggregate that lumps unrelated journeys together.</li>
 * </ul>
 */
@Service
public class AggregationService {

    private static final double EARTH_RADIUS_KM = 6_371.0;

    private final long tripGapMinutes;

    public AggregationService(@Value("${telematics.trip-gap-minutes:5}") long tripGapMinutes) {
        this.tripGapMinutes = tripGapMinutes;
    }

    /**
     * Assigns trip IDs where missing, then computes per-trip aggregates.
     *
     * @param events validated and deserialized usage events
     * @return processed events (with trip IDs filled in) and their aggregates
     */
    public AggregationResult processAndAggregate(List<UsageEvent> events) {
        if (events.isEmpty()) {
            return new AggregationResult(List.of(), List.of());
        }

        // ── 1. Separate events with / without trip_id ───────────
        Map<String, List<UsageEvent>> withTripId = events.stream()
                .filter(e -> e.tripId() != null && !e.tripId().isBlank())
                .collect(Collectors.groupingBy(UsageEvent::tripId));

        List<UsageEvent> withoutTripId = events.stream()
                .filter(e -> e.tripId() == null || e.tripId().isBlank())
                .toList();

        // ── 2. Auto-segment events without trip_id ──────────────
        Map<String, List<UsageEvent>> autoSegmented = autoSegment(withoutTripId);

        // ── 3. Merge all trip groups ────────────────────────────
        Map<String, List<UsageEvent>> allTrips = new LinkedHashMap<>(withTripId);
        allTrips.putAll(autoSegmented);

        // ── 4. Flatten processed events & compute aggregates ────
        List<UsageEvent> processedEvents = new ArrayList<>();
        List<TripAggregate> aggregates = new ArrayList<>();

        for (Map.Entry<String, List<UsageEvent>> entry : allTrips.entrySet()) {
            processedEvents.addAll(entry.getValue());
            aggregates.add(computeAggregate(entry.getKey(), entry.getValue()));
        }

        return new AggregationResult(processedEvents, aggregates);
    }

    // ── Auto-segmentation ───────────────────────────────────────

    Map<String, List<UsageEvent>> autoSegment(List<UsageEvent> events) {
        if (events.isEmpty()) {
            return Map.of();
        }

        List<UsageEvent> sorted = events.stream()
                .sorted(Comparator.comparing(UsageEvent::timestamp))
                .toList();

        Map<String, List<UsageEvent>> segments = new LinkedHashMap<>();
        String currentTripId = UUID.randomUUID().toString();
        List<UsageEvent> currentSegment = new ArrayList<>();
        currentSegment.add(sorted.getFirst().withTripId(currentTripId));

        for (int i = 1; i < sorted.size(); i++) {
            Instant prev = Instant.parse(sorted.get(i - 1).timestamp());
            Instant curr = Instant.parse(sorted.get(i).timestamp());

            if (Duration.between(prev, curr).toMinutes() >= tripGapMinutes) {
                // Gap detected → close current segment, start a new one
                segments.put(currentTripId, List.copyOf(currentSegment));
                currentTripId = UUID.randomUUID().toString();
                currentSegment = new ArrayList<>();
            }
            currentSegment.add(sorted.get(i).withTripId(currentTripId));
        }
        segments.put(currentTripId, List.copyOf(currentSegment));

        return segments;
    }

    // ── Per-trip aggregate computation ──────────────────────────

    TripAggregate computeAggregate(String tripId, List<UsageEvent> events) {
        List<UsageEvent> sorted = events.stream()
                .sorted(Comparator.comparing(UsageEvent::timestamp))
                .toList();

        String vehicleId = sorted.getFirst().vehicleId();
        int eventCount = sorted.size();

        double totalSpeed = 0;
        double maxSpeed = 0;
        int harshBrakingCount = 0;
        double totalDistanceKm = 0;

        for (int i = 0; i < sorted.size(); i++) {
            UsageEvent e = sorted.get(i);
            totalSpeed += e.speedKmh();
            maxSpeed = Math.max(maxSpeed, e.speedKmh());

            if (e.brakingEvent()) {
                harshBrakingCount++;
            }

            if (i > 0) {
                UsageEvent prev = sorted.get(i - 1);
                totalDistanceKm += haversineKm(
                        prev.gps().latitude(), prev.gps().longitude(),
                        e.gps().latitude(), e.gps().longitude());
            }
        }

        double avgSpeed = eventCount > 0 ? totalSpeed / eventCount : 0;

        return new TripAggregate(
                tripId,
                vehicleId,
                eventCount,
                round2(avgSpeed),
                round2(maxSpeed),
                harshBrakingCount,
                round2(totalDistanceKm),
                sorted.getFirst().timestamp(),
                sorted.getLast().timestamp());
    }

    // ── Haversine formula ───────────────────────────────────────

    /**
     * Computes the great-circle distance between two GPS points in kilometres.
     */
    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
