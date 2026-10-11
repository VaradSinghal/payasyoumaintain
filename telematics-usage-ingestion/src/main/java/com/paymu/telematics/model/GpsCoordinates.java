package com.paymu.telematics.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GPS coordinates captured at a single telematics data point.
 * Maps to the "gps" nested object in usage-event.schema.json.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GpsCoordinates(
        double latitude,
        double longitude,
        @JsonProperty("altitude_m") Double altitudeM
) {}
