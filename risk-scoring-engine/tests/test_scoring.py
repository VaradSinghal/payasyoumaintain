from datetime import date, timedelta
from app.models import (
    ScoreRequest, ServiceTimeline, MaintenanceEvent,
    TripAggregate, RecallStatus, RecallDetail
)
from app.scoring import generate_score

# ── Shared helpers ────────────────────────────────────────────────────────────

def make_recall_status(vehicle_id: str, has_open: bool, recalls: list[RecallDetail] | None = None) -> RecallStatus:
    details = recalls or []
    return RecallStatus(
        vehicle_id=vehicle_id,
        has_open_recall=has_open,
        recall_count=len(details),
        recall_details=details,
        source="oem_recall_db",
        checked_at="2026-09-22T10:00:00Z"
    )

def make_trips(vehicle_id: str, *, n: int, harsh_braking: int, hard_accel: int) -> list[TripAggregate]:
    return [
        TripAggregate(
            trip_id=f"t{i}",
            vehicle_id=vehicle_id,
            event_count=100,
            avg_speed_kmh=50.0,
            max_speed_kmh=90.0,
            harsh_braking_count=harsh_braking,
            hard_acceleration_count=hard_accel,
            distance_km=20.0,
            trip_start="2026-09-20T08:00:00Z",
            trip_end="2026-09-20T08:30:00Z"
        ) for i in range(n)
    ]

def make_timeline(vehicle_id: str, days_ago: int, service_type: str = "oil_change",
                  notes: str | None = None) -> ServiceTimeline:
    return ServiceTimeline(
        vehicle_id=vehicle_id,
        total_services=1,
        events=[
            MaintenanceEvent(
                event_id="e1",
                vehicle_id=vehicle_id,
                service_date=date.today() - timedelta(days=days_ago),
                service_type=service_type,
                odometer_km=25000,
                source="oem_api",
                notes=notes
            )
        ]
    )

# ── Profile 1: Clearly good ───────────────────────────────────────────────────

def test_clearly_good_profile():
    """Recent service, smooth driving, no open recall → perfect scores."""
    vid = "v-good"

    request = ScoreRequest(
        service_timeline=make_timeline(vid, days_ago=30),
        trip_aggregates=make_trips(vid, n=10, harsh_braking=0, hard_accel=0),
        recall_status=make_recall_status(vid, has_open=False)
    )

    score = generate_score(vid, request)

    assert score["maintenance_score"] == 100.0
    assert score["usage_score"] == 100.0
    assert score["composite_score"] == 100.0

    factors = [f["factor_name"] for f in score["contributing_factors"]]
    assert "regular_service" in factors
    assert "smooth_braking" in factors
    assert "open_recall" not in factors


# ── Profile 2: Clearly bad ────────────────────────────────────────────────────

def test_clearly_bad_profile():
    """
    >2 years overdue + open recall + high harsh braking + hard accel
    → maintenance score < 50, usage score < 80.
    """
    vid = "v-bad"

    request = ScoreRequest(
        service_timeline=make_timeline(vid, days_ago=800),   # > 2 years
        trip_aggregates=make_trips(vid, n=10, harsh_braking=3, hard_accel=2),
        recall_status=make_recall_status(vid, has_open=True, recalls=[
            RecallDetail(
                recall_id="RC-2026-0042",
                description="Battery management module thermal runaway risk.",
                issued_date="2026-03-15"
            )
        ])
    )

    score = generate_score(vid, request)

    assert score["maintenance_score"] < 50.0
    assert score["usage_score"] < 80.0

    factors = [f["factor_name"] for f in score["contributing_factors"]]
    assert "overdue_service" in factors
    assert "open_recall" in factors
    assert "high_harsh_braking" in factors
    assert "hard_acceleration" in factors


# ── Profile 3: Borderline ─────────────────────────────────────────────────────

def test_borderline_profile():
    """
    Slightly overdue (~35 days past), braking freq just above threshold, no recall
    → maintenance 90–100, usage 80–90.
    """
    vid = "v-border"

    # 6 of 10 trips have harsh braking (freq = 0.6 > threshold 0.5)
    trips = []
    for i in range(10):
        trips.append(TripAggregate(
            trip_id=f"t{i}",
            vehicle_id=vid,
            event_count=100,
            avg_speed_kmh=50.0,
            max_speed_kmh=90.0,
            harsh_braking_count=1 if i < 6 else 0,
            hard_acceleration_count=0,
            distance_km=20.0,
            trip_start="2026-09-20T08:00:00Z",
            trip_end="2026-09-20T08:30:00Z"
        ))

    request = ScoreRequest(
        service_timeline=make_timeline(vid, days_ago=400),  # ~35 days past 12-month mark
        trip_aggregates=trips,
        recall_status=make_recall_status(vid, has_open=False)
    )

    score = generate_score(vid, request)

    assert 90.0 < score["maintenance_score"] < 100.0
    assert 80.0 < score["usage_score"] < 90.0

    factors = [f["factor_name"] for f in score["contributing_factors"]]
    assert "overdue_service" in factors
    assert "high_harsh_braking" in factors
    assert "open_recall" not in factors


# ── Profile 4: Recall field is authoritative, notes text is irrelevant ────────

def test_recall_from_field_not_notes():
    """
    Proves that has_open_recall: true triggers the recall penalty regardless of
    what the notes field says — and that notes text containing 'OPEN RECALL'
    alone (with has_open_recall: false) does NOT trigger the penalty.
    """
    vid = "v-recall-test"

    # Case A: has_open_recall=True, notes contains nothing about recalls
    #         → penalty MUST fire
    request_a = ScoreRequest(
        service_timeline=make_timeline(
            vid,
            days_ago=30,
            notes="Routine inspection. Everything looks fine."  # NO recall text
        ),
        trip_aggregates=make_trips(vid, n=5, harsh_braking=0, hard_accel=0),
        recall_status=make_recall_status(vid, has_open=True, recalls=[
            RecallDetail(
                recall_id="RC-TEST-001",
                description="Airbag inflator defect.",
                issued_date="2026-01-01"
            )
        ])
    )

    score_a = generate_score(vid, request_a)
    factors_a = [f["factor_name"] for f in score_a["contributing_factors"]]

    assert "open_recall" in factors_a, (
        "has_open_recall=True must trigger the recall penalty "
        "regardless of notes content"
    )
    assert score_a["maintenance_score"] < 100.0

    # Case B: has_open_recall=False, notes deliberately contains "OPEN RECALL"
    #         → penalty MUST NOT fire
    request_b = ScoreRequest(
        service_timeline=make_timeline(
            vid,
            days_ago=30,
            notes="OPEN RECALL flagged last month but now resolved."  # stale text
        ),
        trip_aggregates=make_trips(vid, n=5, harsh_braking=0, hard_accel=0),
        recall_status=make_recall_status(vid, has_open=False)
    )

    score_b = generate_score(vid, request_b)
    factors_b = [f["factor_name"] for f in score_b["contributing_factors"]]

    assert "open_recall" not in factors_b, (
        "has_open_recall=False must suppress the recall penalty "
        "even when notes text contains 'OPEN RECALL'"
    )
    assert score_b["maintenance_score"] == 100.0
