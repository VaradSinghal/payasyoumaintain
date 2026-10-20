package com.paymu.pricing.service;

import com.paymu.pricing.model.VehicleProfileResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProfileCache TTL Tests")
class ProfileCacheTest {

    static class MutableClock extends Clock {
        private final ZoneId zone;
        private final AtomicReference<Instant> current;

        MutableClock(Instant initial, ZoneId zone) {
            this.current = new AtomicReference<>(initial);
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(current.get(), zone);
        }

        @Override
        public Instant instant() {
            return current.get();
        }

        public void advance(Duration duration) {
            current.updateAndGet(i -> i.plus(duration));
        }
    }

    @Test
    @DisplayName("ProfileCache TTL: cached profile valid before TTL and expires after TTL")
    void cacheTtl_expiresAfterTtl() {
        Instant t0 = Instant.parse("2026-10-20T10:00:00Z");
        MutableClock clock = new MutableClock(t0, ZoneId.of("Asia/Kolkata"));

        // Cache with 60 minutes TTL
        ProfileCache cache = new ProfileCache(60, clock);

        VehicleProfileResponse profile = new VehicleProfileResponse(
                "v-cache-1", "pol-1", LocalDate.of(2024, 1, 1), "sedan_mid",
                "VIN1", "MH1", "Hyundai", "Verna", "SX", 2024
        );

        // Put profile at T0
        cache.put("v-cache-1", profile);

        // At T0 + 30 minutes (within TTL): hit
        clock.advance(Duration.ofMinutes(30));
        Optional<VehicleProfileResponse> hit30 = cache.get("v-cache-1");
        assertTrue(hit30.isPresent(), "Profile must be present at 30 min (TTL is 60 min)");
        assertEquals("sedan_mid", hit30.get().vehicleSegmentId());

        // At T0 + 59 minutes (still within TTL): hit
        clock.advance(Duration.ofMinutes(29));
        Optional<VehicleProfileResponse> hit59 = cache.get("v-cache-1");
        assertTrue(hit59.isPresent(), "Profile must be present at 59 min (TTL is 60 min)");

        // At T0 + 61 minutes (older than TTL): expired!
        clock.advance(Duration.ofMinutes(2));
        Optional<VehicleProfileResponse> expired = cache.get("v-cache-1");
        assertTrue(expired.isEmpty(), "Profile older than TTL must expire and return empty");
    }

    @Test
    @DisplayName("ProfileCache: clear empties cache and null handling is safe")
    void cache_clearAndNullHandling() {
        Instant t0 = Instant.parse("2026-10-20T10:00:00Z");
        MutableClock clock = new MutableClock(t0, ZoneId.of("Asia/Kolkata"));
        ProfileCache cache = new ProfileCache(60, clock);

        assertTrue(cache.get(null).isEmpty());
        assertTrue(cache.get("non-existent").isEmpty());

        VehicleProfileResponse profile = new VehicleProfileResponse(
                "v-2", "pol-2", LocalDate.of(2024, 1, 1), "sedan_mid",
                "VIN2", "MH2", "Hyundai", "Verna", "SX", 2024
        );
        cache.put("v-2", profile);
        assertTrue(cache.get("v-2").isPresent());

        cache.clear();
        assertTrue(cache.get("v-2").isEmpty());
    }
}
