package com.paymu.telematics.controller;

import com.paymu.telematics.model.GpsCoordinates;
import com.paymu.telematics.model.TripAggregate;
import com.paymu.telematics.model.UsageEvent;
import com.paymu.telematics.service.EventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TelematicsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventStore eventStore;

    @BeforeEach
    void setUp() {
        eventStore.clear();
    }

    @Test
    @DisplayName("GET /aggregates/{vehicleId} for vehicle with no data returns 200 with empty list")
    void getAggregates_emptyVehicle_returns200EmptyList() throws Exception {
        String vehicleId = UUID.randomUUID().toString();
        mockMvc.perform(get("/aggregates/" + vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    @DisplayName("GET /events/{vehicleId} for vehicle with no data returns 200 with empty list")
    void getEvents_emptyVehicle_returns200EmptyList() throws Exception {
        String vehicleId = UUID.randomUUID().toString();
        mockMvc.perform(get("/events/" + vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    @DisplayName("GET /aggregates/not-a-uuid returns 400 with INVALID_REQUEST")
    void getAggregates_malformedUuid_returns400() throws Exception {
        mockMvc.perform(get("/aggregates/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("GET /events/not-a-uuid returns 400 with INVALID_REQUEST")
    void getEvents_malformedUuid_returns400() throws Exception {
        mockMvc.perform(get("/events/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("GET /aggregates/{vehicleId} returns 200 with populated list when data exists")
    void getAggregates_withData_returns200() throws Exception {
        String vehicleId = UUID.randomUUID().toString();
        TripAggregate agg = new TripAggregate(
                UUID.randomUUID().toString(),
                vehicleId,
                10,
                55.0,
                80.0,
                1,
                2,
                15.5,
                Instant.now().minusSeconds(1800).toString(),
                Instant.now().toString());
        eventStore.storeAggregates(List.of(agg));

        mockMvc.perform(get("/aggregates/" + vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].hard_acceleration_count").value(2))
                .andExpect(jsonPath("$[0].harsh_braking_count").value(1));
    }

    @Test
    @DisplayName("GET /events/{vehicleId} returns 200 with populated list when data exists")
    void getEvents_withData_returns200() throws Exception {
        String vehicleId = UUID.randomUUID().toString();
        UsageEvent event = new UsageEvent(
                UUID.randomUUID().toString(),
                vehicleId,
                UUID.randomUUID().toString(),
                Instant.now().toString(),
                new GpsCoordinates(12.97, 77.59, null),
                50.0,
                1.5,
                false);
        eventStore.storeEvents(List.of(event));

        mockMvc.perform(get("/events/" + vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].speed_kmh").value(50.0));
    }
}
