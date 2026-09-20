package com.paymu.maintenance.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response returned by the batch ingest endpoint.
 */
public record BatchIngestResponse(
        @JsonProperty("accepted_count") int acceptedCount,
        @JsonProperty("rejected_count") int rejectedCount,
        @JsonProperty("validation_errors") List<String> validationErrors,
        @JsonProperty("vehicle_ids") List<String> vehicleIds
) {}
