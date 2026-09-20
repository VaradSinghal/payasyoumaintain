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
    public ResponseEntity<VehicleRegistration> getRegistration(@PathVariable String vehicleId) {
        VehicleRegistration registration = registrationService.getRegistration(vehicleId);
        if (registration == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(registration);
    }

    @GetMapping("/vehicles/{vehicleId}/consent")
    public ResponseEntity<ConsentRecord> getConsent(@PathVariable String vehicleId) {
        ConsentRecord consent = registrationService.getConsent(vehicleId);
        if (consent == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(consent);
    }

    @PutMapping("/vehicles/{vehicleId}/consent")
    public ResponseEntity<ConsentRecord> updateConsent(
            @PathVariable String vehicleId,
            @Valid @RequestBody ConsentUpdateRequest request) {
        try {
            ConsentRecord updated = registrationService.updateConsent(
                    vehicleId, 
                    request.usageTrackingOptIn(), 
                    request.maintenanceTrackingOptIn()
            );
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
