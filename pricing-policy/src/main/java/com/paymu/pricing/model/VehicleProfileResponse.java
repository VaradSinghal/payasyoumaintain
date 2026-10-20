package com.paymu.pricing.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * Vehicle profile payload retrieved from identity-consent.
 */
public record VehicleProfileResponse(
        @JsonProperty("vehicle_id") String vehicleId,
        @JsonProperty("policy_id") String policyId,
        @JsonProperty("registration_date") LocalDate registrationDate,
        @JsonProperty("vehicle_segment_id") String vehicleSegmentId,
        @JsonProperty("vin") String vin,
        @JsonProperty("registration_number") String registrationNumber,
        @JsonProperty("make") String make,
        @JsonProperty("model") String model,
        @JsonProperty("variant") String variant,
        @JsonProperty("year_of_manufacture") Integer yearOfManufacture
) {}
