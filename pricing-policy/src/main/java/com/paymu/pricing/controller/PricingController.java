package com.paymu.pricing.controller;

import com.paymu.pricing.model.ConsentEvaluation;
import com.paymu.pricing.model.PremiumResponse;
import com.paymu.pricing.model.PricingBasis;
import com.paymu.pricing.model.ProfileLookupResult;
import com.paymu.pricing.model.ScoreResponse;
import com.paymu.pricing.model.VehicleCatalogueEntry;
import com.paymu.pricing.service.DownstreamOrchestratorService;
import com.paymu.pricing.service.PricingEngine;
import com.paymu.pricing.service.VehicleCatalogueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for the Pricing &amp; Policy service.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code GET /vehicle-catalogue} — retrieve indicative vehicle segments and ex-showroom prices</li>
 *   <li>{@code POST /quote/{vehicleId}} — compute a personalised premium quote</li>
 * </ul>
 */
@RestController
@RequestMapping
public class PricingController {

    private static final Logger log = LoggerFactory.getLogger(PricingController.class);

    private final PricingEngine pricingEngine;
    private final DownstreamOrchestratorService orchestrator;
    private final VehicleCatalogueService catalogueService;

    public PricingController(PricingEngine pricingEngine,
                             DownstreamOrchestratorService orchestrator,
                             VehicleCatalogueService catalogueService) {
        this.pricingEngine = pricingEngine;
        this.orchestrator = orchestrator;
        this.catalogueService = catalogueService;
    }

    /**
     * Retrieve the indicative vehicle catalogue mapping vehicle segments to ex-showroom prices in INR.
     */
    @GetMapping("/vehicle-catalogue")
    public ResponseEntity<List<VehicleCatalogueEntry>> getVehicleCatalogue() {
        return ResponseEntity.ok(catalogueService.getCatalogue());
    }

    /**
     * Compute a premium quote for a vehicle.
     */
    @PostMapping("/quote/{vehicleId}")
    public ResponseEntity<PremiumResponse> computeQuote(
            @PathVariable UUID vehicleId,
            @RequestParam(required = false, name = "policy_id") String policyId) {

        String requestId = UUID.randomUUID().toString();
        String vehicleIdStr = vehicleId.toString();

        // 1. Resolve vehicle profile from identity-consent (or in-memory cache)
        ProfileLookupResult profileLookup = orchestrator.fetchProfile(vehicleIdStr);

        // 2. Derive pricing basis according to the 4 outcomes (throws 503 if unavailable and no cache)
        PricingBasis pricingBasis = pricingEngine.derivePricingBasis(vehicleIdStr, profileLookup);

        // 3. Resolve policy ID
        if (policyId == null) {
            if (profileLookup.isFound() && profileLookup.profile() != null && profileLookup.profile().policyId() != null) {
                policyId = profileLookup.profile().policyId();
            } else {
                policyId = orchestrator.fetchPolicyId(vehicleIdStr);
            }
        }
        if (policyId == null) {
            policyId = UUID.randomUUID().toString();
        }

        // 4. Enforce tracking consent check before querying any tracking/service data
        ConsentEvaluation consent = orchestrator.evaluateConsent(vehicleIdStr);
        if (!consent.isPermitted()) {
            log.info("Consent not established for vehicle {} (flags={}) — applying static standard pricing",
                    vehicleIdStr, consent.dataFlags());
            PremiumResponse staticResponse = pricingEngine.computeStatic(
                    vehicleIdStr, requestId, policyId, pricingBasis, consent.dataFlags(), consent.isUnavailable()
            );
            return ResponseEntity.ok(staticResponse);
        }

        log.info("Computing dynamic quote for vehicle {} (requestId={})", vehicleIdStr, requestId);

        LocalDate regDate = profileLookup.isFound() ? profileLookup.profile().registrationDate() : null;
        int vehicleAgeMonths = pricingEngine.calculateAgeMonths(regDate);
        ScoreResponse score = orchestrator.fetchScore(vehicleIdStr, vehicleAgeMonths);

        if (score == null) {
            log.info("No score available for vehicle {} — applying cold-start neutral pricing", vehicleIdStr);
        }

        PremiumResponse response = pricingEngine.compute(vehicleIdStr, requestId, policyId, pricingBasis, score);
        return ResponseEntity.ok(response);
    }
}
