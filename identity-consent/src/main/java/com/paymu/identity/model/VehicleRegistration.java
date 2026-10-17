package com.paymu.identity.model;

import java.time.Instant;

public record VehicleRegistration(
        String vehicleId,
        String policyId,
        String rcNumber,
        String registrationDate,
        OwnerDetails ownerDetails,
        String kycStatus,
        Instant registeredAt
) {
    public VehicleRegistration mask() {
        return new VehicleRegistration(
                vehicleId,
                policyId,
                rcNumber,
                registrationDate,
                ownerDetails != null ? ownerDetails.mask() : null,
                kycStatus,
                registeredAt
        );
    }
}
