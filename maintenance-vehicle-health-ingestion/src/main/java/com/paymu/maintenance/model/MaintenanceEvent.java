package com.paymu.maintenance.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * A vehicle maintenance or service event.
 * Maps 1:1 to contracts/schemas/maintenance-event.schema.json.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MaintenanceEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("vehicle_id") String vehicleId,
        @JsonProperty("service_date") String serviceDate,
        @JsonProperty("service_type") String serviceType,
        @JsonProperty("odometer_km") int odometerKm,
        @JsonProperty("parts_replaced") List<PartReplaced> partsReplaced,
        String source,
        String notes,
        @JsonProperty("document_refs") List<String> documentRefs,
        @JsonProperty("verification_status") String verificationStatus,
        @JsonProperty("receipt_sha256") String receiptSha256,
        @JsonProperty("recorded_at") Instant recordedAt
) {
    public MaintenanceEvent(String eventId, String vehicleId, String serviceDate,
                            String serviceType, int odometerKm, List<PartReplaced> partsReplaced,
                            String source, String notes, List<String> documentRefs) {
        this(eventId, vehicleId, serviceDate, serviceType, odometerKm, partsReplaced, source, notes, documentRefs, null, null, null);
    }

    public MaintenanceEvent(String eventId, String vehicleId, String serviceDate,
                            String serviceType, int odometerKm, List<PartReplaced> partsReplaced,
                            String source, String notes, List<String> documentRefs,
                            String verificationStatus) {
        this(eventId, vehicleId, serviceDate, serviceType, odometerKm, partsReplaced, source, notes, documentRefs, verificationStatus, null, null);
    }
}
