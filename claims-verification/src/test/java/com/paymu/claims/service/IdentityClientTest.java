package com.paymu.claims.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymu.claims.exception.PolicyVehicleMismatchException;
import com.paymu.claims.exception.VehicleNotRegisteredException;
import com.paymu.claims.model.PolicyBindingResult;
import com.paymu.claims.model.VehicleRegistrationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class IdentityClientTest {

    private static final String BASE_URL = "http://identity-consent:8081/api/v1";
    private static final String VEHICLE_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    private static final String REAL_POLICY_ID = "c4632682-d406-3102-86b7-6a22ef09af5d";

    private final String realIdentityResponseBody = """
            {
              "vehicle_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
              "policy_id": "c4632682-d406-3102-86b7-6a22ef09af5d",
              "rc_number": "MH02AB1234",
              "registration_date": "2024-10-17",
              "owner_details": {
                "full_name": "Aarav Sharma",
                "kyc_document_number": "XXXXXXXX1012",
                "kyc_document_type": "AADHAAR",
                "email": "a***@example.com",
                "phone_number": "XXXXXX3210"
              },
              "kyc_status": "VERIFIED",
              "registered_at": "2026-10-17T10:51:18.371947173Z"
            }
            """;

    private IdentityClient identityClient;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        this.mockServer = MockRestServiceServer.bindTo(builder).build();
        this.identityClient = new IdentityClient(builder.build(), BASE_URL);
    }

    @Test
    @DisplayName("Explicitly deserializes real snake_case response body from identity-consent")
    void deserializesRealIdentityResponse() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        VehicleRegistrationResponse response = mapper.readValue(realIdentityResponseBody, VehicleRegistrationResponse.class);

        assertNotNull(response);
        assertEquals(VEHICLE_ID, response.vehicleId());
        assertEquals(REAL_POLICY_ID, response.policyId());
        assertEquals("MH02AB1234", response.rcNumber());
        assertEquals("2024-10-17", response.registrationDate());
        assertEquals("VERIFIED", response.kycStatus());
        assertEquals("2026-10-17T10:51:18.371947173Z", response.registeredAt());
    }

    @Test
    @DisplayName("Matching policy_id returns PolicyBindingResult.VERIFIED")
    void matchingPolicyId_returnsVerified() {
        mockServer.expect(requestTo(BASE_URL + "/vehicles/" + VEHICLE_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(realIdentityResponseBody, MediaType.APPLICATION_JSON));

        PolicyBindingResult result = identityClient.verifyPolicyBinding(VEHICLE_ID, REAL_POLICY_ID);
        assertEquals(PolicyBindingResult.VERIFIED, result);
        mockServer.verify();
    }

    @Test
    @DisplayName("Mismatched policy_id throws PolicyVehicleMismatchException without revealing real policy_id")
    void mismatchedPolicyId_throwsMismatchException() {
        String wrongPolicyId = "00000000-0000-0000-0000-000000000000";

        mockServer.expect(requestTo(BASE_URL + "/vehicles/" + VEHICLE_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(realIdentityResponseBody, MediaType.APPLICATION_JSON));

        PolicyVehicleMismatchException ex = assertThrows(PolicyVehicleMismatchException.class,
                () -> identityClient.verifyPolicyBinding(VEHICLE_ID, wrongPolicyId));

        assertFalse(ex.getMessage().contains(REAL_POLICY_ID), "Message must not reveal the real policy ID");
        mockServer.verify();
    }

    @Test
    @DisplayName("Vehicle not registered (404) throws VehicleNotRegisteredException")
    void vehicleNotRegistered_throwsNotFoundException() {
        mockServer.expect(requestTo(BASE_URL + "/vehicles/" + VEHICLE_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(VehicleNotRegisteredException.class,
                () -> identityClient.verifyPolicyBinding(VEHICLE_ID, REAL_POLICY_ID));
        mockServer.verify();
    }

    @Test
    @DisplayName("Identity unreachable or 5xx logs warning and returns PolicyBindingResult.UNVERIFIED")
    void identityUnreachableOr5xx_returnsUnverified() {
        mockServer.expect(requestTo(BASE_URL + "/vehicles/" + VEHICLE_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        PolicyBindingResult result = identityClient.verifyPolicyBinding(VEHICLE_ID, REAL_POLICY_ID);
        assertEquals(PolicyBindingResult.UNVERIFIED, result);
        mockServer.verify();
    }
}
