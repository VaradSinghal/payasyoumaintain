package com.paymu.maintenance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymu.maintenance.model.DtcReading;
import com.paymu.maintenance.service.DtcStore;
import com.paymu.maintenance.service.SchemaValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DtcController.class)
class DtcControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DtcStore dtcStore;

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
    }

    @Test
    void shouldAcceptObdDeviceSource() throws Exception {
        String payload = """
                {
                  "vehicle_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                  "timestamp": "2026-10-08T10:00:00Z",
                  "dtc_codes": ["P0101", "C1234"],
                  "confirmed": true,
                  "source": "obd_device",
                  "device_id": "obd-dongle-999"
                }
                """;

        mockMvc.perform(post("/api/v1/dtc-readings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle_id").value("a1b2c3d4-e5f6-7890-abcd-ef1234567890"))
                .andExpect(jsonPath("$.device_id").value("obd-dongle-999"));
    }

    @Test
    void shouldAcceptManualEntrySource() throws Exception {
        String payload = """
                {
                  "vehicle_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                  "timestamp": "2026-10-08T10:05:00Z",
                  "dtc_codes": ["U0100"],
                  "confirmed": false,
                  "source": "manual_entry",
                  "device_id": null
                }
                """;

        mockMvc.perform(post("/api/v1/dtc-readings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle_id").value("a1b2c3d4-e5f6-7890-abcd-ef1234567890"))
                .andExpect(jsonPath("$.source").value("manual_entry"));
    }

    @Test
    void shouldRejectInvalidDtcCode() throws Exception {
        String payload = """
                {
                  "vehicle_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                  "timestamp": "2026-10-08T10:00:00Z",
                  "dtc_codes": ["INVALID_CODE"],
                  "confirmed": true,
                  "source": "obd_device",
                  "device_id": "obd-dongle-999"
                }
                """;

        mockMvc.perform(post("/api/v1/dtc-readings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SCHEMA_VALIDATION_FAILED"));
    }

    @Test
    void shouldReturn404ForEmptyHistoryAndLatest() throws Exception {
        String vehicleId = "unknown-vehicle";

        mockMvc.perform(get("/api/v1/dtc-readings/{id}/latest", vehicleId))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/dtc-readings/{id}/history", vehicleId))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnHistoryAndLatest() throws Exception {
        String vehicleId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        
        String payload1 = """
                {
                  "vehicle_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                  "timestamp": "2026-10-01T10:00:00Z",
                  "dtc_codes": ["P0101"],
                  "confirmed": true,
                  "source": "obd_device",
                  "device_id": "obd-dongle-999"
                }
                """;
                
        String payload2 = """
                {
                  "vehicle_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                  "timestamp": "2026-10-08T10:00:00Z",
                  "dtc_codes": ["P0101", "P0102"],
                  "confirmed": true,
                  "source": "manual_entry",
                  "device_id": null
                }
                """;

        mockMvc.perform(post("/api/v1/dtc-readings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload1))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/dtc-readings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload2))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/dtc-readings/{id}/history", vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].dtc_codes.length()").value(2)) // latest first
                .andExpect(jsonPath("$[1].dtc_codes.length()").value(1));

        mockMvc.perform(get("/api/v1/dtc-readings/{id}/latest", vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("manual_entry"));
    }
}
