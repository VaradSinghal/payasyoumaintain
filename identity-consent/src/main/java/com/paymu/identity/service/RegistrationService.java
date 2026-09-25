package com.paymu.identity.service;

import com.paymu.identity.model.ConsentRecord;
import com.paymu.identity.model.VehicleRegistration;
import com.paymu.identity.model.VehicleRegistrationRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RegistrationService {

    private final Map<String, VehicleRegistration> registrations = new ConcurrentHashMap<>();
    private final Map<String, ConsentRecord> consents = new ConcurrentHashMap<>();
    private final KycService kycService;

    public RegistrationService(KycService kycService) {
        this.kycService = kycService;
    }

    public VehicleRegistration register(VehicleRegistrationRequest request) {
        String kycStatus = kycService.verify(request.ownerDetails());
        String vehicleId = UUID.randomUUID().toString();
        String policyId = UUID.randomUUID().toString();
        
        VehicleRegistration registration = new VehicleRegistration(
                vehicleId,
                policyId,
                request.rcNumber(),
                request.registrationDate().toString(),
                request.ownerDetails(),
                kycStatus,
                Instant.now()
        );
        
        registrations.put(vehicleId, registration);
        
        // Initialize consent with defaults (opted-out) for compliance
        consents.put(vehicleId, ConsentRecord.defaultOptOut(vehicleId));
        
        return registration;
    }

    public VehicleRegistration getRegistration(String vehicleId) {
        return registrations.get(vehicleId);
    }
    
    public ConsentRecord getConsent(String vehicleId) {
        return consents.get(vehicleId);
    }
    
    public ConsentRecord updateConsent(String vehicleId, boolean usageTracking, boolean maintenanceTracking) {
        if (!registrations.containsKey(vehicleId)) {
            throw new IllegalArgumentException("Vehicle ID not found");
        }
        
        ConsentRecord updated = new ConsentRecord(
                vehicleId, 
                usageTracking, 
                maintenanceTracking, 
                Instant.now()
        );
        consents.put(vehicleId, updated);
        return updated;
    }
    
    public void clear() {
        registrations.clear();
        consents.clear();
    }
}
