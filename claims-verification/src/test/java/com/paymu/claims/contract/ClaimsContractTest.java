package com.paymu.claims.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.paymu.claims.model.*;
import com.paymu.claims.service.ClaimStore;
import com.paymu.claims.service.IdentityClient;
import com.paymu.claims.service.MaintenanceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Claims Verification Contract Tests")
class ClaimsContractTest {

    private static final String FNOL_SCHEMA_PATH = "/schemas/fnol-response.schema.json";
    private static final String ERROR_SCHEMA_PATH = "/schemas/error-response.schema.json";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClaimStore claimStore;

    @MockBean
    private MaintenanceClient maintenanceClient;

    @MockBean
    private IdentityClient identityClient;

    private JsonSchema fnolResponseSchema;
    private JsonSchema errorResponseSchema;

    @BeforeEach
    void setUp() {
        claimStore.clear();

        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (InputStream is = getClass().getResourceAsStream(FNOL_SCHEMA_PATH)) {
            assertNotNull(is, () -> "Contract schema not found on classpath: " + FNOL_SCHEMA_PATH);
            fnolResponseSchema = factory.getSchema(is);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load schema from " + FNOL_SCHEMA_PATH, e);
        }

        try (InputStream is = getClass().getResourceAsStream(ERROR_SCHEMA_PATH)) {
            assertNotNull(is, () -> "Contract schema not found on classpath: " + ERROR_SCHEMA_PATH);
            errorResponseSchema = factory.getSchema(is);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load schema from " + ERROR_SCHEMA_PATH, e);
        }

        when(identityClient.verifyPolicyBinding(anyString(), anyString())).thenReturn(PolicyBindingResult.VERIFIED);
    }

    private void validateFnolResponse(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        Set<ValidationMessage> errors = fnolResponseSchema.validate(node);
        assertTrue(errors.isEmpty(), () -> "FNOL response schema validation failed: " + errors);
    }

    private void validateErrorResponse(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        Set<ValidationMessage> errors = errorResponseSchema.validate(node);
        assertTrue(errors.isEmpty(), () -> "Error response schema validation failed: " + errors);
    }

    private String validFnolPayload(String cause) {
        return """
                {
                  "vehicle_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                  "policy_id": "c4632682-d406-3102-86b7-6a22ef09af5d",
                  "incident_date": "%s",
                  "incident_description": "Engine suddenly stalled on highway",
                  "claimed_cause": "%s",
                  "photo_refs": ["https://storage.paym.app/claims/photo1.jpg"],
                  "location": "Bengaluru"
                }
                """.formatted(LocalDate.now().toString(), cause);
    }

    // ── 1. Straight-through triage path ───────────────────────────────────────

