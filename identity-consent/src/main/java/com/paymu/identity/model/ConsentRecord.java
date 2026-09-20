package com.paymu.identity.model;

import java.time.Instant;

public record ConsentRecord(
        String vehicleId,
        boolean usageTrackingOptIn,
        boolean maintenanceTrackingOptIn,
        Instant lastUpdated
) {
    public static ConsentRecord defaultOptOut(String vehicleId) {
        return new ConsentRecord(vehicleId, false, false, Instant.now());
    }
}
