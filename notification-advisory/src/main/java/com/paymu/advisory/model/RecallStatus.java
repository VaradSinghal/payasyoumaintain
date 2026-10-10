package com.paymu.advisory.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Mirrors recall-status.schema.json from maintenance-vehicle-health-ingestion. */
public record RecallStatus(
        @JsonProperty("vehicle_id") String vehicleId,
        @JsonProperty("availability") String availability,
        @JsonProperty("has_open_recall") Boolean hasOpenRecall,
        @JsonProperty("recall_count") Integer recallCount,
        @JsonProperty("recall_details") List<RecallDetail> recallDetails,
        String source,
        @JsonProperty("checked_at") String checkedAt
) {
    public record RecallDetail(
            @JsonProperty("recall_id") String recallId,
            String description,
            @JsonProperty("issued_date") String issuedDate
    ) {}
}
