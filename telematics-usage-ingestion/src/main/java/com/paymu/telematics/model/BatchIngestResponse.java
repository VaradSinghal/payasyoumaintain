package com.paymu.telematics.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response returned by the batch ingest endpoint.
 * Reports per-event accept / reject with partial-batch acceptance.
 */
public record BatchIngestResponse(
        @JsonProperty("accepted_count") int acceptedCount,
        @JsonProperty("rejected_count") int rejectedCount,
        @JsonProperty("validation_errors") List<String> validationErrors,
        List<TripAggregate> aggregates
) {}
