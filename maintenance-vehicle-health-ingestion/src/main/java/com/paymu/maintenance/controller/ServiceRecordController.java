package com.paymu.maintenance.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.ValidationMessage;
import com.paymu.maintenance.client.IdentityClient;
import com.paymu.maintenance.client.VehicleRegistrationResponse;
import com.paymu.maintenance.model.*;
import com.paymu.maintenance.service.ConsistencyCheckService;
import com.paymu.maintenance.service.MaintenanceStore;
import com.paymu.maintenance.service.SchemaValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/service-records")
public class ServiceRecordController {

    private static final Logger log = LoggerFactory.getLogger(ServiceRecordController.class);

    private final SchemaValidationService validationService;
    private final IdentityClient identityClient;
    private final ConsistencyCheckService consistencyCheckService;
    private final MaintenanceStore store;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ServiceRecordController(
            SchemaValidationService validationService,
            IdentityClient identityClient,
            ConsistencyCheckService consistencyCheckService,
            MaintenanceStore store,
            ObjectMapper objectMapper,
            Clock clock) {
        this.validationService = validationService;
        this.identityClient = identityClient;
        this.consistencyCheckService = consistencyCheckService;
        this.store = store;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @PostMapping
    public ResponseEntity<?> createServiceRecord(@RequestBody JsonNode rawNode) {
        // 1. Boundary-validate against request schema
        Set<ValidationMessage> validationErrors = validationService.validateServiceRecordRequest(rawNode);
        if (!validationErrors.isEmpty()) {
            List<String> errorMessages = validationErrors.stream()
                    .map(ValidationMessage::getMessage)
                    .toList();
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_REQUEST",
                    "message", "Request failed contract schema validation: " + String.join("; ", errorMessages),
                    "timestamp", Instant.now(clock).toString()
            ));
        }

        ServiceRecordRequest request;
        try {
            request = objectMapper.treeToValue(rawNode, ServiceRecordRequest.class);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_REQUEST",
                    "message", "Failed to deserialize request payload: " + e.getMessage(),
                    "timestamp", Instant.now(clock).toString()
            ));
        }

        // 2. Identity checks in order:
        //    (a) GET /vehicles/{id} (404 -> VEHICLE_NOT_REGISTERED, 5xx/unreachable -> 503 SERVICE_UNAVAILABLE)
        VehicleRegistrationResponse registration = identityClient.getVehicleRegistration(request.vehicleId());

        //    (b) GET /vehicles/{id}/consent (maintenance_tracking_opt_in false -> 403 CONSENT_REQUIRED)
        identityClient.getConsent(request.vehicleId());

        // 3. Evaluate consistency checks against stored records
        List<MaintenanceEvent> storedEvents = store.getEventsByVehicle(request.vehicleId().toString());
        VerificationResult verification = consistencyCheckService.evaluate(request, registration, storedEvents);

        Instant recordedAt = Instant.now(clock);
        UUID eventId = UUID.randomUUID();

        // 4. Any hard failure -> status rejected, NOT stored, HTTP 200 with stored false
        if ("rejected".equalsIgnoreCase(verification.status())) {
            ServiceRecordResponse response = new ServiceRecordResponse(
                    eventId,
                    request.vehicleId(),
                    request.serviceDate(),
                    request.serviceType(),
                    request.odometerKm(),
                    "self_upload",
                    false,
                    verification,
                    recordedAt
            );
            return ResponseEntity.ok(response);
        }

        // 5. Soft failure (flagged) or passed (accepted) -> stored true, HTTP 201
        boolean stored = true;
        MaintenanceEvent eventToStore = new MaintenanceEvent(
                eventId.toString(),
                request.vehicleId().toString(),
                request.serviceDate().toString(),
                request.serviceType(),
                request.odometerKm(),
                List.of(),
                "self_upload",
                request.notes(),
                request.receiptRef() != null ? List.of(request.receiptRef()) : List.of(),
                verification.status(),
                request.receiptSha256(),
                recordedAt
        );
        store.storeEvent(eventToStore);

        ServiceRecordResponse response = new ServiceRecordResponse(
                eventId,
                request.vehicleId(),
                request.serviceDate(),
                request.serviceType(),
                request.odometerKm(),
                "self_upload",
                stored,
                verification,
                recordedAt
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
