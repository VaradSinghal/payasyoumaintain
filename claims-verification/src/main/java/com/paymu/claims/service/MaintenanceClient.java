package com.paymu.claims.service;

import com.paymu.claims.model.RecallStatus;
import com.paymu.claims.model.ServiceTimeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Fetches maintenance data from maintenance-vehicle-health-ingestion.
 *
 * <p>Both methods return {@code null} on downstream unavailability (404, timeout,
 * connection refused). Callers must handle null gracefully — a missing upstream
 * response is never treated as evidence of fraud.</p>
 */
@Service
public class MaintenanceClient {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceClient.class);

    private final RestClient restClient;

    @Value("${services.maintenance-url}")
    private String maintenanceUrl;

    public MaintenanceClient() {
        this.restClient = RestClient.create();
    }

    /** Package-private constructor for tests — allows injecting a custom base URL. */
    MaintenanceClient(String maintenanceUrl) {
        this.restClient = RestClient.create();
        this.maintenanceUrl = maintenanceUrl;
    }

    /**
     * Fetches the chronological service timeline for a vehicle.
     *
     * @return timeline, or {@code null} if unavailable
     */
    public ServiceTimeline fetchTimeline(String vehicleId) {
        return get(maintenanceUrl + "/timeline/" + vehicleId,
                ServiceTimeline.class, "service timeline", vehicleId);
    }

    /**
     * Fetches the structured recall status for a vehicle.
     * This is the <em>authoritative</em> recall signal — recall presence must never
     * be inferred from free-text maintenance notes.
     *
     * @return recall status, or {@code null} if unavailable
     */
    public RecallStatus fetchRecallStatus(String vehicleId) {
        return get(maintenanceUrl + "/recall-status/" + vehicleId,
                RecallStatus.class, "recall status", vehicleId);
    }

    // ── Generic GET helper ────────────────────────────────────────────────────

    private <T> T get(String url, Class<T> type, String label, String vehicleId) {
        try {
            return restClient.get().uri(url).retrieve().body(type);
        } catch (RestClientException e) {
            log.warn("Could not fetch {} for vehicle {} from {}: {}",
                    label, vehicleId, url, e.getMessage());
            return null;
        }
    }
}
