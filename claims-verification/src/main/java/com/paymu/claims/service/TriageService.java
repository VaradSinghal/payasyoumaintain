package com.paymu.claims.service;

import com.paymu.claims.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Core FNOL triage logic.
 *
 * <h3>Triage rule</h3>
 * <p>For a {@code MECHANICAL_FAILURE} claim, route to {@code MANUAL_REVIEW} if
 * <em>either</em> of the following is true:</p>
 * <ol>
 *   <li>The vehicle has a <em>clean</em> service record within the last
 *       {@code claims.clean-service-window-days} days (default 90).</li>
 *   <li>The structured recall-status endpoint reports
 *       {@code has_open_recall: true}.</li>
 * </ol>
 * <p>Both conditions are evaluated independently and produce separate entries
 * in {@code triage_reasons} so investigators can see exactly what triggered
 * the flag.</p>
 *
 * <h3>What "clean" means</h3>
 * <p>A service event is clean if its {@code notes} field is absent, blank, or
 * contains none of the defect keywords: {@code "critically overdue"},
 * {@code "worn"}, {@code "defect"}, {@code "failed"}.</p>
 *
 * <p><strong>Note:</strong> {@code "open recall"} has been deliberately removed
 * from the defect keyword list. Recall status is a first-class structured
 * resource ({@code RecallStatus.hasOpenRecall}), fetched from
 * {@code GET /recall-status/{vehicleId}} and evaluated as a separate triage
 * condition. Free-text notes are never parsed for recall signals.</p>
 *
 * <h3>Non-mechanical causes</h3>
 * <p>All causes other than {@code MECHANICAL_FAILURE} bypass the maintenance
 * and recall checks entirely and proceed directly to
 * {@code STRAIGHT_THROUGH_PROCESSING}.</p>
 */
@Service
public class TriageService {

    private static final Logger log = LoggerFactory.getLogger(TriageService.class);

    /**
     * Keywords in event notes that mark a service as NOT clean.
     *
     * <p>"open recall" is intentionally absent — recall status comes from the
     * structured {@link RecallStatus} object, not from text parsing.</p>
     */
    private static final List<String> DEFECT_KEYWORDS =
            List.of("critically overdue", "worn", "defect", "failed");

    @Value("${claims.clean-service-window-days:90}")
    private int cleanServiceWindowDays;

    /**
     * Determines the triage decision for an FNOL given the service timeline
     * and recall status fetched from the maintenance service.
     *
     * @param request  the incoming FNOL
     * @param timeline service timeline, or {@code null} if unavailable
     * @param recall   recall status, or {@code null} if unavailable
     * @return triage outcome with reasons
     */
    public TriageResult triage(FnolRequest request,
                               ServiceTimeline timeline,
                               RecallStatus recall) {
        List<String> reasons = new ArrayList<>();
        String maintenanceSignal;

        // ── Non-mechanical cause → skip all maintenance checks ────────────────
        if (request.claimedCause() != ClaimedCause.MECHANICAL_FAILURE) {
            reasons.add("Claimed cause '" + request.claimedCause().getValue()
                    + "' does not trigger maintenance-correlation check.");
            maintenanceSignal = "Not evaluated — mechanical failure not claimed.";
            return new TriageResult(TriageDecision.STRAIGHT_THROUGH_PROCESSING, reasons, maintenanceSignal);
        }

        // ── Mechanical failure — evaluate both signals ─────────────────────────
        boolean flagForRecall       = evaluateRecall(recall, reasons);
        boolean flagForCleanService = evaluateCleanService(timeline, reasons);

        if (flagForRecall || flagForCleanService) {
            // Compose the maintenance signal summary
            List<String> signals = new ArrayList<>();
            if (flagForRecall)       signals.add("open recall on file");
            if (flagForCleanService) signals.add("clean service within " + cleanServiceWindowDays + "-day window");
            maintenanceSignal = String.join("; ", signals) + ".";

            log.info("FNOL for vehicle {} → MANUAL_REVIEW [recall={}, cleanService={}]",
                    request.vehicleId(), flagForRecall, flagForCleanService);
            return new TriageResult(TriageDecision.MANUAL_REVIEW, reasons, maintenanceSignal);
        }

        // ── Neither signal fired → plausible, STP ─────────────────────────────
        reasons.add("Claimed cause is 'mechanical_failure'.");
        reasons.add("No open recall on file and no clean service record found within the last "
                + cleanServiceWindowDays + " days — claim is plausible.");
        maintenanceSignal = "No disqualifying maintenance signals found.";
        return new TriageResult(TriageDecision.STRAIGHT_THROUGH_PROCESSING, reasons, maintenanceSignal);
    }

