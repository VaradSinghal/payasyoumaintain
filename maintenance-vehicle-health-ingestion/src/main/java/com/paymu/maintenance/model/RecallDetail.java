package com.paymu.maintenance.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * A single open recall against a vehicle, sourced from the OEM recall database.
 * Maps to the items of the "recall_details" array in recall-status.schema.json.
 */
public record RecallDetail(
        @JsonProperty("recall_id") String recallId,
        String description,
        @JsonProperty("issued_date") String issuedDate
) {}
