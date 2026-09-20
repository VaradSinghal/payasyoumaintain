package com.paymu.maintenance.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result from the OCR stub (or future real OCR service).
 * Wraps the extracted {@link MaintenanceEvent} with processing metadata.
 */
public record OcrResult(
        String status,
        String message,
        @JsonProperty("extracted_event") MaintenanceEvent extractedEvent
) {
    /** Factory for the current stub implementation. */
    public static OcrResult stubEcho(MaintenanceEvent event) {
        return new OcrResult(
                "stub_echo",
                "OCR stub: typed fields echoed as-is. Real OCR integration pending.",
                event);
    }
}
