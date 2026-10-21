package com.paymu.claims.service;

import com.paymu.claims.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * in {@code triage_reasons} and {@code triage_reason_codes} so investigators can see exactly what triggered
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

    private final int cleanServiceWindowDays;
    private final ZoneId zoneId;
    private final Clock clock;

    @Autowired
    public TriageService(
            @Value("${claims.clean-service-window-days:90}") int cleanServiceWindowDays,
            @Value("${claims.timezone:Asia/Kolkata}") String timezone,
            Clock clock) {
        this.cleanServiceWindowDays = cleanServiceWindowDays;
        this.zoneId = ZoneId.of(timezone);
        this.clock = clock;
    }

    public TriageService() {
        this(90, "Asia/Kolkata", Clock.systemUTC());
    }

    public TriageService(Clock clock, ZoneId zoneId) {
        this(90, zoneId.getId(), clock);
    }

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
        return triage(request, timeline, recall, false);
    }

    /**
     * Determines the triage decision for an FNOL given the service timeline,
     * recall status, and policy binding verification state.
     *
     * @param request                  the incoming FNOL
     * @param timeline                 service timeline, or {@code null} if unavailable
     * @param recall                   recall status, or {@code null} if unavailable
     * @param policyBindingUnverified  true if identity service was unreachable or 5xx
     * @return triage outcome with reasons
     */
    public TriageResult triage(FnolRequest request,
                               ServiceTimeline timeline,
                               RecallStatus recall,
                               boolean policyBindingUnverified) {
        List<String> reasons = new ArrayList<>();
        List<String> reasonCodes = new ArrayList<>();
        String maintenanceSignal;

        if (policyBindingUnverified) {
            reasons.add("policy_binding_unverified");
            reasonCodes.add("policy_binding_unverified");
        }

        // ── Non-mechanical cause → skip all maintenance checks ────────────────
        if (request.claimedCause() != ClaimedCause.MECHANICAL_FAILURE) {
            reasonCodes.add("cause_not_mechanical");
            reasons.add("Claimed cause '" + request.claimedCause().getValue()
                    + "' does not trigger maintenance-correlation check.");
            maintenanceSignal = "Not evaluated — mechanical failure not claimed.";
            TriageDecision decision = policyBindingUnverified
                    ? TriageDecision.MANUAL_REVIEW
                    : TriageDecision.STRAIGHT_THROUGH_PROCESSING;
            return new TriageResult(decision, reasons, reasonCodes, maintenanceSignal);
        }

        // ── Mechanical failure — evaluate signals with truthful precedence ───
        reasonCodes.add("mechanical_failure_claimed");

        boolean hasOpenRecall     = (recall != null && recall.hasOpenRecall());
        boolean recallUnavailable = (recall == null);

        if (hasOpenRecall) {
            reasons.add("Claimed cause is 'mechanical_failure'.");
            reasons.add("open_recall_on_file: vehicle has " + recall.recallCount()
                    + " open manufacturer recall(s) — a pending recall increases mechanical failure"
                    + " plausibility and requires investigator review.");
            reasonCodes.add("open_recall_on_file");
        } else if (recallUnavailable) {
            reasons.add("Claimed cause is 'mechanical_failure'.");
            reasons.add("recall_status_unavailable");
            reasonCodes.add("recall_status_unavailable");
        }

        boolean hasCleanService = evaluateCleanService(timeline, reasons);
        if (hasCleanService) {
            reasonCodes.add("recent_clean_service");
        } else if (!hasOpenRecall && !recallUnavailable) {
            reasonCodes.add("no_recent_clean_service");
        }

        // Precedence: open recall > recall unavailable > clean service > unverified binding > none
        if (hasOpenRecall) {
            maintenanceSignal = "open recall on file.";
            log.info("FNOL for vehicle {} → MANUAL_REVIEW [open_recall=true]", request.vehicleId());
            return new TriageResult(TriageDecision.MANUAL_REVIEW, reasons, reasonCodes, maintenanceSignal);
        } else if (recallUnavailable) {
            maintenanceSignal = "recall status unavailable.";
            log.info("FNOL for vehicle {} → MANUAL_REVIEW [recall_unavailable=true]", request.vehicleId());
            return new TriageResult(TriageDecision.MANUAL_REVIEW, reasons, reasonCodes, maintenanceSignal);
        } else if (hasCleanService) {
            maintenanceSignal = "clean service within " + cleanServiceWindowDays + "-day window.";
            log.info("FNOL for vehicle {} → MANUAL_REVIEW [clean_service=true]", request.vehicleId());
            return new TriageResult(TriageDecision.MANUAL_REVIEW, reasons, reasonCodes, maintenanceSignal);
        } else if (policyBindingUnverified) {
            reasons.add("Claimed cause is 'mechanical_failure'.");
            reasons.add("Confirmed no open recall on file and no clean service record found within the last "
                    + cleanServiceWindowDays + " days.");
            maintenanceSignal = "No disqualifying maintenance signals found, but policy binding unverified.";
            log.info("FNOL for vehicle {} → MANUAL_REVIEW [policy_binding_unverified=true]", request.vehicleId());
            return new TriageResult(TriageDecision.MANUAL_REVIEW, reasons, reasonCodes, maintenanceSignal);
        }

        // ── No disqualifying signals found → plausible, STP ───────────────────
        reasons.add("Claimed cause is 'mechanical_failure'.");
        reasons.add("Confirmed no open recall on file and no clean service record found within the last "
                + cleanServiceWindowDays + " days — claim is plausible.");
        maintenanceSignal = "No disqualifying maintenance signals found.";
        return new TriageResult(TriageDecision.STRAIGHT_THROUGH_PROCESSING, reasons, reasonCodes, maintenanceSignal);
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

        LocalDate today = LocalDate.now(clock.withZone(zoneId));
        LocalDate windowStart = today.minusDays(cleanServiceWindowDays);
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
            List<String> reasonCodes,
            String maintenanceSignal
    ) {
        public TriageResult(TriageDecision decision, List<String> reasons, String maintenanceSignal) {
            this(decision, reasons, List.of(), maintenanceSignal);
        }
    }
}
