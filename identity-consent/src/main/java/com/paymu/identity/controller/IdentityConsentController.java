package com.paymu.identity.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.ValidationMessage;
import com.paymu.identity.model.ConsentRecord;
import com.paymu.identity.model.ConsentUpdateRequest;
import com.paymu.identity.model.VehicleRegistration;
import com.paymu.identity.model.VehicleRegistrationRequest;
import com.paymu.identity.service.RegistrationService;
import com.paymu.identity.service.SchemaValidationService;
import jakarta.validation.Valid;
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
@RequestMapping
public class IdentityConsentController {

    private final RegistrationService registrationService;
    private final SchemaValidationService schemaValidationService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IdentityConsentController(
            RegistrationService registrationService,
            SchemaValidationService schemaValidationService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.registrationService = registrationService;
        this.schemaValidationService = schemaValidationService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @PostMapping("/vehicles")
    public ResponseEntity<?> registerVehicle(@RequestBody JsonNode rawJson) {
        Set<ValidationMessage> errors = schemaValidationService.validateRegistration(rawJson);
        if (!errors.isEmpty()) {
            List<String> messages = errors.stream().map(ValidationMessage::getMessage).toList();
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_REQUEST",
                    "message", "Schema validation failed: " + String.join(", ", messages),
                    "timestamp", Instant.now(clock).toString()
            ));
        }

        VehicleRegistrationRequest request;
        try {
            request = objectMapper.treeToValue(rawJson, VehicleRegistrationRequest.class);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_REQUEST",
                    "message", "Malformed request body: " + e.getMessage(),
                    "timestamp", Instant.now(clock).toString()
            ));
        }

        VehicleRegistration registration = registrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(registration.mask());
    }

    @GetMapping("/vehicles/{vehicleId}")
    public ResponseEntity<?> getRegistration(@PathVariable UUID vehicleId) {
        VehicleRegistration registration = registrationService.getRegistration(vehicleId.toString());
        if (registration == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "NOT_FOUND",
                    "message", "Vehicle not found: " + vehicleId,
                    "timestamp", Instant.now().toString()
            ));
        }
        return ResponseEntity.ok(registration.mask());
    }

    @GetMapping("/vehicles/{vehicleId}/consent")
    public ResponseEntity<?> getConsent(@PathVariable UUID vehicleId) {
        ConsentRecord consent = registrationService.getConsent(vehicleId.toString());
        if (consent == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "NOT_FOUND",
                    "message", "Consent not found for vehicle: " + vehicleId,
                    "timestamp", Instant.now().toString()
            ));
        }
        return ResponseEntity.ok(consent);
    }

    @PutMapping("/vehicles/{vehicleId}/consent")
    public ResponseEntity<?> updateConsent(
            @PathVariable UUID vehicleId,
            @Valid @RequestBody ConsentUpdateRequest request) {
        try {
            ConsentRecord updated = registrationService.updateConsent(
                    vehicleId.toString(), 
                    request.usageTrackingOptIn(), 
                    request.maintenanceTrackingOptIn()
            );
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "NOT_FOUND",
                    "message", "Vehicle not found: " + vehicleId,
                    "timestamp", Instant.now().toString()
            ));
        }
    }
}
