package com.paymu.claims.service;

import com.paymu.claims.model.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TriageService}.
 *
 * <p>All tests are pure in-process — no Spring context, no HTTP calls.</p>
 */
@DisplayName("TriageService")
class TriageServiceTest {

    private TriageService triageService;

    @BeforeEach
    void setUp() {
        triageService = new TriageService();
        // Inject the 90-day window directly without a Spring context
        ReflectionTestUtils.setField(triageService, "cleanServiceWindowDays", 90);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private FnolRequest fnol(ClaimedCause cause) {
        return new FnolRequest(
                "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                "policy-001",
                LocalDate.now().toString(),
                "Describe what happened.",
                cause,
                List.of("photo://ref1"),
                "Bengaluru, Karnataka"
        );
    }

    private ServiceTimeline.MaintenanceEvent event(int daysAgo, String notes) {
        return new ServiceTimeline.MaintenanceEvent(
                "evt-" + daysAgo,
                "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                LocalDate.now().minusDays(daysAgo).toString(),
                "oil_change",
                25000,
                "oem_api",
                notes
        );
    }

    private ServiceTimeline timeline(ServiceTimeline.MaintenanceEvent... events) {
        return new ServiceTimeline(
                "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                events.length,
                events.length > 0 ? events[0].serviceDate() : null,
                25000,
                List.of(events)
        );
    }

    // ── Flagged path: MANUAL_REVIEW ───────────────────────────────────────────

    @Test
    @DisplayName("MANUAL_REVIEW: mechanical failure + clean service within 90 days")
    void mechanicalFailure_withRecentCleanService_isManualReview() {
        FnolRequest request = fnol(ClaimedCause.MECHANICAL_FAILURE);
        ServiceTimeline tl = timeline(event(30, null));  // clean, 30 days ago

        TriageService.TriageResult result = triageService.triage(request, tl);

        assertEquals(TriageDecision.MANUAL_REVIEW, result.decision());
        assertTrue(result.reasons().stream().anyMatch(r -> r.contains("clean service record")),
                "Reasons should explain the clean-service finding");
        assertTrue(result.maintenanceSignal().contains("Clean service found"),
                "maintenanceSignal should describe the window match");
    }

    @Test
    @DisplayName("MANUAL_REVIEW: clean service exactly at window boundary (90 days) still flags")
    void mechanicalFailure_cleanServiceAtWindowEdge_isManualReview() {
        FnolRequest request = fnol(ClaimedCause.MECHANICAL_FAILURE);
        // Exactly 90 days ago → still within the window (not before windowStart)
        ServiceTimeline tl = timeline(event(90, "Routine oil change."));

        TriageService.TriageResult result = triageService.triage(request, tl);

        assertEquals(TriageDecision.MANUAL_REVIEW, result.decision());
    }

    // ── Fast-tracked path: STRAIGHT_THROUGH_PROCESSING ───────────────────────

    @Test
    @DisplayName("STP: non-mechanical cause (collision) skips maintenance check entirely")
    void collision_isAlwaysStraightThrough() {
        FnolRequest request = fnol(ClaimedCause.COLLISION);
        // Even with recent clean service — should be STP
        ServiceTimeline tl = timeline(event(10, null));

        TriageService.TriageResult result = triageService.triage(request, tl);

        assertEquals(TriageDecision.STRAIGHT_THROUGH_PROCESSING, result.decision());
        assertTrue(result.reasons().stream().anyMatch(r -> r.contains("does not trigger")),
                "Reasons should explain cause is not mechanical_failure");
    }

    @Test
    @DisplayName("STP: mechanical failure claimed but service was > 90 days ago")
    void mechanicalFailure_oldService_isStraightThrough() {
        FnolRequest request = fnol(ClaimedCause.MECHANICAL_FAILURE);
        ServiceTimeline tl = timeline(event(120, null));  // 120 days ago — outside window

        TriageService.TriageResult result = triageService.triage(request, tl);

        assertEquals(TriageDecision.STRAIGHT_THROUGH_PROCESSING, result.decision());
        assertTrue(result.reasons().stream().anyMatch(r -> r.contains("No clean service record")));
    }

    @Test
    @DisplayName("STP: mechanical failure claimed with no service history at all")
    void mechanicalFailure_noServiceHistory_isStraightThrough() {
        FnolRequest request = fnol(ClaimedCause.MECHANICAL_FAILURE);

        TriageService.TriageResult result = triageService.triage(request, null);

        assertEquals(TriageDecision.STRAIGHT_THROUGH_PROCESSING, result.decision());
        assertTrue(result.reasons().stream().anyMatch(r -> r.contains("No service history")));
    }

    @Test
    @DisplayName("STP: mechanical failure + recent service but notes indicate defect — NOT clean")
    void mechanicalFailure_recentServiceWithDefectNotes_isStraightThrough() {
        // Service was recent but the notes flagged a critical problem → not a clean record
        FnolRequest request = fnol(ClaimedCause.MECHANICAL_FAILURE);
        ServiceTimeline tl = timeline(event(20,
                "Oil change done. NOTE: Brake service critically overdue — worn pads detected."));

        TriageService.TriageResult result = triageService.triage(request, tl);

        assertEquals(TriageDecision.STRAIGHT_THROUGH_PROCESSING, result.decision(),
                "A recent service with defect notes is NOT a clean service — should be STP");
    }

    @Test
    @DisplayName("STP: mechanical failure + empty timeline events list")
    void mechanicalFailure_emptyTimeline_isStraightThrough() {
        FnolRequest request = fnol(ClaimedCause.MECHANICAL_FAILURE);
        ServiceTimeline emptyTl = new ServiceTimeline(
                "a1b2c3d4-e5f6-7890-abcd-ef1234567890", 0, null, null, List.of());

        TriageService.TriageResult result = triageService.triage(request, emptyTl);

        assertEquals(TriageDecision.STRAIGHT_THROUGH_PROCESSING, result.decision());
    }

    @Test
    @DisplayName("STP: theft and other non-mechanical causes skip check")
    void nonMechanicalCauses_areAlwaysStraightThrough() {
        for (ClaimedCause cause : List.of(ClaimedCause.THEFT, ClaimedCause.NATURAL_DISASTER,
                ClaimedCause.VANDALISM, ClaimedCause.OTHER)) {
            FnolRequest request = fnol(cause);
            TriageService.TriageResult result = triageService.triage(request, timeline(event(5, null)));
            assertEquals(TriageDecision.STRAIGHT_THROUGH_PROCESSING, result.decision(),
                    "Cause " + cause + " should be STP");
        }
    }
}
