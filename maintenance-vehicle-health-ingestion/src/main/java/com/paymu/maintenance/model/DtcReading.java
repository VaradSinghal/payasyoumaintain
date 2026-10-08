package com.paymu.maintenance.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DtcReading(
        @JsonProperty("vehicle_id") String vehicleId,
        @JsonProperty("timestamp") String timestamp,
        @JsonProperty("dtc_codes") List<String> dtcCodes,
        @JsonProperty("confirmed") boolean confirmed,
        @JsonProperty("source") String source,
        @JsonProperty("device_id") String deviceId
) {
}
