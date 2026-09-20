from datetime import date, timedelta
from app.models import ScoreRequest, ServiceTimeline, MaintenanceEvent, TripAggregate
from app.scoring import generate_score

def test_clearly_good_profile():
    # Good profile: recent service, smooth driving (0 harsh braking/accel), moderate speed
    today = date.today()
    
    timeline = ServiceTimeline(
        vehicle_id="v-good",
        total_services=2,
        events=[
            MaintenanceEvent(
                event_id="e1",
                vehicle_id="v-good",
                service_date=today - timedelta(days=30),  # very recent
                service_type="oil_change",
                odometer_km=15000,
                source="oem_api"
            )
        ]
    )
    
    trips = [
        TripAggregate(
            trip_id=f"t{i}",
            vehicle_id="v-good",
            event_count=100,
            avg_speed_kmh=45.0,
            max_speed_kmh=60.0,
            harsh_braking_count=0,
            hard_acceleration_count=0,
            distance_km=15.0,
            trip_start="2026-09-20T08:00:00Z",
            trip_end="2026-09-20T08:30:00Z"
        ) for i in range(10)
    ]
    
    request = ScoreRequest(service_timeline=timeline, trip_aggregates=trips)
    score = generate_score("v-good", request)
    
    assert score["maintenance_score"] == 100.0
    assert score["usage_score"] == 100.0
    assert score["composite_score"] == 100.0
    
    factors = [f["factor_name"] for f in score["contributing_factors"]]
    assert "regular_service" in factors
    assert "smooth_braking" in factors

def test_clearly_bad_profile():
    # Bad profile: >2 years overdue service + open recall, high harsh braking & accel, high speed
    today = date.today()
    
    timeline = ServiceTimeline(
        vehicle_id="v-bad",
        total_services=1,
        events=[
            MaintenanceEvent(
                event_id="e1",
                vehicle_id="v-bad",
                service_date=today - timedelta(days=800), # > 2 years
                service_type="inspection",
                odometer_km=30000,
                source="inspection",
                notes="OPEN RECALL: airbag module"
            )
        ]
    )
    
    # 10 trips, all with harsh braking and hard accel
    trips = [
        TripAggregate(
            trip_id=f"t{i}",
            vehicle_id="v-bad",
            event_count=100,
            avg_speed_kmh=85.0, # high avg speed
            max_speed_kmh=140.0,
            harsh_braking_count=3,
            hard_acceleration_count=2,
            distance_km=25.0,
            trip_start="2026-09-20T08:00:00Z",
            trip_end="2026-09-20T08:30:00Z"
        ) for i in range(10)
    ]
    
    request = ScoreRequest(service_timeline=timeline, trip_aggregates=trips)
    score = generate_score("v-bad", request)
    
    assert score["maintenance_score"] < 50.0  # Heavy penalties applied
    assert score["usage_score"] < 80.0
    
    factors = [f["factor_name"] for f in score["contributing_factors"]]
    assert "overdue_service" in factors
    assert "open_recall" in factors
    assert "high_harsh_braking" in factors
    assert "hard_acceleration" in factors
    assert "high_average_speed" in factors

def test_borderline_profile():
    # Borderline profile: service is just slightly overdue (~13 months), 
    # driving has some harsh braking but not excessive, moderate speed.
    today = date.today()
    
    timeline = ServiceTimeline(
        vehicle_id="v-border",
        total_services=3,
        events=[
            MaintenanceEvent(
                event_id="e1",
                vehicle_id="v-border",
                service_date=today - timedelta(days=400), # ~35 days overdue
                service_type="oil_change",
                odometer_km=45000,
                source="oem_api"
            )
        ]
    )
    
    # 10 trips, 6 have harsh braking (freq > 0.5)
    trips = []
    for i in range(10):
        harsh_braking = 1 if i < 6 else 0 
        trips.append(
            TripAggregate(
                trip_id=f"t{i}",
                vehicle_id="v-border",
                event_count=100,
                avg_speed_kmh=50.0,
                max_speed_kmh=90.0,
                harsh_braking_count=harsh_braking,
                hard_acceleration_count=0,
                distance_km=20.0,
                trip_start="2026-09-20T08:00:00Z",
                trip_end="2026-09-20T08:30:00Z"
            )
        )
        
    request = ScoreRequest(service_timeline=timeline, trip_aggregates=trips)
    score = generate_score("v-border", request)
    
    # Not terrible, but not perfect
    assert 90.0 < score["maintenance_score"] < 100.0 # Small penalty for 35 days
    assert 80.0 < score["usage_score"] < 90.0 # Penalty for braking freq > 0.5
    
    factors = [f["factor_name"] for f in score["contributing_factors"]]
    assert "overdue_service" in factors
    assert "high_harsh_braking" in factors
    assert "high_average_speed" not in factors # Speed penalty avoided
