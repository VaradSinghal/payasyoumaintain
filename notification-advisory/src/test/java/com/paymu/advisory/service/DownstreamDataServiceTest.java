package com.paymu.advisory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymu.advisory.model.RecallStatus;
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
}
