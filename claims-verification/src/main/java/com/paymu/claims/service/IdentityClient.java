package com.paymu.claims.service;

import com.paymu.claims.exception.PolicyVehicleMismatchException;
import com.paymu.claims.exception.VehicleNotRegisteredException;
import com.paymu.claims.model.PolicyBindingResult;
import com.paymu.claims.model.VehicleRegistrationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Client for identity-consent service to verify vehicle registration and policy binding.
 */
@Service
public class IdentityClient {

    private static final Logger log = LoggerFactory.getLogger(IdentityClient.class);

    private final RestClient restClient;
    private final String identityUrl;

    @org.springframework.beans.factory.annotation.Autowired
    public IdentityClient(@Value("${services.identity-url:http://localhost:8081/api/v1}") String identityUrl) {
        this.identityUrl = identityUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    IdentityClient(RestClient restClient, String identityUrl) {
        this.restClient = restClient;
        this.identityUrl = identityUrl;
    }

    /**
     * Verifies that the claimed policy_id matches the vehicle's registered policy_id.
     *
     * @param vehicleId       the UUID of the vehicle
     * @param claimedPolicyId the policy_id submitted in the FNOL request
     * @return PolicyBindingResult.VERIFIED if matching, or UNVERIFIED if identity is down/5xx
     * @throws VehicleNotRegisteredException if vehicle returns 404
     * @throws PolicyVehicleMismatchException if policy_id does not match (without revealing the real policy_id)
     */
    public PolicyBindingResult verifyPolicyBinding(String vehicleId, String claimedPolicyId) {
        try {
            VehicleRegistrationResponse reg = restClient.get()
                    .uri(identityUrl + "/vehicles/" + vehicleId)
                    .retrieve()
                    .body(VehicleRegistrationResponse.class);

            if (reg == null || reg.policyId() == null) {
                log.warn("Identity service returned empty registration or missing policy_id for vehicle {}", vehicleId);
                return PolicyBindingResult.UNVERIFIED;
            }

            if (reg.policyId().equals(claimedPolicyId)) {
                return PolicyBindingResult.VERIFIED;
            } else {
                throw new PolicyVehicleMismatchException(
                        "The policy ID provided does not match the active policy registered for vehicle " + vehicleId + ".");
            }
        } catch (HttpClientErrorException.NotFound e) {
            throw new VehicleNotRegisteredException("Vehicle " + vehicleId + " is not registered.");
        } catch (PolicyVehicleMismatchException | VehicleNotRegisteredException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Identity service unreachable or returned error when verifying policy binding for vehicle {} at {}: {}",
                    vehicleId, identityUrl, e.getMessage());
            return PolicyBindingResult.UNVERIFIED;
        }
    }
}
