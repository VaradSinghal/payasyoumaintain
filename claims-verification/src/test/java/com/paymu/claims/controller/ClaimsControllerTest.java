package com.paymu.claims.controller;

import com.paymu.claims.exception.PolicyVehicleMismatchException;
import com.paymu.claims.exception.VehicleNotRegisteredException;
import com.paymu.claims.model.PolicyBindingResult;
import com.paymu.claims.model.RecallStatus;
import com.paymu.claims.model.ServiceTimeline;
import com.paymu.claims.service.ClaimStore;
import com.paymu.claims.service.IdentityClient;
import com.paymu.claims.service.MaintenanceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ClaimsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClaimStore claimStore;

    @MockBean
    private MaintenanceClient maintenanceClient;

    @MockBean
    private IdentityClient identityClient;

    @BeforeEach
    void setUp() {
        claimStore.clear();
        when(identityClient.verifyPolicyBinding(anyString(), anyString())).thenReturn(PolicyBindingResult.VERIFIED);
    }

    @Test
    @DisplayName("POST /fnol with valid mechanical_failure returns 201")
    void submitFnol_validMechanicalFailure_returns201() throws Exception {
        when(maintenanceClient.fetchTimeline(anyString())).thenReturn(null);
        when(maintenanceClient.fetchRecallStatus(anyString())).thenReturn(
                new RecallStatus("a1b2c3d4-e5f6-7890-abcd-ef1234567890", false, 0, List.of(), "oem_recall_db", "2026-10-11T00:00:00Z")
        );

        String json = """
                {
                    "vehicle_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                    "policy_id": "c4632682-d406-3102-86b7-6a22ef09af5d",
                    "incident_date": "2026-10-11",
                    "incident_description": "Engine suddenly stalled on the highway.",
                    "claimed_cause": "mechanical_failure"
                }
                """;

        mockMvc.perform(post("/fnol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.claim_id").isNotEmpty())
                .andExpect(jsonPath("$.claimed_cause").value("mechanical_failure"))
                .andExpect(jsonPath("$.maintenance_signal").value("No disqualifying maintenance signals found."));
    }

    @Test
    @DisplayName("POST /fnol with uppercase MECHANICAL_FAILURE returns 400 INVALID_REQUEST naming field and allowed values")
    void submitFnol_uppercaseMechanicalFailure_returns400() throws Exception {
        String json = """
                {
                    "vehicle_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                    "policy_id": "c4632682-d406-3102-86b7-6a22ef09af5d",
                    "incident_date": "2026-10-11",
                    "incident_description": "Engine stalled.",
                    "claimed_cause": "MECHANICAL_FAILURE"
                }
                """;

        mockMvc.perform(post("/fnol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(containsString("claimed_cause")))
                .andExpect(jsonPath("$.message").value(containsString("mechanical_failure")))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("POST /fnol with bogus claimed_cause returns 400 INVALID_REQUEST naming field and allowed values")
    void submitFnol_bogusCause_returns400() throws Exception {
        String json = """
                {
                    "vehicle_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                    "policy_id": "c4632682-d406-3102-86b7-6a22ef09af5d",
                    "incident_date": "2026-10-11",
                    "incident_description": "Something broke.",
                    "claimed_cause": "bogus"
                }
                """;

        mockMvc.perform(post("/fnol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(containsString("claimed_cause")))
                .andExpect(jsonPath("$.message").value(containsString("mechanical_failure")))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("POST /fnol with malformed JSON returns 400 INVALID_REQUEST")
    void submitFnol_malformedJson_returns400() throws Exception {
        String malformedJson = "{ \"vehicle_id\": \"a1b2c3d4-e5f6-7890-abcd-ef1234567890\", unclosed";

        mockMvc.perform(post("/fnol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("POST /fnol with wrong types returns 400 INVALID_REQUEST")
    void submitFnol_wrongTypes_returns400() throws Exception {
        String jsonWithWrongType = """
                {
                    "vehicle_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                    "policy_id": "c4632682-d406-3102-86b7-6a22ef09af5d",
                    "incident_date": "2026-10-11",
                    "incident_description": "Engine stalled.",
                    "claimed_cause": "mechanical_failure",
                    "photo_refs": "not-an-array"
                }
                """;

        mockMvc.perform(post("/fnol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonWithWrongType))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("GET /claims/not-a-uuid returns 400 INVALID_REQUEST")
    void getClaim_notAUuid_returns400() throws Exception {
        mockMvc.perform(get("/claims/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(containsString("claimId")))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("GET /claims/{unknown valid uuid} returns 404 NOT_FOUND")
    void getClaim_unknownUuid_returns404() throws Exception {
        String unknownUuid = UUID.randomUUID().toString();
        mockMvc.perform(get("/claims/" + unknownUuid))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("POST /fnol with maintenance down returns maintenance_signal: recall status unavailable.")
    void submitFnol_maintenanceDown_reportsRecallUnavailable() throws Exception {
        // Both timeline and recall return null when maintenance is down
        when(maintenanceClient.fetchTimeline(anyString())).thenReturn(null);
        when(maintenanceClient.fetchRecallStatus(anyString())).thenReturn(null);

        String json = """
                {
                    "vehicle_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                    "policy_id": "c4632682-d406-3102-86b7-6a22ef09af5d",
                    "incident_date": "2026-10-11",
                    "incident_description": "Engine failure.",
                    "claimed_cause": "mechanical_failure"
                }
                """;

        mockMvc.perform(post("/fnol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.triage_decision").value("MANUAL_REVIEW"))
                .andExpect(jsonPath("$.maintenance_signal").value("recall status unavailable."));
    }

    @Test
    @DisplayName("POST /fnol with policy-vehicle mismatch returns 422 and does not reveal real policy ID")
    void submitFnol_policyMismatch_returns422() throws Exception {
        String realPolicyId = "c4632682-d406-3102-86b7-6a22ef09af5d";
        String mismatchedPolicyId = "00000000-0000-0000-0000-000000000000";
        when(identityClient.verifyPolicyBinding(anyString(), anyString())).thenThrow(
                new PolicyVehicleMismatchException("The policy ID provided does not match the active policy registered for vehicle a1b2c3d4-e5f6-7890-abcd-ef1234567890.")
        );

        String json = """
                {
                    "vehicle_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                    "policy_id": "%s",
                    "incident_date": "2026-10-11",
                    "incident_description": "Accident.",
                    "claimed_cause": "collision"
                }
                """.formatted(mismatchedPolicyId);

        mockMvc.perform(post("/fnol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("POLICY_VEHICLE_MISMATCH"))
                .andExpect(jsonPath("$.message").value(not(containsString(realPolicyId))))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("POST /fnol for unregistered vehicle returns 422 VEHICLE_NOT_REGISTERED")
    void submitFnol_vehicleNotRegistered_returns422() throws Exception {
        when(identityClient.verifyPolicyBinding(anyString(), anyString())).thenThrow(
                new VehicleNotRegisteredException("Vehicle a1b2c3d4-e5f6-7890-abcd-ef1234567890 is not registered.")
        );

        String json = """
                {
                    "vehicle_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                    "policy_id": "c4632682-d406-3102-86b7-6a22ef09af5d",
                    "incident_date": "2026-10-11",
                    "incident_description": "Accident.",
                    "claimed_cause": "collision"
                }
                """;

        mockMvc.perform(post("/fnol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("VEHICLE_NOT_REGISTERED"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("POST /fnol with future incident_date returns 422 INVALID_INCIDENT_DATE")
    void submitFnol_futureIncidentDate_returns422() throws Exception {
        String tomorrow = LocalDate.now().plusDays(1).toString();
        String json = """
                {
                    "vehicle_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                    "policy_id": "c4632682-d406-3102-86b7-6a22ef09af5d",
                    "incident_date": "%s",
                    "incident_description": "Accident.",
                    "claimed_cause": "collision"
                }
                """.formatted(tomorrow);

        mockMvc.perform(post("/fnol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INVALID_INCIDENT_DATE"))
                .andExpect(jsonPath("$.message").value(containsString("future")))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("POST /fnol with incident_date older than max age returns 422 INVALID_INCIDENT_DATE")
    void submitFnol_incidentDateTooOld_returns422() throws Exception {
        String pastDate = LocalDate.now().minusDays(400).toString();
        String json = """
                {
                    "vehicle_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                    "policy_id": "c4632682-d406-3102-86b7-6a22ef09af5d",
                    "incident_date": "%s",
                    "incident_description": "Accident.",
                    "claimed_cause": "collision"
                }
                """.formatted(pastDate);

        mockMvc.perform(post("/fnol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INVALID_INCIDENT_DATE"))
                .andExpect(jsonPath("$.message").value(containsString("older than")))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("POST /fnol with invalid date format returns 422 INVALID_INCIDENT_DATE")
    void submitFnol_invalidDateFormat_returns422() throws Exception {
        String json = """
                {
                    "vehicle_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                    "policy_id": "c4632682-d406-3102-86b7-6a22ef09af5d",
                    "incident_date": "invalid-date",
                    "incident_description": "Accident.",
                    "claimed_cause": "collision"
                }
                """;

        mockMvc.perform(post("/fnol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INVALID_INCIDENT_DATE"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("POST /fnol with unverified policy binding forces MANUAL_REVIEW and policy_binding_unverified triage reason even for collision")
    void submitFnol_unverifiedBinding_forcesManualReviewForCollision() throws Exception {
        when(identityClient.verifyPolicyBinding(anyString(), anyString())).thenReturn(PolicyBindingResult.UNVERIFIED);

        String json = """
                {
                    "vehicle_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                    "policy_id": "c4632682-d406-3102-86b7-6a22ef09af5d",
                    "incident_date": "2026-10-11",
                    "incident_description": "Collision on expressway.",
                    "claimed_cause": "collision"
                }
                """;

        mockMvc.perform(post("/fnol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.triage_decision").value("MANUAL_REVIEW"))
                .andExpect(jsonPath("$.triage_reasons", hasItem("policy_binding_unverified")));
    }
}
