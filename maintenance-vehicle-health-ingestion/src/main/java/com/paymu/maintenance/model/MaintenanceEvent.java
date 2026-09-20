package com.paymu.maintenance.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * A vehicle maintenance or service event.
 * Maps 1:1 to contracts/schemas/maintenance-event.schema.json.
 */
public record MaintenanceEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("vehicle_id") String vehicleId,
        @JsonProperty("service_date") String serviceDate,
        @JsonProperty("service_type") String serviceType,
        @JsonProperty("odometer_km") int odometerKm,
        @JsonProperty("parts_replaced") List<PartReplaced> partsReplaced,
        String source,
        String notes,
        @JsonProperty("document_refs") List<String> documentRefs
) {}
