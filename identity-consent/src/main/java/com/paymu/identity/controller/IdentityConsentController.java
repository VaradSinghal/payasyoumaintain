package com.paymu.identity.controller;

import com.paymu.identity.model.ConsentRecord;
import com.paymu.identity.model.ConsentUpdateRequest;
import com.paymu.identity.model.VehicleRegistration;
import com.paymu.identity.model.VehicleRegistrationRequest;
import com.paymu.identity.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping
public class IdentityConsentController {

    private final RegistrationService registrationService;

    public IdentityConsentController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/vehicles")
    public ResponseEntity<VehicleRegistration> registerVehicle(@Valid @RequestBody VehicleRegistrationRequest request) {
        VehicleRegistration registration = registrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(registration);
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
        return ResponseEntity.ok(registration);
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
