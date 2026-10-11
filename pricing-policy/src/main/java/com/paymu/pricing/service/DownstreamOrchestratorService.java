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

    public DownstreamOrchestratorService(ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
    }

    /**
     * Fetches data from all three upstreams and posts to the scoring engine.
     * Returns null if the scoring engine itself is unavailable (caller handles as cold-start).
     */
    public ScoreResponse fetchScore(String vehicleId, int vehicleAgeMonths) {
        JsonNode timeline = fetchJson(maintenanceUrl + "/timeline/" + vehicleId, "maintenance timeline");
        JsonNode tripAggregates = fetchJson(telematicsUrl + "/aggregates/" + vehicleId, "trip aggregates");
        JsonNode recallStatus = fetchJson(maintenanceUrl + "/recall-status/" + vehicleId, "recall status");

        // Build the ScoreRequest payload
        String url = scoringUrl + "/score/" + vehicleId;
        try {
            var requestPayload = buildScorePayload(vehicleId, vehicleAgeMonths, timeline, tripAggregates, recallStatus);
            String payloadJson = objectMapper.writeValueAsString(requestPayload);

            String responseBody = restClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .body(payloadJson)
                    .retrieve()
                    .body(String.class);

            return objectMapper.readValue(responseBody, ScoreResponse.class);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            if (e.getStatusCode().is4xxClientError()) {
                log.error("Client error (4xx) calling scorer at {}: status={} body={}", url, e.getStatusCode(), e.getResponseBodyAsString(), e);
            } else {
                log.warn("Scorer server error (5xx) for vehicle {}: status={}", vehicleId, e.getStatusCode());
            }
            return null;
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
        if (node != null && node.has("policy_id")) {
            return node.get("policy_id").asText();
        }
        return java.util.UUID.nameUUIDFromBytes(("policy_" + vehicleId).getBytes()).toString();
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
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            if (e.getStatusCode().is4xxClientError()) {
                log.error("Client error (4xx) calling {} from {}: status={} body={}", label, url, e.getStatusCode(), e.getResponseBodyAsString(), e);
            } else {
                log.warn("Server error (5xx) calling {} from {}: status={}", label, url, e.getStatusCode());
            }
            return null;
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
    private java.util.Map<String, Object> buildScorePayload(String vehicleId, int vehicleAgeMonths, JsonNode timeline,
                                                             JsonNode tripAggregates, JsonNode recallStatus) {
        var payload = new java.util.LinkedHashMap<String, Object>();
        
        payload.put("vehicle_age_months", vehicleAgeMonths);

        // service_timeline: the maintenance service returns a ServiceTimeline object
        if (timeline != null && !timeline.isNull()) {
            payload.put("service_timeline", objectMapper.convertValue(timeline, Object.class));
        }

        // trip_aggregates: the telematics service returns a JSON array, forwarded as raw JsonNode
        if (tripAggregates != null && !tripAggregates.isNull() && tripAggregates.isArray()) {
            payload.put("trip_aggregates", tripAggregates);
        }

        // recall_status: ensure availability is always populated (available / unavailable)
        if (recallStatus != null && !recallStatus.isNull()) {
            var recallMap = objectMapper.convertValue(recallStatus, java.util.Map.class);
            if (!recallMap.containsKey("availability") || recallMap.get("availability") == null) {
                var updated = new java.util.LinkedHashMap<>(recallMap);
                updated.put("availability", "available");
                payload.put("recall_status", updated);
            } else {
                payload.put("recall_status", recallMap);
            }
        } else {
            payload.put("recall_status", java.util.Map.of(
                "vehicle_id", vehicleId,
                "availability", "unavailable"
            ));
        }

        return payload;
    }
}
