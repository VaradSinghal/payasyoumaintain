package com.paymu.maintenance.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.ValidationMessage;
import com.paymu.maintenance.model.DtcReading;
import com.paymu.maintenance.service.DtcStore;
import com.paymu.maintenance.service.SchemaValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/dtc-readings")
public class DtcController {

    private static final Logger log = LoggerFactory.getLogger(DtcController.class);

    private final DtcStore store;
    private final SchemaValidationService validationService;
    private final ObjectMapper objectMapper;

    public DtcController(DtcStore store, SchemaValidationService validationService, ObjectMapper objectMapper) {
        this.store = store;
        this.validationService = validationService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<?> ingestReading(@RequestBody JsonNode node) {
        Set<ValidationMessage> errors = validationService.validateDtc(node);

        if (!errors.isEmpty()) {
            List<String> errorMessages = errors.stream()
                    .map(ValidationMessage::getMessage)
                    .toList();
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "SCHEMA_VALIDATION_FAILED",
                    "message", "DTC reading failed contract validation.",
                    "details", errorMessages));
        }

        try {
            DtcReading reading = objectMapper.treeToValue(node, DtcReading.class);
            store.storeReading(reading);
            log.info("DTC reading ingested for vehicle {}", reading.vehicleId());
            return ResponseEntity.ok(reading);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "DESERIALIZATION_FAILED",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/{vehicleId}/latest")
    public ResponseEntity<DtcReading> getLatest(@PathVariable String vehicleId) {
        DtcReading latest = store.getLatest(vehicleId);
        if (latest == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(latest);
    }

    @GetMapping("/{vehicleId}/history")
    public ResponseEntity<List<DtcReading>> getHistory(@PathVariable String vehicleId) {
        List<DtcReading> history = store.getHistory(vehicleId);
        return ResponseEntity.ok(history);
    }
}
