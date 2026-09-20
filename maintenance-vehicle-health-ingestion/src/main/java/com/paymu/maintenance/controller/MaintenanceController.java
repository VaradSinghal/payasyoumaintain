package com.paymu.maintenance.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.ValidationMessage;
import com.paymu.maintenance.model.*;
import com.paymu.maintenance.service.MaintenanceStore;
import com.paymu.maintenance.service.OcrStubService;
import com.paymu.maintenance.service.SchemaValidationService;
import com.paymu.maintenance.service.SyntheticDataGenerator;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * REST controller for maintenance event ingestion and vehicle service timelines.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /events} — batch ingest (OEM API style), validated against schema</li>
 *   <li>{@code POST /events/self-upload} — self-upload with stub OCR</li>
 *   <li>{@code GET  /timeline/{vehicleId}} — per-vehicle service timeline</li>
 *   <li>{@code GET  /events/{vehicleId}} — raw events for a vehicle</li>
 *   <li>{@code POST /generate-synthetic} — generate demo data for 5 vehicles</li>
 * </ul>
 */
@RestController
@RequestMapping
public class MaintenanceController {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceController.class);

    private final SchemaValidationService validationService;
    private final OcrStubService ocrStubService;
    private final MaintenanceStore store;
    private final SyntheticDataGenerator syntheticDataGenerator;
    private final ObjectMapper objectMapper;

    public MaintenanceController(SchemaValidationService validationService,
                                  OcrStubService ocrStubService,
                                  MaintenanceStore store,
                                  SyntheticDataGenerator syntheticDataGenerator,
                                  ObjectMapper objectMapper) {
        this.validationService = validationService;
        this.ocrStubService = ocrStubService;
        this.store = store;
        this.syntheticDataGenerator = syntheticDataGenerator;
        this.objectMapper = objectMapper;
    }

    /**
     * Batch ingest maintenance events (OEM API / structured input).
     * Each event is individually validated against the contract schema;
     * valid events are accepted, invalid ones rejected (partial-batch).
     */
    @PostMapping("/events")
    public ResponseEntity<BatchIngestResponse> ingestEvents(@RequestBody List<JsonNode> events) {
        if (events == null || events.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    new BatchIngestResponse(0, 0,
                            List.of("Request body must be a non-empty JSON array."), List.of()));
        }

        List<MaintenanceEvent> validEvents = new ArrayList<>();
        List<String> validationErrors = new ArrayList<>();

        for (int i = 0; i < events.size(); i++) {
            JsonNode node = events.get(i);
            Set<ValidationMessage> errors = validationService.validate(node);

            if (errors.isEmpty()) {
                try {
                    MaintenanceEvent event = objectMapper.treeToValue(node, MaintenanceEvent.class);
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

        // Store valid events
        store.storeEvents(validEvents);

        List<String> vehicleIds = validEvents.stream()
                .map(MaintenanceEvent::vehicleId)
                .distinct()
                .toList();

        log.info("Batch ingest: {} accepted, {} rejected across {} vehicle(s)",
                validEvents.size(), events.size() - validEvents.size(), vehicleIds.size());

        BatchIngestResponse response = new BatchIngestResponse(
                validEvents.size(),
                events.size() - validEvents.size(),
                validationErrors,
                vehicleIds);

        HttpStatus status = validEvents.isEmpty() && !events.isEmpty()
                ? HttpStatus.BAD_REQUEST : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    /**
     * Self-upload path: accepts user-typed fields with an optional document
     * reference.  The stub OCR echoes fields back as a structured event,
     * validates it, and stores it.
     */
    @PostMapping("/events/self-upload")
    public ResponseEntity<?> selfUpload(@RequestBody SelfUploadRequest request) {
        OcrResult ocrResult = ocrStubService.process(request);
        MaintenanceEvent event = ocrResult.extractedEvent();

        // Validate the constructed event against the schema
        JsonNode eventNode = objectMapper.valueToTree(event);
        Set<ValidationMessage> errors = validationService.validate(eventNode);

        if (!errors.isEmpty()) {
            List<String> errorMessages = errors.stream()
                    .map(ValidationMessage::getMessage)
                    .toList();
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "SCHEMA_VALIDATION_FAILED",
                    "message", "Self-upload event failed contract validation.",
                    "details", errorMessages,
                    "ocr_result", ocrResult));
        }

        store.storeEvent(event);
        log.info("Self-upload accepted for vehicle {}", event.vehicleId());

        return ResponseEntity.ok(ocrResult);
    }

    /**
     * Per-vehicle service timeline: ordered history of all maintenance events.
     */
    @GetMapping("/timeline/{vehicleId}")
    public ResponseEntity<ServiceTimeline> getTimeline(@PathVariable String vehicleId) {
        ServiceTimeline timeline = store.getTimeline(vehicleId);
        if (timeline == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(timeline);
    }

    /**
     * Retrieve raw events for a vehicle.
     */
    @GetMapping("/events/{vehicleId}")
    public ResponseEntity<List<MaintenanceEvent>> getEvents(@PathVariable String vehicleId) {
        List<MaintenanceEvent> events = store.getEventsByVehicle(vehicleId);
        if (events.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(events);
    }

    /**
     * Generate and ingest synthetic maintenance data for 5 sample vehicles.
     */
    @PostMapping("/generate-synthetic")
    public ResponseEntity<BatchIngestResponse> generateSynthetic() {
        List<MaintenanceEvent> syntheticEvents = syntheticDataGenerator.generate();

        // Run through normal ingest pipeline (eat our own dog food)
        List<JsonNode> jsonNodes = syntheticEvents.stream()
                .map(e -> (JsonNode) objectMapper.valueToTree(e))
                .toList();

        return ingestEvents(jsonNodes);
    }

}

