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

    assert score["maintenance_score"] == 90.0   # 400 days -> 366-545 band -> 10 penalty
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


# ── Profile 5: Cold-start (brand new, no data) ────────────────────────────────

def test_cold_start_profile():
    """
    Brand new policyholder: no service timeline, no trips, no recall status.
    Must return null maintenance/composite scores (nothing to score), a neutral
    usage score, and a neutral 'insufficient_data' factor.
    """
    vid = "v-new"

    request = ScoreRequest()
    score = generate_score(vid, request)

    assert score["health_status"] == "insufficient_data"
    assert score["maintenance_score"] is None
    assert score["usage_score"] == 100.0
    assert score["composite_score"] is None

    factors = [f["factor_name"] for f in score["contributing_factors"]]
    assert "insufficient_data" in factors
    assert len(factors) == 1

def test_cold_start_with_recall():
    """
    Brand new policyholder with NO service timeline, but OEM DB indicates an
    open recall. The maintenance component is not scored (null), but the recall
    is still surfaced independently.
    """
    vid = "v-new-recall"
    
    request = ScoreRequest(
        recall_status=make_recall_status(vid, has_open=True, recalls=[
            RecallDetail(recall_id="RC-123", description="Fix", issued_date="2026")
        ])
    )
    score = generate_score(vid, request)
    
    assert score["maintenance_score"] is None
    assert score["composite_score"] is None
    assert score["has_open_recall"] is True
    assert score["usage_score"] == 100.0
    
    factors = [f["factor_name"] for f in score["contributing_factors"]]
    assert "open_recall" in factors


# ══ Vehicle Health Scoring Specification — scenarios ═════════════════════════

def ev(eid: str, days_ago: int, service_type: str, odometer: int, source: str,
       vid: str = "v-spec") -> MaintenanceEvent:
    return MaintenanceEvent(
        event_id=eid, vehicle_id=vid,
        service_date=date.today() - timedelta(days=days_ago),
        service_type=service_type, odometer_km=odometer, source=source,
    )

def tl(events: list[MaintenanceEvent], vid: str = "v-spec") -> ServiceTimeline:
    return ServiceTimeline(vehicle_id=vid, total_services=len(events), events=events)

def unavailable_recall(vid: str = "v-spec") -> RecallStatus:
    return RecallStatus(vehicle_id=vid, availability="unavailable")


def test_spec_6_1_normal():
    req = ScoreRequest(
        vehicle_age_months=36,
        service_timeline=tl([
            ev("e1", 200, "oil_change", 30000, "oem_api"),
            ev("e2", 300, "brake_service", 25000, "oem_api"),   # ~10 months ago
        ]),
        recall_status=make_recall_status("v-spec", has_open=False),
    )
    s = generate_score("v-spec", req)
    assert s["health_status"] == "scored"
    assert s["vehicle_health_score"] == 100.0
    assert s["maintenance_score"] == 100.0
    assert s["health_confidence"] == "verified"
    assert s["has_open_recall"] is False
    assert s["data_flags"] == []
    assert s["renewal_recommendation"] == "Eligible for standard renewal - vehicle health profile is strong."


def test_spec_6_2_poor_maintenance():
    req = ScoreRequest(
        vehicle_age_months=60,
        service_timeline=tl([ev("e1", 800, "oil_change", 50000, "oem_api")]),
        recall_status=make_recall_status("v-spec", has_open=True, recalls=[
            RecallDetail(recall_id="RC-1", description="x", issued_date="2026-01-01")]),
    )
    s = generate_score("v-spec", req)
    assert s["vehicle_health_score"] == 15.0   # 100 - 40 - 30 - 15
    assert s["health_confidence"] == "verified"   # low score is earned, not a confidence artifact
    assert s["has_open_recall"] is True
    rec = s["renewal_recommendation"]
    assert rec.startswith("Open recall on file \u2014 schedule service immediately.")
    assert "We recommend a full service and a brake inspection before renewal." in rec
    factors = [f["factor_name"] for f in s["contributing_factors"]]
    assert {"overdue_service", "open_recall", "brake_service_gap"} <= set(factors)


def test_spec_6_3_conflicting_data():
    req = ScoreRequest(
        vehicle_age_months=60,
        service_timeline=tl([
            ev("A", 100, "full_service", 42000, "oem_api"),
            ev("B", 95, "full_service", 38500, "self_upload"),   # 3,500 km *backwards* in 5 days
        ]),
        recall_status=make_recall_status("v-spec", has_open=False),
    )
    s = generate_score("v-spec", req)
    assert s["vehicle_health_score"] == 100.0
    assert "conflicting_records_detected" in s["data_flags"]
    # The self_upload record was dropped, so only oem_api contributes -> verified.
    assert s["health_confidence"] == "verified"


