package com.paymu.maintenance.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Per-vehicle service timeline: an ordered history of all maintenance events.
 */
public record ServiceTimeline(
        @JsonProperty("vehicle_id") String vehicleId,
        @JsonProperty("total_services") int totalServices,
        @JsonProperty("latest_service_date") String latestServiceDate,
        @JsonProperty("latest_odometer_km") Integer latestOdometerKm,
        List<MaintenanceEvent> events
) {}
