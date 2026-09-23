package com.paymu.pricing.controller;

import com.paymu.pricing.model.PremiumResponse;
import com.paymu.pricing.model.ScoreResponse;
import com.paymu.pricing.service.DownstreamOrchestratorService;
import com.paymu.pricing.service.PricingEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for the Pricing &amp; Policy service.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /quote/{vehicleId}} — compute a personalised premium quote</li>
 * </ul>
 *
 * <p>The {@code /quote} endpoint orchestrates three downstream calls:
 * telematics-usage-ingestion → trip aggregates,
 * maintenance-vehicle-health-ingestion → service timeline + recall status,
 * risk-scoring-engine → composite risk score.
 * It then applies the pricing formula and returns a
 * {@code premium-response.schema.json}-compliant payload.</p>
 */
@RestController
@RequestMapping
public class PricingController {

    private static final Logger log = LoggerFactory.getLogger(PricingController.class);

    private final PricingEngine pricingEngine;
    private final DownstreamOrchestratorService orchestrator;

    public PricingController(PricingEngine pricingEngine,
                             DownstreamOrchestratorService orchestrator) {
        this.pricingEngine = pricingEngine;
        this.orchestrator = orchestrator;
    }

    /**
     * Compute a premium quote for a vehicle.
     *
     * <p>Generates a new {@code request_id} and {@code policy_id} for each call.
     * In Phase 4 these will be persisted and linked to the vehicle registration.</p>
     */
    @PostMapping("/quote/{vehicleId}")
    public ResponseEntity<PremiumResponse> computeQuote(@PathVariable String vehicleId) {
        String requestId = UUID.randomUUID().toString();
        String policyId  = UUID.randomUUID().toString();

        log.info("Computing quote for vehicle {} (requestId={})", vehicleId, requestId);

        ScoreResponse score = orchestrator.fetchScore(vehicleId);

        Double usageScore       = score != null ? score.usageScore()       : null;
        Double maintenanceScore = score != null ? score.maintenanceScore()  : null;
        Double compositeScore   = score != null ? score.compositeScore()    : null;

        if (score == null) {
            log.info("No score available for vehicle {} — applying cold-start neutral pricing", vehicleId);
        }

        PremiumResponse response = pricingEngine.compute(
                vehicleId, requestId, policyId,
                usageScore, maintenanceScore, compositeScore);

        return ResponseEntity.ok(response);
    }
}
