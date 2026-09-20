package com.paymu.maintenance.service;

import com.paymu.maintenance.model.MaintenanceEvent;
import com.paymu.maintenance.model.OcrResult;
import com.paymu.maintenance.model.SelfUploadRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Stub OCR service for the self-upload path.
 *
 * <p>In production, this service would accept an uploaded document image,
 * run OCR / document-AI to extract service fields, and return structured
 * data.  For now, it simply echoes the user-typed fields back as a
 * {@link MaintenanceEvent} with {@code source = "self_upload"}.</p>
 */
@Service
public class OcrStubService {

    private static final Logger log = LoggerFactory.getLogger(OcrStubService.class);

    /**
     * Processes a self-upload request by echoing typed fields as a
     * structured maintenance event.
     *
     * @param request user-typed fields and optional document reference
     * @return OCR result containing the "extracted" maintenance event
     */
    public OcrResult process(SelfUploadRequest request) {
        log.info("OCR stub: echoing typed fields for vehicle {}", request.vehicleId());

        List<String> documentRefs = request.documentRef() != null
                ? List.of(request.documentRef())
                : List.of();

        MaintenanceEvent event = new MaintenanceEvent(
                UUID.randomUUID().toString(),
                request.vehicleId(),
                request.serviceDate(),
                request.serviceType(),
                request.odometerKm(),
                request.partsReplaced() != null ? request.partsReplaced() : List.of(),
                "self_upload",
                request.notes(),
                documentRefs);

        return OcrResult.stubEcho(event);
    }
}
