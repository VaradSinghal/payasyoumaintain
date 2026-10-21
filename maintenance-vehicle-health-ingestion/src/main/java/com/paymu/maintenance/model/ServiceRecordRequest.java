package com.paymu.maintenance.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.UUID;

public record ServiceRecordRequest(
        @JsonProperty("vehicle_id") UUID vehicleId,
        @JsonProperty("service_date") LocalDate serviceDate,
        @JsonProperty("service_type") String serviceType,
        @JsonProperty("odometer_km") int odometerKm,
        @JsonProperty("notes") String notes,
        @JsonProperty("receipt_ref") String receiptRef,
        @JsonProperty("receipt_sha256") String receiptSha256
) {}
