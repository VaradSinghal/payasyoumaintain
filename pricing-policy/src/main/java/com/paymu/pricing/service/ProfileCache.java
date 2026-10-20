package com.paymu.pricing.service;

import com.paymu.pricing.model.VehicleProfileResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory cache for successful vehicle profiles fetched from identity-consent.
 */
@Component
public class ProfileCache {

    private record CachedProfile(VehicleProfileResponse profile, Instant expiresAt) {}

    private final ConcurrentMap<String, CachedProfile> cache = new ConcurrentHashMap<>();
    private final long ttlMinutes;
    private final Clock clock;

    public ProfileCache(@Value("${pricing.profile-cache-ttl-minutes:60}") long ttlMinutes, Clock clock) {
        this.ttlMinutes = ttlMinutes;
        this.clock = clock;
    }

    public Optional<VehicleProfileResponse> get(String vehicleId) {
        if (vehicleId == null) {
            return Optional.empty();
        }
        CachedProfile entry = cache.get(vehicleId);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt().isBefore(clock.instant())) {
            cache.remove(vehicleId, entry);
            return Optional.empty();
        }
        return Optional.of(entry.profile());
    }

    public void put(String vehicleId, VehicleProfileResponse profile) {
        if (vehicleId != null && profile != null) {
            Instant expiresAt = clock.instant().plus(ttlMinutes, ChronoUnit.MINUTES);
            cache.put(vehicleId, new CachedProfile(profile, expiresAt));
        }
    }

    public void clear() {
        cache.clear();
    }
}
