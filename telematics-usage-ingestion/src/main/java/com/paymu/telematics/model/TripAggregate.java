package com.paymu.telematics.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Computed aggregates for a single trip.
 * Produced by the {@link com.paymu.telematics.service.AggregationService}.
 */
public record TripAggregate(
        @JsonProperty("trip_id") String tripId,
        @JsonProperty("vehicle_id") String vehicleId,
        @JsonProperty("event_count") int eventCount,
        @JsonProperty("avg_speed_kmh") double avgSpeedKmh,
        @JsonProperty("max_speed_kmh") double maxSpeedKmh,
        @JsonProperty("harsh_braking_count") int harshBrakingCount,
        @JsonProperty("distance_km") double distanceKm,
        @JsonProperty("trip_start") String tripStart,
        @JsonProperty("trip_end") String tripEnd
) {}
