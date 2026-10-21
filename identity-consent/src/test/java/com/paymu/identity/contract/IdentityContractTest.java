package com.paymu.identity.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.paymu.identity.service.RegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Identity & Consent Contract Tests")
class IdentityContractTest {

    private static final String REGISTRATION_SCHEMA_PATH = "/schemas/vehicle-registration-response.schema.json";
    private static final String CONSENT_SCHEMA_PATH = "/schemas/consent-record.schema.json";
    private static final String ERROR_SCHEMA_PATH = "/schemas/error-response.schema.json";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RegistrationService registrationService;

    private JsonSchema registrationSchema;
    private JsonSchema consentSchema;
    private JsonSchema errorSchema;

    @BeforeEach
    void setUp() {
        registrationService.clear();

        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (InputStream is = getClass().getResourceAsStream(REGISTRATION_SCHEMA_PATH)) {
            assertNotNull(is, () -> "Contract schema not found on classpath: " + REGISTRATION_SCHEMA_PATH);
            registrationSchema = factory.getSchema(is);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load schema from " + REGISTRATION_SCHEMA_PATH, e);
        }

        try (InputStream is = getClass().getResourceAsStream(CONSENT_SCHEMA_PATH)) {
            assertNotNull(is, () -> "Contract schema not found on classpath: " + CONSENT_SCHEMA_PATH);
            consentSchema = factory.getSchema(is);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load schema from " + CONSENT_SCHEMA_PATH, e);
        }

        try (InputStream is = getClass().getResourceAsStream(ERROR_SCHEMA_PATH)) {
            assertNotNull(is, () -> "Contract schema not found on classpath: " + ERROR_SCHEMA_PATH);
            errorSchema = factory.getSchema(is);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load schema from " + ERROR_SCHEMA_PATH, e);
        }
    }

    private void validateRegistration(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        Set<ValidationMessage> errors = registrationSchema.validate(node);
        assertTrue(errors.isEmpty(), () -> "Registration schema validation failed: " + errors);
    }

    private void validateConsent(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        Set<ValidationMessage> errors = consentSchema.validate(node);
        assertTrue(errors.isEmpty(), () -> "Consent schema validation failed: " + errors);
    }

    private void validateError(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        Set<ValidationMessage> errors = errorSchema.validate(node);
        assertTrue(errors.isEmpty(), () -> "Error schema validation failed: " + errors);
    }

    private String validRegistrationPayload(String segmentId) {
        return """
                {
                  "rc_number": "KA01AB1234",
                  "registration_date": "%s",
                  "vehicle_segment_id": %s,
                  "owner_details": {
                    "full_name": "Aarav Sharma",
                    "kyc_document_number": "123456789012",
                    "kyc_document_type": "AADHAAR",
                    "email": "aarav.sharma@example.com",
                    "phone_number": "9876543210"
                  }
                }
                """.formatted(LocalDate.now().toString(), segmentId != null ? "\"" + segmentId + "\"" : "null");
    }

    // ── 1. POST /vehicles response conforms to schema with masked fields & segment ──

