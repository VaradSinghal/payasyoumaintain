package com.paymu.pricing.controller;

import com.paymu.pricing.exception.GlobalExceptionHandler;
import com.paymu.pricing.model.PremiumResponse;
import com.paymu.pricing.model.ScoreResponse;
import com.paymu.pricing.service.DownstreamOrchestratorService;
import com.paymu.pricing.service.PricingEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("PricingController")
class PricingControllerTest {

    private PricingController controller;
    private PricingEngine pricingEngine;
    private DownstreamOrchestratorService orchestrator;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        pricingEngine = new PricingEngine();
        orchestrator = Mockito.mock(DownstreamOrchestratorService.class);
        controller = new PricingController(pricingEngine, orchestrator);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Generated policy_id is stable across repeated calls for the same vehicle via identity-consent")
    void stablePolicyIdAcrossRepeatedCalls() {
        String vehicleId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        UUID uuid = UUID.fromString(vehicleId);
        String stablePolicyId = "pol-12345-stable";

        // Mock the orchestrator to return a stable policy ID from identity-consent
        when(orchestrator.fetchPolicyId(vehicleId)).thenReturn(stablePolicyId);
        when(orchestrator.evaluateConsent(vehicleId)).thenReturn(com.paymu.pricing.model.ConsentEvaluation.permitted());
        // Mock the score so it doesn't fail
        when(orchestrator.fetchScore(org.mockito.ArgumentMatchers.eq(vehicleId), org.mockito.ArgumentMatchers.anyInt())).thenReturn(
                new ScoreResponse("s-1", vehicleId, 100.0, 100.0, 100.0, "now", "v1", "scored", 100.0, "verified", "renew", false, List.of(), List.of())
        );

        // First call
        ResponseEntity<PremiumResponse> response1 = controller.computeQuote(uuid, null);
        assertNotNull(response1.getBody());
        assertEquals(stablePolicyId, response1.getBody().policyId(), "policyId should match the one returned by identity-consent");

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
    @DisplayName("POST /quote/{validUuid} with permitted consent returns dynamic quote")
    void validUuidReturns200() throws Exception {
        String vehicleId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        when(orchestrator.fetchPolicyId(vehicleId)).thenReturn("pol-123");
        when(orchestrator.evaluateConsent(vehicleId)).thenReturn(com.paymu.pricing.model.ConsentEvaluation.permitted());
        when(orchestrator.fetchScore(org.mockito.ArgumentMatchers.eq(vehicleId), org.mockito.ArgumentMatchers.anyInt())).thenReturn(null);

        mockMvc.perform(post("/quote/" + vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle_id").value(vehicleId))
                .andExpect(jsonPath("$.base_premium").isNumber());

        Mockito.verify(orchestrator).fetchScore(org.mockito.ArgumentMatchers.eq(vehicleId), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("Opted-out vehicle routes to static path with zero data calls and not_evaluated status")
    void optedOutVehicle_returnsStaticPathWithZeroDataCalls() throws Exception {
        String vehicleId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        when(orchestrator.fetchPolicyId(vehicleId)).thenReturn("pol-123");
        when(orchestrator.evaluateConsent(vehicleId)).thenReturn(
                com.paymu.pricing.model.ConsentEvaluation.denied(List.of("consent_not_given_usage", "consent_not_given_maintenance"))
        );

        mockMvc.perform(post("/quote/" + vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle_id").value(vehicleId))
                .andExpect(jsonPath("$.dynamic_multiplier").value(1.0))
                .andExpect(jsonPath("$.final_premium").value(19968.0))
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
    @DisplayName("Unregistered vehicle routes to static path with vehicle_not_registered flag and zero data calls")
    void unregisteredVehicle_returnsStaticPath() throws Exception {
        String vehicleId = UUID.randomUUID().toString();
        when(orchestrator.fetchPolicyId(vehicleId)).thenReturn("pol-unreg");
        when(orchestrator.evaluateConsent(vehicleId)).thenReturn(
                com.paymu.pricing.model.ConsentEvaluation.denied(List.of("vehicle_not_registered"))
        );

        mockMvc.perform(post("/quote/" + vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle_id").value(vehicleId))
                .andExpect(jsonPath("$.final_premium").value(13650.0))
                .andExpect(jsonPath("$.score_detail.health_status").value("not_evaluated"))
                .andExpect(jsonPath("$.score_detail.data_flags[0]").value("vehicle_not_registered"));

        Mockito.verify(orchestrator, Mockito.never()).fetchScore(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("Consent unavailable routes to static path with consent_status_unavailable flag and fail-closed recommendation")
    void consentUnavailable_returnsStaticPathWithUnavailableRecommendation() throws Exception {
        String vehicleId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        when(orchestrator.fetchPolicyId(vehicleId)).thenReturn("pol-123");
        when(orchestrator.evaluateConsent(vehicleId)).thenReturn(
                com.paymu.pricing.model.ConsentEvaluation.unavailable(List.of("consent_status_unavailable"))
        );

        mockMvc.perform(post("/quote/" + vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score_detail.health_status").value("not_evaluated"))
                .andExpect(jsonPath("$.score_detail.data_flags[0]").value("consent_status_unavailable"))
                .andExpect(jsonPath("$.score_detail.renewal_recommendation").value("We couldn't check your tracking preferences right now, so standard pricing is shown."));

        Mockito.verify(orchestrator, Mockito.never()).fetchScore(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
    }
}
