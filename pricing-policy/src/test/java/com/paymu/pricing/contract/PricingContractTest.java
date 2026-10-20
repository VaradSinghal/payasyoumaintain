package com.paymu.pricing.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.paymu.pricing.controller.PricingController;
import com.paymu.pricing.model.ConsentEvaluation;
import com.paymu.pricing.model.PremiumResponse;
import com.paymu.pricing.model.ScoreResponse;
import com.paymu.pricing.service.DownstreamOrchestratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ResponseEntity;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@DisplayName("Pricing Contract Tests")
class PricingContractTest {

    private static final String SCHEMA_PATH = "/schemas/premium-response.schema.json";

    @MockBean
    private DownstreamOrchestratorService orchestrator;

    @Autowired
    private PricingController controller;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonSchema schema;

    @BeforeEach
    void loadSchema() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (InputStream is = getClass().getResourceAsStream(SCHEMA_PATH)) {
            assertNotNull(is, () -> "Contract schema not found on classpath: " + SCHEMA_PATH);
            schema = factory.getSchema(is);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load schema from " + SCHEMA_PATH, e);
        }
    }

    private void validateJsonAgainstSchema(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        Set<ValidationMessage> errors = schema.validate(node);
        assertTrue(errors.isEmpty(), () -> "Schema validation failed with errors: " + errors);
    }

    @Test
    @DisplayName("Path 1: Dynamic (scored) path conforms to premium-response.schema.json")
    void dynamicScoredPathConformsToSchema() throws Exception {
        String vehicleId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        UUID uuid = UUID.fromString(vehicleId);

        when(orchestrator.fetchPolicyId(vehicleId)).thenReturn("pol-dynamic-123");
        when(orchestrator.evaluateConsent(vehicleId)).thenReturn(ConsentEvaluation.permitted());
        when(orchestrator.fetchScore(eq(vehicleId), anyInt())).thenReturn(
                new ScoreResponse(
                        "score-dyn-1",
                        vehicleId,
                        85.0,
                        90.0,
                        87.5,
                        Instant.now().toString(),
                        "v1.2.0",
                        "scored",
                        90.0,
                        "verified",
                        "Eligible for preferred tier renewal.",
                        false,
                        List.of(),
                        List.of(
                                new ScoreResponse.ContributingFactor(
                                        "smooth_driving",
                                        0.05,
                                        "positive",
                                        "Consistent gentle acceleration and braking."
                                )
                        )
                )
        );

        ResponseEntity<PremiumResponse> response = controller.computeQuote(uuid, null);
        assertNotNull(response.getBody());

        String json = objectMapper.writeValueAsString(response.getBody());
        validateJsonAgainstSchema(json);
    }

    @Test
    @DisplayName("Path 2: Static not_evaluated (opted out) path conforms to premium-response.schema.json")
    void staticNotEvaluatedOptedOutConformsToSchema() throws Exception {
        String vehicleId = "b2c3d4e5-f6a7-8901-bcde-f12345678901";
        UUID uuid = UUID.fromString(vehicleId);

        when(orchestrator.fetchPolicyId(vehicleId)).thenReturn("pol-optout-123");
        when(orchestrator.evaluateConsent(vehicleId)).thenReturn(
                ConsentEvaluation.denied(List.of("consent_not_given_usage", "consent_not_given_maintenance"))
        );

        ResponseEntity<PremiumResponse> response = controller.computeQuote(uuid, null);
        assertNotNull(response.getBody());

        String json = objectMapper.writeValueAsString(response.getBody());
        validateJsonAgainstSchema(json);
    }

    @Test
    @DisplayName("Path 3: Cold start (insufficient_data) path conforms to premium-response.schema.json")
    void coldStartInsufficientDataConformsToSchema() throws Exception {
        String vehicleId = "e5f6a7b8-c9d0-1234-efab-345678901234";
        UUID uuid = UUID.fromString(vehicleId);

        when(orchestrator.fetchPolicyId(vehicleId)).thenReturn("pol-coldstart-123");
        when(orchestrator.evaluateConsent(vehicleId)).thenReturn(ConsentEvaluation.permitted());
        when(orchestrator.fetchScore(eq(vehicleId), anyInt())).thenReturn(
                new ScoreResponse(
                        "score-cold-1",
                        vehicleId,
                        null,
                        null,
                        null,
                        Instant.now().toString(),
                        "v1.2.0",
                        "insufficient_data",
                        null,
                        null,
                        "Schedule a baseline inspection to begin health tracking for this vehicle.",
                        false,
                        List.of("usage_data_unavailable"),
                        List.of(
                                new ScoreResponse.ContributingFactor(
                                        "insufficient_data",
                                        0.0,
                                        "neutral",
                                        "Not enough data to determine specific risk factors."
                                )
                        )
                )
        );

        ResponseEntity<PremiumResponse> response = controller.computeQuote(uuid, null);
        assertNotNull(response.getBody());

        String json = objectMapper.writeValueAsString(response.getBody());
        validateJsonAgainstSchema(json);
    }

    @Test
    @DisplayName("Path 4: Consent status unavailable path conforms to premium-response.schema.json")
    void consentStatusUnavailableConformsToSchema() throws Exception {
        String vehicleId = "c3d4e5f6-a7b8-9012-cdef-123456789012";
        UUID uuid = UUID.fromString(vehicleId);

        when(orchestrator.fetchPolicyId(vehicleId)).thenReturn("pol-unavail-123");
        when(orchestrator.evaluateConsent(vehicleId)).thenReturn(
                ConsentEvaluation.unavailable(List.of("consent_status_unavailable"))
        );

        ResponseEntity<PremiumResponse> response = controller.computeQuote(uuid, null);
        assertNotNull(response.getBody());

        String json = objectMapper.writeValueAsString(response.getBody());
        validateJsonAgainstSchema(json);
    }

    @Test
    @DisplayName("Schema validation fails if service emits a field the schema doesn't define")
    void schemaValidationFailsOnUnexpectedField() throws Exception {
        String vehicleId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        UUID uuid = UUID.fromString(vehicleId);

        when(orchestrator.fetchPolicyId(vehicleId)).thenReturn("pol-123");
        when(orchestrator.evaluateConsent(vehicleId)).thenReturn(ConsentEvaluation.permitted());
        when(orchestrator.fetchScore(eq(vehicleId), anyInt())).thenReturn(
                new ScoreResponse(
                        "score-test-1",
                        vehicleId,
                        80.0,
                        80.0,
                        80.0,
                        Instant.now().toString(),
                        "v1.2.0",
                        "scored",
                        80.0,
                        "verified",
                        "Renew",
                        false,
                        List.of(),
                        List.of()
                )
        );

        ResponseEntity<PremiumResponse> response = controller.computeQuote(uuid, null);
        assertNotNull(response.getBody());
        String json = objectMapper.writeValueAsString(response.getBody());

        // Verify valid first
        validateJsonAgainstSchema(json);

        // Inject an unexpected root field
        ObjectNode rootNode = (ObjectNode) objectMapper.readTree(json);
        rootNode.put("unexpected_root_field", "value");

        Set<ValidationMessage> rootErrors = schema.validate(rootNode);
        assertFalse(rootErrors.isEmpty(), "Schema must fail if service emits an unexpected root field");
        assertTrue(rootErrors.stream().anyMatch(e -> e.getMessage().contains("unexpected_root_field")),
                "Error should mention unexpected_root_field");

        // Test unexpected field in score_detail
        when(orchestrator.fetchScore(eq(vehicleId), anyInt())).thenReturn(
                new ScoreResponse(
                        "score-dyn-1",
                        vehicleId,
                        85.0,
                        90.0,
                        87.5,
                        Instant.now().toString(),
                        "v1.2.0",
                        "scored",
                        90.0,
                        "verified",
                        "Renew",
                        false,
                        List.of(),
                        List.of()
                )
        );
        ResponseEntity<PremiumResponse> scoreResponse = controller.computeQuote(uuid, null);
        assertNotNull(scoreResponse.getBody());
        String scoreJson = objectMapper.writeValueAsString(scoreResponse.getBody());
        ObjectNode scoreRootNode = (ObjectNode) objectMapper.readTree(scoreJson);
        ((ObjectNode) scoreRootNode.get("score_detail")).put("unexpected_score_detail_field", "drift");

        Set<ValidationMessage> scoreErrors = schema.validate(scoreRootNode);
        assertFalse(scoreErrors.isEmpty(), "Schema must fail if score_detail emits an unexpected field");
        assertTrue(scoreErrors.stream().anyMatch(e -> e.getMessage().contains("unexpected_score_detail_field")),
                "Error should mention unexpected_score_detail_field");
    }
}