    @Test
    @DisplayName("FNOL response conforms to contract: Straight-Through path")
    void testStraightThroughTriageConformsToSchema() throws Exception {
        when(maintenanceClient.fetchTimeline(anyString())).thenReturn(null);
        when(maintenanceClient.fetchRecallStatus(anyString())).thenReturn(
                new RecallStatus("a1b2c3d4-e5f6-7890-abcd-ef1234567890", false, 0, List.of(), "oem_recall_db", "2026-10-11T00:00:00Z")
        );

        MvcResult result = mockMvc.perform(post("/fnol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validFnolPayload("mechanical_failure")))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        validateFnolResponse(body);
        JsonNode node = objectMapper.readTree(body);
        assertEquals("STRAIGHT_THROUGH_PROCESSING", node.get("triage_decision").asText());
        assertTrue(node.get("triage_reason_codes").toString().contains("no_recent_clean_service"));
    }

    // ── 2. Manual review on clean service path ────────────────────────────────

    @Test
    @DisplayName("FNOL response conforms to contract: Clean Service manual review path")
    void testCleanServiceManualReviewConformsToSchema() throws Exception {
        ServiceTimeline.MaintenanceEvent cleanEvent = new ServiceTimeline.MaintenanceEvent(
                "evt-1",
                "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                LocalDate.now().minusDays(10).toString(),
                "oil_change",
                25000,
                "oem_api",
                null
        );
        ServiceTimeline tl = new ServiceTimeline("a1b2c3d4-e5f6-7890-abcd-ef1234567890", 1, cleanEvent.serviceDate(), 25000, List.of(cleanEvent));

        when(maintenanceClient.fetchTimeline(anyString())).thenReturn(tl);
        when(maintenanceClient.fetchRecallStatus(anyString())).thenReturn(
                new RecallStatus("a1b2c3d4-e5f6-7890-abcd-ef1234567890", false, 0, List.of(), "oem_recall_db", "2026-10-11T00:00:00Z")
        );

        MvcResult result = mockMvc.perform(post("/fnol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validFnolPayload("mechanical_failure")))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        validateFnolResponse(body);
        JsonNode node = objectMapper.readTree(body);
        assertEquals("MANUAL_REVIEW", node.get("triage_decision").asText());
        assertTrue(node.get("triage_reason_codes").toString().contains("recent_clean_service"));
    }

    // ── 3. Manual review on open recall path ──────────────────────────────────

    @Test
    @DisplayName("FNOL response conforms to contract: Open Recall manual review path")
    void testOpenRecallManualReviewConformsToSchema() throws Exception {
        when(maintenanceClient.fetchTimeline(anyString())).thenReturn(null);
        when(maintenanceClient.fetchRecallStatus(anyString())).thenReturn(
                new RecallStatus("a1b2c3d4-e5f6-7890-abcd-ef1234567890", true, 1,
                        List.of(new RecallStatus.RecallDetail("RC-101", "Brake booster defect", "2026-01-01")),
                        "oem_recall_db", "2026-10-11T00:00:00Z")
        );

        MvcResult result = mockMvc.perform(post("/fnol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validFnolPayload("mechanical_failure")))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        validateFnolResponse(body);
        JsonNode node = objectMapper.readTree(body);
        assertEquals("MANUAL_REVIEW", node.get("triage_decision").asText());
        assertTrue(node.get("triage_reason_codes").toString().contains("open_recall_on_file"));
    }

    // ── 4. Manual review on recall unavailable path ───────────────────────────

    @Test
    @DisplayName("FNOL response conforms to contract: Recall Unavailable manual review path")
    void testRecallUnavailableManualReviewConformsToSchema() throws Exception {
        when(maintenanceClient.fetchTimeline(anyString())).thenReturn(null);
        when(maintenanceClient.fetchRecallStatus(anyString())).thenReturn(null);

        MvcResult result = mockMvc.perform(post("/fnol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validFnolPayload("mechanical_failure")))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        validateFnolResponse(body);
        JsonNode node = objectMapper.readTree(body);
        assertEquals("MANUAL_REVIEW", node.get("triage_decision").asText());
        assertTrue(node.get("triage_reason_codes").toString().contains("recall_status_unavailable"));
    }

    // ── 5. Manual review on binding unverified path ───────────────────────────

    @Test
    @DisplayName("FNOL response conforms to contract: Binding Unverified manual review path")
    void testBindingUnverifiedManualReviewConformsToSchema() throws Exception {
        when(identityClient.verifyPolicyBinding(anyString(), anyString())).thenReturn(PolicyBindingResult.UNVERIFIED);
        when(maintenanceClient.fetchTimeline(anyString())).thenReturn(null);
        when(maintenanceClient.fetchRecallStatus(anyString())).thenReturn(
                new RecallStatus("a1b2c3d4-e5f6-7890-abcd-ef1234567890", false, 0, List.of(), "oem_recall_db", "2026-10-11T00:00:00Z")
        );

        MvcResult result = mockMvc.perform(post("/fnol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validFnolPayload("collision")))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        validateFnolResponse(body);
        JsonNode node = objectMapper.readTree(body);
        assertEquals("MANUAL_REVIEW", node.get("triage_decision").asText());
        assertTrue(node.get("triage_reason_codes").toString().contains("policy_binding_unverified"));
    }

    // ── 6. GET claim body conforms to schema ─────────────────────────────────

    @Test
    @DisplayName("GET /claims/{claimId} conforms to fnol-response schema")
    void testGetClaimConformsToSchema() throws Exception {
        when(maintenanceClient.fetchTimeline(anyString())).thenReturn(null);
        when(maintenanceClient.fetchRecallStatus(anyString())).thenReturn(
                new RecallStatus("a1b2c3d4-e5f6-7890-abcd-ef1234567890", false, 0, List.of(), "oem_recall_db", "2026-10-11T00:00:00Z")
        );

        MvcResult createResult = mockMvc.perform(post("/fnol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validFnolPayload("mechanical_failure")))
                .andExpect(status().isCreated())
                .andReturn();

        String claimId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("claim_id").asText();

        MvcResult getResult = mockMvc.perform(get("/claims/{claimId}", claimId))
                .andExpect(status().isOk())
                .andReturn();

        validateFnolResponse(getResult.getResponse().getContentAsString());
    }

    // ── 7. Undefined field rejection test ─────────────────────────────────────

    @Test
    @DisplayName("fnol-response schema rejects payloads with undefined additionalProperties")
    void testFnolResponseSchemaRejectsUndefinedFields() throws Exception {
        when(maintenanceClient.fetchTimeline(anyString())).thenReturn(null);
        when(maintenanceClient.fetchRecallStatus(anyString())).thenReturn(
                new RecallStatus("a1b2c3d4-e5f6-7890-abcd-ef1234567890", false, 0, List.of(), "oem_recall_db", "2026-10-11T00:00:00Z")
        );

        MvcResult result = mockMvc.perform(post("/fnol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validFnolPayload("mechanical_failure")))
                .andExpect(status().isCreated())
                .andReturn();

        ObjectNode node = (ObjectNode) objectMapper.readTree(result.getResponse().getContentAsString());
        node.put("unexpected_injected_field", "forbidden_value");

        Set<ValidationMessage> errors = fnolResponseSchema.validate(node);
        assertFalse(errors.isEmpty(), "Schema validation must fail when undefined field is present");
        assertTrue(errors.stream().anyMatch(e -> e.getMessage().contains("unexpected_injected_field")));
    }

    // ── 8. Exception handler: 400 Bad Request conforms to error-response ──────

    @Test
    @DisplayName("Exception handler 400 conforms to error-response schema")
    void testExceptionHandler400ConformsToSchema() throws Exception {
        MvcResult result = mockMvc.perform(post("/fnol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bad_json\": true}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        validateErrorResponse(result.getResponse().getContentAsString());
    }

    // ── 9. Exception handler: 404 Not Found conforms to error-response ────────

    @Test
    @DisplayName("Exception handler 404 conforms to error-response schema")
    void testExceptionHandler404ConformsToSchema() throws Exception {
        MvcResult result = mockMvc.perform(get("/claims/{claimId}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andReturn();

        validateErrorResponse(result.getResponse().getContentAsString());
    }

    // ── 10. Exception handler: 422 Unprocessable conforms to error-response ───

    @Test
    @DisplayName("Exception handler 422 conforms to error-response schema")
    void testExceptionHandler422ConformsToSchema() throws Exception {
        String futureDatePayload = """
                {
                  "vehicle_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                  "policy_id": "c4632682-d406-3102-86b7-6a22ef09af5d",
                  "incident_date": "2099-01-01",
                  "incident_description": "Future accident",
                  "claimed_cause": "mechanical_failure"
                }
                """;

        MvcResult result = mockMvc.perform(post("/fnol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(futureDatePayload))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        validateErrorResponse(result.getResponse().getContentAsString());
    }
}
