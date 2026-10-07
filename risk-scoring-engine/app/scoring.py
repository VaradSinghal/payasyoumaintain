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
UNVERIFIED_SCORE_CAP = 85.0
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
    has_open_recall: bool = False
    overdue: bool = False
    needs_brake: bool = False
    # Numeric maintenance component fed to the composite.
    maintenance_score: float = 100.0


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


def calculate_vehicle_health(request: ScoreRequest) -> HealthResult:
    """Deterministic Vehicle Health Score per the Vehicle Health Scoring Specification."""
    factors: List[ContributingFactor] = []
    flags: List[str] = []

    # Recall handling. Notes text is NEVER read — recall_status is the sole source.
    rs = request.recall_status
    recall_unavailable = rs is not None and rs.availability == "unavailable"
    has_open_recall = bool(rs is not None and not recall_unavailable and rs.has_open_recall)
    if recall_unavailable:
        flags.append("recall_status_unavailable")

    recall_penalty = 0.0
    if has_open_recall:
        recall_penalty = RECALL_PENALTY
        factors.append(ContributingFactor(
            factor_name="open_recall",
            impact=0.3,
            direction="negative",
            description=f"Vehicle has {rs.recall_count} open manufacturer recall(s)."
        ))

    timeline = request.service_timeline

    # Cold start: timeline omitted entirely -> neutral maintenance score (unchanged
    # behaviour), minus a structured open recall if one is known.
    if timeline is None:
        return HealthResult(
            status="insufficient_data", score=None, confidence=None,
            factors=factors, flags=flags, has_open_recall=has_open_recall,
            maintenance_score=max(0.0, 100.0 - recall_penalty),
        )

    # Timeline provided but empty -> Insufficient Data; component not scored.
    if not timeline.events:
        return HealthResult(
            status="insufficient_data", score=None, confidence=None,
            factors=factors, flags=flags, has_open_recall=has_open_recall,
            maintenance_score=max(0.0, 100.0 - recall_penalty),
        )

    events, conflict = _resolve_conflicts(timeline.events)
    if conflict:
        flags.append("conflicting_records_detected")

    # 1. Service recency
    latest = max(events, key=lambda e: e.service_date)
    days = _days_since(latest.service_date)
    recency_penalty = _recency_penalty(days)
    overdue = recency_penalty > 0
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

    # 2. Safety-critical (brake) gap
    brake_recent = any(
        e.service_type in BRAKE_SERVICE_TYPES and _days_since(e.service_date) <= BRAKE_WINDOW_DAYS
        for e in events
    )
    gap_penalty = 0.0
    needs_brake = False
    age = request.vehicle_age_months
    if not brake_recent:
        if age is None:
            # Cannot establish the vehicle is 24+ months old; do not penalise.
            flags.append("vehicle_age_unknown")
        elif age >= BRAKE_MIN_VEHICLE_AGE_MONTHS:
            gap_penalty = BRAKE_GAP_PENALTY
            needs_brake = True
            factors.append(ContributingFactor(
                factor_name="brake_service_gap", impact=0.2, direction="negative",
                description="No brake-related service on record within the last 24 months."
            ))

    score = max(0.0, 100.0 - recency_penalty - recall_penalty - gap_penalty)

    # 3. Source confidence rule
    confidence = "verified"
    if all(e.source == SELF_UPLOAD for e in events):
        confidence = "unverified_self_reported"
        if score > UNVERIFIED_SCORE_CAP:
            score = UNVERIFIED_SCORE_CAP
            factors.append(ContributingFactor(
                factor_name="unverified_source_cap", impact=0.1, direction="negative",
                description="Score capped: all service records are self-reported."
            ))

    return HealthResult(
        status="scored", score=score, confidence=confidence, factors=factors,
        flags=flags, has_open_recall=has_open_recall, overdue=overdue,
        needs_brake=needs_brake, maintenance_score=score,
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

def calculate_usage_score(request: ScoreRequest) -> Tuple[float, List[ContributingFactor]]:
    score = 100.0
    factors = []
    
    trips = request.trip_aggregates
    if not trips:
        return score, factors
        
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
            
    composite = (m_score * 0.4) + (u_score * 0.6)
    
    result = {
        "score_id": str(uuid.uuid4()),
        "vehicle_id": vehicle_id,
        "usage_score": round(u_score, 2),
        "maintenance_score": round(m_score, 2),
        "composite_score": round(composite, 2),
        "computed_at": datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z'),
        "model_version": MODEL_VERSION,
        "contributing_factors": [f.model_dump() for f in factors],
        "health_status": health.status,
        "vehicle_health_score": None if health.score is None else round(health.score, 2),
        "renewal_recommendation": generate_recommendation(health),
        "has_open_recall": health.has_open_recall,
        "data_flags": health.flags,
    }
    # health_confidence is only defined when a score exists (schema enum has no null).
    if health.confidence is not None:
        result["health_confidence"] = health.confidence
    return result

