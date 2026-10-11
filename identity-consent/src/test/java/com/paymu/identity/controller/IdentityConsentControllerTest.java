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
}
