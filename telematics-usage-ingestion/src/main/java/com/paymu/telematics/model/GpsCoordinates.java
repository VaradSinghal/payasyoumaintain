package com.paymu.telematics.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GPS coordinates captured at a single telematics data point.
 * Maps to the "gps" nested object in usage-event.schema.json.
 */
public record GpsCoordinates(
        double latitude,
        double longitude,
        @JsonProperty("altitude_m") Double altitudeM
) {}
