package com.paymu.maintenance.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.paymu.maintenance.client.ConsentRecord;
import com.paymu.maintenance.client.IdentityClient;
import com.paymu.maintenance.client.VehicleRegistrationResponse;
import com.paymu.maintenance.controller.ServiceRecordController;
import com.paymu.maintenance.exception.ConsentRequiredException;
import com.paymu.maintenance.exception.GlobalExceptionHandler;
import com.paymu.maintenance.exception.IdentityServiceUnavailableException;
import com.paymu.maintenance.exception.VehicleNotRegisteredException;
import com.paymu.maintenance.model.ConsistencyCheck;
import com.paymu.maintenance.model.VerificationResult;
import com.paymu.maintenance.service.ConsistencyCheckService;
import com.paymu.maintenance.service.MaintenanceStore;
import com.paymu.maintenance.service.SchemaValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ServiceRecordController.class)
@DisplayName("Service Record Controller Contract Tests")
class ServiceRecordContractTest {

    private static final String RESPONSE_SCHEMA_PATH = "/schemas/service-record-response.schema.json";
    private static final String ERROR_SCHEMA_PATH = "/schemas/error-response.schema.json";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SchemaValidationService validationService;

    @MockBean
    private IdentityClient identityClient;

    @MockBean
    private ConsistencyCheckService consistencyCheckService;

    @MockBean
    private MaintenanceStore store;

    @MockBean
    private Clock clock;

    private JsonSchema responseSchema;
    private JsonSchema errorSchema;

    @BeforeEach
    void setUp() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (InputStream is = getClass().getResourceAsStream(RESPONSE_SCHEMA_PATH)) {
            assertNotNull(is);
            responseSchema = factory.getSchema(is);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load schema " + RESPONSE_SCHEMA_PATH, e);
        }

        try (InputStream is = getClass().getResourceAsStream(ERROR_SCHEMA_PATH)) {
            assertNotNull(is);
            errorSchema = factory.getSchema(is);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load schema " + ERROR_SCHEMA_PATH, e);
        }

