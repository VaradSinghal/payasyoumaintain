package com.paymu.maintenance.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ServiceRecordResponse(
        @JsonProperty("event_id") UUID eventId,
        @JsonProperty("vehicle_id") UUID vehicleId,
        @JsonProperty("service_date") LocalDate serviceDate,
        @JsonProperty("service_type") String serviceType,
        @JsonProperty("odometer_km") int odometerKm,
        @JsonProperty("source") String source,
        @JsonProperty("stored") boolean stored,
        @JsonProperty("verification") VerificationResult verification,
        @JsonProperty("recorded_at") Instant recordedAt
) {}
