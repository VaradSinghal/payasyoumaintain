package com.paymu.advisory.service;

import com.paymu.advisory.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Generates a prioritised list of plain-language advisory messages for a vehicle
 * based on its recall status, service timeline, and risk score.
 *
 * <h3>Advisory types and triggers</h3>
 * <table>
 *   <tr><th>Type</th><th>Priority</th><th>Trigger</th></tr>
 *   <tr><td>OPEN_RECALL</td><td>CRITICAL</td><td>{@code recall_status.has_open_recall == true}</td></tr>
 *   <tr><td>OVERDUE_SERVICE</td><td>HIGH</td><td>Last service &gt; {@code advisory.overdue-service-days} ago</td></tr>
 *   <tr><td>LOW_RISK_SCORE</td><td>MEDIUM</td><td>Composite score &lt; {@code advisory.low-score-threshold}</td></tr>
 *   <tr><td>SAFE_DRIVING_COMMENDATION</td><td>LOW</td><td>Composite score &ge; 90 — positive reinforcement</td></tr>
 * </table>
 *
 * <p>The returned list is sorted CRITICAL → HIGH → MEDIUM → LOW.
 * An empty list means nothing needs flagging.</p>
 */
@Service
public class AdvisoryGeneratorService {

    @Value("${advisory.overdue-service-days:365}")
    private int overdueServiceDays;

    @Value("${advisory.low-score-threshold:60.0}")
    private double lowScoreThreshold;

    /**
     * Produces the prioritised advisory list.
     *
     * @param recall   recall status (nullable — treated as no open recall if absent)
     * @param timeline service timeline (nullable — no service advisory generated if absent)
     * @param score    risk score (nullable — no score advisory generated if absent)
     */
    public List<AdvisoryMessage> generate(RecallStatus recall,
                                          ServiceTimeline timeline,
                                          ScoreResponse score) {
        List<AdvisoryMessage> messages = new ArrayList<>();

        // ── 1. CRITICAL: Open recall ──────────────────────────────────────────
        if (recall != null && recall.hasOpenRecall()) {
            String detail = buildRecallDetail(recall);
            messages.add(new AdvisoryMessage(
                    "OPEN_RECALL",
                    AdvisoryPriority.CRITICAL,
                    "Manufacturer Recall — Immediate Action Required",
                    "Your vehicle has " + recall.recallCount()
                            + " open manufacturer recall(s). " + detail
                            + " Please contact your dealer to schedule a free recall repair as soon as possible.",
                    "https://paym.app/recalls"
            ));
        }

        // ── 2. HIGH: Overdue service ──────────────────────────────────────────
        if (timeline != null && timeline.latestServiceDate() != null) {
            LocalDate lastService = parseDateSafe(timeline.latestServiceDate());
            if (lastService != null) {
                long daysAgo = java.time.temporal.ChronoUnit.DAYS.between(lastService, LocalDate.now());
                if (daysAgo > overdueServiceDays) {
                    long overdueDays = daysAgo - overdueServiceDays;
                    messages.add(new AdvisoryMessage(
                            "OVERDUE_SERVICE",
                            AdvisoryPriority.HIGH,
                            "Service Overdue by " + overdueDays + " Day(s)",
                            "Your last recorded service was " + daysAgo + " days ago"
                                    + " (last: " + timeline.latestServiceDate() + "). "
                                    + "Vehicles serviced on schedule attract lower premiums and avoid "
                                    + "claim complications. Book a service appointment today.",
                            "https://paym.app/service-booking"
                    ));
                }
            }
        } else if (timeline != null && timeline.totalServices() == 0) {
            // Vehicle enrolled but has never had a recorded service
            messages.add(new AdvisoryMessage(
                    "NO_SERVICE_RECORD",
                    AdvisoryPriority.HIGH,
                    "No Service Records on File",
                    "We have no maintenance records for your vehicle. "
                            + "Upload your past service receipts to unlock maintenance-linked discounts.",
                    "https://paym.app/upload-service"
            ));
        }

        // ── 3. MEDIUM: Low risk score ─────────────────────────────────────────
        if (score != null) {
            double composite = score.compositeScore() != null
                    ? score.compositeScore()
                    : (score.maintenanceScore() * 0.4 + score.usageScore() * 0.6);

            if (composite < lowScoreThreshold) {
                messages.add(new AdvisoryMessage(
                        "LOW_RISK_SCORE",
                        AdvisoryPriority.MEDIUM,
                        "Your Risk Score Is Affecting Your Premium",
                        String.format(
                                "Your current composite risk score is %.0f/100. "
                                        + "Improving your maintenance adherence and driving behaviour "
                                        + "can reduce your premium at the next renewal. "
                                        + "Tip: ensure your service is up to date and avoid harsh braking.",
                                composite),
                        "https://paym.app/score-details"
                ));
            } else if (composite >= 90.0) {
                // Positive reinforcement for top-tier drivers
                messages.add(new AdvisoryMessage(
                        "SAFE_DRIVING_COMMENDATION",
                        AdvisoryPriority.LOW,
                        "Great Driving — You're on Track for a Discount",
                        String.format(
                                "Your risk score is %.0f/100 — you're in the top tier. "
                                        + "Keep up the good work to lock in your lowest premium at renewal.",
                                composite),
                        "https://paym.app/score-details"
                ));
            }
        }

        // Sort: CRITICAL < HIGH < MEDIUM < LOW (by enum ordinal)
        messages.sort(Comparator.comparingInt(m -> m.priority().ordinal()));
        return messages;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildRecallDetail(RecallStatus recall) {
        if (recall.recallDetails() == null || recall.recallDetails().isEmpty()) {
            return "";
        }
        RecallStatus.RecallDetail first = recall.recallDetails().getFirst();
        return "Recall " + first.recallId() + ": " + first.description() + ".";
    }

    private LocalDate parseDateSafe(String dateStr) {
        if (dateStr == null) return null;
        try {
            return LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
