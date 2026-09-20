package com.paymu.identity.model;

import java.time.Instant;

public record VehicleRegistration(
        String vehicleId,
        String rcNumber,
        String registrationDate,
        OwnerDetails ownerDetails,
        String kycStatus,
        Instant registeredAt
) {}
