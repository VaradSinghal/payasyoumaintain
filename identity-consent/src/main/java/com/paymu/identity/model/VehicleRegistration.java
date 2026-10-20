package com.paymu.identity.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record VehicleRegistration(
        String vehicleId,
        String policyId,
        String rcNumber,
        String registrationDate,
        OwnerDetails ownerDetails,
        String kycStatus,
        Instant registeredAt,
        @JsonProperty("vehicle_segment_id")
        String vehicleSegmentId
) {
    public VehicleRegistration(
            String vehicleId,
            String policyId,
            String rcNumber,
            String registrationDate,
            OwnerDetails ownerDetails,
            String kycStatus,
            Instant registeredAt
    ) {
        this(vehicleId, policyId, rcNumber, registrationDate, ownerDetails, kycStatus, registeredAt, null);
    }

    public VehicleRegistration mask() {
        return new VehicleRegistration(
                vehicleId,
                policyId,
                rcNumber,
                registrationDate,
                ownerDetails != null ? ownerDetails.mask() : null,
                kycStatus,
                registeredAt,
                vehicleSegmentId
        );
    }
}
