package com.paymu.pricing.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Actuarial pricing basis details returned with the premium response.
 */
public record PricingBasis(
        @JsonProperty("source") String source,
        @JsonProperty("vehicle_segment_id") String vehicleSegmentId,
        @JsonProperty("ex_showroom_price_inr") long exShowroomPriceInr,
        @JsonProperty("vehicle_age_years") int vehicleAgeYears,
        @JsonProperty("depreciation_factor") double depreciationFactor,
        @JsonProperty("idv") double idv,
        @JsonProperty("ncb_pct") double ncbPct
) {}
