package com.paymu.pricing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymu.pricing.config.PricingProperties;
import com.paymu.pricing.exception.GlobalExceptionHandler;
import com.paymu.pricing.model.ConsentEvaluation;
import com.paymu.pricing.model.PremiumResponse;
import com.paymu.pricing.model.ProfileLookupResult;
import com.paymu.pricing.model.ScoreResponse;
import com.paymu.pricing.model.VehicleProfileResponse;
import com.paymu.pricing.service.DownstreamOrchestratorService;
import com.paymu.pricing.service.PricingEngine;
import com.paymu.pricing.service.VehicleCatalogueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("PricingController")
class PricingControllerTest {

    private PricingController controller;
    private PricingEngine pricingEngine;
    private DownstreamOrchestratorService orchestrator;
    private VehicleCatalogueService catalogueService;
    private Clock clock;
    private MockMvc mockMvc;

    private static final String A1B2_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-10-20T10:00:00Z"), ZoneId.of("Asia/Kolkata"));
        catalogueService = new VehicleCatalogueService(new ObjectMapper());
        catalogueService.init();

        PricingProperties properties = new PricingProperties();
        properties.setTimezone("Asia/Kolkata");
        properties.setProfileCacheTtlMinutes(60);
        properties.setDemoNcb(Map.of(
                A1B2_ID, 0.20
        ));

