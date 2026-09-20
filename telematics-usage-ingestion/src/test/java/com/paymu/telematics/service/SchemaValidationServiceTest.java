package com.paymu.telematics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SchemaValidationService}.
 * Validates that the service correctly accepts/rejects payloads
 * against {@code contracts/schemas/usage-event.schema.json}.
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
        event.put("timestamp", "2026-09-20T10:15:30.000Z");
        event.put("speed_kmh", 55.3);
        event.put("acceleration_ms2", 1.2);
        event.put("braking_event", false);

        ObjectNode gps = mapper.createObjectNode();
        gps.put("latitude", 12.9716);
        gps.put("longitude", 77.5946);
        event.set("gps", gps);

        return event;
    }

    // ── Valid payloads ──────────────────────────────────────────

    @Test
    @DisplayName("Valid event with all required fields passes validation")
    void validEvent_passes() {
        Set<ValidationMessage> errors = validationService.validate(validEvent());
        assertTrue(errors.isEmpty(), "Expected no errors but got: " + errors);
    }

    @Test
    @DisplayName("Valid event with optional trip_id passes validation")
    void validEventWithOptionalTripId_passes() {
        ObjectNode event = validEvent();
        event.put("trip_id", UUID.randomUUID().toString());
        Set<ValidationMessage> errors = validationService.validate(event);
        assertTrue(errors.isEmpty(), "Expected no errors but got: " + errors);
    }

    @Test
    @DisplayName("Valid event with optional altitude passes validation")
    void validEventWithAltitude_passes() {
        ObjectNode event = validEvent();
        ((ObjectNode) event.get("gps")).put("altitude_m", 920.5);
        Set<ValidationMessage> errors = validationService.validate(event);
        assertTrue(errors.isEmpty(), "Expected no errors but got: " + errors);
    }

    // ── Missing required fields ─────────────────────────────────

    @Test
    @DisplayName("Missing event_id is rejected")
    void missingEventId_rejected() {
        ObjectNode event = validEvent();
        event.remove("event_id");
        Set<ValidationMessage> errors = validationService.validate(event);
        assertFalse(errors.isEmpty(), "Expected validation errors for missing event_id");
    }

    @Test
    @DisplayName("Missing vehicle_id is rejected")
    void missingVehicleId_rejected() {
        ObjectNode event = validEvent();
        event.remove("vehicle_id");
        Set<ValidationMessage> errors = validationService.validate(event);
        assertFalse(errors.isEmpty(), "Expected validation errors for missing vehicle_id");
    }

    @Test
    @DisplayName("Missing gps object is rejected")
    void missingGps_rejected() {
        ObjectNode event = validEvent();
        event.remove("gps");
        Set<ValidationMessage> errors = validationService.validate(event);
        assertFalse(errors.isEmpty(), "Expected validation errors for missing gps");
    }

    @Test
    @DisplayName("Missing timestamp is rejected")
    void missingTimestamp_rejected() {
        ObjectNode event = validEvent();
        event.remove("timestamp");
        Set<ValidationMessage> errors = validationService.validate(event);
        assertFalse(errors.isEmpty(), "Expected validation errors for missing timestamp");
    }

    @Test
    @DisplayName("Missing speed_kmh is rejected")
    void missingSpeed_rejected() {
        ObjectNode event = validEvent();
        event.remove("speed_kmh");
        Set<ValidationMessage> errors = validationService.validate(event);
        assertFalse(errors.isEmpty(), "Expected validation errors for missing speed_kmh");
    }

    @Test
    @DisplayName("Missing braking_event is rejected")
    void missingBrakingEvent_rejected() {
        ObjectNode event = validEvent();
        event.remove("braking_event");
        Set<ValidationMessage> errors = validationService.validate(event);
        assertFalse(errors.isEmpty(), "Expected validation errors for missing braking_event");
    }

    // ── Invalid types ───────────────────────────────────────────

    @Test
    @DisplayName("String value for speed_kmh is rejected")
    void stringSpeed_rejected() {
        ObjectNode event = validEvent();
        event.put("speed_kmh", "fast");
        Set<ValidationMessage> errors = validationService.validate(event);
        assertFalse(errors.isEmpty(), "Expected type error for speed_kmh");
    }

    @Test
    @DisplayName("String value for braking_event is rejected")
    void stringBrakingEvent_rejected() {
        ObjectNode event = validEvent();
        event.put("braking_event", "yes");
        Set<ValidationMessage> errors = validationService.validate(event);
        assertFalse(errors.isEmpty(), "Expected type error for braking_event");
    }

    // ── Out-of-range values ─────────────────────────────────────

    @Test
    @DisplayName("Negative speed is rejected (minimum: 0)")
    void negativeSpeed_rejected() {
        ObjectNode event = validEvent();
        event.put("speed_kmh", -10.0);
        Set<ValidationMessage> errors = validationService.validate(event);
        assertFalse(errors.isEmpty(), "Expected range error for negative speed");
    }

    @Test
    @DisplayName("Latitude > 90 is rejected")
    void latitudeTooHigh_rejected() {
        ObjectNode event = validEvent();
        ((ObjectNode) event.get("gps")).put("latitude", 91.0);
        Set<ValidationMessage> errors = validationService.validate(event);
        assertFalse(errors.isEmpty(), "Expected range error for latitude > 90");
    }

    @Test
    @DisplayName("Longitude < -180 is rejected")
    void longitudeTooLow_rejected() {
        ObjectNode event = validEvent();
        ((ObjectNode) event.get("gps")).put("longitude", -181.0);
        Set<ValidationMessage> errors = validationService.validate(event);
        assertFalse(errors.isEmpty(), "Expected range error for longitude < -180");
    }

    // ── Additional properties ───────────────────────────────────

    @Test
    @DisplayName("Extra top-level field is rejected (additionalProperties: false)")
    void extraTopLevelField_rejected() {
        ObjectNode event = validEvent();
        event.put("rogue_field", "should not be here");
        Set<ValidationMessage> errors = validationService.validate(event);
        assertFalse(errors.isEmpty(), "Expected error for additional property");
    }

    @Test
    @DisplayName("Extra field inside gps is rejected")
    void extraGpsField_rejected() {
        ObjectNode event = validEvent();
        ((ObjectNode) event.get("gps")).put("heading", 180);
        Set<ValidationMessage> errors = validationService.validate(event);
        assertFalse(errors.isEmpty(), "Expected error for additional property inside gps");
    }

    // ── Missing nested required fields ──────────────────────────

    @Test
    @DisplayName("GPS missing latitude is rejected")
    void gpsMissingLatitude_rejected() {
        ObjectNode event = validEvent();
        ((ObjectNode) event.get("gps")).remove("latitude");
        Set<ValidationMessage> errors = validationService.validate(event);
        assertFalse(errors.isEmpty(), "Expected error for missing gps.latitude");
    }
}
