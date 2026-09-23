package com.paymu.advisory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymu.advisory.model.RecallStatus;
import com.paymu.advisory.model.ScoreResponse;
import com.paymu.advisory.model.ServiceTimeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Fetches data from maintenance-vehicle-health-ingestion and risk-scoring-engine
 * to supply the advisory generator.
 *
 * <p>Call order:</p>
 * <ol>
 *   <li>GET /timeline/{vehicleId}        → {@link ServiceTimeline}</li>
 *   <li>GET /recall-status/{vehicleId}   → {@link RecallStatus}</li>
 *   <li>POST /score/{vehicleId} with timeline + recall → {@link ScoreResponse}</li>
 * </ol>
 *
 * <p>Each call degrades gracefully — a failure returns null, and the advisory
 * generator simply skips the rules that rely on that data.</p>
 */
@Service
public class DownstreamDataService {

    private static final Logger log = LoggerFactory.getLogger(DownstreamDataService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${services.maintenance-url}")
    private String maintenanceUrl;

    @Value("${services.scoring-url}")
    private String scoringUrl;

    public DownstreamDataService(ObjectMapper objectMapper) {
        this.restClient = RestClient.create();
        this.objectMapper = objectMapper;
    }

    public ServiceTimeline fetchTimeline(String vehicleId) {
        return get(maintenanceUrl + "/timeline/" + vehicleId, ServiceTimeline.class, "service timeline");
    }

    public RecallStatus fetchRecallStatus(String vehicleId) {
        return get(maintenanceUrl + "/recall-status/" + vehicleId, RecallStatus.class, "recall status");
    }

    /**
     * Posts the already-fetched timeline and recall data to the scoring engine
     * to get a risk score. This avoids a second downstream pull inside the scorer.
     */
    public ScoreResponse fetchScore(String vehicleId, ServiceTimeline timeline, RecallStatus recall) {
        try {
            // Build a minimal ScoreRequest — only include what we have
            var payload = new java.util.LinkedHashMap<String, Object>();
            if (timeline != null) {
                payload.put("service_timeline", objectMapper.convertValue(timeline, Object.class));
            }
            if (recall != null) {
                payload.put("recall_status", objectMapper.convertValue(recall, Object.class));
            }
            // trip_aggregates omitted — advisory doesn't need to re-fetch telematics;
            // the score engine cold-starts those inputs as neutral 100.

            String body = objectMapper.writeValueAsString(payload);
            String responseBody = restClient.post()
                    .uri(scoringUrl + "/score/" + vehicleId)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            return responseBody != null ? objectMapper.readValue(responseBody, ScoreResponse.class) : null;

        } catch (RestClientException e) {
            log.warn("Risk scoring engine unavailable for vehicle {}: {}", vehicleId, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Failed to parse score response for vehicle {}: {}", vehicleId, e.getMessage());
            return null;
        }
    }

    // ── Generic GET helper ────────────────────────────────────────────────────

    private <T> T get(String url, Class<T> type, String label) {
        try {
            return restClient.get().uri(url).retrieve().body(type);
        } catch (RestClientException e) {
            log.debug("Could not fetch {} from {}: {}", label, url, e.getMessage());
            return null;
        }
    }
}
