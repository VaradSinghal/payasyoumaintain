package com.paymu.advisory.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Maps the response from identity-consent GET /vehicles/{id}/consent.
 * Explicitly annotates snake_case JSON properties to avoid naming strategy discrepancies.
 */
public record ConsentResponse(
        @JsonProperty("vehicle_id") String vehicleId,
        @JsonProperty("usage_tracking_opt_in") Boolean usageTrackingOptIn,
        @JsonProperty("maintenance_tracking_opt_in") Boolean maintenanceTrackingOptIn,
        @JsonProperty("updated_at") String updatedAt
) {}
