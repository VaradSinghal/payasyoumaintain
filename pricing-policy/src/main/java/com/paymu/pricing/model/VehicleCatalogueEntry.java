package com.paymu.pricing.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Entry in the indicative vehicle catalogue.
 */
public record VehicleCatalogueEntry(
        @JsonProperty("id") String id,
        @JsonProperty("label") String label,
        @JsonProperty("ex_showroom_price_inr") long exShowroomPriceInr
) {}
