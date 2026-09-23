package com.paymu.claims.controller;

import com.paymu.claims.model.FnolRequest;
import com.paymu.claims.model.FnolResponse;
import com.paymu.claims.model.ServiceTimeline;
import com.paymu.claims.service.ClaimStore;
import com.paymu.claims.service.MaintenanceClient;
import com.paymu.claims.service.TriageService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for claims intake and verification.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /fnol}            — submit a First Notice of Loss</li>
 *   <li>{@code GET  /claims/{claimId}} — retrieve a previously submitted claim</li>
 * </ul>
 */
@RestController
@RequestMapping
public class ClaimsController {

    private static final Logger log = LoggerFactory.getLogger(ClaimsController.class);

    private final TriageService triageService;
    private final MaintenanceClient maintenanceClient;
    private final ClaimStore claimStore;

    public ClaimsController(TriageService triageService,
                            MaintenanceClient maintenanceClient,
                            ClaimStore claimStore) {
        this.triageService = triageService;
        this.maintenanceClient = maintenanceClient;
        this.claimStore = claimStore;
    }

    /**
     * Submit a First Notice of Loss.
     *
     * <ol>
     *   <li>Fetches the vehicle's service timeline from maintenance-vehicle-health-ingestion.</li>
     *   <li>Runs the triage rule against the FNOL and timeline.</li>
     *   <li>Persists and returns the triage result.</li>
     * </ol>
     */
    @PostMapping("/fnol")
    public ResponseEntity<FnolResponse> submitFnol(@Valid @RequestBody FnolRequest request) {
        log.info("FNOL received for vehicle {} — cause: {}", request.vehicleId(), request.claimedCause());

        ServiceTimeline timeline = maintenanceClient.fetchTimeline(request.vehicleId());

        TriageService.TriageResult triage = triageService.triage(request, timeline);
        FnolResponse response = claimStore.save(request, triage);

        log.info("FNOL {} → {} for vehicle {}", response.claimId(),
                response.triageDecision(), request.vehicleId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieve a previously submitted claim by its system-assigned ID.
     */
    @GetMapping("/claims/{claimId}")
    public ResponseEntity<FnolResponse> getClaim(@PathVariable String claimId) {
        FnolResponse claim = claimStore.findById(claimId);
        if (claim == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(claim);
    }
}