        when(clock.instant()).thenReturn(Instant.parse("2026-10-21T06:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Kolkata"));
    }

    private void validateResponseContract(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        Set<ValidationMessage> errors = responseSchema.validate(node);
        assertTrue(errors.isEmpty(), () -> "Service record response schema validation failed: " + errors);
    }

    private void validateErrorContract(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        Set<ValidationMessage> errors = errorSchema.validate(node);
        assertTrue(errors.isEmpty(), () -> "Error response schema validation failed: " + errors);
    }

    private List<ConsistencyCheck> makeChecks(boolean allPassed, boolean hardFailed) {
        return List.of(
                new ConsistencyCheck("date_not_future", !hardFailed, hardFailed ? "Future" : "OK"),
                new ConsistencyCheck("date_after_registration", true, "OK"),
                new ConsistencyCheck("odometer_monotonic", allPassed, allPassed ? "OK" : "Odometer backward"),
                new ConsistencyCheck("odometer_rate_plausible", true, "OK"),
                new ConsistencyCheck("not_duplicate", true, "OK"),
                new ConsistencyCheck("daily_limit", true, "OK")
        );
    }

    // ── 1. Contract Tests for Outcomes (accepted, flagged, rejected) ──────────

    @Test
    @DisplayName("Outcome: accepted returns HTTP 201 and conforms to service-record-response schema")
    void testAcceptedOutcomeConformsToContract() throws Exception {
        UUID vid = UUID.randomUUID();
        when(validationService.validateServiceRecordRequest(any())).thenReturn(Collections.emptySet());
        when(identityClient.getVehicleRegistration(vid))
                .thenReturn(new VehicleRegistrationResponse(vid, LocalDate.of(2024, 1, 1), "POL-1"));
        when(identityClient.getConsent(vid))
                .thenReturn(new ConsentRecord(vid, true, true));
        when(store.getEventsByVehicle(vid.toString()))
                .thenReturn(List.of());

        VerificationResult accepted = new VerificationResult("accepted", makeChecks(true, false));
        when(consistencyCheckService.evaluate(any(), any(), any())).thenReturn(accepted);

        String requestJson = """
                {
                    "vehicle_id": "%s",
                    "service_date": "2026-09-20",
                    "service_type": "oil_change",
                    "odometer_km": 15000,
                    "notes": "Routine oil change"
                }
                """.formatted(vid);

        MvcResult result = mockMvc.perform(post("/service-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        validateResponseContract(body);

        JsonNode node = objectMapper.readTree(body);
        assertEquals("accepted", node.get("verification").get("status").asText());
        assertTrue(node.get("stored").asBoolean());
        assertEquals("self_upload", node.get("source").asText());
    }

    @Test
    @DisplayName("Outcome: flagged (soft failure) returns HTTP 201 and conforms to service-record-response schema")
    void testFlaggedOutcomeConformsToContract() throws Exception {
        UUID vid = UUID.randomUUID();
        when(validationService.validateServiceRecordRequest(any())).thenReturn(Collections.emptySet());
        when(identityClient.getVehicleRegistration(vid))
                .thenReturn(new VehicleRegistrationResponse(vid, LocalDate.of(2024, 1, 1), "POL-1"));
        when(identityClient.getConsent(vid))
                .thenReturn(new ConsentRecord(vid, true, true));

        VerificationResult flagged = new VerificationResult("flagged", makeChecks(false, false));
        when(consistencyCheckService.evaluate(any(), any(), any())).thenReturn(flagged);

        String requestJson = """
                {
                    "vehicle_id": "%s",
                    "service_date": "2026-10-10",
                    "service_type": "brake_service",
                    "odometer_km": 12000
                }
                """.formatted(vid);

        MvcResult result = mockMvc.perform(post("/service-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        validateResponseContract(body);

        JsonNode node = objectMapper.readTree(body);
        assertEquals("flagged", node.get("verification").get("status").asText());
        assertTrue(node.get("stored").asBoolean());
    }

    @Test
    @DisplayName("Outcome: rejected (hard failure) returns HTTP 200 with stored=false and conforms to schema")
    void testRejectedOutcomeConformsToContract() throws Exception {
        UUID vid = UUID.randomUUID();
        when(validationService.validateServiceRecordRequest(any())).thenReturn(Collections.emptySet());
        when(identityClient.getVehicleRegistration(vid))
                .thenReturn(new VehicleRegistrationResponse(vid, LocalDate.of(2024, 1, 1), "POL-1"));
        when(identityClient.getConsent(vid))
                .thenReturn(new ConsentRecord(vid, true, true));

        VerificationResult rejected = new VerificationResult("rejected", makeChecks(false, true));
        when(consistencyCheckService.evaluate(any(), any(), any())).thenReturn(rejected);

        String requestJson = """
                {
                    "vehicle_id": "%s",
                    "service_date": "2026-10-22",
                    "service_type": "oil_change",
                    "odometer_km": 15000
                }
                """.formatted(vid);

        MvcResult result = mockMvc.perform(post("/service-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        validateResponseContract(body);

        JsonNode node = objectMapper.readTree(body);
        assertEquals("rejected", node.get("verification").get("status").asText());
        assertFalse(node.get("stored").asBoolean());
    }

    // ── 2. Identity and Boundary Error Paths ──────────────────────────────────

    @Test
    @DisplayName("400: boundary validation failure returns INVALID_REQUEST")
    void testBoundaryValidationFailure() throws Exception {
        UUID vid = UUID.randomUUID();
        // Missing required odometer_km
        String requestJson = """
                {
                    "vehicle_id": "%s",
                    "service_date": "2026-09-20",
                    "service_type": "oil_change"
                }
                """.formatted(vid);

        // Real schema validation for request:
        SchemaValidationService realValidator = new SchemaValidationService();
        JsonNode node = objectMapper.readTree(requestJson);
        Set<ValidationMessage> errs = realValidator.validateServiceRecordRequest(node);
        assertFalse(errs.isEmpty());
        when(validationService.validateServiceRecordRequest(any())).thenReturn(errs);

        MvcResult result = mockMvc.perform(post("/service-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andReturn();

        validateErrorContract(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("404: unregistered vehicle returns VEHICLE_NOT_REGISTERED")
    void testUnregisteredVehicleReturns404() throws Exception {
        UUID vid = UUID.randomUUID();
        when(validationService.validateServiceRecordRequest(any())).thenReturn(Collections.emptySet());
        when(identityClient.getVehicleRegistration(vid))
                .thenThrow(new VehicleNotRegisteredException("Vehicle " + vid + " is not registered."));

        String requestJson = """
                {
                    "vehicle_id": "%s",
                    "service_date": "2026-09-20",
                    "service_type": "oil_change",
                    "odometer_km": 15000
                }
                """.formatted(vid);

        MvcResult result = mockMvc.perform(post("/service-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("VEHICLE_NOT_REGISTERED"))
                .andReturn();

        validateErrorContract(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("403: consent required returns CONSENT_REQUIRED")
    void testConsentRequiredReturns403() throws Exception {
        UUID vid = UUID.randomUUID();
        when(validationService.validateServiceRecordRequest(any())).thenReturn(Collections.emptySet());
        when(identityClient.getVehicleRegistration(vid))
                .thenReturn(new VehicleRegistrationResponse(vid, LocalDate.of(2024, 1, 1), "POL-1"));
        when(identityClient.getConsent(vid))
                .thenThrow(new ConsentRequiredException("Maintenance tracking consent is required to submit service records."));

        String requestJson = """
                {
                    "vehicle_id": "%s",
                    "service_date": "2026-09-20",
                    "service_type": "oil_change",
                    "odometer_km": 15000
                }
                """.formatted(vid);

        MvcResult result = mockMvc.perform(post("/service-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("CONSENT_REQUIRED"))
                .andReturn();

        validateErrorContract(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("503: identity unreachable returns SERVICE_UNAVAILABLE")
    void testIdentityUnreachableReturns503() throws Exception {
        UUID vid = UUID.randomUUID();
        when(validationService.validateServiceRecordRequest(any())).thenReturn(Collections.emptySet());
        when(identityClient.getVehicleRegistration(vid))
                .thenThrow(new IdentityServiceUnavailableException("Identity service unreachable or returned 5xx."));

        String requestJson = """
                {
                    "vehicle_id": "%s",
                    "service_date": "2026-09-20",
                    "service_type": "oil_change",
                    "odometer_km": 15000
                }
                """.formatted(vid);

        MvcResult result = mockMvc.perform(post("/service-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"))
                .andReturn();

        validateErrorContract(result.getResponse().getContentAsString());
    }
}
