package com.paymu.advisory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymu.advisory.model.AdvisoryMessage;
import com.paymu.advisory.model.AdvisoryPriority;
import com.paymu.advisory.service.AdvisoryGeneratorService;
import com.paymu.advisory.service.DownstreamDataService;
import com.paymu.advisory.service.NotificationDeliveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdvisoryController.class)
@DisplayName("AdvisoryController")
class AdvisoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DownstreamDataService downstreamDataService;

    @MockBean
    private AdvisoryGeneratorService advisoryGeneratorService;

    @MockBean
    private NotificationDeliveryService deliveryService;

    @Test
    @DisplayName("Malformed UUID parameter returns 400 with INVALID_REQUEST")
    void malformedUuidReturns400() throws Exception {
        mockMvc.perform(get("/advisories/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Valid UUID returns 200 and advisory payload")
    void validUuidReturns200() throws Exception {
        UUID vehicleId = UUID.randomUUID();
        when(advisoryGeneratorService.generate(any(), any(), any()))
                .thenReturn(List.of(new AdvisoryMessage(
                        "SAFE_DRIVING_COMMENDATION",
                        AdvisoryPriority.LOW,
                        "Great Driving",
                        "Keep it up",
                        "https://paym.app"
                )));

        mockMvc.perform(get("/advisories/" + vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle_id").value(vehicleId.toString()))
                .andExpect(jsonPath("$.advisories[0].advisory_type").value("SAFE_DRIVING_COMMENDATION"));
    }
}
