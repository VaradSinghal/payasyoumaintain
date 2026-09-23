package com.paymu.pricing.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response matching contracts/schemas/premium-response.schema.json.
 */
public record PremiumResponse(
        @JsonProperty("request_id") String requestId,
        @JsonProperty("vehicle_id") String vehicleId,
        @JsonProperty("policy_id") String policyId,
        @JsonProperty("base_premium") double basePremium,
        @JsonProperty("dynamic_multiplier") double dynamicMultiplier,
        @JsonProperty("final_premium") double finalPremium,
        String currency,
        @JsonProperty("discount_breakdown") List<DiscountBreakdown> discountBreakdown,
        @JsonProperty("valid_until") String validUntil,
        @JsonProperty("computed_at") String computedAt
) {}
