package com.paymu.pricing.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Downstream request to the risk-scoring-engine's /score/{vehicleId} endpoint.
 * Only the fields that pricing-policy needs to send; the engine accepts the full
 * ScoreRequest body.
 */
public record ScoreEngineRequest(
        @JsonProperty("service_timeline") ServiceTimeline serviceTimeline,
        @JsonProperty("trip_aggregates") java.util.List<TripAggregate> tripAggregates,
        @JsonProperty("recall_status") RecallStatus recallStatus
) {
    public record ServiceTimeline(
            @JsonProperty("vehicle_id") String vehicleId,
            @JsonProperty("total_services") int totalServices,
            @JsonProperty("latest_service_date") String latestServiceDate,
            @JsonProperty("latest_odometer_km") Integer latestOdometerKm,
            java.util.List<Object> events
    ) {}

    public record TripAggregate(
            @JsonProperty("trip_id") String tripId,
            @JsonProperty("vehicle_id") String vehicleId,
            @JsonProperty("event_count") int eventCount,
            @JsonProperty("avg_speed_kmh") double avgSpeedKmh,
            @JsonProperty("max_speed_kmh") double maxSpeedKmh,
            @JsonProperty("harsh_braking_count") int harshBrakingCount,
            @JsonProperty("hard_acceleration_count") Integer hardAccelerationCount,
            @JsonProperty("distance_km") double distanceKm,
            @JsonProperty("trip_start") String tripStart,
            @JsonProperty("trip_end") String tripEnd
    ) {}

    public record RecallStatus(
            @JsonProperty("vehicle_id") String vehicleId,
            @JsonProperty("has_open_recall") boolean hasOpenRecall,
            @JsonProperty("recall_count") int recallCount,
            @JsonProperty("recall_details") java.util.List<Object> recallDetails,
            String source,
            @JsonProperty("checked_at") String checkedAt
    ) {}
}
