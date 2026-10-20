package com.paymu.claims.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VehicleRegistrationResponse(
        @JsonProperty("vehicle_id") String vehicleId,
        @JsonProperty("policy_id") String policyId,
        @JsonProperty("rc_number") String rcNumber,
        @JsonProperty("registration_date") String registrationDate,
        @JsonProperty("kyc_status") String kycStatus,
        @JsonProperty("registered_at") String registeredAt
) {}
