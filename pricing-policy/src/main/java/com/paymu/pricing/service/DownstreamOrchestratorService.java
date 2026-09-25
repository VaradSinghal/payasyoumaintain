package com.paymu.pricing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymu.pricing.model.ScoreEngineRequest;
import com.paymu.pricing.model.ScoreResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Orchestrates three downstream HTTP calls to assemble a risk score for a vehicle:
 *
 * <ol>
 *   <li>GET telematics-usage-ingestion /aggregates/{vehicleId}</li>
 *   <li>GET maintenance-vehicle-health-ingestion /timeline/{vehicleId}</li>
 *   <li>GET maintenance-vehicle-health-ingestion /recall-status/{vehicleId}</li>
 *   <li>POST risk-scoring-engine /score/{vehicleId} with all three payloads</li>
 * </ol>
 *
 * <p>Each downstream call gracefully degrades: if it fails or returns 404, that
 * input is omitted from the scoring request. The scoring engine handles missing
 * inputs (cold-start) with a neutral 100/100 score.</p>
 */
@Service
public class DownstreamOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(DownstreamOrchestratorService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${services.identity-url}")
    private String identityUrl;

    @Value("${services.telematics-url}")
    private String telematicsUrl;

    @Value("${services.maintenance-url}")
    private String maintenanceUrl;

    @Value("${services.scoring-url}")
    private String scoringUrl;

    public DownstreamOrchestratorService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    /**
     * Fetches data from all three upstreams and posts to the scoring engine.
     * Returns null if the scoring engine itself is unavailable (caller handles as cold-start).
     */
    public ScoreResponse fetchScore(String vehicleId) {
        JsonNode timeline = fetchJson(maintenanceUrl + "/timeline/" + vehicleId, "maintenance timeline");
        JsonNode tripAggregates = fetchJson(telematicsUrl + "/aggregates/" + vehicleId, "trip aggregates");
        JsonNode recallStatus = fetchJson(maintenanceUrl + "/recall-status/" + vehicleId, "recall status");

        // Build the ScoreRequest payload
        try {
            var requestPayload = buildScorePayload(vehicleId, timeline, tripAggregates, recallStatus);
            String payloadJson = objectMapper.writeValueAsString(requestPayload);

            String responseBody = restClient.post()
                    .uri(scoringUrl + "/score/" + vehicleId)
                    .header("Content-Type", "application/json")
                    .body(payloadJson)
                    .retrieve()
                    .body(String.class);

            return objectMapper.readValue(responseBody, ScoreResponse.class);
        } catch (RestClientException e) {
            log.warn("Risk scoring engine unavailable for vehicle {}: {}", vehicleId, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Failed to parse score response for vehicle {}: {}", vehicleId, e.getMessage());
            return null;
        }
    }

    /**
     * Fetches the policy ID from identity-consent for the given vehicle.
     * Returns null if unavailable or missing.
     */
    public String fetchPolicyId(String vehicleId) {
        JsonNode node = fetchJson(identityUrl + "/vehicles/" + vehicleId, "vehicle registration");
        if (node != null && node.has("policyId")) {
            return node.get("policyId").asText();
        }
        return null;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private JsonNode fetchJson(String url, String label) {
        try {
            String body = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) return null;
            return objectMapper.readTree(body);
        } catch (RestClientException e) {
            log.debug("Could not fetch {} from {}: {}", label, url, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Failed to parse {} from {}: {}", label, url, e.getMessage());
            return null;
        }
    }

    /**
     * Assembles a score-engine request from the raw JSON nodes returned by the
     * upstream services. Each field is optional — absent fields result in a
     * cold-start neutral score from the engine.
     */
    private java.util.Map<String, Object> buildScorePayload(String vehicleId, JsonNode timeline,
                                                             JsonNode tripAggregates, JsonNode recallStatus) {
        var payload = new java.util.LinkedHashMap<String, Object>();

        // service_timeline: the maintenance service returns a ServiceTimeline object
        if (timeline != null && !timeline.isNull()) {
            payload.put("service_timeline", objectMapper.convertValue(timeline, Object.class));
        }

        // trip_aggregates: the telematics service returns a JSON array
        if (tripAggregates != null && !tripAggregates.isNull() && tripAggregates.isArray()) {
            payload.put("trip_aggregates", objectMapper.convertValue(tripAggregates, List.class));
        }

        // recall_status: the maintenance service always returns a recall status object
        if (recallStatus != null && !recallStatus.isNull()) {
            payload.put("recall_status", objectMapper.convertValue(recallStatus, Object.class));
        }

        return payload;
    }
}
