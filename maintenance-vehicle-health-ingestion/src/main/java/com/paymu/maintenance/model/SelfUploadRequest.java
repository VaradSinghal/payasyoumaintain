package com.paymu.maintenance.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Request DTO for the self-upload path.
 * The user types in the maintenance fields and optionally attaches a document.
 * The stub OCR service echoes these fields back as a {@link MaintenanceEvent}.
 */
public record SelfUploadRequest(
        @JsonProperty("vehicle_id") String vehicleId,
        @JsonProperty("service_date") String serviceDate,
        @JsonProperty("service_type") String serviceType,
        @JsonProperty("odometer_km") int odometerKm,
        @JsonProperty("parts_replaced") List<PartReplaced> partsReplaced,
        String notes,
        @JsonProperty("document_ref") String documentRef
) {}
