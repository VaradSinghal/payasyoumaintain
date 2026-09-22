import uuid
from datetime import datetime, date, timezone
from typing import Tuple, List
from .models import ScoreRequest, ContributingFactor, RecallStatus

MODEL_VERSION = "v1.0.0-stub"

def _days_since(d: date) -> int:
    return (date.today() - d).days

def calculate_maintenance_score(request: ScoreRequest) -> Tuple[float, List[ContributingFactor]]:
    score = 100.0
    factors = []
    
    timeline = request.service_timeline
    if not timeline or not timeline.events:
        return score, factors
    
    latest_event = sorted(timeline.events, key=lambda e: e.service_date, reverse=True)[0]
    days_since_service = _days_since(latest_event.service_date)
    
    # Check for overdue service (assume > 12 months is overdue)
    if days_since_service > 365:
        penalty = min(40.0, (days_since_service - 365) * 0.1)
        score -= penalty
        factors.append(ContributingFactor(
            factor_name="overdue_service",
            impact=0.4,
            direction="negative",
            description=f"Service is overdue by {days_since_service - 365} days."
        ))
    else:
        factors.append(ContributingFactor(
            factor_name="regular_service",
            impact=0.2,
            direction="positive",
            description="Vehicle is being serviced regularly."
        ))
        
    # Check for open recalls via structured recall_status field.
    # Notes text is NEVER read for this — recall_status.has_open_recall is the sole source.
    if request.recall_status is not None and request.recall_status.has_open_recall:
        count = request.recall_status.recall_count
        score -= 30.0
        factors.append(ContributingFactor(
            factor_name="open_recall",
            impact=0.3,
            direction="negative",
            description=f"Vehicle has {count} open manufacturer recall(s)."
        ))

    return max(0.0, score), factors

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
    m_score, m_factors = calculate_maintenance_score(request)
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
    
    return {
        "score_id": str(uuid.uuid4()),
        "vehicle_id": vehicle_id,
        "usage_score": round(u_score, 2),
        "maintenance_score": round(m_score, 2),
        "composite_score": round(composite, 2),
        "computed_at": datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z'),
        "model_version": MODEL_VERSION,
        "contributing_factors": [f.model_dump() for f in factors]
    }
