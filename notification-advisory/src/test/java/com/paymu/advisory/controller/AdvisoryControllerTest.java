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
        when(downstreamDataService.evaluateConsent(vehicleId.toString()))
                .thenReturn(com.paymu.advisory.model.ConsentEvaluation.permitted());
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

        org.mockito.Mockito.verify(downstreamDataService).fetchTripAggregates(vehicleId.toString());
    }

    @Test
    @DisplayName("Opted-out vehicle returns TRACKING_DISABLED advisory and makes zero data calls")
    void optedOut_returnsTrackingDisabled() throws Exception {
        UUID vehicleId = UUID.randomUUID();
        when(downstreamDataService.evaluateConsent(vehicleId.toString()))
                .thenReturn(com.paymu.advisory.model.ConsentEvaluation.denied());

        mockMvc.perform(get("/advisories/" + vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle_id").value(vehicleId.toString()))
                .andExpect(jsonPath("$.advisories.length()").value(1))
                .andExpect(jsonPath("$.advisories[0].advisory_type").value("TRACKING_DISABLED"))
                .andExpect(jsonPath("$.advisories[0].priority").value("LOW"))
                .andExpect(jsonPath("$.advisories[0].title").value("Personalised insights are off"))
                .andExpect(jsonPath("$.advisories[0].message").value("Turn on usage and maintenance tracking to get service reminders and a personalised premium."))
                .andExpect(jsonPath("$.advisories[0].action_url").value("https://paym.app/settings/consent"));

        org.mockito.Mockito.verify(downstreamDataService, org.mockito.Mockito.never()).fetchTripAggregates(any());
        org.mockito.Mockito.verify(downstreamDataService, org.mockito.Mockito.never()).fetchTimeline(any());
        org.mockito.Mockito.verify(downstreamDataService, org.mockito.Mockito.never()).fetchRecallStatus(any());
        org.mockito.Mockito.verify(downstreamDataService, org.mockito.Mockito.never()).fetchScore(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Consent status unavailable returns empty advisories list and makes zero data calls")
    void consentUnavailable_returnsEmptyList() throws Exception {
        UUID vehicleId = UUID.randomUUID();
        when(downstreamDataService.evaluateConsent(vehicleId.toString()))
                .thenReturn(com.paymu.advisory.model.ConsentEvaluation.unavailable());

        mockMvc.perform(get("/advisories/" + vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle_id").value(vehicleId.toString()))
                .andExpect(jsonPath("$.advisories.length()").value(0));

        org.mockito.Mockito.verify(downstreamDataService, org.mockito.Mockito.never()).fetchTripAggregates(any());
        org.mockito.Mockito.verify(downstreamDataService, org.mockito.Mockito.never()).fetchTimeline(any());
        org.mockito.Mockito.verify(downstreamDataService, org.mockito.Mockito.never()).fetchRecallStatus(any());
        org.mockito.Mockito.verify(downstreamDataService, org.mockito.Mockito.never()).fetchScore(any(), any(), any(), any());
    }
}
