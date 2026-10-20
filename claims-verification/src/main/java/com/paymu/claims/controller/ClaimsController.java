package com.paymu.claims.controller;

import com.paymu.claims.model.FnolRequest;
import com.paymu.claims.model.FnolResponse;
import com.paymu.claims.model.PolicyBindingResult;
import com.paymu.claims.model.RecallStatus;
import com.paymu.claims.model.ServiceTimeline;
import com.paymu.claims.service.ClaimStore;
import com.paymu.claims.service.IdentityClient;
import com.paymu.claims.service.IncidentDateValidator;
import com.paymu.claims.service.MaintenanceClient;
import com.paymu.claims.service.TriageService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for claims intake and verification.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /fnol}             — submit a First Notice of Loss</li>
 *   <li>{@code GET  /claims/{claimId}} — retrieve a previously submitted claim</li>
 * </ul>
 */
@RestController
@RequestMapping
public class ClaimsController {

    private static final Logger log = LoggerFactory.getLogger(ClaimsController.class);

    private final TriageService triageService;
    private final MaintenanceClient maintenanceClient;
    private final IdentityClient identityClient;
    private final IncidentDateValidator incidentDateValidator;
    private final ClaimStore claimStore;

    public ClaimsController(TriageService triageService,
                            MaintenanceClient maintenanceClient,
                            IdentityClient identityClient,
                            IncidentDateValidator incidentDateValidator,
                            ClaimStore claimStore) {
        this.triageService = triageService;
        this.maintenanceClient = maintenanceClient;
        this.identityClient = identityClient;
        this.incidentDateValidator = incidentDateValidator;
        this.claimStore = claimStore;
    }

    /**
     * Submit a First Notice of Loss.
     *
     * <ol>
     *   <li>Validates that incident_date is a valid ISO date, not in the future, and within max age.</li>
     *   <li>Verifies policy binding against identity-consent GET /vehicles/{vehicleId}.</li>
     *   <li>Fetches the vehicle's service timeline and structured recall status from maintenance service.</li>
     *   <li>Runs the triage rule against the FNOL, timeline, and recall status (forcing MANUAL_REVIEW if binding unverified).</li>
     *   <li>Persists and returns the triage result.</li>
     * </ol>
     *
     * <p>Both downstream calls degrade independently — a null result from either
     * means that signal is skipped, not that the claim is blocked.</p>
     */
    @PostMapping("/fnol")
    public ResponseEntity<FnolResponse> submitFnol(@Valid @RequestBody FnolRequest request) {
        log.info("FNOL received for vehicle {} — cause: {}", request.vehicleId(), request.claimedCause());

        incidentDateValidator.validate(request.incidentDate());

        PolicyBindingResult binding = identityClient.verifyPolicyBinding(request.vehicleId(), request.policyId());
        boolean isUnverified = (binding == PolicyBindingResult.UNVERIFIED);

        ServiceTimeline timeline = maintenanceClient.fetchTimeline(request.vehicleId());
        RecallStatus    recall   = maintenanceClient.fetchRecallStatus(request.vehicleId());

        TriageService.TriageResult triage = triageService.triage(request, timeline, recall, isUnverified);
        FnolResponse response = claimStore.save(request, triage);

        log.info("FNOL {} → {} for vehicle {}", response.claimId(),
                response.triageDecision(), request.vehicleId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieve a previously submitted claim by its system-assigned ID.
     */
    @GetMapping("/claims/{claimId}")
    public ResponseEntity<?> getClaim(@PathVariable UUID claimId) {
        FnolResponse claim = claimStore.findById(claimId.toString());
        if (claim == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "NOT_FOUND",
                    "message", "Claim not found: " + claimId,
                    "timestamp", Instant.now().toString()
            ));
        }
        return ResponseEntity.ok(claim);
    }
}
