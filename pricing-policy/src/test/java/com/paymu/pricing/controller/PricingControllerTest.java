package com.paymu.pricing.controller;

import com.paymu.pricing.model.PremiumResponse;
import com.paymu.pricing.model.ScoreResponse;
import com.paymu.pricing.service.DownstreamOrchestratorService;
import com.paymu.pricing.service.PricingEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DisplayName("PricingController")
class PricingControllerTest {

    private PricingController controller;
    private PricingEngine pricingEngine;
    private DownstreamOrchestratorService orchestrator;

    @BeforeEach
    void setUp() {
        pricingEngine = new PricingEngine();
        orchestrator = Mockito.mock(DownstreamOrchestratorService.class);
        controller = new PricingController(pricingEngine, orchestrator);
    }

    @Test
    @DisplayName("Generated policy_id is stable across repeated calls for the same vehicle via identity-consent")
    void stablePolicyIdAcrossRepeatedCalls() {
        String vehicleId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        String stablePolicyId = "pol-12345-stable";

        // Mock the orchestrator to return a stable policy ID from identity-consent
        when(orchestrator.fetchPolicyId(vehicleId)).thenReturn(stablePolicyId);
        // Mock the score so it doesn't fail
        when(orchestrator.fetchScore(vehicleId)).thenReturn(
                new ScoreResponse("s-1", vehicleId, 100.0, 100.0, 100.0, "now", "v1", List.of())
        );

        // First call
        ResponseEntity<PremiumResponse> response1 = controller.computeQuote(vehicleId, null);
        assertNotNull(response1.getBody());
        assertEquals(stablePolicyId, response1.getBody().policyId(), "policyId should match the one returned by identity-consent");

        // Second call
        ResponseEntity<PremiumResponse> response2 = controller.computeQuote(vehicleId, null);
        assertNotNull(response2.getBody());
        assertEquals(stablePolicyId, response2.getBody().policyId(), "policyId should be stable across repeated calls");
    }
}