def test_spec_6_4a_incomplete_zero_events():
    req = ScoreRequest(
        vehicle_age_months=12,
        service_timeline=tl([]),
        recall_status=make_recall_status("v-spec", has_open=False),
    )
    s = generate_score("v-spec", req)
    assert s["health_status"] == "insufficient_data"
    assert s["vehicle_health_score"] is None
    assert s["maintenance_score"] is None
    assert s["composite_score"] is None
    assert s["has_open_recall"] is False   # recall source was available and confirmed none
    assert "health_confidence" not in s
    assert s["renewal_recommendation"].startswith("Schedule a baseline inspection")


def test_spec_6_4b_incomplete_self_upload_recall_unavailable():
    req = ScoreRequest(
        vehicle_age_months=36,
        service_timeline=tl([ev("e1", 60, "tyre_rotation", 20000, "self_upload")]),
        recall_status=unavailable_recall(),
    )
    s = generate_score("v-spec", req)
    assert s["vehicle_health_score"] == 85.0   # 0 + recall excluded + 15 gap
    assert s["health_confidence"] == "unverified_self_reported"
    assert "recall_status_unavailable" in s["data_flags"]
    assert s["has_open_recall"] is None   # unknown, not collapsed to False
    assert s["maintenance_score"] == 85.0
    assert s["composite_score"] is not None   # maintenance is scored here, so composite exists
    assert "self-reported" in s["renewal_recommendation"]


# ── Rule-level tests ──

def _score_days(days: int) -> float:
    req = ScoreRequest(
        vehicle_age_months=12,
        service_timeline=tl([ev("e1", days, "full_service", 1000, "oem_api")]),
    )
    return generate_score("v-spec", req)["vehicle_health_score"]

def test_recency_bands_exact():
    assert _score_days(365) == 100.0
    assert _score_days(366) == 90.0
    assert _score_days(545) == 90.0
    assert _score_days(546) == 75.0
    assert _score_days(730) == 75.0
    assert _score_days(731) == 60.0

def test_brake_gap_skipped_for_young_vehicle_and_applied_for_old():
    base = [ev("e1", 30, "oil_change", 1000, "oem_api")]
    young = generate_score("v", ScoreRequest(vehicle_age_months=23, service_timeline=tl(base)))
    old = generate_score("v", ScoreRequest(vehicle_age_months=24, service_timeline=tl(base)))
    assert young["vehicle_health_score"] == 100.0
    assert old["vehicle_health_score"] == 85.0

def test_self_upload_only_is_capped_at_85():
    req = ScoreRequest(
        vehicle_age_months=12,
        service_timeline=tl([ev("e1", 10, "brake_service", 1000, "self_upload")]),
    )
    s = generate_score("v", req)
    assert s["vehicle_health_score"] == 85.0
    assert s["health_confidence"] == "unverified_self_reported"

def test_recall_unavailable_is_not_defaulted_to_zero_penalty_flag_only():
    # Omitted recall_status: no flag. Explicit unavailable: flagged, still no penalty.
    omitted = generate_score("v", ScoreRequest(
        vehicle_age_months=12,
        service_timeline=tl([ev("e1", 10, "full_service", 1000, "oem_api")])))
    unavail = generate_score("v", ScoreRequest(
        vehicle_age_months=12, recall_status=unavailable_recall("v"),
        service_timeline=tl([ev("e1", 10, "full_service", 1000, "oem_api")])))
    assert "recall_status_unavailable" not in omitted["data_flags"]
    assert "recall_status_unavailable" in unavail["data_flags"]

def test_open_recall_line_prepended_regardless_of_band():
    req = ScoreRequest(
        vehicle_age_months=12,
        service_timeline=tl([ev("e1", 10, "full_service", 1000, "oem_api")]),
        recall_status=make_recall_status("v", has_open=True),
    )
    s = generate_score("v", req)
    assert s["vehicle_health_score"] == 70.0
    assert s["renewal_recommendation"].startswith("Open recall on file \u2014 schedule service immediately.")

def test_has_open_recall_is_null_not_false_when_unavailable():
    for timeline in (None, tl([ev("e1", 10, "full_service", 1000, "oem_api")])):
        s = generate_score("v", ScoreRequest(
            vehicle_age_months=12, service_timeline=timeline,
            recall_status=unavailable_recall("v")))
        assert s["has_open_recall"] is None
        assert "recall_status_unavailable" in s["data_flags"]
    # Confirmed 'no recall' remains a distinct, concrete False.
    confirmed = generate_score("v", ScoreRequest(
        recall_status=make_recall_status("v", has_open=False)))
    assert confirmed["has_open_recall"] is False

def test_composite_is_null_when_maintenance_score_is_null():
    # Usage data is present and good, but must NOT be blended into a usage-only composite.
    s = generate_score("v", ScoreRequest(
        service_timeline=tl([]),
        trip_aggregates=make_trips("v", n=5, harsh_braking=0, hard_accel=0)))
    assert s["usage_score"] == 100.0
    assert s["maintenance_score"] is None
    assert s["composite_score"] is None


