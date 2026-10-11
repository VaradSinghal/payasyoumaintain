package com.paymu.maintenance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymu.maintenance.exception.GlobalExceptionHandler;
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
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("MaintenanceController")
class MaintenanceControllerTest {

    private MaintenanceController controller;
    private MaintenanceStore store;
    private RecallStore recallStore;
    private SchemaValidationService schemaValidationService;
    private OcrStubService ocrStubService;
    private SyntheticDataGenerator syntheticDataGenerator;
    private ObjectMapper objectMapper;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        store = Mockito.mock(MaintenanceStore.class);
        recallStore = Mockito.mock(RecallStore.class);
        schemaValidationService = Mockito.mock(SchemaValidationService.class);
        ocrStubService = Mockito.mock(OcrStubService.class);
        syntheticDataGenerator = Mockito.mock(SyntheticDataGenerator.class);
        objectMapper = new ObjectMapper();

        controller = new MaintenanceController(
                schemaValidationService,
                ocrStubService,
                store,
                recallStore,
                syntheticDataGenerator,
                objectMapper
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /timeline/{vehicleId} returns 200 with empty timeline when vehicle has no events")
    void emptyTimelineReturns200WithEmptyEvents() throws Exception {
        UUID vehicleId = UUID.randomUUID();
        when(store.getTimeline(vehicleId.toString())).thenReturn(null);

        mockMvc.perform(get("/timeline/" + vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle_id").value(vehicleId.toString()))
                .andExpect(jsonPath("$.total_services").value(0))
                .andExpect(jsonPath("$.latest_service_date").doesNotExist())
                .andExpect(jsonPath("$.latest_odometer_km").doesNotExist())
                .andExpect(jsonPath("$.events").isArray())
                .andExpect(jsonPath("$.events.length()").value(0));
    }

    @Test
    @DisplayName("GET /timeline/not-a-uuid returns 400 with INVALID_REQUEST")
    void malformedUuidReturns400() throws Exception {
        mockMvc.perform(get("/timeline/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("GET /timeline/{vehicleId} returns 200 with populated events when vehicle has history")
    void populatedTimelineReturns200() throws Exception {
        UUID vehicleId = UUID.randomUUID();
        MaintenanceEvent event = new MaintenanceEvent(
                "evt-1", vehicleId.toString(), "2026-03-01", "oil_change",
                12000, List.of(new PartReplaced("Filter", "F-1", 1)), "dealer", "Routine", List.of()
        );
        ServiceTimeline timeline = new ServiceTimeline(vehicleId.toString(), 1, "2026-03-01", 12000, List.of(event));
        when(store.getTimeline(vehicleId.toString())).thenReturn(timeline);

        mockMvc.perform(get("/timeline/" + vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle_id").value(vehicleId.toString()))
                .andExpect(jsonPath("$.total_services").value(1))
                .andExpect(jsonPath("$.latest_service_date").value("2026-03-01"))
                .andExpect(jsonPath("$.latest_odometer_km").value(12000))
                .andExpect(jsonPath("$.events.length()").value(1));
    }
}
