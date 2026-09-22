package com.paymu.maintenance.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Open recall status for a vehicle.
 * Maps 1:1 to contracts/schemas/recall-status.schema.json.
 *
 * <p>This is the authoritative source of recall truth for the platform.
 * Free-text notes on {@link MaintenanceEvent} remain for genuine unstructured
 * comments only — they are never parsed for recall status.</p>
 */
public record RecallStatus(
        @JsonProperty("vehicle_id") String vehicleId,
        @JsonProperty("has_open_recall") boolean hasOpenRecall,
        @JsonProperty("recall_count") int recallCount,
        @JsonProperty("recall_details") List<RecallDetail> recallDetails,
        String source,
        @JsonProperty("checked_at") String checkedAt
) {
    /**
     * The sole valid value for {@code source}, per the schema's {@code const} constraint.
     */
    public static final String SOURCE = "oem_recall_db";
}