    @Test
    @DisplayName("POST /vehicles response conforms to vehicle-registration-response contract")
    void testRegisterVehicleConformsToContract() throws Exception {
        MvcResult result = mockMvc.perform(post("/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistrationPayload("sedan_mid")))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        validateRegistration(body);

        JsonNode node = objectMapper.readTree(body);
        assertEquals("sedan_mid", node.get("vehicle_segment_id").asText());

        JsonNode owner = node.get("owner_details");
        assertTrue(owner.get("email").asText().contains("***"), "Email should be masked");
        assertTrue(owner.get("phone_number").asText().startsWith("XXXXXX"), "Phone should be masked");
        assertTrue(owner.get("kyc_document_number").asText().startsWith("XXXXXXXX"), "KYC doc should be masked");
    }

    // ── 2. GET /vehicles/{id} response conforms to schema ─────────────────────

    @Test
    @DisplayName("GET /vehicles/{id} response conforms to vehicle-registration-response contract")
    void testGetVehicleConformsToContract() throws Exception {
        MvcResult regResult = mockMvc.perform(post("/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistrationPayload("suv_compact")))
                .andExpect(status().isCreated())
                .andReturn();

        String vehicleId = objectMapper.readTree(regResult.getResponse().getContentAsString()).get("vehicle_id").asText();

        MvcResult getResult = mockMvc.perform(get("/vehicles/{vehicleId}", vehicleId))
                .andExpect(status().isOk())
                .andReturn();

        String body = getResult.getResponse().getContentAsString();
        validateRegistration(body);

        JsonNode node = objectMapper.readTree(body);
        assertEquals("suv_compact", node.get("vehicle_segment_id").asText());
        JsonNode owner = node.get("owner_details");
        assertTrue(owner.get("email").asText().contains("***"));
    }

    // ── 3. GET /vehicles/{id}/consent response conforms to schema ─────────────

    @Test
    @DisplayName("GET /vehicles/{id}/consent conforms to consent-record contract")
    void testGetConsentConformsToContract() throws Exception {
        MvcResult regResult = mockMvc.perform(post("/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistrationPayload(null)))
                .andExpect(status().isCreated())
                .andReturn();

        String vehicleId = objectMapper.readTree(regResult.getResponse().getContentAsString()).get("vehicle_id").asText();

        MvcResult consentResult = mockMvc.perform(get("/vehicles/{vehicleId}/consent", vehicleId))
                .andExpect(status().isOk())
                .andReturn();

        validateConsent(consentResult.getResponse().getContentAsString());
    }

    // ── 4. PUT /vehicles/{id}/consent response conforms to schema ─────────────

    @Test
    @DisplayName("PUT /vehicles/{id}/consent conforms to consent-record contract")
    void testPutConsentConformsToContract() throws Exception {
        MvcResult regResult = mockMvc.perform(post("/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistrationPayload(null)))
                .andExpect(status().isCreated())
                .andReturn();

        String vehicleId = objectMapper.readTree(regResult.getResponse().getContentAsString()).get("vehicle_id").asText();

        String updatePayload = """
                {
                  "usage_tracking_opt_in": true,
                  "maintenance_tracking_opt_in": true
                }
                """;

        MvcResult updateResult = mockMvc.perform(put("/vehicles/{vehicleId}/consent", vehicleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andReturn();

        String body = updateResult.getResponse().getContentAsString();
        validateConsent(body);
        JsonNode node = objectMapper.readTree(body);
        assertTrue(node.get("usage_tracking_opt_in").asBoolean());
        assertTrue(node.get("maintenance_tracking_opt_in").asBoolean());
    }

    // ── 5. Rejects undefined fields in response ───────────────────────────────

    @Test
    @DisplayName("Registration response schema rejects unexpected fields")
    void testRegistrationSchemaRejectsUnexpectedFields() throws Exception {
        MvcResult regResult = mockMvc.perform(post("/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistrationPayload("sedan_mid")))
                .andExpect(status().isCreated())
                .andReturn();

        ObjectNode node = (ObjectNode) objectMapper.readTree(regResult.getResponse().getContentAsString());
        node.put("unregistered_field", "malicious_injection");

        Set<ValidationMessage> errors = registrationSchema.validate(node);
        assertFalse(errors.isEmpty(), "Schema validation must fail for undefined property");
        assertTrue(errors.stream().anyMatch(e -> e.getMessage().contains("unregistered_field")));
    }

    // ── 6. Exception handler 400 conforms to error-response schema ────────────

    @Test
    @DisplayName("Identity exception handler 400 conforms to error-response schema")
    void testExceptionHandler400ConformsToSchema() throws Exception {
        MvcResult result = mockMvc.perform(post("/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bad_field\": 123}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        validateError(result.getResponse().getContentAsString());
    }

    // ── 7. Exception handler 404 conforms to error-response schema ────────────

    @Test
    @DisplayName("Identity exception handler 404 conforms to error-response schema")
    void testExceptionHandler404ConformsToSchema() throws Exception {
        MvcResult result = mockMvc.perform(get("/vehicles/{vehicleId}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andReturn();

        validateError(result.getResponse().getContentAsString());
    }
}