    // ── Recall evaluation ─────────────────────────────────────────────────────

    /**
     * Checks the structured recall status. Adds a distinct {@code "open_recall_on_file"}
     * reason entry if triggered. Returns true if this signal alone is sufficient to
     * route to MANUAL_REVIEW.
     */
    private boolean evaluateRecall(RecallStatus recall, List<String> reasons) {
        if (recall != null && recall.hasOpenRecall()) {
            reasons.add("Claimed cause is 'mechanical_failure'.");
            reasons.add("open_recall_on_file: vehicle has " + recall.recallCount()
                    + " open manufacturer recall(s) — a pending recall increases mechanical failure"
                    + " plausibility and requires investigator review.");
            return true;
        }
        return false;
    }

    // ── Clean-service evaluation ──────────────────────────────────────────────

    /**
     * Checks whether a clean service exists within the configured window.
     * Adds reason entries if triggered. Returns true if this signal alone
     * is sufficient to route to MANUAL_REVIEW.
     */
    private boolean evaluateCleanService(ServiceTimeline timeline, List<String> reasons) {
        if (timeline == null || timeline.events() == null || timeline.events().isEmpty()) {
            // Absence of history is not a disqualifying signal
            return false;
        }

        LocalDate windowStart = LocalDate.now().minusDays(cleanServiceWindowDays);
        boolean hasRecentCleanService = timeline.events().stream()
                .filter(e -> parseDateSafe(e.serviceDate()) != null)
                .filter(e -> !parseDateSafe(e.serviceDate()).isBefore(windowStart))
                .anyMatch(this::isCleanService);

        if (hasRecentCleanService) {
            // Only add the "claimed cause" preamble if recall didn't already add it
            boolean claimedCauseAlreadyAdded = reasons.stream()
                    .anyMatch(r -> r.startsWith("Claimed cause is"));
            if (!claimedCauseAlreadyAdded) {
                reasons.add("Claimed cause is 'mechanical_failure'.");
            }
            reasons.add("Vehicle has a clean service record within the last "
                    + cleanServiceWindowDays + " days.");
            reasons.add("A recent clean service contradicts an immediate mechanical failure — "
                    + "manual verification required.");
            return true;
        }
        return false;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * A service event is clean if its notes field contains none of the defect keywords.
     * Absence of notes (null or blank) is treated as clean — benefit of the doubt to claimant.
     * "open recall" is NOT a defect keyword here; recall comes from the RecallStatus object.
     */
    private boolean isCleanService(ServiceTimeline.MaintenanceEvent event) {
        if (event.notes() == null || event.notes().isBlank()) {
            return true;
        }
        String lowerNotes = event.notes().toLowerCase();
        return DEFECT_KEYWORDS.stream().noneMatch(lowerNotes::contains);
    }

    private LocalDate parseDateSafe(String dateStr) {
        if (dateStr == null) return null;
        try {
            return LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            log.debug("Could not parse service date '{}' — skipping event", dateStr);
            return null;
        }
    }

    /** Internal value object returned by {@link #triage}. */
    public record TriageResult(
            TriageDecision decision,
            List<String> reasons,
            String maintenanceSignal
    ) {}
}
