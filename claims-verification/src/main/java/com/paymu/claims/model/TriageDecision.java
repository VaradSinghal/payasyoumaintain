package com.paymu.claims.model;

/**
 * Outcome of FNOL triage.
 *
 * <ul>
 *   <li>{@code MANUAL_REVIEW} — claim cannot be processed automatically; a human
 *       investigator must evaluate it before payment authorisation.</li>
 *   <li>{@code STRAIGHT_THROUGH_PROCESSING} — claim meets all automated eligibility
 *       criteria and can proceed to settlement without manual intervention.</li>
 * </ul>
 */
public enum TriageDecision {
    MANUAL_REVIEW,
    STRAIGHT_THROUGH_PROCESSING
}
