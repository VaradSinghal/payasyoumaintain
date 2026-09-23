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
 * <pre>
 * IF claimed_cause == MECHANICAL_FAILURE
 *   AND there is a clean service record within the last {window} days
 * THEN → MANUAL_REVIEW
 *      (a vehicle just passed a recent service is unlikely to have suffered
 *       mechanical failure immediately after; warrants investigation)
 * ELSE → STRAIGHT_THROUGH_PROCESSING
 * </pre>
 *
 * <p>"Clean" means the service record has no notes flagging a critical defect
 * (e.g. no note containing "critically overdue", "open recall", "worn" etc.).
 * This is a best-effort text check on the notes field — the definitive recall
 * signal comes from the RecallStore, not here.</p>
 *
 * <p>The window defaults to 90 days and is externally configurable via
 * {@code claims.clean-service-window-days}.</p>
 */
@Service
public class TriageService {

    private static final Logger log = LoggerFactory.getLogger(TriageService.class);

    /** Keywords in event notes that mark a service as NOT clean. */
    private static final List<String> DEFECT_KEYWORDS =
            List.of("critically overdue", "worn", "open recall", "defect", "failed");

    @Value("${claims.clean-service-window-days:90}")
    private int cleanServiceWindowDays;

    /**
     * Determines the triage decision for an FNOL request given the optional service timeline.
     *
     * @param request  the incoming FNOL
     * @param timeline the vehicle's service timeline from maintenance-vehicle-health-ingestion,
     *                 or {@code null} if the maintenance service was unavailable
     * @return triage outcome with reasons
     */
    public TriageResult triage(FnolRequest request, ServiceTimeline timeline) {
        List<String> reasons = new ArrayList<>();
        String maintenanceSignal;

        if (request.claimedCause() != ClaimedCause.MECHANICAL_FAILURE) {
            reasons.add("Claimed cause '" + request.claimedCause().getValue()
                    + "' does not trigger maintenance-correlation check.");
            maintenanceSignal = "Not evaluated — mechanical failure not claimed.";
            return new TriageResult(TriageDecision.STRAIGHT_THROUGH_PROCESSING, reasons, maintenanceSignal);
        }

        // Claimed cause IS mechanical failure — look for a clean recent service
        if (timeline == null || timeline.events() == null || timeline.events().isEmpty()) {
            reasons.add("No service history found — cannot correlate with claimed mechanical failure.");
            maintenanceSignal = "No service records available.";
            return new TriageResult(TriageDecision.STRAIGHT_THROUGH_PROCESSING, reasons, maintenanceSignal);
        }

        LocalDate windowStart = LocalDate.now().minusDays(cleanServiceWindowDays);
        boolean hasRecentCleanService = timeline.events().stream()
                .filter(e -> parseDateSafe(e.serviceDate()) != null)
                .filter(e -> !parseDateSafe(e.serviceDate()).isBefore(windowStart))
                .anyMatch(e -> isCleanService(e));

        if (hasRecentCleanService) {
            reasons.add("Claimed cause is 'mechanical_failure'.");
            reasons.add("Vehicle has a clean service record within the last "
                    + cleanServiceWindowDays + " days.");
            reasons.add("A recent clean service contradicts an immediate mechanical failure — "
                    + "manual verification required.");
            maintenanceSignal = "Clean service found within " + cleanServiceWindowDays
                    + "-day window of incident date.";
            log.info("FNOL for vehicle {} routed to MANUAL_REVIEW — clean service within {}d",
                    request.vehicleId(), cleanServiceWindowDays);
            return new TriageResult(TriageDecision.MANUAL_REVIEW, reasons, maintenanceSignal);
        }

        // Mechanical failure claimed but no clean recent service → plausible, STP
        reasons.add("Claimed cause is 'mechanical_failure'.");
        reasons.add("No clean service record found within the last " + cleanServiceWindowDays
                + " days — claim is plausible.");
        maintenanceSignal = "No clean service within " + cleanServiceWindowDays
                + "-day window; mechanical failure claim is consistent with maintenance record.";
        return new TriageResult(TriageDecision.STRAIGHT_THROUGH_PROCESSING, reasons, maintenanceSignal);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * A service event is "clean" if its notes field contains no defect keywords.
     * Absence of notes is also treated as clean (no reported issues).
     */
    private boolean isCleanService(ServiceTimeline.MaintenanceEvent event) {
        if (event.notes() == null || event.notes().isBlank()) {
            return true;
        }
        String lowerNotes = event.notes().toLowerCase();
        boolean defectFound = DEFECT_KEYWORDS.stream().anyMatch(lowerNotes::contains);
        return !defectFound;
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
