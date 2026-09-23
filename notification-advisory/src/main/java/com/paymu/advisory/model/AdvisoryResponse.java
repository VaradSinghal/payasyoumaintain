package com.paymu.advisory.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response from the {@code GET /advisories/{vehicleId}} endpoint.
 *
 * @param vehicleId    the vehicle these advisories apply to
 * @param advisories   prioritised list of advisory messages (empty if nothing needs flagging)
 * @param generatedAt  ISO-8601 timestamp of when the list was generated
 */
public record AdvisoryResponse(
        @JsonProperty("vehicle_id") String vehicleId,
        List<AdvisoryMessage> advisories,
        @JsonProperty("generated_at") String generatedAt
) {}
