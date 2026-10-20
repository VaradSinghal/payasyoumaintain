package com.paymu.maintenance.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.paymu.maintenance.controller.DtcController;
import com.paymu.maintenance.service.DtcStore;
import com.paymu.maintenance.service.SchemaValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.InputStream;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DtcController.class)
@DisplayName("DTC Contract Tests")
class DtcContractTest {

    private static final String SCHEMA_PATH = "/schemas/dtc-reading.schema.json";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DtcStore dtcStore;

    private JsonSchema schema;

    @TestConfiguration
    static class TestConfig {
        @Bean
        public SchemaValidationService schemaValidationService() {
            return new SchemaValidationService();
        }

        @Bean
        public DtcStore dtcStore() {
            return new DtcStore();
        }
    }

    @BeforeEach
    void setUp() {
        dtcStore.clear();

        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (InputStream is = getClass().getResourceAsStream(SCHEMA_PATH)) {
            assertNotNull(is, () -> "Contract schema not found on classpath: " + SCHEMA_PATH);
            schema = factory.getSchema(is);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load schema from " + SCHEMA_PATH, e);
        }
    }

    private void validateJsonAgainstSchema(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        Set<ValidationMessage> errors = schema.validate(node);
        assertTrue(errors.isEmpty(), () -> "Schema validation failed with errors: " + errors);
    }

    @Test
    @DisplayName("POST response, GET latest, and each GET history item validate against dtc-reading.schema.json")
    void testPostGetLatestAndGetHistoryConformToContract() throws Exception {
        String vehicleId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

        // 1. POST an OBD device reading and validate POST response against schema
        String obdPayload = """
                {
                  "vehicle_id": "%s",
                  "timestamp": "2026-10-17T12:00:00Z",
                  "confirmed_codes": ["P0101", "C1234"],
                  "pending_codes": ["U0100"],
                  "source": "obd_device",
                  "device_id": "obd-dongle-999"
                }
                """.formatted(vehicleId);

        MvcResult postResult = mockMvc.perform(post("/dtc-readings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(obdPayload))
                .andExpect(status().isOk())
                .andReturn();

        String postResponse = postResult.getResponse().getContentAsString();
        validateJsonAgainstSchema(postResponse);

        // 2. GET latest and validate response against schema
        MvcResult latestResult = mockMvc.perform(get("/dtc-readings/{vehicleId}/latest", vehicleId))
                .andExpect(status().isOk())
                .andReturn();

        String latestResponse = latestResult.getResponse().getContentAsString();
        validateJsonAgainstSchema(latestResponse);

        // 3. POST a second manual reading
        String manualPayload = """
                {
                  "vehicle_id": "%s",
                  "timestamp": "2026-10-17T13:00:00Z",
                  "confirmed_codes": [],
                  "pending_codes": [],
                  "source": "manual_entry",
                  "device_id": null
                }
                """.formatted(vehicleId);

        MvcResult postResult2 = mockMvc.perform(post("/dtc-readings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manualPayload))
                .andExpect(status().isOk())
                .andReturn();
        validateJsonAgainstSchema(postResult2.getResponse().getContentAsString());

        // 4. GET history and validate each item against schema
        MvcResult historyResult = mockMvc.perform(get("/dtc-readings/{vehicleId}/history", vehicleId))
                .andExpect(status().isOk())
                .andReturn();

        String historyResponse = historyResult.getResponse().getContentAsString();
        JsonNode historyArray = objectMapper.readTree(historyResponse);
        assertTrue(historyArray.isArray(), "History response must be a JSON array");
        assertEquals(2, historyArray.size(), "History should contain exactly 2 readings");

        for (int i = 0; i < historyArray.size(); i++) {
            final int index = i;
            JsonNode item = historyArray.get(i);
            Set<ValidationMessage> errors = schema.validate(item);
            assertTrue(errors.isEmpty(), () -> "History item [" + index + "] schema validation failed: " + errors);
        }
    }

    @Test
    @DisplayName("Schema validation rejects unexpected fields in DTC response")
    void schemaValidationRejectsUnexpectedFields() throws Exception {
        String validReadingJson = """
                {
                  "vehicle_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                  "timestamp": "2026-10-17T12:00:00Z",
                  "confirmed_codes": ["P0101"],
                  "pending_codes": [],
                  "source": "obd_device",
                  "device_id": "obd-dongle-999"
                }
                """;

        ObjectNode node = (ObjectNode) objectMapper.readTree(validReadingJson);
        node.put("unexpected_field", "forbidden_value");

        Set<ValidationMessage> errors = schema.validate(node);
        assertFalse(errors.isEmpty(), "Schema must fail if response carries an unexpected field");
        assertTrue(errors.stream().anyMatch(e -> e.getMessage().contains("unexpected_field")),
                "Error should mention unexpected_field");
    }
}
