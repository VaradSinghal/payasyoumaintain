package com.paymu.pricing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@RestClientTest(DownstreamOrchestratorService.class)
class DownstreamOrchestratorServiceTest {

    @Autowired
    private DownstreamOrchestratorService service;

    @Autowired
    private MockRestServiceServer server;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "maintenanceUrl", "http://maintenance");
        org.springframework.test.util.ReflectionTestUtils.setField(service, "telematicsUrl", "http://telematics");
        org.springframework.test.util.ReflectionTestUtils.setField(service, "scoringUrl", "http://scoring");
        org.springframework.test.util.ReflectionTestUtils.setField(service, "identityUrl", "http://identity");
    }

    @Test
    void fetchScore_failingRecallClientSendsUnavailableToScorer() throws Exception {
        String vehicleId = "v-123";
        
        // Mock timeline (returns something)
        server.expect(requestTo("http://maintenance/timeline/" + vehicleId))
                .andRespond(withSuccess("{\"events\": []}", org.springframework.http.MediaType.APPLICATION_JSON));
                
        // Mock telematics (returns something)
        server.expect(requestTo("http://telematics/aggregates/" + vehicleId))
                .andRespond(withSuccess("[]", org.springframework.http.MediaType.APPLICATION_JSON));

        // Mock recall failing
        server.expect(requestTo("http://maintenance/recall-status/" + vehicleId))
                .andRespond(withServerError());

        // Mock scoring to check the request payload
        server.expect(requestTo("http://scoring/score/" + vehicleId))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    String body = request.getBody().toString();
                    assertTrue(body.contains("\"availability\":\"unavailable\""));
                    assertTrue(body.contains("\"vehicle_id\":\"v-123\""));
                })
                .andRespond(withSuccess("{\"score_id\":\"s-1\"}", org.springframework.http.MediaType.APPLICATION_JSON));

        service.fetchScore(vehicleId, 24);
        
        server.verify();
    }

    @Test
    void fetchPolicyId_failingClientReturnsDeterministicUuid() {
        String vehicleId = "v-123";
        server.expect(requestTo("http://identity/vehicles/" + vehicleId))
                .andRespond(withServerError());

        String policyId = service.fetchPolicyId(vehicleId);

        assertNotNull(policyId);
        assertEquals(java.util.UUID.nameUUIDFromBytes(("policy_" + vehicleId).getBytes()).toString(), policyId);
        server.verify();
    }

    @Test
    void fetchScore_deserializesNullUsageScoreSuccessfully() throws Exception {
        String vehicleId = "v-456";
        server.expect(requestTo("http://maintenance/timeline/" + vehicleId))
                .andRespond(withSuccess("{\"events\": []}", org.springframework.http.MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://telematics/aggregates/" + vehicleId))
                .andRespond(withSuccess("[]", org.springframework.http.MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://maintenance/recall-status/" + vehicleId))
                .andRespond(withSuccess("{\"vehicle_id\":\"v-456\",\"availability\":\"available\",\"has_open_recall\":false}", org.springframework.http.MediaType.APPLICATION_JSON));

        String scorerResponse = """
            {
                "score_id": "s-null-usage",
                "vehicle_id": "v-456",
                "usage_score": null,
                "maintenance_score": null,
                "composite_score": null,
                "computed_at": "2026-10-11T12:00:00Z",
                "model_version": "v1.2.0",
                "health_status": "insufficient_data",
                "vehicle_health_score": null,
                "renewal_recommendation": "Schedule baseline inspection",
                "has_open_recall": false,
                "data_flags": ["usage_data_unavailable"],
                "contributing_factors": []
            }
        """;

        server.expect(requestTo("http://scoring/score/" + vehicleId))
                .andRespond(withSuccess(scorerResponse, org.springframework.http.MediaType.APPLICATION_JSON));

        com.paymu.pricing.model.ScoreResponse result = service.fetchScore(vehicleId, 12);

        assertNotNull(result);
        assertNull(result.usageScore());
        assertNull(result.maintenanceScore());
        assertNull(result.compositeScore());
        assertTrue(result.dataFlags().contains("usage_data_unavailable"));
        server.verify();
    }

    @Test
    void fetchScore_forwardsTripAggregatesWithExtraFieldIntact() throws Exception {
        String vehicleId = "v-789";
        String extraAggregates = """
            [
              {
                "trip_id": "t-789",
                "vehicle_id": "v-789",
                "hard_acceleration_count": 9,
                "future_telematics_metric": 42.5
              }
            ]
            """;

        server.expect(requestTo("http://maintenance/timeline/" + vehicleId))
                .andRespond(withSuccess("{\"events\": []}", org.springframework.http.MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://telematics/aggregates/" + vehicleId))
                .andRespond(withSuccess(extraAggregates, org.springframework.http.MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://maintenance/recall-status/" + vehicleId))
                .andRespond(withSuccess("{\"vehicle_id\":\"v-789\",\"has_open_recall\":false}", org.springframework.http.MediaType.APPLICATION_JSON));

        server.expect(requestTo("http://scoring/score/" + vehicleId))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    String body = request.getBody().toString();
                    com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(body);
                    com.fasterxml.jackson.databind.JsonNode aggregates = root.get("trip_aggregates");
                    assertNotNull(aggregates);
                    assertTrue(aggregates.isArray());
                    assertEquals(9, aggregates.get(0).get("hard_acceleration_count").asInt());
                    assertEquals(42.5, aggregates.get(0).get("future_telematics_metric").asDouble());
                })
                .andRespond(withSuccess("{\"score_id\":\"s-extra\"}", org.springframework.http.MediaType.APPLICATION_JSON));

        service.fetchScore(vehicleId, 24);
        server.verify();
    }

    @Test
    void fetchScore_emptyTimelineSendsServiceTimelineWithEmptyEvents() throws Exception {
        String vehicleId = "v-empty";
        String emptyTimeline = """
            {
                "vehicle_id": "v-empty",
                "total_services": 0,
                "latest_service_date": null,
                "latest_odometer_km": null,
                "events": []
            }
            """;

        server.expect(requestTo("http://maintenance/timeline/" + vehicleId))
                .andRespond(withSuccess(emptyTimeline, org.springframework.http.MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://telematics/aggregates/" + vehicleId))
                .andRespond(withSuccess("[]", org.springframework.http.MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://maintenance/recall-status/" + vehicleId))
                .andRespond(withSuccess("{\"vehicle_id\":\"v-empty\"}", org.springframework.http.MediaType.APPLICATION_JSON));

        server.expect(requestTo("http://scoring/score/" + vehicleId))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    String body = request.getBody().toString();
                    com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(body);
                    com.fasterxml.jackson.databind.JsonNode timeline = root.get("service_timeline");
                    assertNotNull(timeline, "service_timeline must be sent when empty timeline returned 200");
                    assertEquals(0, timeline.get("total_services").asInt());
                    assertTrue(timeline.get("events").isArray());
                    assertEquals(0, timeline.get("events").size());
                })
                .andRespond(withSuccess("{\"score_id\":\"s-empty\"}", org.springframework.http.MediaType.APPLICATION_JSON));

        service.fetchScore(vehicleId, 12);
        server.verify();
    }

    @Test
    void fetchScore_unreachableTimelineOmitsServiceTimeline() throws Exception {
        String vehicleId = "v-unreach";

        // Unreachable maintenance timeline (500 Server Error)
        server.expect(requestTo("http://maintenance/timeline/" + vehicleId))
                .andRespond(withServerError());
        server.expect(requestTo("http://telematics/aggregates/" + vehicleId))
                .andRespond(withSuccess("[]", org.springframework.http.MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://maintenance/recall-status/" + vehicleId))
                .andRespond(withSuccess("{\"vehicle_id\":\"v-unreach\"}", org.springframework.http.MediaType.APPLICATION_JSON));

        server.expect(requestTo("http://scoring/score/" + vehicleId))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    String body = request.getBody().toString();
                    com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(body);
                    assertNull(root.get("service_timeline"), "service_timeline must be omitted when maintenance is unreachable");
                })
                .andRespond(withSuccess("{\"score_id\":\"s-unreach\"}", org.springframework.http.MediaType.APPLICATION_JSON));

        service.fetchScore(vehicleId, 12);
        server.verify();
    }

    @Test
    void fetchScore_clientError4xxLogsAndReturnsNullGracefully() {
        String vehicleId = "v-4xx";
        server.expect(requestTo("http://maintenance/timeline/" + vehicleId))
                .andRespond(withSuccess("{\"events\": []}", org.springframework.http.MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://telematics/aggregates/" + vehicleId))
                .andRespond(withSuccess("[]", org.springframework.http.MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://maintenance/recall-status/" + vehicleId))
                .andRespond(withSuccess("{\"vehicle_id\":\"v-4xx\"}", org.springframework.http.MediaType.APPLICATION_JSON));

        server.expect(requestTo("http://scoring/score/" + vehicleId))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY)
                        .body("{\"detail\": \"bad schema\"}")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON));

        com.paymu.pricing.model.ScoreResponse score = service.fetchScore(vehicleId, 12);
        assertNull(score);
        server.verify();
    }
}
