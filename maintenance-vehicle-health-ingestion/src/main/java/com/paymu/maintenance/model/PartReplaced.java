package com.paymu.maintenance.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single part replaced during a maintenance service.
 * Maps to the items of the "parts_replaced" array in maintenance-event.schema.json.
 */
public record PartReplaced(
        @JsonProperty("part_name") String partName,
        @JsonProperty("part_number") String partNumber,
        Integer quantity
) {}
