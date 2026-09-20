package com.paymu.maintenance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SchemaValidationService}.
 * Validates payloads against {@code contracts/schemas/maintenance-event.schema.json}.
 */
class SchemaValidationServiceTest {

    private SchemaValidationService validationService;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        validationService = new SchemaValidationService();
        mapper = new ObjectMapper();
    }

    // ── Helpers ─────────────────────────────────────────────────

    private ObjectNode validEvent() {
        ObjectNode event = mapper.createObjectNode();
        event.put("event_id", UUID.randomUUID().toString());
        event.put("vehicle_id", UUID.randomUUID().toString());
        event.put("service_date", "2026-06-15");
        event.put("service_type", "oil_change");
        event.put("odometer_km", 25000);
        event.put("source", "oem_api");

        ArrayNode parts = mapper.createArrayNode();
        ObjectNode part = mapper.createObjectNode();
        part.put("part_name", "Oil Filter");
        parts.add(part);
        event.set("parts_replaced", parts);

        return event;
    }

    // ── Valid payloads ──────────────────────────────────────────

    @Nested
    @DisplayName("Valid payloads")
    class ValidPayloads {

        @Test
        @DisplayName("Fully populated event passes validation")
        void fullEvent_passes() {
            ObjectNode event = validEvent();
            event.put("notes", "Routine oil change.");

            ArrayNode docRefs = mapper.createArrayNode();
            docRefs.add("s3://paymu-docs/invoices/abc123.pdf");
            event.set("document_refs", docRefs);

            Set<ValidationMessage> errors = validationService.validate(event);
            assertTrue(errors.isEmpty(), "Expected no errors but got: " + errors);
        }

        @Test
        @DisplayName("Event with empty parts_replaced array passes")
        void emptyParts_passes() {
            ObjectNode event = validEvent();
            event.set("parts_replaced", mapper.createArrayNode());
            Set<ValidationMessage> errors = validationService.validate(event);
            assertTrue(errors.isEmpty(), "Expected no errors but got: " + errors);
        }

        @Test
        @DisplayName("All valid service_type enum values pass")
        void allServiceTypes_pass() {
            String[] types = {
                    "oil_change", "tyre_rotation", "brake_service", "battery_replacement",
                    "engine_service", "transmission_service", "suspension_repair",
                    "electrical_repair", "body_repair", "inspection", "full_service", "other"
            };
            for (String type : types) {
                ObjectNode event = validEvent();
                event.put("service_type", type);
                Set<ValidationMessage> errors = validationService.validate(event);
                assertTrue(errors.isEmpty(), "service_type '" + type + "' should be valid but got: " + errors);
            }
        }

        @Test
        @DisplayName("All valid source enum values pass")
        void allSources_pass() {
            String[] sources = {"oem_api", "self_upload", "inspection"};
            for (String source : sources) {
                ObjectNode event = validEvent();
                event.put("source", source);
                Set<ValidationMessage> errors = validationService.validate(event);
                assertTrue(errors.isEmpty(), "source '" + source + "' should be valid but got: " + errors);
            }
        }

        @Test
        @DisplayName("Part with all fields passes")
        void partWithAllFields_passes() {
            ObjectNode event = validEvent();
            ArrayNode parts = mapper.createArrayNode();
            ObjectNode part = mapper.createObjectNode();
            part.put("part_name", "Brake Pad");
            part.put("part_number", "BP-2024A");
            part.put("quantity", 2);
            parts.add(part);
            event.set("parts_replaced", parts);

            Set<ValidationMessage> errors = validationService.validate(event);
            assertTrue(errors.isEmpty(), "Expected no errors but got: " + errors);
        }
    }

    // ── Missing required fields ─────────────────────────────────

    @Nested
    @DisplayName("Missing required fields")
    class MissingRequired {

        @Test
        @DisplayName("Missing event_id is rejected")
        void missingEventId() {
            ObjectNode event = validEvent();
            event.remove("event_id");
            assertFalse(validationService.validate(event).isEmpty());
        }

        @Test
        @DisplayName("Missing vehicle_id is rejected")
        void missingVehicleId() {
            ObjectNode event = validEvent();
            event.remove("vehicle_id");
            assertFalse(validationService.validate(event).isEmpty());
        }

        @Test
        @DisplayName("Missing service_date is rejected")
        void missingServiceDate() {
            ObjectNode event = validEvent();
            event.remove("service_date");
            assertFalse(validationService.validate(event).isEmpty());
        }

        @Test
        @DisplayName("Missing service_type is rejected")
        void missingServiceType() {
            ObjectNode event = validEvent();
            event.remove("service_type");
            assertFalse(validationService.validate(event).isEmpty());
        }

        @Test
        @DisplayName("Missing odometer_km is rejected")
        void missingOdometer() {
            ObjectNode event = validEvent();
            event.remove("odometer_km");
            assertFalse(validationService.validate(event).isEmpty());
        }

        @Test
        @DisplayName("Missing parts_replaced is rejected")
        void missingParts() {
            ObjectNode event = validEvent();
            event.remove("parts_replaced");
            assertFalse(validationService.validate(event).isEmpty());
        }

        @Test
        @DisplayName("Missing source is rejected")
        void missingSource() {
            ObjectNode event = validEvent();
            event.remove("source");
            assertFalse(validationService.validate(event).isEmpty());
        }

        @Test
        @DisplayName("Part missing part_name is rejected")
        void partMissingName() {
            ObjectNode event = validEvent();
            ArrayNode parts = mapper.createArrayNode();
            ObjectNode part = mapper.createObjectNode();
            part.put("part_number", "BP-2024A");
            parts.add(part);
            event.set("parts_replaced", parts);
            assertFalse(validationService.validate(event).isEmpty());
        }
    }

    // ── Invalid values ──────────────────────────────────────────

    @Nested
    @DisplayName("Invalid values")
    class InvalidValues {

        @Test
        @DisplayName("Invalid service_type enum value is rejected")
        void invalidServiceType() {
            ObjectNode event = validEvent();
            event.put("service_type", "car_wash");
            assertFalse(validationService.validate(event).isEmpty());
        }

        @Test
        @DisplayName("Invalid source enum value is rejected")
        void invalidSource() {
            ObjectNode event = validEvent();
            event.put("source", "manual_entry");
            assertFalse(validationService.validate(event).isEmpty());
        }

        @Test
        @DisplayName("Negative odometer is rejected")
        void negativeOdometer() {
            ObjectNode event = validEvent();
            event.put("odometer_km", -100);
            assertFalse(validationService.validate(event).isEmpty());
        }

        @Test
        @DisplayName("String value for odometer_km is rejected")
        void stringOdometer() {
            ObjectNode event = validEvent();
            event.put("odometer_km", "twenty-five thousand");
            assertFalse(validationService.validate(event).isEmpty());
        }

        @Test
        @DisplayName("Extra top-level field is rejected (additionalProperties: false)")
        void extraField() {
            ObjectNode event = validEvent();
            event.put("garage_name", "FastFix Auto");
            assertFalse(validationService.validate(event).isEmpty());
        }

        @Test
        @DisplayName("Extra field inside parts_replaced item is rejected")
        void extraPartField() {
            ObjectNode event = validEvent();
            ArrayNode parts = mapper.createArrayNode();
            ObjectNode part = mapper.createObjectNode();
            part.put("part_name", "Brake Pad");
            part.put("cost", 1500);
            parts.add(part);
            event.set("parts_replaced", parts);
            assertFalse(validationService.validate(event).isEmpty());
        }
    }
}
