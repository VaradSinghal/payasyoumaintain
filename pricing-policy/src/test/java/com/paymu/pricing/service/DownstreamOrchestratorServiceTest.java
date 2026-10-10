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
}
