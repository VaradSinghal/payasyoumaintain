package com.paymu.advisory.service;

import com.fasterxml.jackson.databind.JsonNode;
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

/**
 * Fetches data from telematics-usage-ingestion, maintenance-vehicle-health-ingestion,
 * and risk-scoring-engine to supply the advisory generator.
 *
 * <p>Call order:</p>
 * <ol>
 *   <li>GET /aggregates/{vehicleId}      → Trip Aggregates (JsonNode)</li>
 *   <li>GET /timeline/{vehicleId}        → {@link ServiceTimeline}</li>
 *   <li>GET /recall-status/{vehicleId}   → {@link RecallStatus}</li>
 *   <li>POST /score/{vehicleId} with trip_aggregates, timeline + recall → {@link ScoreResponse}</li>
 * </ol>
 *
 * <p>Each call degrades gracefully — a failure returns null, and the downstream
 * engine simply cold-starts those inputs as neutral 100 or skips rules.</p>
 */
@Service
public class DownstreamDataService {

    private static final Logger log = LoggerFactory.getLogger(DownstreamDataService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${services.telematics-url}")
    private String telematicsUrl;

    @Value("${services.maintenance-url}")
    private String maintenanceUrl;

    @Value("${services.scoring-url}")
    private String scoringUrl;

    public DownstreamDataService(ObjectMapper objectMapper) {
        this.restClient = RestClient.create();
        this.objectMapper = objectMapper;
    }

    public JsonNode fetchTripAggregates(String vehicleId) {
        return get(telematicsUrl + "/aggregates/" + vehicleId, JsonNode.class, "trip aggregates");
    }

    public ServiceTimeline fetchTimeline(String vehicleId) {
        return get(maintenanceUrl + "/timeline/" + vehicleId, ServiceTimeline.class, "service timeline");
    }

    public RecallStatus fetchRecallStatus(String vehicleId) {
        return get(maintenanceUrl + "/recall-status/" + vehicleId, RecallStatus.class, "recall status");
    }

    /**
     * Posts the already-fetched telematics, timeline and recall data to the scoring engine
     * to get a risk score. This avoids a second downstream pull inside the scorer.
     */
    public ScoreResponse fetchScore(String vehicleId, JsonNode tripAggregates, ServiceTimeline timeline, RecallStatus recall) {
        try {
            // Build a minimal ScoreRequest — only include what we have
            var payload = new java.util.LinkedHashMap<String, Object>();
            if (tripAggregates != null) {
                payload.put("trip_aggregates", tripAggregates);
            }
            if (timeline != null) {
                payload.put("service_timeline", objectMapper.convertValue(timeline, Object.class));
            }
            if (recall != null) {
                payload.put("recall_status", objectMapper.convertValue(recall, Object.class));
            }

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
