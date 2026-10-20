package com.paymu.identity.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;

public record VehicleRegistrationRequest(
        @NotBlank(message = "RC number is required")
        String rcNumber,
        
        @NotNull(message = "Registration date is required")
        @PastOrPresent(message = "Registration date cannot be in the future")
        LocalDate registrationDate,
        
        @Valid
        @NotNull(message = "Owner details are required")
        OwnerDetails ownerDetails,

        @JsonProperty("vehicle_segment_id")
        String vehicleSegmentId
) {
    public VehicleRegistrationRequest(String rcNumber, LocalDate registrationDate, OwnerDetails ownerDetails) {
        this(rcNumber, registrationDate, ownerDetails, null);
    }
}
