package com.paymu.pricing.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.paymu.pricing.controller.PricingController;
import com.paymu.pricing.model.*;
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
import java.time.LocalDate;
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

    private static final String PREMIUM_SCHEMA_PATH = "/schemas/premium-response.schema.json";
    private static final String CATALOGUE_SCHEMA_PATH = "/schemas/vehicle-catalogue.schema.json";

    @MockBean
    private DownstreamOrchestratorService orchestrator;

    @Autowired
    private PricingController controller;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonSchema premiumSchema;
    private JsonSchema catalogueSchema;

    @BeforeEach
    void loadSchemas() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (InputStream is = getClass().getResourceAsStream(PREMIUM_SCHEMA_PATH)) {
            assertNotNull(is, () -> "Contract schema not found on classpath: " + PREMIUM_SCHEMA_PATH);
            premiumSchema = factory.getSchema(is);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load schema from " + PREMIUM_SCHEMA_PATH, e);
        }

        try (InputStream is = getClass().getResourceAsStream(CATALOGUE_SCHEMA_PATH)) {
            assertNotNull(is, () -> "Contract schema not found on classpath: " + CATALOGUE_SCHEMA_PATH);
            catalogueSchema = factory.getSchema(is);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load schema from " + CATALOGUE_SCHEMA_PATH, e);
        }
    }

    private void validatePremiumJson(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        Set<ValidationMessage> errors = premiumSchema.validate(node);
        assertTrue(errors.isEmpty(), () -> "Premium schema validation failed with errors: " + errors);
    }

    private void validateCatalogueJson(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        Set<ValidationMessage> errors = catalogueSchema.validate(node);
        assertTrue(errors.isEmpty(), () -> "Catalogue schema validation failed with errors: " + errors);
    }

    @Test
    @DisplayName("Catalogue endpoint: GET /vehicle-catalogue serialised response conforms to vehicle-catalogue.schema.json")
    void vehicleCatalogueResponseConformsToSchema() throws Exception {
        ResponseEntity<List<VehicleCatalogueEntry>> response = controller.getVehicleCatalogue();
        assertNotNull(response.getBody());
        assertEquals(6, response.getBody().size());

        String json = objectMapper.writeValueAsString(response.getBody());
        validateCatalogueJson(json);
    }

    @Test
    @DisplayName("Source 1: 'demo_profile' path conforms to premium-response.schema.json")
    void demoProfileSourceConformsToSchema() throws Exception {
        String vehicleId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        UUID uuid = UUID.fromString(vehicleId);

        VehicleProfileResponse profile = new VehicleProfileResponse(
                vehicleId, "pol-demo-123", LocalDate.now().minusYears(2), "sedan_mid",
                "VIN", "MH1", "Hyundai", "Verna", "SX", 2024
        );

        when(orchestrator.fetchProfile(vehicleId)).thenReturn(ProfileLookupResult.found(profile));
        when(orchestrator.evaluateConsent(vehicleId)).thenReturn(ConsentEvaluation.permitted());
        when(orchestrator.fetchScore(eq(vehicleId), anyInt())).thenReturn(mockScore(vehicleId));

        ResponseEntity<PremiumResponse> response = controller.computeQuote(uuid, null);
        assertNotNull(response.getBody());
        assertEquals("demo_profile", response.getBody().pricingBasis().source());

        String json = objectMapper.writeValueAsString(response.getBody());
        validatePremiumJson(json);
    }

    @Test
    @DisplayName("Source 2: 'registered' non-demo source conforms to premium-response.schema.json")
    void registeredSourceConformsToSchema() throws Exception {
        String vehicleId = UUID.randomUUID().toString();
        UUID uuid = UUID.fromString(vehicleId);

        VehicleProfileResponse profile = new VehicleProfileResponse(
                vehicleId, "pol-reg-123", LocalDate.now().minusYears(2), "sedan_mid",
                "VIN", "MH1", "Hyundai", "Verna", "SX", 2024
        );

        when(orchestrator.fetchProfile(vehicleId)).thenReturn(ProfileLookupResult.found(profile));
        when(orchestrator.evaluateConsent(vehicleId)).thenReturn(ConsentEvaluation.permitted());
        when(orchestrator.fetchScore(eq(vehicleId), anyInt())).thenReturn(mockScore(vehicleId));

        ResponseEntity<PremiumResponse> response = controller.computeQuote(uuid, null);
        assertNotNull(response.getBody());
        assertEquals("registered", response.getBody().pricingBasis().source());

        String json = objectMapper.writeValueAsString(response.getBody());
        validatePremiumJson(json);
    }

    @Test
    @DisplayName("Source 3: 'generic_default' unregistered source conforms to premium-response.schema.json")
    void genericDefaultSourceConformsToSchema() throws Exception {
        String vehicleId = UUID.randomUUID().toString();
        UUID uuid = UUID.fromString(vehicleId);

        when(orchestrator.fetchProfile(vehicleId)).thenReturn(ProfileLookupResult.unregistered());
        when(orchestrator.evaluateConsent(vehicleId)).thenReturn(
                ConsentEvaluation.denied(List.of("vehicle_not_registered"))
        );

        ResponseEntity<PremiumResponse> response = controller.computeQuote(uuid, null);
        assertNotNull(response.getBody());
        assertEquals("generic_default", response.getBody().pricingBasis().source());
        assertNull(response.getBody().pricingBasis().vehicleSegmentId());

        String json = objectMapper.writeValueAsString(response.getBody());
        validatePremiumJson(json);
    }

    @Test
    @DisplayName("Static path: not_evaluated path includes pricing_basis and conforms to premium-response.schema.json")
    void staticPathConformsToSchema() throws Exception {
        String vehicleId = "b2c3d4e5-f6a7-8901-bcde-f12345678901";
        UUID uuid = UUID.fromString(vehicleId);

        VehicleProfileResponse profile = new VehicleProfileResponse(
                vehicleId, "pol-optout-123", LocalDate.now().minusYears(4), "hatchback_compact",
                "VIN", "MH2", "Maruti", "Swift", "ZXi", 2022
        );

        when(orchestrator.fetchProfile(vehicleId)).thenReturn(ProfileLookupResult.found(profile));
        when(orchestrator.evaluateConsent(vehicleId)).thenReturn(
                ConsentEvaluation.denied(List.of("consent_not_given_usage", "consent_not_given_maintenance"))
        );

        ResponseEntity<PremiumResponse> response = controller.computeQuote(uuid, null);
        assertNotNull(response.getBody());
        assertEquals("not_evaluated", response.getBody().scoreDetail().healthStatus());
        assertNotNull(response.getBody().pricingBasis(), "Static path must pass pricing_basis through");

        String json = objectMapper.writeValueAsString(response.getBody());
        validatePremiumJson(json);
    }

    @Test
    @DisplayName("Schema validation fails if service emits a field the schema doesn't define")
    void schemaValidationFailsOnUnexpectedField() throws Exception {
        String vehicleId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        UUID uuid = UUID.fromString(vehicleId);

        VehicleProfileResponse profile = new VehicleProfileResponse(
                vehicleId, "pol-123", LocalDate.now().minusYears(2), "sedan_mid",
                "VIN", "MH1", "Hyundai", "Verna", "SX", 2024
        );

        when(orchestrator.fetchProfile(vehicleId)).thenReturn(ProfileLookupResult.found(profile));
        when(orchestrator.evaluateConsent(vehicleId)).thenReturn(ConsentEvaluation.permitted());
        when(orchestrator.fetchScore(eq(vehicleId), anyInt())).thenReturn(mockScore(vehicleId));

        ResponseEntity<PremiumResponse> response = controller.computeQuote(uuid, null);
        assertNotNull(response.getBody());
        String json = objectMapper.writeValueAsString(response.getBody());

        // Valid first
        validatePremiumJson(json);

        // Inject unexpected root field
        ObjectNode rootNode = (ObjectNode) objectMapper.readTree(json);
        rootNode.put("unexpected_root_field", "value");

        Set<ValidationMessage> rootErrors = premiumSchema.validate(rootNode);
        assertFalse(rootErrors.isEmpty(), "Schema must fail if service emits an unexpected root field");
        assertTrue(rootErrors.stream().anyMatch(e -> e.getMessage().contains("unexpected_root_field")));

        // Inject unexpected field into pricing_basis
        ObjectNode pbNode = (ObjectNode) rootNode.get("pricing_basis");
        pbNode.put("unexpected_pricing_basis_field", "extra");

        Set<ValidationMessage> pbErrors = premiumSchema.validate(rootNode);
        assertFalse(pbErrors.isEmpty(), "Schema must fail if pricing_basis has unexpected field");
    }

    private ScoreResponse mockScore(String vehicleId) {
        return new ScoreResponse(
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
        );
    }
}
