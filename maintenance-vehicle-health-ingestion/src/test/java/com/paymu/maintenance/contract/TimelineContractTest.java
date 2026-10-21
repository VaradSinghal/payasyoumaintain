package com.paymu.maintenance.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.paymu.maintenance.controller.MaintenanceController;
import com.paymu.maintenance.model.MaintenanceEvent;
import com.paymu.maintenance.model.PartReplaced;
import com.paymu.maintenance.model.ServiceTimeline;
import com.paymu.maintenance.service.MaintenanceStore;
import com.paymu.maintenance.service.OcrStubService;
import com.paymu.maintenance.service.RecallStore;
import com.paymu.maintenance.service.SchemaValidationService;
import com.paymu.maintenance.service.SyntheticDataGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MaintenanceController.class)
@DisplayName("Maintenance Service Timeline Contract Tests")
class TimelineContractTest {

    private static final String TIMELINE_SCHEMA_PATH = "/schemas/service-timeline.schema.json";
    private static final String ERROR_SCHEMA_PATH = "/schemas/error-response.schema.json";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SchemaValidationService validationService;

    @MockBean
    private OcrStubService ocrStubService;

    @MockBean
    private MaintenanceStore store;

    @MockBean
    private RecallStore recallStore;

    @MockBean
    private SyntheticDataGenerator syntheticDataGenerator;

    private JsonSchema timelineSchema;
    private JsonSchema errorSchema;

    @BeforeEach
    void setUp() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (InputStream is = getClass().getResourceAsStream(TIMELINE_SCHEMA_PATH)) {
            assertNotNull(is, () -> "Contract schema not found on classpath: " + TIMELINE_SCHEMA_PATH);
            timelineSchema = factory.getSchema(is);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load schema from " + TIMELINE_SCHEMA_PATH, e);
        }

