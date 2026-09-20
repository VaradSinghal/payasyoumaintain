package com.paymu.telematics.service;

import com.paymu.telematics.model.AggregationResult;
import com.paymu.telematics.model.GpsCoordinates;
import com.paymu.telematics.model.TripAggregate;
import com.paymu.telematics.model.UsageEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AggregationService}.
 * Covers average speed, harsh-braking count, Haversine distance,
 * time-gap auto-segmentation, and edge cases.
 */
class AggregationServiceTest {

    private AggregationService aggregationService;

    @BeforeEach
    void setUp() {
        aggregationService = new AggregationService(5); // 5-minute gap
    }

    // ── Helpers ─────────────────────────────────────────────────

    private UsageEvent event(String tripId, String vehicleId, Instant ts,
                              double lat, double lon, double speed, boolean braking) {
        return new UsageEvent(
                UUID.randomUUID().toString(),
                vehicleId,
                tripId,
                ts.toString(),
                new GpsCoordinates(lat, lon, null),
                speed,
                braking ? -5.0 : 0.5,
                braking);
    }

    // ── Average speed ───────────────────────────────────────────

    @Nested
    @DisplayName("Average speed computation")
    class AvgSpeed {

        @Test
        @DisplayName("Average speed is arithmetic mean of all event speeds")
        void computesCorrectly() {
            String tripId = UUID.randomUUID().toString();
            String vehicleId = UUID.randomUUID().toString();
            Instant t = Instant.parse("2026-09-20T10:00:00Z");

            List<UsageEvent> events = List.of(
                    event(tripId, vehicleId, t, 12.97, 77.59, 40.0, false),
                    event(tripId, vehicleId, t.plusSeconds(3), 12.9701, 77.5901, 60.0, false),
                    event(tripId, vehicleId, t.plusSeconds(6), 12.9702, 77.5902, 80.0, false));

            AggregationResult result = aggregationService.processAndAggregate(events);
            assertEquals(1, result.aggregates().size());

            TripAggregate agg = result.aggregates().getFirst();
            assertEquals(60.0, agg.avgSpeedKmh(), 0.01, "Avg of 40, 60, 80 should be 60");
            assertEquals(80.0, agg.maxSpeedKmh(), 0.01);
        }
    }

    // ── Harsh braking count ─────────────────────────────────────

    @Nested
    @DisplayName("Harsh braking count")
    class HarshBraking {

        @Test
        @DisplayName("Counts events where braking_event is true")
        void countsCorrectly() {
            String tripId = UUID.randomUUID().toString();
            String vehicleId = UUID.randomUUID().toString();
            Instant t = Instant.parse("2026-09-20T10:00:00Z");

            List<UsageEvent> events = List.of(
                    event(tripId, vehicleId, t, 12.97, 77.59, 50, false),
                    event(tripId, vehicleId, t.plusSeconds(3), 12.9701, 77.5901, 45, true),
                    event(tripId, vehicleId, t.plusSeconds(6), 12.9702, 77.5902, 55, false),
                    event(tripId, vehicleId, t.plusSeconds(9), 12.9703, 77.5903, 40, true),
                    event(tripId, vehicleId, t.plusSeconds(12), 12.9704, 77.5904, 60, true));

            AggregationResult result = aggregationService.processAndAggregate(events);
            TripAggregate agg = result.aggregates().getFirst();
            assertEquals(3, agg.harshBrakingCount());
        }

        @Test
        @DisplayName("Returns zero when no braking events")
        void zeroBraking() {
            String tripId = UUID.randomUUID().toString();
            String vehicleId = UUID.randomUUID().toString();
            Instant t = Instant.parse("2026-09-20T10:00:00Z");

            List<UsageEvent> events = List.of(
                    event(tripId, vehicleId, t, 12.97, 77.59, 50, false),
                    event(tripId, vehicleId, t.plusSeconds(3), 12.9701, 77.5901, 55, false));

            AggregationResult result = aggregationService.processAndAggregate(events);
            assertEquals(0, result.aggregates().getFirst().harshBrakingCount());
        }
    }

    // ── Haversine distance ──────────────────────────────────────

