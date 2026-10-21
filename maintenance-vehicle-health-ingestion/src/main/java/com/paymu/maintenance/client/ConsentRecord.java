package com.paymu.maintenance.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ConsentRecord(
        @JsonProperty("vehicle_id") UUID vehicleId,
        @JsonProperty("usage_tracking_opt_in") Boolean usageTrackingOptIn,
        @JsonProperty("maintenance_tracking_opt_in") Boolean maintenanceTrackingOptIn
) {}
