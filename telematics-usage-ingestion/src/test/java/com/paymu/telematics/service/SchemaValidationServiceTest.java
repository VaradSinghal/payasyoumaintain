package com.paymu.telematics.service;


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

    // ── Generator Full Output Validation ────────────────────────

    @Test
    @DisplayName("SyntheticDataGenerator's FULL output passes schema validation with zero rejections")
    void generatorFullOutput_passesSchemaValidation() {
        SyntheticDataGenerator generator = new SyntheticDataGenerator();
        java.util.List<com.paymu.telematics.model.UsageEvent> events = generator.generate();

        assertFalse(events.isEmpty(), "Synthetic generator produced no events");

        for (int i = 0; i < events.size(); i++) {
            com.paymu.telematics.model.UsageEvent event = events.get(i);
            com.fasterxml.jackson.databind.JsonNode node = mapper.valueToTree(event);
            Set<ValidationMessage> errors = validationService.validate(node);
            assertTrue(errors.isEmpty(),
                    "Event[" + i + "] for vehicle " + event.vehicleId() + " rejected by schema: " + errors);
        }
    }

    // ── Trip Aggregate Schema & Synthetic Profiles Validation ───

    @Test
    @DisplayName("Every aggregate produced from SyntheticDataGenerator passes trip-aggregate.schema.json")
    void allAggregatesPassTripAggregateSchema() {
        SyntheticDataGenerator generator = new SyntheticDataGenerator();
        java.util.List<com.paymu.telematics.model.UsageEvent> events = generator.generate();
        AggregationService aggService = new AggregationService(5, 3.0);
        com.paymu.telematics.model.AggregationResult result = aggService.processAndAggregate(events);

        assertFalse(result.aggregates().isEmpty(), "Synthetic generator produced no aggregates");

        for (int i = 0; i < result.aggregates().size(); i++) {
            com.paymu.telematics.model.TripAggregate agg = result.aggregates().get(i);
            com.fasterxml.jackson.databind.JsonNode node = mapper.valueToTree(agg);
            Set<ValidationMessage> errors = validationService.validateTripAggregate(node);
            assertTrue(errors.isEmpty(),
                    "Aggregate[" + i + "] for vehicle " + agg.vehicleId() + " rejected by schema: " + errors);
        }
    }

    @Test
    @DisplayName("Synthetic vehicle profiles adhere to intended acceleration and braking thresholds")
    void syntheticVehicleProfiles_meetExpectations() {
        SyntheticDataGenerator generator = new SyntheticDataGenerator();
        java.util.List<com.paymu.telematics.model.UsageEvent> events = generator.generate();
        AggregationService aggService = new AggregationService(5, 3.0);
        com.paymu.telematics.model.AggregationResult result = aggService.processAndAggregate(events);

        // Map aggregates by vehicle ID
        java.util.Map<String, java.util.List<com.paymu.telematics.model.TripAggregate>> aggsByVehicle =
                result.aggregates().stream().collect(java.util.stream.Collectors.groupingBy(com.paymu.telematics.model.TripAggregate::vehicleId));

        String v1 = SyntheticDataGenerator.VEHICLE_IDS[0]; // NORMAL
        String v2 = SyntheticDataGenerator.VEHICLE_IDS[1]; // POOR
        String v3 = SyntheticDataGenerator.VEHICLE_IDS[2]; // CONFLICTING
        String v4 = SyntheticDataGenerator.VEHICLE_IDS[3]; // UNVERIFIED
        String v5 = SyntheticDataGenerator.VEHICLE_IDS[4]; // INSUFFICIENT

        // 1. NORMAL: careful driving, 0 harsh brakes, 0 hard accels, every accel < 2.0
        java.util.List<com.paymu.telematics.model.TripAggregate> v1Aggs = aggsByVehicle.get(v1);
        assertNotNull(v1Aggs);
        assertEquals(12, v1Aggs.size());
        double v1BrakeAvg = v1Aggs.stream().mapToInt(com.paymu.telematics.model.TripAggregate::harshBrakingCount).average().orElse(0);
        double v1AccelAvg = v1Aggs.stream().mapToInt(com.paymu.telematics.model.TripAggregate::hardAccelerationCount).average().orElse(0);
        assertEquals(0.0, v1BrakeAvg);
        assertEquals(0.0, v1AccelAvg);
        events.stream().filter(e -> e.vehicleId().equals(v1)).forEach(e -> {
            assertFalse(e.brakingEvent());
            assertTrue(e.accelerationMs2() < 2.0, "Vehicle 1 acceleration must be < 2.0: " + e.accelerationMs2());
        });

        // 2. POOR: both deductions fire (avg harsh brake > 0.5, avg hard accel > 0.5)
        java.util.List<com.paymu.telematics.model.TripAggregate> v2Aggs = aggsByVehicle.get(v2);
        assertNotNull(v2Aggs);
        assertEquals(4, v2Aggs.size());
        double v2BrakeAvg = v2Aggs.stream().mapToInt(com.paymu.telematics.model.TripAggregate::harshBrakingCount).average().orElse(0);
        double v2AccelAvg = v2Aggs.stream().mapToInt(com.paymu.telematics.model.TripAggregate::hardAccelerationCount).average().orElse(0);
        assertTrue(v2BrakeAvg > 0.5, "Vehicle 2 avg harsh brakes should be > 0.5, got: " + v2BrakeAvg);
        assertTrue(v2AccelAvg > 0.5, "Vehicle 2 avg hard accels should be > 0.5, got: " + v2AccelAvg);

        // 3. CONFLICTING: harsh braking only (avg harsh brake > 0.5, avg hard accel <= 0.5)
        java.util.List<com.paymu.telematics.model.TripAggregate> v3Aggs = aggsByVehicle.get(v3);
        assertNotNull(v3Aggs);
        assertEquals(4, v3Aggs.size());
        double v3BrakeAvg = v3Aggs.stream().mapToInt(com.paymu.telematics.model.TripAggregate::harshBrakingCount).average().orElse(0);
        double v3AccelAvg = v3Aggs.stream().mapToInt(com.paymu.telematics.model.TripAggregate::hardAccelerationCount).average().orElse(0);
        assertTrue(v3BrakeAvg > 0.5, "Vehicle 3 avg harsh brakes should be > 0.5, got: " + v3BrakeAvg);
        assertEquals(0.0, v3AccelAvg, "Vehicle 3 avg hard accels should be 0.0, got: " + v3AccelAvg);

        // 4. UNVERIFIED: careful driving, 0 harsh brakes, 0 hard accels, every accel < 2.0
        java.util.List<com.paymu.telematics.model.TripAggregate> v4Aggs = aggsByVehicle.get(v4);
        assertNotNull(v4Aggs);
        assertEquals(6, v4Aggs.size());
        double v4BrakeAvg = v4Aggs.stream().mapToInt(com.paymu.telematics.model.TripAggregate::harshBrakingCount).average().orElse(0);
        double v4AccelAvg = v4Aggs.stream().mapToInt(com.paymu.telematics.model.TripAggregate::hardAccelerationCount).average().orElse(0);
        assertEquals(0.0, v4BrakeAvg);
        assertEquals(0.0, v4AccelAvg);
        events.stream().filter(e -> e.vehicleId().equals(v4)).forEach(e -> {
            assertFalse(e.brakingEvent());
            assertTrue(e.accelerationMs2() < 2.0, "Vehicle 4 acceleration must be < 2.0: " + e.accelerationMs2());
        });

        // 5. INSUFFICIENT: no trips
        assertNull(aggsByVehicle.get(v5));
    }
}
