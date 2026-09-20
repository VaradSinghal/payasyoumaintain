package com.paymu.telematics.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single telematics data point from an in-vehicle OBD-II / IoT device.
 * Maps 1:1 to contracts/schemas/usage-event.schema.json.
 *
 * <p>{@code tripId} is optional in the contract — when absent, the
 * {@link com.paymu.telematics.service.AggregationService} auto-segments
 * events into trips using a configurable time-gap threshold.</p>
 */
public record UsageEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("vehicle_id") String vehicleId,
        @JsonProperty("trip_id") String tripId,
        String timestamp,
        GpsCoordinates gps,
        @JsonProperty("speed_kmh") double speedKmh,
        @JsonProperty("acceleration_ms2") double accelerationMs2,
        @JsonProperty("braking_event") boolean brakingEvent
) {

    /**
     * Returns a copy of this event with the given trip ID assigned.
     * Used during auto-segmentation when the original event has no trip ID.
     */
    public UsageEvent withTripId(String newTripId) {
        return new UsageEvent(eventId, vehicleId, newTripId, timestamp, gps,
                speedKmh, accelerationMs2, brakingEvent);
    }
}
