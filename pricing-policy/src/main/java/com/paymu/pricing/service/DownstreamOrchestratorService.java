package com.paymu.pricing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymu.pricing.model.ConsentEvaluation;
import com.paymu.pricing.model.ConsentResponse;
import com.paymu.pricing.model.ProfileLookupResult;
import com.paymu.pricing.model.ScoreResponse;
import com.paymu.pricing.model.VehicleProfileResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates downstream HTTP calls for pricing calculation:
 * <ul>
 *   <li>identity-consent: vehicle profile and tracking consent</li>
 *   <li>telematics-usage-ingestion: trip aggregates</li>
 *   <li>maintenance-vehicle-health-ingestion: service timeline + recall status</li>
 *   <li>risk-scoring-engine: composite risk score</li>
 * </ul>
 */
@Service
public class DownstreamOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(DownstreamOrchestratorService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ProfileCache profileCache;

    @Value("${services.identity-url}")
    private String identityUrl;

    @Value("${services.telematics-url}")
    private String telematicsUrl;

    @Value("${services.maintenance-url}")
    private String maintenanceUrl;

    @Value("${services.scoring-url}")
    private String scoringUrl;

    public DownstreamOrchestratorService(ObjectMapper objectMapper,
                                         RestClient.Builder restClientBuilder,
                                         ProfileCache profileCache) {
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
        this.profileCache = profileCache;
    }

    /**
     * Look up the vehicle profile from identity-consent (or local memory cache).
     * 2-second timeout enforced by HTTP client settings.
     */
    public ProfileLookupResult fetchProfile(String vehicleId) {
        Optional<VehicleProfileResponse> cached = profileCache.get(vehicleId);
        if (cached.isPresent()) {
            return ProfileLookupResult.found(cached.get());
        }

        String url = identityUrl + "/vehicles/" + vehicleId;
        try {
            String body = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);

            if (body == null || body.isBlank()) {
                return ProfileLookupResult.unregistered();
            }

            VehicleProfileResponse profile = objectMapper.readValue(body, VehicleProfileResponse.class);
            profileCache.put(vehicleId, profile);
            return ProfileLookupResult.found(profile);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            return ProfileLookupResult.unregistered();
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) {
                return ProfileLookupResult.unregistered();
            }
            log.warn("Server error ({}) calling identity for profile {}", e.getStatusCode(), vehicleId);
            return ProfileLookupResult.unavailable();
        } catch (RestClientException e) {
            log.warn("Connection error or timeout calling identity for profile {}: {}", vehicleId, e.getMessage());
            return ProfileLookupResult.unavailable();
        } catch (Exception e) {
            log.warn("Unexpected error calling identity for profile {}: {}", vehicleId, e.getMessage());
            return ProfileLookupResult.unavailable();
        }
    }

    /**
     * Checks tracking consent for the vehicle before any data services are accessed.
     * Enforces fail-closed semantics on timeouts/errors.
     */
    public ConsentEvaluation evaluateConsent(String vehicleId) {
        String url = identityUrl + "/vehicles/" + vehicleId + "/consent";
        try {
            String body = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                log.warn("Empty consent response from {} for vehicle {}", url, vehicleId);
                return ConsentEvaluation.unavailable(List.of("consent_status_unavailable"));
            }
            ConsentResponse consent = objectMapper.readValue(body, ConsentResponse.class);
            boolean usage = Boolean.TRUE.equals(consent.usageTrackingOptIn());
            boolean maintenance = Boolean.TRUE.equals(consent.maintenanceTrackingOptIn());
            if (usage && maintenance) {
                return ConsentEvaluation.permitted();
            }
            List<String> flags = new ArrayList<>();
            if (!usage) {
                flags.add("consent_not_given_usage");
            }
            if (!maintenance) {
                flags.add("consent_not_given_maintenance");
            }
            return ConsentEvaluation.denied(flags);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            return ConsentEvaluation.denied(List.of("vehicle_not_registered"));
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) {
                return ConsentEvaluation.denied(List.of("vehicle_not_registered"));
            }
            log.warn("Server error ({}) checking consent from {} for vehicle {}: {}", e.getStatusCode(), url, vehicleId, e.getMessage());
            return ConsentEvaluation.unavailable(List.of("consent_status_unavailable"));
        } catch (RestClientException e) {
            log.warn("Connection error or timeout checking consent from {} for vehicle {}: {}", url, vehicleId, e.getMessage());
            return ConsentEvaluation.unavailable(List.of("consent_status_unavailable"));
        } catch (Exception e) {
            log.warn("Unexpected error checking consent from {} for vehicle {}: {}", url, vehicleId, e.getMessage());
            return ConsentEvaluation.unavailable(List.of("consent_status_unavailable"));
        }
    }

    /**
     * Fetches the policy ID from identity-consent for the given vehicle.
     * Returns a deterministic UUID fallback if unavailable or missing.
     */
    public String fetchPolicyId(String vehicleId) {
        ProfileLookupResult result = fetchProfile(vehicleId);
        if (result.isFound() && result.profile() != null && result.profile().policyId() != null) {
            return result.profile().policyId();
        }
        return java.util.UUID.nameUUIDFromBytes(("policy_" + vehicleId).getBytes()).toString();
    }

    /**
     * Fetches data from upstreams and posts to the scoring engine.
     * Returns null if the scoring engine is unavailable (cold-start).
     */
    public ScoreResponse fetchScore(String vehicleId, int vehicleAgeMonths) {
        JsonNode timeline = fetchJson(maintenanceUrl + "/timeline/" + vehicleId, "maintenance timeline");
        JsonNode tripAggregates = fetchJson(telematicsUrl + "/aggregates/" + vehicleId, "trip aggregates");
        JsonNode recallStatus = fetchJson(maintenanceUrl + "/recall-status/" + vehicleId, "recall status");

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

    private java.util.Map<String, Object> buildScorePayload(String vehicleId, int vehicleAgeMonths, JsonNode timeline,
                                                             JsonNode tripAggregates, JsonNode recallStatus) {
        var payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("vehicle_age_months", vehicleAgeMonths);

        if (timeline != null && !timeline.isNull()) {
            payload.put("service_timeline", objectMapper.convertValue(timeline, Object.class));
        }

        if (tripAggregates != null && !tripAggregates.isNull() && tripAggregates.isArray()) {
            payload.put("trip_aggregates", tripAggregates);
        }

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
