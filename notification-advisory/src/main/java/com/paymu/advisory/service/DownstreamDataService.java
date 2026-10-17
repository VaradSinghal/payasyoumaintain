package com.paymu.advisory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymu.advisory.model.ConsentEvaluation;
import com.paymu.advisory.model.ConsentResponse;
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

    @Value("${services.identity-url}")
    private String identityUrl;

    @Value("${services.telematics-url}")
    private String telematicsUrl;

    @Value("${services.maintenance-url}")
    private String maintenanceUrl;

    @Value("${services.scoring-url}")
    private String scoringUrl;

    public DownstreamDataService(ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public ConsentEvaluation evaluateConsent(String vehicleId) {
        String url = identityUrl + "/vehicles/" + vehicleId + "/consent";
        try {
            String body = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                log.warn("Empty consent response from {} for vehicle {}", url, vehicleId);
                return ConsentEvaluation.unavailable();
            }
            ConsentResponse consent = objectMapper.readValue(body, ConsentResponse.class);
            boolean usage = Boolean.TRUE.equals(consent.usageTrackingOptIn());
            boolean maintenance = Boolean.TRUE.equals(consent.maintenanceTrackingOptIn());
            if (usage && maintenance) {
                return ConsentEvaluation.permitted();
            }
            return ConsentEvaluation.denied();
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            return ConsentEvaluation.denied();
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) {
                return ConsentEvaluation.denied();
            }
            log.warn("Server error ({}) checking consent from {} for vehicle {}: {}", e.getStatusCode(), url, vehicleId, e.getMessage());
            return ConsentEvaluation.unavailable();
        } catch (RestClientException e) {
            log.warn("Connection error or timeout checking consent from {} for vehicle {}: {}", url, vehicleId, e.getMessage());
            return ConsentEvaluation.unavailable();
        } catch (Exception e) {
            log.warn("Unexpected error checking consent from {} for vehicle {}: {}", url, vehicleId, e.getMessage());
            return ConsentEvaluation.unavailable();
        }
    }

    public JsonNode fetchTripAggregates(String vehicleId) {
        return get(telematicsUrl + "/aggregates/" + vehicleId, JsonNode.class, "trip aggregates");
    }

    public ServiceTimeline fetchTimeline(String vehicleId) {
        return get(maintenanceUrl + "/timeline/" + vehicleId, ServiceTimeline.class, "service timeline");
    }

    public RecallStatus fetchRecallStatus(String vehicleId) {
        RecallStatus status = get(maintenanceUrl + "/recall-status/" + vehicleId, RecallStatus.class, "recall status");
        if (status == null) {
            return new RecallStatus(vehicleId, "unavailable", null, 0, java.util.Collections.emptyList(), "", "");
        }
        String availability = status.availability() != null ? status.availability() : "available";
        return new RecallStatus(
                status.vehicleId(),
                availability,
                status.hasOpenRecall(),
                status.recallCount(),
                status.recallDetails(),
                status.source(),
                status.checkedAt()
        );
    }

    /**
     * Posts the already-fetched telematics, timeline and recall data to the scoring engine
     * to get a risk score. This avoids a second downstream pull inside the scorer.
     */
    public ScoreResponse fetchScore(String vehicleId, JsonNode tripAggregates, ServiceTimeline timeline, RecallStatus recall) {
        String url = scoringUrl + "/score/" + vehicleId;
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
                RecallStatus toSend = recall;
                if (toSend.availability() == null) {
                    toSend = new RecallStatus(
                            toSend.vehicleId(),
                            "available",
                            toSend.hasOpenRecall(),
                            toSend.recallCount(),
                            toSend.recallDetails(),
                            toSend.source(),
                            toSend.checkedAt()
                    );
                }
                payload.put("recall_status", objectMapper.convertValue(toSend, Object.class));
            }

            String body = objectMapper.writeValueAsString(payload);
            String responseBody = restClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            return responseBody != null ? objectMapper.readValue(responseBody, ScoreResponse.class) : null;

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

    // ── Generic GET helper ────────────────────────────────────────────────────

    private <T> T get(String url, Class<T> type, String label) {
        try {
            return restClient.get().uri(url).retrieve().body(type);
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
        }
    }
}