        pricingEngine = new PricingEngine(catalogueService, properties, clock);
        orchestrator = Mockito.mock(DownstreamOrchestratorService.class);
        controller = new PricingController(pricingEngine, orchestrator, catalogueService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(clock))
                .build();
    }

    @Test
    @DisplayName("GET /vehicle-catalogue returns 200 with 6 indicative vehicle segments")
    void getVehicleCatalogueReturns200WithEntries() throws Exception {
        mockMvc.perform(get("/vehicle-catalogue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].id").value("hatchback_compact"))
                .andExpect(jsonPath("$[0].ex_showroom_price_inr").value(800000))
                .andExpect(jsonPath("$[3].id").value("sedan_mid"))
                .andExpect(jsonPath("$[3].ex_showroom_price_inr").value(1200000));
    }

    @Test
    @DisplayName("Generated policy_id is stable across repeated calls for the same vehicle via identity-consent")
    void stablePolicyIdAcrossRepeatedCalls() {
        String vehicleId = A1B2_ID;
        UUID uuid = UUID.fromString(vehicleId);
        String stablePolicyId = "pol-12345-stable";

        VehicleProfileResponse profile = new VehicleProfileResponse(
                vehicleId, stablePolicyId, LocalDate.of(2024, 10, 20), "sedan_mid",
                "VIN", "MH1", "Make", "Model", "Var", 2024
        );
        when(orchestrator.fetchProfile(vehicleId)).thenReturn(ProfileLookupResult.found(profile));
        when(orchestrator.evaluateConsent(vehicleId)).thenReturn(ConsentEvaluation.permitted());
        when(orchestrator.fetchScore(org.mockito.ArgumentMatchers.eq(vehicleId), org.mockito.ArgumentMatchers.anyInt())).thenReturn(
                new ScoreResponse("s-1", vehicleId, 100.0, 100.0, 100.0, "now", "v1", "scored", 100.0, "verified", "renew", false, List.of(), List.of())
        );

        // First call
        ResponseEntity<PremiumResponse> response1 = controller.computeQuote(uuid, null);
        assertNotNull(response1.getBody());
        assertEquals(stablePolicyId, response1.getBody().policyId(), "policyId should match identity-consent profile");

        // Second call
        ResponseEntity<PremiumResponse> response2 = controller.computeQuote(uuid, null);
        assertNotNull(response2.getBody());
        assertEquals(stablePolicyId, response2.getBody().policyId(), "policyId should be stable across repeated calls");
    }

    @Test
    @DisplayName("POST /quote/not-a-uuid returns 400 with INVALID_REQUEST")
    void malformedUuidReturns400() throws Exception {
        mockMvc.perform(post("/quote/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("POST /quote/{validUuid} with permitted consent returns dynamic quote with pricing_basis")
    void validUuidReturns200() throws Exception {
        String vehicleId = A1B2_ID;
        VehicleProfileResponse profile = new VehicleProfileResponse(
                vehicleId, "pol-123", LocalDate.of(2024, 10, 20), "sedan_mid",
                "VIN", "MH1", "Hyundai", "Verna", "SX", 2024
        );
        when(orchestrator.fetchProfile(vehicleId)).thenReturn(ProfileLookupResult.found(profile));
        when(orchestrator.evaluateConsent(vehicleId)).thenReturn(ConsentEvaluation.permitted());
        when(orchestrator.fetchScore(org.mockito.ArgumentMatchers.eq(vehicleId), org.mockito.ArgumentMatchers.anyInt())).thenReturn(null);

        mockMvc.perform(post("/quote/" + vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle_id").value(vehicleId))
                .andExpect(jsonPath("$.base_premium").value(24960.0))
                .andExpect(jsonPath("$.pricing_basis.source").value("demo_profile"))
                .andExpect(jsonPath("$.pricing_basis.vehicle_segment_id").value("sedan_mid"))
                .andExpect(jsonPath("$.pricing_basis.ex_showroom_price_inr").value(1200000))
                .andExpect(jsonPath("$.pricing_basis.vehicle_age_years").value(2))
                .andExpect(jsonPath("$.pricing_basis.depreciation_factor").value(0.80))
                .andExpect(jsonPath("$.pricing_basis.idv").value(960000.0))
                .andExpect(jsonPath("$.pricing_basis.ncb_pct").value(0.20));

        Mockito.verify(orchestrator).fetchScore(org.mockito.ArgumentMatchers.eq(vehicleId), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("Opted-out vehicle routes to static path with zero data calls and not_evaluated status")
    void optedOutVehicle_returnsStaticPathWithZeroDataCalls() throws Exception {
        String vehicleId = A1B2_ID;
        VehicleProfileResponse profile = new VehicleProfileResponse(
                vehicleId, "pol-123", LocalDate.of(2024, 10, 20), "sedan_mid",
                "VIN", "MH1", "Hyundai", "Verna", "SX", 2024
        );
        when(orchestrator.fetchProfile(vehicleId)).thenReturn(ProfileLookupResult.found(profile));
        when(orchestrator.evaluateConsent(vehicleId)).thenReturn(
                ConsentEvaluation.denied(List.of("consent_not_given_usage", "consent_not_given_maintenance"))
        );

        mockMvc.perform(post("/quote/" + vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle_id").value(vehicleId))
                .andExpect(jsonPath("$.dynamic_multiplier").value(1.0))
                .andExpect(jsonPath("$.final_premium").value(19968.0))
                .andExpect(jsonPath("$.pricing_basis.source").value("demo_profile"))
                .andExpect(jsonPath("$.discount_breakdown[?(@.reason == 'standard_pricing_no_consent')].adjustment_pct").value(0.0))
                .andExpect(jsonPath("$.score_detail.health_status").value("not_evaluated"))
                .andExpect(jsonPath("$.score_detail.usage_score").doesNotExist())
                .andExpect(jsonPath("$.score_detail.maintenance_score").doesNotExist())
                .andExpect(jsonPath("$.score_detail.data_flags[0]").value("consent_not_given_usage"))
                .andExpect(jsonPath("$.score_detail.data_flags[1]").value("consent_not_given_maintenance"))
                .andExpect(jsonPath("$.score_detail.contributing_factors[0].factor_name").value("tracking_off"))
                .andExpect(jsonPath("$.score_detail.renewal_recommendation").value("Turn on usage and maintenance tracking to get a personalised premium and vehicle health recommendations."));

        Mockito.verify(orchestrator, Mockito.never()).fetchScore(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("Unregistered vehicle routes to static path with vehicle_not_registered flag and generic_default profile")
    void unregisteredVehicle_returnsStaticPath() throws Exception {
        String vehicleId = UUID.randomUUID().toString();
        when(orchestrator.fetchProfile(vehicleId)).thenReturn(ProfileLookupResult.unregistered());
        when(orchestrator.evaluateConsent(vehicleId)).thenReturn(
                ConsentEvaluation.denied(List.of("vehicle_not_registered"))
        );

        mockMvc.perform(post("/quote/" + vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle_id").value(vehicleId))
                .andExpect(jsonPath("$.final_premium").value(13650.0))
                .andExpect(jsonPath("$.pricing_basis.source").value("generic_default"))
                .andExpect(jsonPath("$.pricing_basis.ex_showroom_price_inr").value(750000))
                .andExpect(jsonPath("$.pricing_basis.vehicle_age_years").value(3))
                .andExpect(jsonPath("$.pricing_basis.depreciation_factor").value(0.70))
                .andExpect(jsonPath("$.score_detail.health_status").value("not_evaluated"))
                .andExpect(jsonPath("$.score_detail.data_flags[0]").value("vehicle_not_registered"));

        Mockito.verify(orchestrator, Mockito.never()).fetchScore(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("Consent unavailable routes to static path with consent_status_unavailable flag using profile")
    void consentUnavailable_returnsStaticPathWithUnavailableRecommendation() throws Exception {
        String vehicleId = A1B2_ID;
        VehicleProfileResponse profile = new VehicleProfileResponse(
                vehicleId, "pol-123", LocalDate.of(2024, 10, 20), "sedan_mid",
                "VIN", "MH1", "Hyundai", "Verna", "SX", 2024
        );
        when(orchestrator.fetchProfile(vehicleId)).thenReturn(ProfileLookupResult.found(profile));
        when(orchestrator.evaluateConsent(vehicleId)).thenReturn(
                ConsentEvaluation.unavailable(List.of("consent_status_unavailable"))
        );

        mockMvc.perform(post("/quote/" + vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.final_premium").value(19968.0))
                .andExpect(jsonPath("$.score_detail.health_status").value("not_evaluated"))
                .andExpect(jsonPath("$.score_detail.data_flags[0]").value("consent_status_unavailable"))
                .andExpect(jsonPath("$.score_detail.renewal_recommendation").value("We couldn't check your tracking preferences right now, so standard pricing is shown."));

        Mockito.verify(orchestrator, Mockito.never()).fetchScore(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("Outcome (d): Identity unavailable and no cached profile returns HTTP 503 SERVICE_UNAVAILABLE")
    void identityUnavailableAndNoCache_returns503() throws Exception {
        String randomUuid = UUID.randomUUID().toString();
        when(orchestrator.fetchProfile(randomUuid)).thenReturn(ProfileLookupResult.unavailable());

        mockMvc.perform(post("/quote/" + randomUuid))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
