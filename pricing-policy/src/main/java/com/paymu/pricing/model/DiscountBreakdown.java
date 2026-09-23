package com.paymu.pricing.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One line item in the discount_breakdown array in premium-response.schema.json.
 *
 * @param reason        human-readable label (e.g. "safe_driving_discount")
 * @param adjustmentPct negative = discount, positive = surcharge, expressed as a percentage
 */
public record DiscountBreakdown(
        String reason,
        @JsonProperty("adjustment_pct") double adjustmentPct
) {}
