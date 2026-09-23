package com.paymu.advisory.service;

import com.paymu.advisory.model.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AdvisoryGeneratorService}.
 * All tests are pure in-process — no Spring context, no HTTP calls.
 */
@DisplayName("AdvisoryGeneratorService")
class AdvisoryGeneratorServiceTest {

    private AdvisoryGeneratorService service;

    @BeforeEach
    void setUp() {
        service = new AdvisoryGeneratorService();
        ReflectionTestUtils.setField(service, "overdueServiceDays", 365);
        ReflectionTestUtils.setField(service, "lowScoreThreshold", 60.0);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RecallStatus openRecall() {
        return new RecallStatus(
                "v-001", true, 1,
                List.of(new RecallStatus.RecallDetail(
                        "RC-2026-0042", "Battery management thermal risk", "2026-03-15")),
                "oem_recall_db", "2026-09-23T10:00:00Z"
        );
    }

    private RecallStatus noRecall() {
        return new RecallStatus("v-001", false, 0, List.of(), "oem_recall_db", "2026-09-23T10:00:00Z");
    }

    private ServiceTimeline recentTimeline() {
        // Last service 30 days ago → not overdue
        String date = LocalDate.now().minusDays(30).toString();
        return new ServiceTimeline("v-001", 3, date, 25000, List.of());
    }

    private ServiceTimeline overdueTimeline() {
        // Last service 400 days ago → 35 days overdue
        String date = LocalDate.now().minusDays(400).toString();
        return new ServiceTimeline("v-001", 5, date, 30000, List.of());
    }

    private ServiceTimeline noServiceTimeline() {
        return new ServiceTimeline("v-001", 0, null, null, List.of());
    }

    private ScoreResponse goodScore() {
        return new ScoreResponse("s-1", "v-001", 95.0, 98.0, 96.8,
                "2026-09-23T10:00:00Z", "v1.0.0-stub", List.of());
    }

    private ScoreResponse badScore() {
        return new ScoreResponse("s-2", "v-001", 55.0, 42.0, 47.2,
                "2026-09-23T10:00:00Z", "v1.0.0-stub", List.of());
    }

    // ── Required test 1: Open recall triggers CRITICAL advisory ───────────────

    @Test
    @DisplayName("Open recall → CRITICAL advisory with recall details")
    void openRecall_generatesCriticalAdvisory() {
        List<AdvisoryMessage> result = service.generate(openRecall(), recentTimeline(), goodScore());

        AdvisoryMessage recallMsg = result.stream()
                .filter(m -> m.advisoryType().equals("OPEN_RECALL"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected OPEN_RECALL advisory"));

        assertEquals(AdvisoryPriority.CRITICAL, recallMsg.priority());
        assertTrue(recallMsg.message().contains("1 open manufacturer recall"),
                "Message should mention recall count");
        assertTrue(recallMsg.message().contains("RC-2026-0042"),
                "Message should include recall ID from details");
    }

    // ── Required test 2: Overdue service triggers HIGH advisory ───────────────

    @Test
    @DisplayName("Overdue service → HIGH advisory with days count")
    void overdueService_generatesHighAdvisory() {
        List<AdvisoryMessage> result = service.generate(noRecall(), overdueTimeline(), goodScore());

        AdvisoryMessage overdueMsg = result.stream()
                .filter(m -> m.advisoryType().equals("OVERDUE_SERVICE"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected OVERDUE_SERVICE advisory"));

        assertEquals(AdvisoryPriority.HIGH, overdueMsg.priority());
        assertTrue(overdueMsg.title().contains("35"),
                "Title should state number of days overdue");
        assertTrue(overdueMsg.message().contains("400 days ago"),
                "Message should mention days since last service");
    }

    // ── Required test 3: Empty case — healthy vehicle, no advisories ──────────

    @Test
    @DisplayName("Healthy vehicle → empty advisory list")
    void healthyVehicle_returnsEmptyList() {
        // No recall, recent service, excellent score
        List<AdvisoryMessage> result = service.generate(noRecall(), recentTimeline(), goodScore());

        // Should have no warnings or surcharges — only possibly a LOW commendation
        boolean hasNegativeAdvisory = result.stream()
                .anyMatch(m -> m.priority() == AdvisoryPriority.CRITICAL
                        || m.priority() == AdvisoryPriority.HIGH
                        || m.priority() == AdvisoryPriority.MEDIUM);

        assertFalse(hasNegativeAdvisory,
                "Healthy vehicle with no recall, recent service, and high score should have "
                        + "no CRITICAL/HIGH/MEDIUM advisories");
    }

    // ── Additional: Low score triggers MEDIUM advisory ────────────────────────

    @Test
    @DisplayName("Low composite score → MEDIUM advisory")
    void lowScore_generatesMediumAdvisory() {
        List<AdvisoryMessage> result = service.generate(noRecall(), recentTimeline(), badScore());

        AdvisoryMessage scoreMsg = result.stream()
                .filter(m -> m.advisoryType().equals("LOW_RISK_SCORE"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected LOW_RISK_SCORE advisory"));

        assertEquals(AdvisoryPriority.MEDIUM, scoreMsg.priority());
        assertTrue(scoreMsg.message().contains("47"),
                "Message should reference the composite score");
    }

    // ── Additional: Priority ordering is correct ──────────────────────────────

    @Test
    @DisplayName("Multiple flags → advisories sorted CRITICAL first")
    void multipleFlags_sortedByPriority() {
        // Open recall + overdue service + bad score → three advisories
        List<AdvisoryMessage> result = service.generate(openRecall(), overdueTimeline(), badScore());

        assertTrue(result.size() >= 3, "Expected at least 3 advisories");
        assertEquals(AdvisoryPriority.CRITICAL, result.get(0).priority(),
                "First advisory must be CRITICAL");
        assertEquals(AdvisoryPriority.HIGH, result.get(1).priority(),
                "Second advisory must be HIGH");
        assertEquals(AdvisoryPriority.MEDIUM, result.get(2).priority(),
                "Third advisory must be MEDIUM");
    }

    // ── Additional: All inputs null → no NPE, empty list ─────────────────────

    @Test
    @DisplayName("All inputs null (cold-start) → empty list, no exception")
    void allInputsNull_returnsEmptyList() {
        List<AdvisoryMessage> result = assertDoesNotThrow(
                () -> service.generate(null, null, null));
        assertTrue(result.isEmpty(),
                "Cold-start with no data should produce no advisories");
    }

    // ── Additional: No service records on file ────────────────────────────────

    @Test
    @DisplayName("No service records → HIGH 'No Service Record' advisory")
    void noServiceRecords_generatesHighAdvisory() {
        List<AdvisoryMessage> result = service.generate(noRecall(), noServiceTimeline(), null);

        AdvisoryMessage msg = result.stream()
                .filter(m -> m.advisoryType().equals("NO_SERVICE_RECORD"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected NO_SERVICE_RECORD advisory"));

        assertEquals(AdvisoryPriority.HIGH, msg.priority());
    }

    // ── Additional: Positive reinforcement for top drivers ────────────────────

    @Test
    @DisplayName("Excellent score (>=90) → LOW commendation advisory")
    void excellentScore_generatesCommendation() {
        List<AdvisoryMessage> result = service.generate(noRecall(), recentTimeline(), goodScore());

        boolean hasCommendation = result.stream()
                .anyMatch(m -> m.advisoryType().equals("SAFE_DRIVING_COMMENDATION"));
        assertTrue(hasCommendation,
                "Score of 96.8 should trigger a commendation advisory");
    }
}
