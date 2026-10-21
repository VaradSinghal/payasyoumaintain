import uuid
from dataclasses import dataclass, field
from datetime import datetime, date, timezone
from typing import Tuple, List, Optional
from .models import ScoreRequest, ContributingFactor, MaintenanceEvent

MODEL_VERSION = "v1.1.0-health-spec"

# ── Vehicle Health Scoring Specification constants ────────────────────────────
RECENCY_BANDS = [(365, 0.0), (545, 10.0), (730, 25.0)]  # inclusive upper bounds
RECENCY_MAX_PENALTY = 40.0                               # > 730 days
RECALL_PENALTY = 30.0
BRAKE_GAP_PENALTY = 15.0
BRAKE_WINDOW_DAYS = 24 * 30 + 10                         # ~24 months (730 days)
BRAKE_MIN_VEHICLE_AGE_MONTHS = 24
BRAKE_SERVICE_TYPES = {"brake_service", "full_service"}
# Self-report credit cap configuration:
# A vehicle whose records are all self-reported is capped at 85.
# If trusted records (oem_api, inspection) exist, self-reported records cannot lift
# the health score above max(trusted, 85).
SELF_REPORT_CREDIT_CAP = 85.0
UNVERIFIED_SCORE_CAP = SELF_REPORT_CREDIT_CAP
TRUSTED_SOURCES = {"oem_api", "inspection"}
SELF_UPLOAD = "self_upload"
# Conflict detection: events this close in time are "near-overlapping"; the odometer
# must not go backwards or advance more than a plausible daily distance.
CONFLICT_WINDOW_DAYS = 7
MAX_PLAUSIBLE_KM_PER_DAY = 1000

def _days_since(d: date) -> int:
    return (date.today() - d).days


@dataclass
class HealthResult:
    status: str                      # "scored" | "insufficient_data"
    score: Optional[float]           # None when insufficient_data
    confidence: Optional[str]        # None when insufficient_data
    factors: List[ContributingFactor] = field(default_factory=list)
    flags: List[str] = field(default_factory=list)
    # None = unknown (recall source unavailable); never collapsed to False.
    has_open_recall: Optional[bool] = False
    overdue: bool = False
    needs_brake: bool = False
    # Maintenance component fed to the composite. None when insufficient_data.
    maintenance_score: Optional[float] = None


def _recency_penalty(days: int) -> float:
    for upper, penalty in RECENCY_BANDS:
        if days <= upper:
            return penalty
    return RECENCY_MAX_PENALTY


def _resolve_conflicts(events: List[MaintenanceEvent]) -> Tuple[List[MaintenanceEvent], bool]:
    """
    Detects near-overlapping events with inconsistent odometer readings. When the
    conflicting pair differs in source confidence, the self_upload record is dropped
    from scoring (higher-confidence oem_api/inspection wins). Returns (usable, conflict_found).
    """
    ordered = sorted(enumerate(events), key=lambda p: (p[1].service_date, p[1].odometer_km))
    dropped = set()
    conflict = False
    for a in range(len(ordered)):
        for b in range(a + 1, len(ordered)):
            (ia, ea), (ib, eb) = ordered[a], ordered[b]
            gap = (eb.service_date - ea.service_date).days
            if gap > CONFLICT_WINDOW_DAYS:
                break
            delta_km = eb.odometer_km - ea.odometer_km
            if delta_km < 0 or delta_km > max(gap, 1) * MAX_PLAUSIBLE_KM_PER_DAY:
                conflict = True
                a_trusted = ea.source in TRUSTED_SOURCES
                b_trusted = eb.source in TRUSTED_SOURCES
                if a_trusted and not b_trusted:
                    dropped.add(ib)
                elif b_trusted and not a_trusted:
                    dropped.add(ia)
    usable = [e for i, e in enumerate(events) if i not in dropped]
    return usable, conflict


def _evaluate_event_set(
    events_subset: List[MaintenanceEvent],
    age: Optional[int],
    recall_penalty: float
) -> Tuple[float, float, float, bool, bool]:
    """
    Computes (score, recency_penalty, gap_penalty, overdue, needs_brake)
    for a given subset of maintenance events.
    """
    latest = max(events_subset, key=lambda e: e.service_date)
    days = _days_since(latest.service_date)
    recency_penalty = _recency_penalty(days)
    overdue = recency_penalty > 0

    brake_recent = any(
        e.service_type in BRAKE_SERVICE_TYPES and _days_since(e.service_date) <= BRAKE_WINDOW_DAYS
        for e in events_subset
    )
    gap_penalty = 0.0
    needs_brake = False
    if not brake_recent:
        if age is not None and age >= BRAKE_MIN_VEHICLE_AGE_MONTHS:
            gap_penalty = BRAKE_GAP_PENALTY
            needs_brake = True

    score = max(0.0, 100.0 - recency_penalty - recall_penalty - gap_penalty)
    return score, recency_penalty, gap_penalty, overdue, needs_brake


