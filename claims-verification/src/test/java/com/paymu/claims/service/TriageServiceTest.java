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
 * <p>All tests are pure in-process — no Spring context, no HTTP calls.
 * The service signature is {@code triage(FnolRequest, ServiceTimeline, RecallStatus)}.</p>
 */
@DisplayName("TriageService")
class TriageServiceTest {

    private TriageService triageService;

    @BeforeEach
    void setUp() {
        triageService = new TriageService();
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

    /** A RecallStatus with has_open_recall = true. */
    private RecallStatus openRecall() {
        return new RecallStatus(
                "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                true, 1,
                List.of(new RecallStatus.RecallDetail(
                        "RC-2026-0042", "Battery management thermal risk", "2026-03-15")),
                "oem_recall_db", "2026-09-24T05:00:00Z"
        );
    }

    /** A RecallStatus with has_open_recall = false. */
    private RecallStatus noRecall() {
        return new RecallStatus(
                "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                false, 0, List.of(), "oem_recall_db", "2026-09-24T05:00:00Z"
        );
    }

    // ── MANUAL_REVIEW paths ───────────────────────────────────────────────────

    @Test
    @DisplayName("MANUAL_REVIEW: mechanical failure + clean service within 90 days")
    void mechanicalFailure_withRecentCleanService_isManualReview() {
        FnolRequest request = fnol(ClaimedCause.MECHANICAL_FAILURE);
        ServiceTimeline tl  = timeline(event(30, null));  // clean, 30 days ago

        TriageService.TriageResult result = triageService.triage(request, tl, noRecall());

        assertEquals(TriageDecision.MANUAL_REVIEW, result.decision());
        assertTrue(result.reasons().stream().anyMatch(r -> r.contains("clean service record")),
                "Reasons should explain the clean-service finding");
        assertTrue(result.maintenanceSignal().contains("clean service within"),
                "maintenanceSignal should describe the window match");
    }

    @Test
    @DisplayName("MANUAL_REVIEW: clean service exactly at window boundary (90 days) still flags")
    void mechanicalFailure_cleanServiceAtWindowEdge_isManualReview() {
        FnolRequest request = fnol(ClaimedCause.MECHANICAL_FAILURE);
        ServiceTimeline tl  = timeline(event(90, "Routine oil change."));

        TriageService.TriageResult result = triageService.triage(request, tl, noRecall());

        assertEquals(TriageDecision.MANUAL_REVIEW, result.decision());
    }

    /**
     * Core requirement: an open recall triggers MANUAL_REVIEW entirely from the
     * structured RecallStatus field — even with no service records and no notes whatsoever.
     * This proves that recall detection is NOT based on note text.
     */
    @Test
    @DisplayName("MANUAL_REVIEW: open_recall_on_file triggers flag — notes play no role")
    void openRecall_triggersManualReview_independentOfNotes() {
        FnolRequest request = fnol(ClaimedCause.MECHANICAL_FAILURE);
        // Old service outside the 90-day window, and no recall-related text in notes
        ServiceTimeline tl = timeline(event(200, "Routine oil change completed normally."));

        TriageService.TriageResult result = triageService.triage(request, tl, openRecall());

        assertEquals(TriageDecision.MANUAL_REVIEW, result.decision(),
                "has_open_recall: true must trigger MANUAL_REVIEW regardless of notes");

        assertTrue(result.reasons().stream().anyMatch(r -> r.contains("open_recall_on_file")),
                "triage_reasons must contain a distinct 'open_recall_on_file' entry");

        // Confirm the clean-service signal did NOT fire (old service outside window)
        assertFalse(result.reasons().stream().anyMatch(r -> r.contains("clean service record")),
                "Clean-service reason must NOT appear when service is outside the window");
    }

    @Test
    @DisplayName("MANUAL_REVIEW: both signals fire independently — both reasons present")
    void bothSignals_bothReasonsPresent() {
        FnolRequest request = fnol(ClaimedCause.MECHANICAL_FAILURE);
        ServiceTimeline tl  = timeline(event(30, null));   // recent clean service

        TriageService.TriageResult result = triageService.triage(request, tl, openRecall());

        assertEquals(TriageDecision.MANUAL_REVIEW, result.decision());
        assertTrue(result.reasons().stream().anyMatch(r -> r.contains("open_recall_on_file")),
                "open_recall_on_file reason should be present");
        assertTrue(result.reasons().stream().anyMatch(r -> r.contains("clean service record")),
                "clean service record reason should also be present");
    }

    // ── STP paths ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("STP: non-mechanical cause (collision) skips all maintenance checks")
    void collision_isAlwaysStraightThrough() {
        FnolRequest request = fnol(ClaimedCause.COLLISION);
        ServiceTimeline tl  = timeline(event(10, null));

        TriageService.TriageResult result = triageService.triage(request, tl, openRecall());

        assertEquals(TriageDecision.STRAIGHT_THROUGH_PROCESSING, result.decision(),
                "Non-mechanical cause must skip maintenance + recall checks");
        assertTrue(result.reasons().stream().anyMatch(r -> r.contains("does not trigger")));
    }

    @Test
    @DisplayName("STP: mechanical failure, service > 90 days ago, no open recall")
    void mechanicalFailure_oldService_noRecall_isStraightThrough() {
        FnolRequest request = fnol(ClaimedCause.MECHANICAL_FAILURE);
        ServiceTimeline tl  = timeline(event(120, null));   // outside window

        TriageService.TriageResult result = triageService.triage(request, tl, noRecall());

        assertEquals(TriageDecision.STRAIGHT_THROUGH_PROCESSING, result.decision());
        assertTrue(result.reasons().stream().anyMatch(r -> r.contains("No open recall")));
    }

    @Test
    @DisplayName("STP: mechanical failure with no service history and no recall")
    void mechanicalFailure_noHistory_noRecall_isStraightThrough() {
        FnolRequest request = fnol(ClaimedCause.MECHANICAL_FAILURE);

        TriageService.TriageResult result = triageService.triage(request, null, null);

        assertEquals(TriageDecision.STRAIGHT_THROUGH_PROCESSING, result.decision());
    }

    @Test
    @DisplayName("STP: mechanical failure + recent service with defect notes (NOT clean) + no recall")
    void mechanicalFailure_recentDefectService_noRecall_isStraightThrough() {
        FnolRequest request = fnol(ClaimedCause.MECHANICAL_FAILURE);
        ServiceTimeline tl  = timeline(event(20,
                "Oil change done. NOTE: Brake service critically overdue — worn pads detected."));

        TriageService.TriageResult result = triageService.triage(request, tl, noRecall());

        assertEquals(TriageDecision.STRAIGHT_THROUGH_PROCESSING, result.decision(),
                "Defect notes mean service is NOT clean — should be STP with no recall");
    }

    @Test
    @DisplayName("STP: mechanical failure + recent service with 'open recall' in notes — notes NOT parsed for recall")
    void openRecallInNotes_doesNotTriggerManualReview() {
        // The word "open recall" appears in notes. If we were still doing text-matching,
        // this would have incorrectly marked the service as not-clean and routed to STP.
        // Now that "open recall" is removed from DEFECT_KEYWORDS:
        //   - The service IS clean (only non-recall defect keywords are checked)
        //   - recall=noRecall() → has_open_recall: false
        // Result: MANUAL_REVIEW fires because the service IS clean and within window.
        // This confirms notes text is no longer parsed for recall status.
        FnolRequest request = fnol(ClaimedCause.MECHANICAL_FAILURE);
        ServiceTimeline tl  = timeline(event(20,
                "Service completed. Reminder: OEM sent open recall notice RC-2026-0042."));

        TriageService.TriageResult result = triageService.triage(request, tl, noRecall());

        // The service is clean (open recall note is no longer a defect keyword),
        // so the clean-service window fires → MANUAL_REVIEW.
        assertEquals(TriageDecision.MANUAL_REVIEW, result.decision(),
                "Notes mentioning recall no longer disqualify the service as clean; "
                        + "clean-service window fires → MANUAL_REVIEW");
        assertFalse(result.reasons().stream().anyMatch(r -> r.contains("open_recall_on_file")),
                "open_recall_on_file must NOT appear — recall came from noRecall() endpoint, not notes");
    }

    @Test
    @DisplayName("STP: theft and other non-mechanical causes skip check even with open recall")
    void nonMechanicalCauses_areAlwaysStraightThrough() {
        for (ClaimedCause cause : List.of(ClaimedCause.THEFT, ClaimedCause.NATURAL_DISASTER,
                ClaimedCause.VANDALISM, ClaimedCause.OTHER)) {
            FnolRequest request = fnol(cause);
            TriageService.TriageResult result = triageService.triage(
                    request, timeline(event(5, null)), openRecall());
            assertEquals(TriageDecision.STRAIGHT_THROUGH_PROCESSING, result.decision(),
                    "Cause " + cause + " should be STP regardless of recall status");
        }
    }
}
