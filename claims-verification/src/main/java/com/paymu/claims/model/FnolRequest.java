package com.paymu.claims.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * First Notice of Loss (FNOL) request payload.
 *
 * @param vehicleId         UUID of the vehicle involved in the incident
 * @param policyId          UUID of the active policy
 * @param incidentDate      ISO-8601 date of the incident (e.g. "2026-09-23")
 * @param incidentDescription free-text description of what happened
 * @param claimedCause      driver-reported cause (e.g. "mechanical_failure", "collision", "theft")
 * @param photoRefs         URIs or object-storage keys for incident photos (may be empty)
 * @param location          Optional free-text incident location
 */
public record FnolRequest(
        @NotBlank(message = "vehicle_id is required")
        String vehicleId,

        @NotBlank(message = "policy_id is required")
        String policyId,

        @NotBlank(message = "incident_date is required")
        String incidentDate,

        @NotBlank(message = "incident_description is required")
        String incidentDescription,

        @NotNull(message = "claimed_cause is required")
        ClaimedCause claimedCause,

        List<String> photoRefs,

        String location
) {}
