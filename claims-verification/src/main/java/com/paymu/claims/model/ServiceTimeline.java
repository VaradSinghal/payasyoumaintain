package com.paymu.claims.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Mirrors the ServiceTimeline shape returned by maintenance-vehicle-health-ingestion. */
public record ServiceTimeline(
        @JsonProperty("vehicle_id") String vehicleId,
        @JsonProperty("total_services") int totalServices,
        @JsonProperty("latest_service_date") String latestServiceDate,
        @JsonProperty("latest_odometer_km") Integer latestOdometerKm,
        List<MaintenanceEvent> events
) {
    public record MaintenanceEvent(
            @JsonProperty("event_id") String eventId,
            @JsonProperty("vehicle_id") String vehicleId,
            @JsonProperty("service_date") String serviceDate,
            @JsonProperty("service_type") String serviceType,
            @JsonProperty("odometer_km") int odometerKm,
            String source,
            String notes
    ) {}
}
