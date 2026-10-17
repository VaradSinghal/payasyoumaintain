package com.paymu.identity.controller;

import com.paymu.identity.service.RegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IdentityConsentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        registrationService.clear();
    }

    @Test
    @DisplayName("GET /vehicles/{unknown valid uuid} returns 404 NOT_FOUND")
    void getVehicle_unknownUuid_returns404() throws Exception {
        String unknownUuid = UUID.randomUUID().toString();
        mockMvc.perform(get("/vehicles/" + unknownUuid))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("GET /vehicles/{unknown valid uuid}/consent returns 404 NOT_FOUND")
    void getConsent_unknownUuid_returns404() throws Exception {
        String unknownUuid = UUID.randomUUID().toString();
        mockMvc.perform(get("/vehicles/" + unknownUuid + "/consent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("PUT /vehicles/{unknown valid uuid}/consent returns 404 NOT_FOUND")
    void putConsent_unknownUuid_returns404() throws Exception {
        String unknownUuid = UUID.randomUUID().toString();
        String json = """
                {
                    "usage_tracking_opt_in": true,
                    "maintenance_tracking_opt_in": true
                }
                """;

        mockMvc.perform(put("/vehicles/" + unknownUuid + "/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("GET /vehicles/not-a-uuid returns 400 INVALID_REQUEST")
    void getVehicle_notAUuid_returns400() throws Exception {
        mockMvc.perform(get("/vehicles/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(containsString("vehicleId")))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("GET /vehicles/not-a-uuid/consent returns 400 INVALID_REQUEST")
    void getConsent_notAUuid_returns400() throws Exception {
        mockMvc.perform(get("/vehicles/not-a-uuid/consent"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(containsString("vehicleId")))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("PUT /vehicles/not-a-uuid/consent returns 400 INVALID_REQUEST")
    void putConsent_notAUuid_returns400() throws Exception {
        String json = """
                {
                    "usage_tracking_opt_in": true,
                    "maintenance_tracking_opt_in": true
                }
                """;

        mockMvc.perform(put("/vehicles/not-a-uuid/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(containsString("vehicleId")))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("POST /vehicles with missing required field returns 400 INVALID_REQUEST")
    void registerVehicle_missingRequiredField_returns400() throws Exception {
        // Missing rc_number
        String json = """
                {
                    "registration_date": "2026-01-01",
                    "owner_details": {
                        "full_name": "Jane Doe",
                        "id_number": "ABCDE1234F",
                        "id_type": "PAN",
                        "email": "jane@example.com",
                        "phone": "+919876543210"
                    }
                }
                """;

        mockMvc.perform(post("/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("POST /vehicles with malformed JSON returns 400 INVALID_REQUEST")
    void registerVehicle_malformedJson_returns400() throws Exception {
        String malformedJson = "{ \"rc_number\": \"KA-01-AB-1234\", invalid-json";

        mockMvc.perform(post("/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("POST /vehicles valid registration then GET and PUT consent succeed")
    void registerVehicle_lifecycle_succeeds() throws Exception {
        String validJson = """
                {
                    "rc_number": "KA-01-AB-1234",
                    "registration_date": "2026-01-01",
                    "owner_details": {
                        "full_name": "Jane Doe",
                        "kyc_document_number": "ABCDE1234F",
                        "kyc_document_type": "PAN",
                        "email": "jane@example.com",
                        "phone_number": "+919876543210"
                    }
                }
                """;

        String responseStr = mockMvc.perform(post("/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vehicle_id").isNotEmpty())
                .andExpect(jsonPath("$.policy_id").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        // Extract vehicle_id from response
        com.fasterxml.jackson.databind.JsonNode node =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(responseStr);
        String vehicleId = node.get("vehicle_id").asText();

        // GET vehicle
        mockMvc.perform(get("/vehicles/" + vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle_id").value(vehicleId));

        // GET consent
        mockMvc.perform(get("/vehicles/" + vehicleId + "/consent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usage_tracking_opt_in").value(false))
                .andExpect(jsonPath("$.maintenance_tracking_opt_in").value(false));

        // PUT consent
        String updateJson = """
                {
                    "usage_tracking_opt_in": true,
                    "maintenance_tracking_opt_in": true
                }
                """;
        mockMvc.perform(put("/vehicles/" + vehicleId + "/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usage_tracking_opt_in").value(true))
                .andExpect(jsonPath("$.maintenance_tracking_opt_in").value(true));
    }

    @Test
    @DisplayName("PII minimization masks kyc_document_number, phone_number, and email on POST and GET")
    void piiMinimization_masksSensitiveFields() throws Exception {
        String payload = """
                {
                    "rc_number": "KA01AB1234",
                    "registration_date": "2024-01-01",
                    "owner_details": {
                        "full_name": "Test User",
                        "kyc_document_number": "123456789012",
                        "kyc_document_type": "AADHAAR",
                        "email": "testuser@example.com",
                        "phone_number": "9876543210"
                    }
                }
                """;

        String postRes = mockMvc.perform(post("/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.owner_details.kyc_document_number").value("XXXXXXXX9012"))
                .andExpect(jsonPath("$.owner_details.phone_number").value("XXXXXX3210"))
                .andExpect(jsonPath("$.owner_details.email").value("t***@example.com"))
                .andReturn().getResponse().getContentAsString();

        String vehicleId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(postRes).get("vehicle_id").asText();

        // Stored value remains intact in memory
        var stored = registrationService.getRegistration(vehicleId);
        org.junit.jupiter.api.Assertions.assertNotNull(stored);
        org.junit.jupiter.api.Assertions.assertEquals("123456789012", stored.ownerDetails().kycDocumentNumber());
        org.junit.jupiter.api.Assertions.assertEquals("9876543210", stored.ownerDetails().phoneNumber());
        org.junit.jupiter.api.Assertions.assertEquals("testuser@example.com", stored.ownerDetails().email());

        // GET response returns masked values
        mockMvc.perform(get("/vehicles/" + vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner_details.kyc_document_number").value("XXXXXXXX9012"))
                .andExpect(jsonPath("$.owner_details.phone_number").value("XXXXXX3210"))
                .andExpect(jsonPath("$.owner_details.email").value("t***@example.com"));
    }

    @Test
    @DisplayName("DemoSeeder seeds all 5 demo vehicles with deterministic policy UUIDs and opt-in consent")
    void demoSeeder_seedsDemoVehicles() {
        var seeder = new com.paymu.identity.service.DemoSeeder(registrationService);
        seeder.seedDemoVehicles();

        String v1 = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        String v2 = "b2c3d4e5-f6a7-8901-bcde-f12345678901";
        String v3 = "c3d4e5f6-a7b8-9012-cdef-123456789012";
        String v4 = "d4e5f6a7-b8c9-0123-defa-234567890123";
        String v5 = "e5f6a7b8-c9d0-1234-efab-345678901234";

        var reg1 = registrationService.getRegistration(v1);
        org.junit.jupiter.api.Assertions.assertNotNull(reg1);
        org.junit.jupiter.api.Assertions.assertEquals("c4632682-d406-3102-86b7-6a22ef09af5d", reg1.policyId());

        var reg2 = registrationService.getRegistration(v2);
        org.junit.jupiter.api.Assertions.assertNotNull(reg2);
        org.junit.jupiter.api.Assertions.assertEquals("4c4caec8-846d-31d6-af88-43c25c038aa4", reg2.policyId());

        var reg3 = registrationService.getRegistration(v3);
        org.junit.jupiter.api.Assertions.assertNotNull(reg3);
        org.junit.jupiter.api.Assertions.assertEquals("f613e582-17e0-37cf-b317-856cbca14248", reg3.policyId());

        var reg5 = registrationService.getRegistration(v5);
        org.junit.jupiter.api.Assertions.assertNotNull(reg5);
        org.junit.jupiter.api.Assertions.assertEquals("a2aa245d-ecbc-3719-b180-cfc5ca5c3866", reg5.policyId());

        for (String vid : List.of(v1, v2, v3, v4, v5)) {
            var consent = registrationService.getConsent(vid);
            org.junit.jupiter.api.Assertions.assertNotNull(consent);
            org.junit.jupiter.api.Assertions.assertTrue(consent.usageTrackingOptIn());
            org.junit.jupiter.api.Assertions.assertTrue(consent.maintenanceTrackingOptIn());
        }

        // Idempotency check: running seed again does not duplicate or fail
        seeder.seedDemoVehicles();
        org.junit.jupiter.api.Assertions.assertEquals("c4632682-d406-3102-86b7-6a22ef09af5d", registrationService.getRegistration(v1).policyId());
    }
}
