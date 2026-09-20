package com.paymu.identity.model;

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
        OwnerDetails ownerDetails
) {}
