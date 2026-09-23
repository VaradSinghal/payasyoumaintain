package com.paymu.claims.service;

import com.paymu.claims.model.ServiceTimeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Fetches a vehicle's service timeline from maintenance-vehicle-health-ingestion.
 *
 * <p>Returns {@code null} if the downstream service is unavailable or returns a 404
 * (e.g. vehicle has no maintenance records yet). Callers must handle null gracefully.</p>
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

    /** Package-private constructor for tests — allows injecting a custom URL. */
    MaintenanceClient(String maintenanceUrl) {
        this.restClient = RestClient.create();
        this.maintenanceUrl = maintenanceUrl;
    }

    public ServiceTimeline fetchTimeline(String vehicleId) {
        String url = maintenanceUrl + "/timeline/" + vehicleId;
        try {
            return restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(ServiceTimeline.class);
        } catch (RestClientException e) {
            log.warn("Could not fetch service timeline for vehicle {} from {}: {}",
                    vehicleId, url, e.getMessage());
            return null;
        }
    }
}
