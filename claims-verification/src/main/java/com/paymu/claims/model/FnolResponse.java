package com.paymu.claims.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * FNOL triage response.
 *
 * @param claimId           system-assigned UUID for this claim
 * @param vehicleId         echoed from request
 * @param policyId          echoed from request
 * @param claimedCause      echoed from request
 * @param triageDecision    routing outcome
 * @param triageReasons     ordered list of human-readable reasons for the decision
 * @param maintenanceSignal summary of what the service timeline contributed to triage
 * @param submittedAt       ISO-8601 timestamp of intake
 */
public record FnolResponse(
        @JsonProperty("claim_id") String claimId,
        @JsonProperty("vehicle_id") String vehicleId,
        @JsonProperty("policy_id") String policyId,
        @JsonProperty("claimed_cause") ClaimedCause claimedCause,
        @JsonProperty("triage_decision") TriageDecision triageDecision,
        @JsonProperty("triage_reasons") List<String> triageReasons,
        @JsonProperty("maintenance_signal") String maintenanceSignal,
        @JsonProperty("submitted_at") String submittedAt
) {}