    @Nested
    @DisplayName("Haversine distance calculation")
    class HaversineDistance {

        @Test
        @DisplayName("Known reference: ~111 km per degree of latitude at equator")
        void degreeOfLatitude() {
            double dist = AggregationService.haversineKm(0.0, 0.0, 1.0, 0.0);
            assertEquals(111.19, dist, 0.5, "1° latitude at equator ≈ 111.19 km");
        }

        @Test
        @DisplayName("Same point yields zero distance")
        void samePoint() {
            double dist = AggregationService.haversineKm(12.97, 77.59, 12.97, 77.59);
            assertEquals(0.0, dist, 0.001);
        }

        @Test
        @DisplayName("Bengaluru to Chennai ≈ 290 km")
        void bengaluruToChennai() {
            // Bengaluru: 12.9716°N, 77.5946°E — Chennai: 13.0827°N, 80.2707°E
            double dist = AggregationService.haversineKm(12.9716, 77.5946, 13.0827, 80.2707);
            assertEquals(290.0, dist, 10.0, "Bengaluru to Chennai ≈ 290 km");
        }

        @Test
        @DisplayName("Cumulative distance is sum of segment distances")
        void cumulativeDistanceInAggregate() {
            String tripId = UUID.randomUUID().toString();
            String vehicleId = UUID.randomUUID().toString();
            Instant t = Instant.parse("2026-09-20T10:00:00Z");

            // Three points: 0,0 → 0,1 → 0,2 (each ≈ 111 km at equator)
            List<UsageEvent> events = List.of(
                    event(tripId, vehicleId, t, 0.0, 0.0, 50, false),
                    event(tripId, vehicleId, t.plusSeconds(3), 0.0, 1.0, 50, false),
                    event(tripId, vehicleId, t.plusSeconds(6), 0.0, 2.0, 50, false));

            AggregationResult result = aggregationService.processAndAggregate(events);
            TripAggregate agg = result.aggregates().getFirst();
            assertEquals(222.39, agg.distanceKm(), 1.0, "Two segments of ~111 km each");
        }
    }

    // ── Time-gap auto-segmentation ──────────────────────────────

    @Nested
    @DisplayName("Auto-segmentation by time gap")
    class AutoSegmentation {

        @Test
        @DisplayName("Events within 5 minutes stay in the same trip")
        void noGap_singleTrip() {
            String vehicleId = UUID.randomUUID().toString();
            Instant t = Instant.parse("2026-09-20T10:00:00Z");

            // All events within 4 minutes — no gap
            List<UsageEvent> events = List.of(
                    event(null, vehicleId, t, 12.97, 77.59, 50, false),
                    event(null, vehicleId, t.plusSeconds(60), 12.9701, 77.5901, 55, false),
                    event(null, vehicleId, t.plusSeconds(120), 12.9702, 77.5902, 52, false),
                    event(null, vehicleId, t.plusSeconds(240), 12.9704, 77.5904, 48, false));

            AggregationResult result = aggregationService.processAndAggregate(events);
            assertEquals(1, result.aggregates().size(), "All events should be one trip");
            assertEquals(4, result.aggregates().getFirst().eventCount());
        }

        @Test
        @DisplayName("Events with >5 min gap are split into separate trips")
        void gap_splitTrips() {
            String vehicleId = UUID.randomUUID().toString();
            Instant t = Instant.parse("2026-09-20T08:00:00Z");

            List<UsageEvent> events = List.of(
                    // Morning commute
                    event(null, vehicleId, t, 12.97, 77.59, 40, false),
                    event(null, vehicleId, t.plusSeconds(60), 12.9701, 77.5901, 45, false),
                    event(null, vehicleId, t.plusSeconds(120), 12.9702, 77.5902, 50, true),
                    // --- 8-hour gap (parked all day) ---
                    event(null, vehicleId, t.plusSeconds(8 * 3600), 12.98, 77.60, 35, false),
                    event(null, vehicleId, t.plusSeconds(8 * 3600 + 60), 12.9801, 77.6001, 42, false));

            AggregationResult result = aggregationService.processAndAggregate(events);
            assertEquals(2, result.aggregates().size(), "Should split into 2 trips");

            // First trip: 3 events, second trip: 2 events
            List<TripAggregate> sorted = result.aggregates().stream()
                    .sorted((a, b) -> a.tripStart().compareTo(b.tripStart()))
                    .toList();
            assertEquals(3, sorted.get(0).eventCount());
            assertEquals(2, sorted.get(1).eventCount());
        }

