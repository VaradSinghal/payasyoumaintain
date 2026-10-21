package com.paymu.advisory.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.paymu.advisory.controller.AdvisoryController;
import com.paymu.advisory.model.AdvisoryMessage;
import com.paymu.advisory.model.AdvisoryPriority;
import com.paymu.advisory.model.ConsentEvaluation;
import com.paymu.advisory.service.AdvisoryGeneratorService;
import com.paymu.advisory.service.DownstreamDataService;
import com.paymu.advisory.service.NotificationDeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdvisoryController.class)
@DisplayName("Notification Advisory Contract Tests")
class AdvisoryContractTest {

    private static final String ADVISORY_SCHEMA_PATH = "/schemas/advisory-response.schema.json";
    private static final String ERROR_SCHEMA_PATH = "/schemas/error-response.schema.json";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DownstreamDataService downstreamDataService;

    @MockBean
    private AdvisoryGeneratorService advisoryGeneratorService;

    @MockBean
    private NotificationDeliveryService deliveryService;

    private JsonSchema advisorySchema;
    private JsonSchema errorSchema;

    @BeforeEach
    void setUp() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (InputStream is = getClass().getResourceAsStream(ADVISORY_SCHEMA_PATH)) {
            assertNotNull(is, () -> "Contract schema not found on classpath: " + ADVISORY_SCHEMA_PATH);
            advisorySchema = factory.getSchema(is);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load schema from " + ADVISORY_SCHEMA_PATH, e);
        }