        try (InputStream is = getClass().getResourceAsStream(ERROR_SCHEMA_PATH)) {
            assertNotNull(is, () -> "Contract schema not found on classpath: " + ERROR_SCHEMA_PATH);
            errorSchema = factory.getSchema(is);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load schema from " + ERROR_SCHEMA_PATH, e);
        }
    }

    private void validateTimeline(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        Set<ValidationMessage> errors = timelineSchema.validate(node);
        assertTrue(errors.isEmpty(), () -> "Timeline schema validation failed: " + errors);
    }

    private void validateError(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        Set<ValidationMessage> errors = errorSchema.validate(node);
        assertTrue(errors.isEmpty(), () -> "Error schema validation failed: " + errors);
    }

    // ── 1. Timeline with events ───────────────────────────────────────────────

    @Test
    @DisplayName("Timeline with OEM events conforms to service-timeline schema")
    void testTimelineWithEventsConformsToContract() throws Exception {
        String vehicleId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        MaintenanceEvent event1 = new MaintenanceEvent(
                "37212948-c1a8-4b7e-9c96-134bc9d51092",
                vehicleId,
                "2026-02-20",
                "brake_service",
                28000,
                List.of(new PartReplaced("Front Brake Pads", "BP-FR-2024", 1)),
                "oem_api",
                "Front brake pads replaced. OEM verified.",
                List.of()
        );
        MaintenanceEvent event2 = new MaintenanceEvent(
                "bf9e7b21-e415-4f2d-8a73-0eeab983a443",
                vehicleId,
                "2026-09-05",
                "full_service",
                35000,
                List.of(
                        new PartReplaced("Engine Oil 5W-30", "OIL-5W30-5L", 1),
                        new PartReplaced("Oil Filter", "OF-2024A", 1)
                ),
                "oem_api",
                "Full service nominal.",
                List.of()
        );

        ServiceTimeline timeline = new ServiceTimeline(vehicleId, 2, "2026-09-05", 35000, List.of(event1, event2));
        when(store.getTimeline(vehicleId)).thenReturn(timeline);

        MvcResult result = mockMvc.perform(get("/timeline/{vehicleId}", vehicleId))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        validateTimeline(body);

        JsonNode node = objectMapper.readTree(body);
        assertEquals(2, node.get("total_services").asInt());
        assertEquals("2026-09-05", node.get("latest_service_date").asText());
    }

    // ── 2. Empty timeline ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Empty timeline conforms to service-timeline schema")
    void testEmptyTimelineConformsToContract() throws Exception {
        String vehicleId = UUID.randomUUID().toString();
        when(store.getTimeline(vehicleId)).thenReturn(null);

        MvcResult result = mockMvc.perform(get("/timeline/{vehicleId}", vehicleId))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        validateTimeline(body);

        JsonNode node = objectMapper.readTree(body);
        assertEquals(0, node.get("total_services").asInt());
        assertTrue(node.get("latest_service_date").isNull());
        assertTrue(node.get("latest_odometer_km").isNull());
        assertEquals(0, node.get("events").size());
    }

    // ── 3. Timeline with self_upload event ────────────────────────────────────

    @Test
    @DisplayName("Timeline with self_upload event conforms to service-timeline schema")
    void testTimelineWithSelfUploadEventConformsToContract() throws Exception {
        String vehicleId = "c3d4e5f6-a7b8-9012-cdef-123456789012";
        MaintenanceEvent selfUploadEvent = new MaintenanceEvent(
                "61a0914e-9ff3-4f93-b6c8-f8cf4a6fbb82",
                vehicleId,
                "2026-05-15",
                "oil_change",
                42000,
                List.of(new PartReplaced("Mobil1 0W-20", "M1-0W20", 1)),
                "self_upload",
                "DIY oil change in garage.",
                List.of("https://storage.paym.app/invoices/oil-receipt.pdf")
        );

        ServiceTimeline timeline = new ServiceTimeline(vehicleId, 1, "2026-05-15", 42000, List.of(selfUploadEvent));
        when(store.getTimeline(vehicleId)).thenReturn(timeline);

        MvcResult result = mockMvc.perform(get("/timeline/{vehicleId}", vehicleId))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        validateTimeline(body);

        JsonNode node = objectMapper.readTree(body);
        assertEquals(1, node.get("total_services").asInt());
        assertEquals("self_upload", node.get("events").get(0).get("source").asText());
    }

    // ── 4. Undefined fields rejected by schema ────────────────────────────────

    @Test
    @DisplayName("Timeline schema rejects unexpected additionalProperties")
    void testTimelineSchemaRejectsUnexpectedFields() throws Exception {
        String vehicleId = UUID.randomUUID().toString();
        ServiceTimeline timeline = new ServiceTimeline(vehicleId, 0, null, null, List.of());
        when(store.getTimeline(vehicleId)).thenReturn(timeline);

        MvcResult result = mockMvc.perform(get("/timeline/{vehicleId}", vehicleId))
                .andExpect(status().isOk())
                .andReturn();

        ObjectNode node = (ObjectNode) objectMapper.readTree(result.getResponse().getContentAsString());
        node.put("unauthorized_injected_metric", 9999);

        Set<ValidationMessage> errors = timelineSchema.validate(node);
        assertFalse(errors.isEmpty(), "Schema validation must fail for unexpected property");
        assertTrue(errors.stream().anyMatch(e -> e.getMessage().contains("unauthorized_injected_metric")));
    }

    // ── 5. Exception handler 400 Bad Request conforms to error-response ───────

    @Test
    @DisplayName("Maintenance exception handler 400 conforms to error-response schema")
    void testExceptionHandler400ConformsToSchema() throws Exception {
        MvcResult result = mockMvc.perform(get("/timeline/not-a-valid-uuid"))
                .andExpect(status().isBadRequest())
                .andReturn();

        validateError(result.getResponse().getContentAsString());
    }

    // ── 7. Default vs include_flagged timelines ──────────────────────────────

    @Test
    @DisplayName("Timeline: default excludes flagged records while include_flagged=true includes them")
    void testDefaultVsIncludeFlaggedTimeline() throws Exception {
        String vehicleId = UUID.randomUUID().toString();

        MaintenanceEvent acceptedEvt = new MaintenanceEvent(
                UUID.randomUUID().toString(), vehicleId, "2026-09-01", "oil_change", 15000,
                List.of(new PartReplaced("Filter", "F-1", 1)), "self_upload", "", List.of(), "accepted");
        MaintenanceEvent flaggedEvt = new MaintenanceEvent(
                UUID.randomUUID().toString(), vehicleId, "2026-10-01", "brake_service", 12000,
                List.of(new PartReplaced("Pads", "P-1", 1)), "self_upload", "", List.of(), "flagged");

        ServiceTimeline defaultTimeline = new ServiceTimeline(
                vehicleId, 1, "2026-09-01", 15000, List.of(acceptedEvt), 1);
        ServiceTimeline fullTimeline = new ServiceTimeline(
                vehicleId, 2, "2026-10-01", 12000, List.of(acceptedEvt, flaggedEvt), 1);

        when(store.getTimeline(vehicleId)).thenReturn(defaultTimeline);
        when(store.getTimeline(vehicleId, true)).thenReturn(fullTimeline);

        // Default query:
        MvcResult resDefault = mockMvc.perform(get("/timeline/{id}", vehicleId))
                .andExpect(status().isOk())
                .andReturn();
        validateTimeline(resDefault.getResponse().getContentAsString());
        JsonNode nodeDefault = objectMapper.readTree(resDefault.getResponse().getContentAsString());
        assertEquals(1, nodeDefault.get("total_services").asInt());
        assertEquals(1, nodeDefault.get("flagged_count").asInt());
        assertEquals(1, nodeDefault.get("events").size());

        // With include_flagged=true:
        MvcResult resFull = mockMvc.perform(get("/timeline/{id}?include_flagged=true", vehicleId))
                .andExpect(status().isOk())
                .andReturn();
        validateTimeline(resFull.getResponse().getContentAsString());
        JsonNode nodeFull = objectMapper.readTree(resFull.getResponse().getContentAsString());
        assertEquals(2, nodeFull.get("total_services").asInt());
        assertEquals(1, nodeFull.get("flagged_count").asInt());
        assertEquals(2, nodeFull.get("events").size());
    }
}

