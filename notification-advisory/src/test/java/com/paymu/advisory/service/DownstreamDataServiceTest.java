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
    void fetchRecallStatus_successfulClientSetsAvailable() {
        String vehicleId = "v-123";
        String maintenanceResponse = """
            {
                "vehicle_id": "v-123",
                "has_open_recall": false,
                "recall_count": 0,
                "recall_details": [],
                "source": "oem_recall_db",
                "checked_at": "2026-10-11T12:00:00Z"
            }
            """;
        server.expect(requestTo("http://maintenance/recall-status/" + vehicleId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(maintenanceResponse, MediaType.APPLICATION_JSON));

        RecallStatus result = service.fetchRecallStatus(vehicleId);

        assertNotNull(result);
        assertEquals("available", result.availability());
        assertFalse(result.hasOpenRecall());
        assertEquals(0, result.recallCount());
        server.verify();
    }

    @Test
    void fetchScore_serializesAvailabilityOnSuccessPath() throws Exception {
        String vehicleId = "v-123";
        RecallStatus recall = new RecallStatus(vehicleId, "available", false, 0, java.util.List.of(), "oem_recall_db", "2026-10-11T12:00:00Z");

        server.expect(requestTo("http://scoring/score/" + vehicleId))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    String body = request.getBody().toString();
                    com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(body);
                    com.fasterxml.jackson.databind.JsonNode recallNode = root.get("recall_status");
                    assertNotNull(recallNode, "recall_status must be present in request body");
                    assertEquals("available", recallNode.get("availability").asText());
                    assertFalse(recallNode.get("availability").isNull());
                })
                .andRespond(withSuccess("{\"score_id\":\"sc-1\"}", MediaType.APPLICATION_JSON));

        ScoreResponse score = service.fetchScore(vehicleId, null, null, recall);
        assertNotNull(score);
        server.verify();
    }

    @Test
    void fetchScore_serializesAvailabilityOnFailurePath() throws Exception {
        String vehicleId = "v-123";
        RecallStatus recall = new RecallStatus(vehicleId, "unavailable", null, 0, java.util.List.of(), "", "");

        server.expect(requestTo("http://scoring/score/" + vehicleId))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    String body = request.getBody().toString();
                    com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(body);
                    com.fasterxml.jackson.databind.JsonNode recallNode = root.get("recall_status");
                    assertNotNull(recallNode, "recall_status must be present in request body");
                    assertEquals("unavailable", recallNode.get("availability").asText());
                    assertFalse(recallNode.get("availability").isNull());
                })
                .andRespond(withSuccess("{\"score_id\":\"sc-1\"}", MediaType.APPLICATION_JSON));

        ScoreResponse score = service.fetchScore(vehicleId, null, null, recall);
        assertNotNull(score);
        server.verify();
    }

    @Test
    void fetchScore_forwardsTripAggregatesWithExtraFieldIntact() throws Exception {
        String vehicleId = "v-123";
        String rawAggregatesJson = """
            [
              {
                "trip_id": "trip-001",
                "vehicle_id": "v-123",
                "hard_acceleration_count": 7,
                "unknown_telematics_field": "custom_signal_value"
              }
            ]
            """;
        com.fasterxml.jackson.databind.JsonNode tripAggregates = objectMapper.readTree(rawAggregatesJson);

        server.expect(requestTo("http://scoring/score/" + vehicleId))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    String body = request.getBody().toString();
                    com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(body);
                    com.fasterxml.jackson.databind.JsonNode aggregatesNode = root.get("trip_aggregates");
                    assertNotNull(aggregatesNode, "trip_aggregates must be in request body");
                    assertTrue(aggregatesNode.isArray());
                    com.fasterxml.jackson.databind.JsonNode first = aggregatesNode.get(0);
                    assertEquals(7, first.get("hard_acceleration_count").asInt());
                    assertEquals("custom_signal_value", first.get("unknown_telematics_field").asText());
                })
                .andRespond(withSuccess("{\"score_id\":\"sc-1\"}", MediaType.APPLICATION_JSON));

        ScoreResponse score = service.fetchScore(vehicleId, tripAggregates, null, null);
        assertNotNull(score);
        server.verify();
    }

    @Test
    void fetchScore_clientError4xxLogsAndReturnsNullGracefully() {
        String vehicleId = "v-123";
        server.expect(requestTo("http://scoring/score/" + vehicleId))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY)
                        .body("{\"detail\": \"Unprocessable Entity\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        ScoreResponse score = service.fetchScore(vehicleId, null, null, null);
        assertNull(score);
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
