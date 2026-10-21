package com.paymu.maintenance.client;

import com.paymu.maintenance.exception.ConsentRequiredException;
import com.paymu.maintenance.exception.IdentityServiceUnavailableException;
import com.paymu.maintenance.exception.VehicleNotRegisteredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.UUID;

@Component
public class IdentityClient {

    private static final Logger log = LoggerFactory.getLogger(IdentityClient.class);

    private final RestClient restClient;
    private final String identityUrl;

    @Autowired
    public IdentityClient(@Value("${services.identity-url:http://localhost:8081/api/v1}") String identityUrl) {
        this.identityUrl = identityUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    public IdentityClient(RestClient restClient, String identityUrl) {
        this.restClient = restClient;
        this.identityUrl = identityUrl;
    }

    public VehicleRegistrationResponse getVehicleRegistration(UUID vehicleId) {
        try {
            VehicleRegistrationResponse response = restClient.get()
                    .uri(identityUrl + "/vehicles/" + vehicleId)
                    .retrieve()
                    .body(VehicleRegistrationResponse.class);

            if (response == null || response.vehicleId() == null) {
                log.warn("Identity returned null response for vehicle {}", vehicleId);
                throw new IdentityServiceUnavailableException("Identity service returned empty registration.");
            }
            return response;
        } catch (HttpClientErrorException.NotFound e) {
            throw new VehicleNotRegisteredException("Vehicle " + vehicleId + " is not registered.");
        } catch (HttpClientErrorException e) {
            log.warn("Identity returned client error {} for vehicle {}", e.getStatusCode(), vehicleId);
            throw new IdentityServiceUnavailableException("Identity service returned error: " + e.getStatusCode());
        } catch (HttpServerErrorException | ResourceAccessException e) {
            log.warn("Identity service unreachable or returned 5xx for vehicle {}: {}", vehicleId, e.getMessage());
            throw new IdentityServiceUnavailableException("Identity service unreachable or returned 5xx.");
        } catch (VehicleNotRegisteredException | IdentityServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Unexpected error contacting identity for vehicle {}: {}", vehicleId, e.getMessage());
            throw new IdentityServiceUnavailableException("Identity service unavailable.");
        }
    }

    public ConsentRecord getConsent(UUID vehicleId) {
        try {
            ConsentRecord consent = restClient.get()
                    .uri(identityUrl + "/vehicles/" + vehicleId + "/consent")
                    .retrieve()
                    .body(ConsentRecord.class);

            if (consent == null || consent.maintenanceTrackingOptIn() == null || !consent.maintenanceTrackingOptIn()) {
                throw new ConsentRequiredException("Maintenance tracking consent is required to submit service records.");
            }
            return consent;
        } catch (ConsentRequiredException e) {
            throw e;
        } catch (HttpClientErrorException.NotFound e) {
            throw new VehicleNotRegisteredException("Vehicle " + vehicleId + " is not registered.");
        } catch (HttpClientErrorException e) {
            log.warn("Identity returned client error {} for consent of vehicle {}", e.getStatusCode(), vehicleId);
            throw new IdentityServiceUnavailableException("Identity service returned error: " + e.getStatusCode());
        } catch (HttpServerErrorException | ResourceAccessException e) {
            log.warn("Identity service unreachable or returned 5xx for consent of vehicle {}: {}", vehicleId, e.getMessage());
            throw new IdentityServiceUnavailableException("Identity service unreachable or returned 5xx.");
        } catch (Exception e) {
            log.warn("Unexpected error contacting identity for consent of vehicle {}: {}", vehicleId, e.getMessage());
            throw new IdentityServiceUnavailableException("Identity service unavailable when checking consent.");
        }
    }
}