        @Test
        @DisplayName("Exactly 5-minute gap triggers a new trip")
        void exactGap_splits() {
            String vehicleId = UUID.randomUUID().toString();
            Instant t = Instant.parse("2026-09-20T10:00:00Z");

            List<UsageEvent> events = List.of(
                    event(null, vehicleId, t, 12.97, 77.59, 50, false),
                    event(null, vehicleId, t.plusSeconds(300), 12.9701, 77.5901, 55, false)); // exactly 5 min

            AggregationResult result = aggregationService.processAndAggregate(events);
            assertEquals(2, result.aggregates().size(), "Exactly 5-min gap should split");
        }

        @Test
        @DisplayName("All auto-segmented events receive a non-null trip_id")
        void assignsTripIds() {
            String vehicleId = UUID.randomUUID().toString();
            Instant t = Instant.parse("2026-09-20T10:00:00Z");

            List<UsageEvent> events = List.of(
                    event(null, vehicleId, t, 12.97, 77.59, 50, false),
                    event(null, vehicleId, t.plusSeconds(30), 12.9701, 77.5901, 55, false));

            AggregationResult result = aggregationService.processAndAggregate(events);
            for (UsageEvent e : result.processedEvents()) {
                assertNotNull(e.tripId(), "Every processed event should have a trip_id");
                assertFalse(e.tripId().isBlank());
            }
        }
    }

    // ── Edge cases ──────────────────────────────────────────────

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Empty event list produces empty result")
        void emptyList() {
            AggregationResult result = aggregationService.processAndAggregate(List.of());
            assertTrue(result.aggregates().isEmpty());
            assertTrue(result.processedEvents().isEmpty());
        }

        @Test
        @DisplayName("Single event produces valid aggregate")
        void singleEvent() {
            String tripId = UUID.randomUUID().toString();
            String vehicleId = UUID.randomUUID().toString();
            Instant t = Instant.parse("2026-09-20T10:00:00Z");

            List<UsageEvent> events = List.of(
                    event(tripId, vehicleId, t, 12.97, 77.59, 50, true));

            AggregationResult result = aggregationService.processAndAggregate(events);
            assertEquals(1, result.aggregates().size());

            TripAggregate agg = result.aggregates().getFirst();
            assertEquals(1, agg.eventCount());
            assertEquals(50.0, agg.avgSpeedKmh(), 0.01);
            assertEquals(50.0, agg.maxSpeedKmh(), 0.01);
            assertEquals(1, agg.harshBrakingCount());
            assertEquals(0.0, agg.distanceKm(), 0.001, "Single point = zero distance");
        }

        @Test
        @DisplayName("Mixed events: some with trip_id, some without")
        void mixedTripIds() {
            String vehicleId = UUID.randomUUID().toString();
            String knownTripId = UUID.randomUUID().toString();
            Instant t = Instant.parse("2026-09-20T10:00:00Z");

            List<UsageEvent> events = List.of(
                    // Two events with explicit trip_id
                    event(knownTripId, vehicleId, t, 12.97, 77.59, 50, false),
                    event(knownTripId, vehicleId, t.plusSeconds(3), 12.9701, 77.5901, 55, false),
                    // Two events without trip_id (auto-segmented)
                    event(null, vehicleId, t.plusSeconds(600), 12.98, 77.60, 40, false),
                    event(null, vehicleId, t.plusSeconds(603), 12.9801, 77.6001, 45, false));

            AggregationResult result = aggregationService.processAndAggregate(events);
            assertEquals(2, result.aggregates().size(), "Should produce 2 trips");

            // One aggregate should have the known trip_id
            boolean hasKnownTrip = result.aggregates().stream()
                    .anyMatch(a -> knownTripId.equals(a.tripId()));
            assertTrue(hasKnownTrip, "Should preserve the explicit trip_id");
        }
    }
}
