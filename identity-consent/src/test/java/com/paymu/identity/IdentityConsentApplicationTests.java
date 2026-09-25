package com.paymu.identity;

import com.paymu.identity.model.ConsentRecord;
import com.paymu.identity.model.OwnerDetails;
import com.paymu.identity.model.VehicleRegistration;
import com.paymu.identity.model.VehicleRegistrationRequest;
import com.paymu.identity.service.RegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class IdentityConsentApplicationTests {

    @Autowired
    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        registrationService.clear();
    }

    @Test
    @DisplayName("New vehicle registration defaults to opted-out for compliance")
    void testDefaultOptOut() {
        OwnerDetails owner = new OwnerDetails(
                "Jane Doe",
                "ABCDE1234F",
                "PAN",
                "jane@example.com",
                "+919876543210"
        );
        
        VehicleRegistrationRequest request = new VehicleRegistrationRequest(
                "KA-01-AB-1234",
                LocalDate.now(),
                owner
        );
        
        VehicleRegistration reg = registrationService.register(request);
        assertNotNull(reg.vehicleId(), "Vehicle ID should be generated");
        assertEquals("VERIFIED", reg.kycStatus(), "Mock KYC should verify");
        
        ConsentRecord consent = registrationService.getConsent(reg.vehicleId());
        assertNotNull(consent, "Consent record should be created");
        
        // Compliance requirement: explicit opt-in required, default is opted-out
        assertFalse(consent.usageTrackingOptIn(), "Usage tracking should default to opted-out");
        assertFalse(consent.maintenanceTrackingOptIn(), "Maintenance tracking should default to opted-out");
    }

    @Test
    @DisplayName("Consent can be updated explicitly")
    void testConsentUpdate() {
        OwnerDetails owner = new OwnerDetails(
                "John Smith",
                "123456789012",
                "AADHAAR",
                "john@example.com",
                "+919876543211"
        );
        
        VehicleRegistrationRequest request = new VehicleRegistrationRequest(
                "MH-12-CD-5678",
                LocalDate.now(),
                owner
        );
        
        VehicleRegistration reg = registrationService.register(request);
        
        // Update consent
        ConsentRecord updated = registrationService.updateConsent(
                reg.vehicleId(), 
                true,  // usage
                false  // maintenance
        );
        
        assertTrue(updated.usageTrackingOptIn(), "Usage tracking should be opted in");
        assertFalse(updated.maintenanceTrackingOptIn(), "Maintenance tracking should be opted out");
        
        ConsentRecord fetched = registrationService.getConsent(reg.vehicleId());
        assertTrue(fetched.usageTrackingOptIn());
        assertFalse(fetched.maintenanceTrackingOptIn());
    }

    @Test
    @DisplayName("Generated policy_id is stable across repeated fetches")
    void testStablePolicyId() {
        OwnerDetails owner = new OwnerDetails(
                "Stable Owner",
                "PAN1234567",
                "PAN",
                "stable@example.com",
                "+919876543212"
        );
        VehicleRegistrationRequest request = new VehicleRegistrationRequest(
                "DL-01-XX-9999",
                LocalDate.now(),
                owner
        );
        VehicleRegistration reg = registrationService.register(request);
        
        String generatedPolicyId = reg.policyId();
        assertNotNull(generatedPolicyId, "policyId should be generated on registration");

        VehicleRegistration fetched1 = registrationService.getRegistration(reg.vehicleId());
        VehicleRegistration fetched2 = registrationService.getRegistration(reg.vehicleId());
        
        assertEquals(generatedPolicyId, fetched1.policyId(), "policyId should be stable across fetches");
        assertEquals(generatedPolicyId, fetched2.policyId(), "policyId should be stable across fetches");
    }
}