def calculate_vehicle_health(request: ScoreRequest) -> HealthResult:
    """Deterministic Vehicle Health Score per the Vehicle Health Scoring Specification."""
    factors: List[ContributingFactor] = []
    flags: List[str] = []

    # Recall handling. Notes text is NEVER read — recall_status is the sole source.
    rs = request.recall_status
    recall_unavailable = rs is None or rs.availability == "unavailable"
    recall_confirmed_open = bool(rs is not None and not recall_unavailable and rs.has_open_recall)
    # Unavailable or omitted -> null (unknown), distinct from a confirmed False.
    has_open_recall = None if recall_unavailable else recall_confirmed_open
    if recall_unavailable:
        flags.append("recall_status_unavailable")

    recall_penalty = 0.0
    if recall_confirmed_open:
        recall_penalty = RECALL_PENALTY
        factors.append(ContributingFactor(
            factor_name="open_recall",
            impact=0.3,
            direction="negative",
            description=f"Vehicle has {rs.recall_count} open manufacturer recall(s)."
        ))

    timeline = request.service_timeline

    # No service data (timeline omitted, or provided with zero events): the
    # maintenance component is not scored. maintenance_score stays None; the
    # open-recall flag is still surfaced independently.
    if timeline is None or not timeline.events:
        return HealthResult(
            status="insufficient_data", score=None, confidence=None,
            factors=factors, flags=flags, has_open_recall=has_open_recall,
            maintenance_score=None,
        )

    events, conflict = _resolve_conflicts(timeline.events)
    if conflict:
        flags.append("conflicting_records_detected")

    if not events:
        return HealthResult(
            status="insufficient_data", score=None, confidence=None,
            factors=factors, flags=flags, has_open_recall=has_open_recall,
            maintenance_score=None,
        )

    # 1. Evaluate all usable events
    score_all, recency_penalty, gap_penalty, overdue, needs_brake = _evaluate_event_set(
        events, request.vehicle_age_months, recall_penalty
    )

    latest = max(events, key=lambda e: e.service_date)
    days = _days_since(latest.service_date)
    if overdue:
        factors.append(ContributingFactor(
            factor_name="overdue_service", impact=0.4, direction="negative",
            description=f"Last service was {days} days ago."
        ))
    else:
        factors.append(ContributingFactor(
            factor_name="regular_service", impact=0.2, direction="positive",
            description="Vehicle is being serviced regularly."
        ))

    brake_recent = any(
        e.service_type in BRAKE_SERVICE_TYPES and _days_since(e.service_date) <= BRAKE_WINDOW_DAYS
        for e in events
    )
    if not brake_recent:
        if request.vehicle_age_months is None:
            flags.append("vehicle_age_unknown")
        elif request.vehicle_age_months >= BRAKE_MIN_VEHICLE_AGE_MONTHS:
            factors.append(ContributingFactor(
                factor_name="brake_service_gap", impact=0.2, direction="negative",
                description="No brake-related service on record within the last 24 months."
            ))

    # 2. Self-report credit cap rule:
    # trusted = health score computed from oem_api and inspection records only.
    # all = score computed from all accepted records (score_all).
    # If no trusted record exists: final = min(all, 85).
    # Otherwise: final = min(all, max(trusted, 85)).
    # health_confidence is unverified_self_reported when final > trusted (or no trusted record exists), otherwise verified.
    trusted_events = [e for e in events if e.source in TRUSTED_SOURCES]
    if not trusted_events:
        final_score = min(score_all, SELF_REPORT_CREDIT_CAP)
        confidence = "unverified_self_reported"
        if final_score < score_all:
            factors.append(ContributingFactor(
                factor_name="unverified_source_cap", impact=0.1, direction="negative",
                description="Score capped: all service records are self-reported."
            ))
    else:
        score_trusted, _, _, _, _ = _evaluate_event_set(
            trusted_events, request.vehicle_age_months, recall_penalty
        )
        final_score = min(score_all, max(score_trusted, SELF_REPORT_CREDIT_CAP))
        if final_score > score_trusted:
            confidence = "unverified_self_reported"
        else:
            confidence = "verified"

        if final_score < score_all:
            factors.append(ContributingFactor(
                factor_name="unverified_source_cap", impact=0.1, direction="negative",
                description="Score capped: self-reported records cannot lift score above 85 or above trusted baseline."
            ))

    return HealthResult(
        status="scored", score=final_score, confidence=confidence, factors=factors,
        flags=flags, has_open_recall=has_open_recall, overdue=overdue,
        needs_brake=needs_brake, maintenance_score=final_score,
    )



