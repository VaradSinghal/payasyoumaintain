package com.paymu.telematics.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.ValidationMessage;
import com.paymu.telematics.model.AggregationResult;
import com.paymu.telematics.model.BatchIngestResponse;
import com.paymu.telematics.model.TripAggregate;
import com.paymu.telematics.model.UsageEvent;
import com.paymu.telematics.service.AggregationService;
import com.paymu.telematics.service.EventStore;
import com.paymu.telematics.service.SchemaValidationService;
import com.paymu.telematics.service.SyntheticDataGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * REST controller for telematics event ingestion and retrieval.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /events} — batch ingest with per-event schema validation</li>
 *   <li>{@code GET  /events/{vehicleId}} — retrieve raw events for a vehicle</li>
 *   <li>{@code GET  /aggregates/{vehicleId}} — retrieve trip aggregates for a vehicle</li>
 *   <li>{@code POST /generate-synthetic} — generate and ingest demo data for 5 vehicles</li>
 * </ul>
 */
@RestController
@RequestMapping
public class TelematicsController {

    private static final Logger log = LoggerFactory.getLogger(TelematicsController.class);

    private final SchemaValidationService validationService;
    private final AggregationService aggregationService;
    private final EventStore eventStore;
    private final SyntheticDataGenerator syntheticDataGenerator;
    private final ObjectMapper objectMapper;

    public TelematicsController(SchemaValidationService validationService,
                                AggregationService aggregationService,
                                EventStore eventStore,
                                SyntheticDataGenerator syntheticDataGenerator,
                                ObjectMapper objectMapper) {
        this.validationService = validationService;
        this.aggregationService = aggregationService;
        this.eventStore = eventStore;
        this.syntheticDataGenerator = syntheticDataGenerator;
        this.objectMapper = objectMapper;
    }

    /**
     * Batch ingest trip events.
     *
     * <p>Each event in the array is validated individually against
     * {@code contracts/schemas/usage-event.schema.json}.  Valid events are
     * accepted, invalid events are rejected — partial-batch acceptance.</p>
     *
     * @param events JSON array of usage event objects
     * @return response with accepted/rejected counts and computed aggregates
     */
    @PostMapping("/events")
    public ResponseEntity<BatchIngestResponse> ingestEvents(@RequestBody List<JsonNode> events) {
        if (events == null || events.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    new BatchIngestResponse(0, 0, List.of("Request body must be a non-empty JSON array."), List.of()));
        }

        List<UsageEvent> validEvents = new ArrayList<>();
        List<String> validationErrors = new ArrayList<>();

        for (int i = 0; i < events.size(); i++) {
            JsonNode node = events.get(i);
            Set<ValidationMessage> errors = validationService.validate(node);

            if (errors.isEmpty()) {
                try {
                    UsageEvent event = objectMapper.treeToValue(node, UsageEvent.class);
                    validEvents.add(event);
                } catch (Exception e) {
                    validationErrors.add("Event[" + i + "]: deserialization failed — " + e.getMessage());
                }
            } else {
                for (ValidationMessage error : errors) {
                    validationErrors.add("Event[" + i + "]: " + error.getMessage());
                }
            }
        }

        // Process valid events: auto-segment trips and compute aggregates
        AggregationResult result = aggregationService.processAndAggregate(validEvents);

        // Store
        eventStore.storeEvents(result.processedEvents());
        eventStore.storeAggregates(result.aggregates());

        log.info("Batch ingest: {} accepted, {} rejected, {} trip(s) computed",
                validEvents.size(), events.size() - validEvents.size(), result.aggregates().size());

        BatchIngestResponse response = new BatchIngestResponse(
                validEvents.size(),
                events.size() - validEvents.size(),
                validationErrors,
                result.aggregates());

        // 200 if any accepted, 400 if nothing was accepted from a non-empty batch
        HttpStatus status = validEvents.isEmpty() ? HttpStatus.BAD_REQUEST : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    /**
     * Retrieve all raw telematics events for a vehicle.
     */
    @GetMapping("/events/{vehicleId}")
    public ResponseEntity<List<UsageEvent>> getEvents(@PathVariable String vehicleId) {
        List<UsageEvent> events = eventStore.getEventsByVehicle(vehicleId);
        if (events.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(events);
    }

    /**
     * Retrieve trip aggregates for a vehicle.
     */
    @GetMapping("/aggregates/{vehicleId}")
    public ResponseEntity<List<TripAggregate>> getAggregates(@PathVariable String vehicleId) {
        List<TripAggregate> aggregates = eventStore.getAggregatesByVehicle(vehicleId);
        if (aggregates.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(aggregates);
    }

    /**
     * Generate and ingest synthetic trip data for 5 sample vehicles.
     * Returns the computed aggregates for all generated trips.
     */
    @PostMapping("/generate-synthetic")
    public ResponseEntity<BatchIngestResponse> generateSynthetic() {
        List<UsageEvent> syntheticEvents = syntheticDataGenerator.generate();

        // Convert to JsonNode for schema validation (eat our own dog food)
        List<JsonNode> jsonNodes = syntheticEvents.stream()
                .map(e -> objectMapper.valueToTree(e))
                .map(node -> (JsonNode) node)
                .toList();

        // Run through the normal ingest pipeline
        return ingestEvents(jsonNodes);
    }
}
