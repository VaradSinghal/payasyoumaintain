package com.paymu.maintenance.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VehicleRegistrationResponse(
        @JsonProperty("vehicle_id") UUID vehicleId,
        @JsonProperty("registration_date") LocalDate registrationDate,
        @JsonProperty("policy_id") String policyId
) {}
