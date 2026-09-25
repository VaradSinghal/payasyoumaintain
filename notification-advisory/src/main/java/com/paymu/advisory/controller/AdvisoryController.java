package com.paymu.advisory.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.paymu.advisory.model.AdvisoryMessage;
import com.paymu.advisory.model.AdvisoryResponse;
import com.paymu.advisory.model.RecallStatus;
import com.paymu.advisory.model.ScoreResponse;
import com.paymu.advisory.model.ServiceTimeline;
import com.paymu.advisory.service.AdvisoryGeneratorService;
import com.paymu.advisory.service.DownstreamDataService;
import com.paymu.advisory.service.NotificationDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * REST controller for the Notification &amp; Advisory service.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code GET /advisories/{vehicleId}} — generate and deliver a prioritised
 *       advisory list for a vehicle, drawing from live upstream data.</li>
 * </ul>
 */
@RestController
@RequestMapping
public class AdvisoryController {

    private static final Logger log = LoggerFactory.getLogger(AdvisoryController.class);

    private final DownstreamDataService downstreamDataService;
    private final AdvisoryGeneratorService advisoryGeneratorService;
    private final NotificationDeliveryService deliveryService;

    public AdvisoryController(DownstreamDataService downstreamDataService,
                              AdvisoryGeneratorService advisoryGeneratorService,
                              NotificationDeliveryService deliveryService) {
        this.downstreamDataService = downstreamDataService;
        this.advisoryGeneratorService = advisoryGeneratorService;
        this.deliveryService = deliveryService;
    }

    /**
     * Generate and deliver advisories for a vehicle.
     *
     * <ol>
     *   <li>Fetches trip aggregates from telematics service.</li>
     *   <li>Fetches service timeline and recall status from maintenance service.</li>
     *   <li>Posts those to the risk-scoring-engine to get a composite score.</li>
     *   <li>Generates a prioritised advisory list.</li>
     *   <li>Stubs delivery (logs to console).</li>
     *   <li>Returns the list to the caller.</li>
     * </ol>
     *
     * <p>An empty {@code advisories} array is a valid, successful response —
     * it means the vehicle has no outstanding flags.</p>
     */
    @GetMapping("/advisories/{vehicleId}")
    public ResponseEntity<AdvisoryResponse> getAdvisories(@PathVariable String vehicleId) {
        log.info("Generating advisories for vehicle {}", vehicleId);

        JsonNode tripAggregates  = downstreamDataService.fetchTripAggregates(vehicleId);
        ServiceTimeline timeline = downstreamDataService.fetchTimeline(vehicleId);
        RecallStatus recall      = downstreamDataService.fetchRecallStatus(vehicleId);
        ScoreResponse score      = downstreamDataService.fetchScore(vehicleId, tripAggregates, timeline, recall);

        List<AdvisoryMessage> advisories = advisoryGeneratorService.generate(recall, timeline, score);

        deliveryService.deliver(vehicleId, advisories);

        return ResponseEntity.ok(new AdvisoryResponse(vehicleId, advisories, Instant.now().toString()));
    }
}
