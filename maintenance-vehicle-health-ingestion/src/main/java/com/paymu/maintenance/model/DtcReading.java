package com.paymu.maintenance.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DtcReading(
        @JsonProperty("vehicle_id") String vehicleId,
        @JsonProperty("timestamp") String timestamp,
        @JsonProperty("confirmed_codes") List<String> confirmedCodes,
        @JsonProperty("pending_codes") List<String> pendingCodes,
        @JsonProperty("source") String source,
        @JsonProperty("device_id") String deviceId
) {
}
