from typing import List, Optional
from pydantic import BaseModel, ConfigDict
from datetime import date

# --- Maintenance Models ---

class PartReplaced(BaseModel):
    part_name: str
    part_number: Optional[str] = None
    quantity: int = 1

class MaintenanceEvent(BaseModel):
    event_id: str
    vehicle_id: str
    service_date: date
    service_type: str
    odometer_km: int
    parts_replaced: List[PartReplaced] = []
    source: str
    notes: Optional[str] = None          # Free-text technician/customer comment only.
    document_refs: List[str] = []        # Notes are NEVER parsed for recall status.

class ServiceTimeline(BaseModel):
    vehicle_id: str
    total_services: int
    latest_service_date: Optional[str] = None
    latest_odometer_km: Optional[int] = None
    events: List[MaintenanceEvent] = []

# --- Recall Models (recall-status.schema.json) ---

class RecallDetail(BaseModel):
    recall_id: str
    description: str
    issued_date: str

class RecallStatus(BaseModel):
    vehicle_id: str
    has_open_recall: bool
    recall_count: int
    recall_details: List[RecallDetail] = []
    source: str
    checked_at: str

# --- Telematics Models ---

class TripAggregate(BaseModel):
    trip_id: str
    vehicle_id: str
    event_count: int
    avg_speed_kmh: float
    max_speed_kmh: float
    harsh_braking_count: int
    hard_acceleration_count: Optional[int] = 0  # Added for future compatibility, defaults to 0
    distance_km: float
    trip_start: str
    trip_end: str

# --- Request/Response Models ---

class ScoreRequest(BaseModel):
    service_timeline: Optional[ServiceTimeline] = None
    trip_aggregates: List[TripAggregate] = []
    recall_status: Optional[RecallStatus] = None  # Structured recall input per recall-status.schema.json

class ContributingFactor(BaseModel):
    factor_name: str
    impact: float
    direction: str
    description: Optional[str] = None

class ScoreResponse(BaseModel):
    model_config = ConfigDict(protected_namespaces=())
    score_id: str
    vehicle_id: str
    usage_score: float
    maintenance_score: float
    composite_score: float
    computed_at: str
    model_version: str
    contributing_factors: List[ContributingFactor]
