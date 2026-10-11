package com.paymu.advisory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymu.advisory.model.RecallStatus;
import com.paymu.advisory.model.ScoreResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@RestClientTest(DownstreamDataService.class)
class DownstreamDataServiceTest {

    @Autowired
    private DownstreamDataService service;

    @Autowired
    private MockRestServiceServer server;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "maintenanceUrl", "http://maintenance");
        org.springframework.test.util.ReflectionTestUtils.setField(service, "scoringUrl", "http://scoring");
    }

    @Test
    void fetchRecallStatus_failingClientReturnsUnavailable() {
        String vehicleId = "v-123";
        server.expect(requestTo("http://maintenance/recall-status/" + vehicleId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        RecallStatus result = service.fetchRecallStatus(vehicleId);

        assertNotNull(result);
        assertEquals("unavailable", result.availability());
        assertNull(result.hasOpenRecall()); // Should be null
        
        server.verify();
    }

    @Test
    void fetchScore_deserializesNullUsageScore() {
        String vehicleId = "v-123";
        String responseJson = """
            {
                "score_id": "sc-1",
                "vehicle_id": "v-123",
                "usage_score": null,
                "maintenance_score": 85.0,
                "composite_score": null,
                "computed_at": "2026-10-11T12:00:00Z",
                "model_version": "v1.2.0",
                "contributing_factors": []
            }
            """;

        server.expect(requestTo("http://scoring/score/" + vehicleId))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        ScoreResponse score = service.fetchScore(vehicleId, null, null, null);

        assertNotNull(score);
        assertNull(score.usageScore());
        assertEquals(85.0, score.maintenanceScore());
        assertNull(score.compositeScore());
        server.verify();
    }
}