        try (InputStream is = getClass().getResourceAsStream(ERROR_SCHEMA_PATH)) {
            assertNotNull(is, () -> "Contract schema not found on classpath: " + ERROR_SCHEMA_PATH);
            errorSchema = factory.getSchema(is);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load schema from " + ERROR_SCHEMA_PATH, e);
        }
    }

    private void validateAdvisoryResponse(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        Set<ValidationMessage> errors = advisorySchema.validate(node);
        assertTrue(errors.isEmpty(), () -> "Advisory schema validation failed: " + errors);
    }

    private void validateErrorResponse(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        Set<ValidationMessage> errors = errorSchema.validate(node);
        assertTrue(errors.isEmpty(), () -> "Error schema validation failed: " + errors);
    }

    // ── 1. Every advisory type conforming to schema ───────────────────────────

    @Test
    @DisplayName("Advisory response with every active advisory type conforms to schema")
    void testEveryAdvisoryTypeConformsToSchema() throws Exception {
        UUID vehicleId = UUID.randomUUID();
        when(downstreamDataService.evaluateConsent(vehicleId.toString()))
                .thenReturn(ConsentEvaluation.permitted());

        List<AdvisoryMessage> allTypes = List.of(
                new AdvisoryMessage("OPEN_RECALL", AdvisoryPriority.CRITICAL, "Recall Alert", "Immediate repair needed", "https://oem.example.com/recall"),
                new AdvisoryMessage("OVERDUE_SERVICE", AdvisoryPriority.HIGH, "Service Overdue", "Vehicle is overdue for service", null),
                new AdvisoryMessage("LOW_RISK_SCORE", AdvisoryPriority.HIGH, "Risk Alert", "Driving risk elevated", null),
                new AdvisoryMessage("NO_SERVICE_RECORD", AdvisoryPriority.MEDIUM, "No Records", "No service events recorded yet", null),
                new AdvisoryMessage("SAFE_DRIVING_COMMENDATION", AdvisoryPriority.LOW, "Great Driving", "Keep up the safe driving", null),
                new AdvisoryMessage("TRACKING_DISABLED", AdvisoryPriority.MEDIUM, "Tracking Off", "Enable tracking for discounts", null)
        );
        when(advisoryGeneratorService.generate(any(), any(), any())).thenReturn(allTypes);

        MvcResult result = mockMvc.perform(get("/advisories/{vehicleId}", vehicleId))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        validateAdvisoryResponse(body);

        JsonNode node = objectMapper.readTree(body);
        assertEquals(6, node.get("advisories").size());
    }

    // ── 2. Empty advisories array ─────────────────────────────────────────────

    @Test
    @DisplayName("Empty advisory response conforms to schema")
    void testEmptyAdvisoryResponseConformsToSchema() throws Exception {
        UUID vehicleId = UUID.randomUUID();
        when(downstreamDataService.evaluateConsent(vehicleId.toString()))
                .thenReturn(ConsentEvaluation.permitted());
        when(advisoryGeneratorService.generate(any(), any(), any())).thenReturn(List.of());

        MvcResult result = mockMvc.perform(get("/advisories/{vehicleId}", vehicleId))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        validateAdvisoryResponse(body);

        JsonNode node = objectMapper.readTree(body);
        assertEquals(0, node.get("advisories").size());
    }

    // ── 3. TRACKING_DISABLED when opted out ───────────────────────────────────

    @Test
    @DisplayName("TRACKING_DISABLED response when opted out conforms to schema")
    void testTrackingDisabledOptedOutConformsToSchema() throws Exception {
        UUID vehicleId = UUID.randomUUID();
        when(downstreamDataService.evaluateConsent(vehicleId.toString()))
                .thenReturn(ConsentEvaluation.denied());

        MvcResult result = mockMvc.perform(get("/advisories/{vehicleId}", vehicleId))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        validateAdvisoryResponse(body);

        JsonNode node = objectMapper.readTree(body);
        assertEquals(1, node.get("advisories").size());
        assertEquals("TRACKING_DISABLED", node.get("advisories").get(0).get("advisory_type").asText());
    }

    // ── 4. Undefined fields rejected by schema ────────────────────────────────

    @Test
    @DisplayName("Advisory schema rejects unexpected additionalProperties")
    void testAdvisorySchemaRejectsUnexpectedFields() throws Exception {
        UUID vehicleId = UUID.randomUUID();
        when(downstreamDataService.evaluateConsent(vehicleId.toString()))
                .thenReturn(ConsentEvaluation.permitted());
        when(advisoryGeneratorService.generate(any(), any(), any())).thenReturn(List.of(
                new AdvisoryMessage("SAFE_DRIVING_COMMENDATION", AdvisoryPriority.LOW, "Commendation", "Safe driving", null)
        ));

        MvcResult result = mockMvc.perform(get("/advisories/{vehicleId}", vehicleId))
                .andExpect(status().isOk())
                .andReturn();

        ObjectNode node = (ObjectNode) objectMapper.readTree(result.getResponse().getContentAsString());
        node.put("unexpected_custom_field", "forbidden");

        Set<ValidationMessage> errors = advisorySchema.validate(node);
        assertFalse(errors.isEmpty(), "Schema validation must fail on unexpected property");
        assertTrue(errors.stream().anyMatch(e -> e.getMessage().contains("unexpected_custom_field")));
    }

    // ── 5. Exception handler 400 Bad Request conforms to error-response ───────

    @Test
    @DisplayName("Advisory exception handler 400 conforms to error-response schema")
    void testExceptionHandler400ConformsToSchema() throws Exception {
        MvcResult result = mockMvc.perform(get("/advisories/not-a-valid-uuid"))
                .andExpect(status().isBadRequest())
                .andReturn();

        validateErrorResponse(result.getResponse().getContentAsString());
    }

    // ── 6. Exception handler 404 Not Found conforms to error-response ─────────

    @Test
    @DisplayName("Advisory exception handler 404 conforms to error-response schema")
    void testExceptionHandler404ConformsToSchema() throws Exception {
        MvcResult result = mockMvc.perform(get("/non-existent-advisory-endpoint"))
                .andExpect(status().isNotFound())
                .andReturn();

        validateErrorResponse(result.getResponse().getContentAsString());
    }
}