def generate_recommendation(h: HealthResult) -> str:
    if h.status == "insufficient_data":
        text = "Schedule a baseline inspection to begin health tracking for this vehicle."
    else:
        items = []
        if h.overdue:
            items.append("a full service")
        if h.needs_brake:
            items.append("a brake inspection")
        joined = " and ".join(items)
        if h.score >= 85:
            text = "Eligible for standard renewal - vehicle health profile is strong."
        elif h.score >= 60:
            target = joined or "a routine service check"
            text = (f"Eligible for renewal - consider scheduling {target} "
                    "before your next service interval.")
        else:
            target = joined or "a full vehicle health check"
            text = ("Eligible for renewal - vehicle health profile shows meaningful gaps. "
                    f"We recommend {target} before renewal.")
        if h.confidence == "unverified_self_reported":
            text += (" Based on self-reported records only; we recommend confirming your next "
                     "service through our partner network for a fully verified score.")
    if h.has_open_recall:
        text = "Open recall on file \u2014 schedule service immediately. " + text
    return text


def calculate_maintenance_score(request: ScoreRequest) -> Tuple[float, List[ContributingFactor]]:
    h = calculate_vehicle_health(request)
    return h.maintenance_score, h.factors

def calculate_usage_score(request: ScoreRequest) -> Tuple[Optional[float], List[ContributingFactor]]:
    trips = request.trip_aggregates
    if not trips:
        return None, []

    score = 100.0
    factors = []
        
    total_trips = len(trips)
    total_harsh_brakes = sum(t.harsh_braking_count for t in trips)
    total_hard_accels = sum(t.hard_acceleration_count or 0 for t in trips)
    
    # Primary factor: harsh braking and hard acceleration frequency
    braking_freq = total_harsh_brakes / total_trips
    accel_freq = total_hard_accels / total_trips
    
    if braking_freq > 0.5:
        score -= 15.0
        factors.append(ContributingFactor(
            factor_name="high_harsh_braking",
            impact=0.4,
            direction="negative",
            description="Frequent harsh braking detected."
        ))
    elif braking_freq == 0:
        factors.append(ContributingFactor(
            factor_name="smooth_braking",
            impact=0.1,
            direction="positive",
            description="Smooth braking profile."
        ))
        
    if accel_freq > 0.5:
        score -= 10.0
        factors.append(ContributingFactor(
            factor_name="hard_acceleration",
            impact=0.3,
            direction="negative",
            description="Frequent hard acceleration detected."
        ))

    return max(0.0, score), factors

def generate_score(vehicle_id: str, request: ScoreRequest) -> dict:
    health = calculate_vehicle_health(request)
    m_score, m_factors = health.maintenance_score, health.factors
    u_score, u_factors = calculate_usage_score(request)
    
    factors = m_factors + u_factors
    flags = list(health.flags)
    if u_score is None:
        flags.append("usage_data_unavailable")
    
    # Ensure there's at least one contributing factor if both lists are empty
    if not factors:
        factors.append(ContributingFactor(
            factor_name="insufficient_data",
            impact=0.0,
            direction="neutral",
            description="Not enough data to determine specific risk factors."
        ))
        
    # Normalize impacts to sum to ~1.0 if there are factors with non-zero impact
    total_impact = sum(f.impact for f in factors)
    if total_impact > 0:
        for f in factors:
            f.impact = round(f.impact / total_impact, 2)
            
    # composite_score is null unless both component scores exist
    composite = None if (m_score is None or u_score is None) else (m_score * 0.4) + (u_score * 0.6)
    
    result = {
        "score_id": str(uuid.uuid4()),
        "vehicle_id": vehicle_id,
        "usage_score": None if u_score is None else round(u_score, 2),
        "maintenance_score": None if m_score is None else round(m_score, 2),
        "composite_score": None if composite is None else round(composite, 2),
        "computed_at": datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z'),
        "model_version": MODEL_VERSION,
        "contributing_factors": [f.model_dump() for f in factors],
        "health_status": health.status,
        "vehicle_health_score": None if health.score is None else round(health.score, 2),
        "renewal_recommendation": generate_recommendation(health),
        "has_open_recall": health.has_open_recall,
        "data_flags": flags,
    }
    # health_confidence is only defined when a score exists (schema enum has no null).
    if health.confidence is not None:
        result["health_confidence"] = health.confidence
    return result

