# Pay As You Maintain

**A usage-based insurance (UBI) platform that dynamically prices vehicle policies based on real-time driving behaviour and maintenance adherence.**

> Submitted to **Guidewire Software** as a technical portfolio project.  
> Author: Varad Singhal · GitHub: [VaradSinghal/payasyoumaintain](https://github.com/VaradSinghal/payasyoumaintain)

---

## Overview

Pay As You Maintain (PAYM) is a cloud-native, microservices-based insurance pricing platform that moves beyond static actuarial tables. Instead of pricing risk solely on historical claims, PAYM continuously adjusts premiums using two dynamic signals:

1. **Driving behaviour** — harsh braking and hard acceleration captured via OBD-II / telematics hardware.
2. **Maintenance adherence** — verified service records from OEM APIs, self-uploaded receipts, and inspection records, cross-referenced against a live manufacturer recall database.

The result is a premium that rewards responsible drivers and well-maintained vehicles, and flags risk early rather than after a claim.

---

## Architecture

PAYM is implemented as a polyglot microservices monorepo. Each service owns a single bounded context and communicates via REST. A shared `/contracts` directory acts as the single source of truth for all inter-service data schemas (JSON Schema Draft 2020-12), enforced at every service boundary.

```
                          ┌────────────────────────────────────────────────┐
                          │              Pay As You Maintain               │
                          └────────────────────────────────────────────────┘

  ┌──────────────────┐    ┌──────────────────────┐    ┌────────────────────────────────┐
  │ identity-consent │    │telematics-usage-      │    │maintenance-vehicle-health-      │
  │   (port 8081)    │    │ingestion (port 8082)  │    │ingestion (port 8083)            │
  │                  │    │                      │    │                                │
  │ • KYC stub       │    │ • Batch trip ingest  │    │ • Structured event ingest      │
  │ • Consent mgmt   │    │ • Time-gap segment.  │    │ • OCR stub for self-upload     │
  │ • Default opt-out│    │ • Haversine distance │    │ • Service timeline API         │
  └──────────────────┘    │ • In-memory store    │    │ • RecallStore (OEM recall DB)  │
                          └──────────┬───────────┘    └────────────┬───────────────────┘
                                     │                             │
                                     └──────────────┬──────────────┘
                                                    │
                                          ┌─────────▼──────────┐
                                          │  risk-scoring-     │
                                          │  engine (8084)     │
                                          │                    │
                                          │ • FastAPI/Python   │
                                          │ • Maintenance score│
                                          │ • Usage score      │
                                          │ • Recall penalty   │
                                          │ • Explainability   │
                                          │   (contributing_   │
                                          │    factors)        │
                                          └─────────┬──────────┘
                                                    │
                                          ┌─────────▼──────────┐
                                          │  pricing-policy    │
                                          │  (port 8085)       │
                                          │                    │
                                          │ • IDV depreciation │
                                          │ • NCB discount     │
                                          │ • Dynamic risk     │
                                          │   multiplier       │
                                          │ • Quote API        │
                                          └────────────────────┘
```

---

## Services

### ✅ `identity-consent` — Port 8081
**Stack:** Java 21 / Spring Boot 3.3

Manages vehicle owner identity, KYC verification, and data-sharing consent.

| Endpoint | Method | Description |
|---|---|---|
| `/api/v1/vehicles` | `POST` | Register a vehicle with owner details and RC number |
| `/api/v1/vehicles/{id}` | `GET` | Retrieve registration details |
| `/api/v1/vehicles/{id}/consent` | `GET` | Retrieve current consent record |
| `/api/v1/vehicles/{id}/consent` | `PUT` | Update opt-in / opt-out for usage and maintenance tracking |

**Key design decisions:**
- `KycService` is a Java interface. `MockKycService` (`VERIFIED` always) is the active bean; a real Aadhaar/PAN provider is a bean swap with zero controller changes.
- **Consent defaults to opted-out** for both usage and maintenance tracking — a hard compliance requirement. Static pricing applies until explicit opt-in.

---

### ✅ `telematics-usage-ingestion` — Port 8082
**Stack:** Java 21 / Spring Boot 3.3

Ingests raw OBD-II / telematics event streams, validates them against the shared schema, and computes per-trip aggregates.

| Endpoint | Method | Description |
|---|---|---|
| `/api/v1/events` | `POST` | Batch ingest trip events (partial-batch: per-event accept/reject) |
| `/api/v1/events/{vehicleId}` | `GET` | Retrieve raw events for a vehicle |
| `/api/v1/aggregates/{vehicleId}` | `GET` | Retrieve computed trip aggregates |
| `/api/v1/generate-synthetic` | `POST` | Seed demo data for 5 reference vehicles |

**Key design decisions:**
- **Time-gap auto-segmentation**: when `trip_id` is absent (e.g. offline sync uploading a full day), consecutive events > 5 minutes apart are split into separate trips, preventing nonsense aggregates (e.g. average speed diluted by hours of parking).
- **Haversine formula** for GPS distance calculation.
- **Partial-batch acceptance**: a batch with 9 valid and 1 invalid event accepts the 9 and reports the rejection — never drops the entire batch.

---

### ✅ `maintenance-vehicle-health-ingestion` — Port 8083
**Stack:** Java 21 / Spring Boot 3.3

Ingests and stores vehicle service records from multiple sources. Provides a per-vehicle service timeline and structured recall status.

| Endpoint | Method | Description |
|---|---|---|
| `/api/v1/events` | `POST` | Batch ingest maintenance events (OEM API style) |
| `/api/v1/events/self-upload` | `POST` | Self-upload path with OCR stub (echoes typed fields) |
| `/api/v1/timeline/{vehicleId}` | `GET` | Chronological service history |
| `/api/v1/events/{vehicleId}` | `GET` | Raw events for a vehicle |
| `/api/v1/recall-status/{vehicleId}` | `GET` | Structured open-recall status |
| `/api/v1/generate-synthetic` | `POST` | Seed demo data for 5 reference vehicles |

**Key design decisions:**
- **Recall status is a first-class structured resource** (`RecallStatus` model, `RecallStore` service). Free-text `notes` on maintenance events are for genuine unstructured comments only — recall data is never inferred from text.
- `OcrStubService` implements an interface; real document-AI integration is a Phase 4 bean swap.
- Five synthetic vehicle profiles for demoability: perfect maintainer, overdue, missed interval with open recall, mixed sources, new vehicle.

---

### ✅ `risk-scoring-engine` — Port 8084
**Stack:** Python 3.12 / FastAPI / Pydantic

Stateless scoring service that computes usage and maintenance risk scores for a vehicle given raw history data. Validates its output against the shared `score.schema.json` contract.

| Endpoint | Method | Description |
|---|---|---|
| `/score/{vehicleId}` | `POST` | Compute risk score from telematics and maintenance input |

**Request body (`ScoreRequest`):**
```json
{
  "service_timeline": { ... },    
  "trip_aggregates":  [ ... ],    
  "recall_status":    { ... }     
}
```

All three fields are optional. A completely empty request (cold-start) returns neutral scores of **100/100** — no penalty for a new policyholder with no history.

**Scoring rules:**

| Score | Base | Deductions |
|---|---|---|
| **Maintenance** | 100 | Up to −40 for overdue service (>12 months, scales with days); −30 for `has_open_recall: true` |
| **Usage** | 100 | −15 for harsh braking frequency > 0.5 events/trip; −10 for hard acceleration frequency > 0.5 events/trip |

> **Average speed is explicitly excluded** from the Usage Score to avoid penalising highway-heavy commuters relative to stop-start city drivers.

**Composite:** `(maintenance_score × 0.40) + (usage_score × 0.60)`

Each response includes a `contributing_factors` array with normalised impact weights (SHAP-style explainability), suitable for customer-facing disclosure and regulatory audit.

---

### ✅ `pricing-policy` — Port 8085
**Stack:** Java 21 / Spring Boot 3.3

Orchestrates three downstream calls (telematics, maintenance, scoring engine) and applies the full pricing formula to produce a `premium-response.schema.json`-compliant quote.

| Endpoint | Method | Description |
|---|---|---|
| `/api/v1/quote/{vehicleId}` | `POST` | Compute a personalised premium quote |

**Pricing formula:**

```
IDV           = ex_showroom_price × IRDAI_depreciation_slab(vehicle_age_years)
base_premium  = IDV × 2.6%                            (base OD rate)
od_premium    = base_premium × (1 − NCB_percentage)   (No Claim Bonus)
dynamic_mult  = 1.50 − 0.80 × (composite_score / 100) (linear: score 0→1.50×, score 100→0.70×)
final_premium = round(od_premium × dynamic_mult)
```

**Cold-start handling:** if the scoring engine is unavailable or returns no data, `dynamic_mult` is fixed at **1.0** (neutral) and the response includes a `cold_start_neutral` entry in `discount_breakdown`.

**Response includes `discount_breakdown`** — an itemised array of every adjustment (NCB, risk discount/surcharge) for full pricing transparency.

---

### ✅ `claims-verification` — Port 8086
**Stack:** Java 21 / Spring Boot 3.3

First Notice of Loss (FNOL) intake and automated claim triage. Each FNOL is correlated against the vehicle's verified service history to determine whether it can proceed automatically or requires a human investigator.

| Endpoint | Method | Description |
|---|---|---|
| `/api/v1/fnol` | `POST` | Submit a First Notice of Loss |
| `/api/v1/claims/{claimId}` | `GET` | Retrieve a submitted claim and its triage decision |

**FNOL request fields:** `vehicle_id`, `policy_id`, `incident_date`, `incident_description`, `claimed_cause` (enum: `mechanical_failure` \| `collision` \| `theft` \| `natural_disaster` \| `vandalism` \| `other`), `photo_refs[]`, `location`.

**Triage rule:**
```
IF  claimed_cause == "mechanical_failure"
AND vehicle has a clean service record within the last 90 days
THEN → MANUAL_REVIEW
ELSE → STRAIGHT_THROUGH_PROCESSING
```

**Rationale:** A vehicle that recently passed a clean service inspection is unlikely to suffer immediate mechanical failure through normal wear. A mechanical failure claim immediately after a clean service is a statistical anomaly that warrants investigator review before payment authorisation.

**Key design decisions:**
- `ClaimedCause` is a Java enum — invalid values are rejected at the JSON parsing layer with a structured 400, not by application logic.
- "Clean" service is conservative: any service event with no notes, or notes free of defect keywords (`critically overdue`, `worn`, `open recall`, `defect`, `failed`), is treated as clean. Benefit of the doubt goes to the claimant.
- The 90-day window is externalised to `claims.clean-service-window-days` in `application.yml` — actuaries can adjust it without a code change.
- Downstream maintenance call degrades gracefully: unavailable maintenance service → STP, not an error.

---

### ✅ `notification-advisory` — Port 8087
**Stack:** Java 21 / Spring Boot 3.3

Generates a prioritised list of plain-language advisory messages for a vehicle owner by correlating live recall status, service timeline, and risk score data. Stubs SMS/push delivery as a structured console log, ready for a real FCM/SNS swap in Phase 4.

| Endpoint | Method | Description |
|---|---|---|
| `/api/v1/advisories/{vehicleId}` | `GET` | Generate and deliver a prioritised advisory list |

**Advisory types and priorities:**

| Type | Priority | Trigger |
|---|---|---|
| `OPEN_RECALL` | CRITICAL | `recall_status.has_open_recall == true` |
| `OVERDUE_SERVICE` | HIGH | Last service > 365 days ago |
| `NO_SERVICE_RECORD` | HIGH | Vehicle has zero service records on file |
| `LOW_RISK_SCORE` | MEDIUM | Composite score < 60 |
| `SAFE_DRIVING_COMMENDATION` | LOW | Composite score ≥ 90 (positive reinforcement) |

The returned list is sorted CRITICAL → HIGH → MEDIUM → LOW. An empty list is a valid response — it means the vehicle has no outstanding flags.

**Data flow:** fetches service timeline and recall status from the maintenance service (:8083), then passes that data directly to the risk-scoring-engine (:8084) — avoiding redundant downstream pulls. Trip aggregates are omitted from the advisory score call (neutral cold-start), since usage-pattern coaching is not in scope for this service.

**Key design decisions:**
- All thresholds (`overdue-service-days: 365`, `low-score-threshold: 60.0`) are externalised to `application.yml` — product can tune them without a code change.
- `NotificationDeliveryService` is a separate `@Service` bean, currently logging to console. Phase 4 replaces the bean with real FCM/SNS — no controller or generator changes needed.
- Each advisory rule is independently nullable-input-safe: if any upstream call fails, that rule is skipped rather than failing the entire response.

---

## Shared Contracts

All inter-service data contracts are defined as JSON Schema Draft 2020-12 files in `/contracts/schemas`. Every service validates incoming and outgoing payloads against these schemas at its boundary.

| Schema | Version | Producer(s) | Consumer(s) |
|---|---|---|---|
| `usage-event.schema.json` | v1.0.0 | telematics-usage-ingestion | risk-scoring-engine |
| `maintenance-event.schema.json` | v1.0.0 | maintenance-vehicle-health-ingestion | risk-scoring-engine |
| `recall-status.schema.json` | v1.0.0 | maintenance-vehicle-health-ingestion | risk-scoring-engine |
| `score.schema.json` | v1.0.0 | risk-scoring-engine | pricing-policy |
| `premium-request.schema.json` | v1.0.0 | pricing-policy (internal) | pricing-policy |
| `premium-response.schema.json` | v1.0.0 | pricing-policy | mobile-app, notification-advisory |

**Schema governance:**
- Schemas follow Semantic Versioning via their `$id` URI.
- Adding an optional field → patch bump. Adding a required field → minor bump. Removing/renaming → major bump with a parallel-support migration window.
- Java services use `maven-resources-plugin` to copy schemas from `/contracts` into `target/classes/schemas` at build time, ensuring build-time schema drift is caught in CI before deployment.

---

## CI / CD

GitHub Actions workflow at `.github/workflows/ci.yml` runs on every push and pull request:

- `mvn test` for each Java service (identity-consent, telematics-usage-ingestion, maintenance-vehicle-health-ingestion, pricing-policy, claims-verification, notification-advisory)
- `pytest` for risk-scoring-engine
- `flutter test` for mobile-app
- JSON Schema meta-schema validation for all files in `/contracts/schemas`

Build fails on any test failure or schema validation error.

---

## Test Coverage Summary

| Service | Tests | What is covered |
|---|---|---|
| `identity-consent` | 2 | Default opted-out compliance, KYC mock, consent update |
| `telematics-usage-ingestion` | 31 | Schema validation (17), aggregation math, Haversine distance, time-gap segmentation |
| `maintenance-vehicle-health-ingestion` | 31 | Schema validation (19), OCR stub, store CRUD/ordering, vehicle isolation |
| `risk-scoring-engine` | 6 | Good/bad/borderline profiles, recall field authority (not notes), cold-start |
| `pricing-policy` | 8 | Cold-start neutral, score→multiplier interpolation, maintenance score → premium ordering, NCB, response shape |
| `claims-verification` | 8 | MANUAL_REVIEW path, STP paths (old service, no history, defect notes, non-mechanical, empty timeline, all cause variants), 90-day window boundary |
| `notification-advisory` | 8 | Open-recall CRITICAL advisory, overdue-service HIGH advisory, empty healthy-vehicle case, low score MEDIUM, priority ordering, all-null cold-start, no-record HIGH, commendation LOW |
| **Total** | **94** | |

---

## Reference Vehicle IDs

All five services use the same deterministic set of vehicle UUIDs for demo data, enabling cross-service end-to-end scoring:

| UUID | Profile |
|---|---|
| `a1b2c3d4-e5f6-7890-abcd-ef1234567890` | Perfect maintainer — mid-segment sedan, 2yr old, 20% NCB |
| `b2c3d4e5-f6a7-8901-bcde-f12345678901` | Overdue — compact hatchback, 4yr old, 10% NCB |
| `c3d4e5f6-a7b8-9012-cdef-123456789012` | Missed interval + open recall RC-2026-0042 — premium SUV, 3yr old |
| `d4e5f6a7-b8c9-0123-defa-234567890123` | Mixed sources — mid-range sedan, 5yr old, 25% NCB |
| `e5f6a7b8-c9d0-1234-efab-345678901234` | New vehicle — luxury SUV, <6 months old |

---

## Local Development

### Prerequisites

| Dependency | Version |
|---|---|
| Java | 21+ |
| Maven | 3.9+ |
| Python | 3.12+ |
| Flutter | 3.x |
| Docker & Docker Compose | Latest |

### Running a service individually

```bash
# Java services (example: telematics)
cd telematics-usage-ingestion
mvn spring-boot:run

# Python risk-scoring-engine
cd risk-scoring-engine
python -m venv venv && source venv/bin/activate   # Windows: .\venv\Scripts\Activate.ps1
pip install -r requirements.txt
python -m app.main
```

### Running tests

```bash
# Java service (from the service directory)
mvn test

# Python
cd risk-scoring-engine
$env:PYTHONPATH="."; python -m pytest
```

### Running the full stack

```bash
docker compose up -d
```

> All seven application services start in dependency order via `depends_on: condition: service_healthy`.
> Build context is the monorepo root — `docker compose build` compiles all Java services inside the container using a two-stage Maven+JRE image.

---

## Design Principles

1. **Schema-first, contract-driven**: every inter-service payload is governed by a versioned JSON Schema. Services cannot drift silently — schema violations fail the build.

2. **Interface + stub pattern for all external integrations**: KYC (Aadhaar/PAN), OCR (document recognition), and OEM recall database are all Java interfaces with mock implementations. Real integrations are a bean swap; no business logic changes.

3. **Structured data over text parsing**: recall status is a dedicated first-class resource (`recall-status.schema.json`), not inferred from free-text maintenance notes. This eliminates fragile string-matching and makes recall penalties auditable.

4. **Graceful degradation over hard failure**: every downstream call in `pricing-policy` degrades individually. A scoring engine outage does not break quote generation — it returns a neutral cold-start premium instead.

5. **Explainability by design**: the `contributing_factors` array in every score response is a SHAP-style impact decomposition, required for regulatory disclosure and customer transparency under India's Motor Vehicles Act / IRDAI guidelines.

6. **Deterministic synthetic data**: all five reference vehicles share the same hardcoded UUIDs across all services, making end-to-end demo flows reproducible without real telematics hardware or OEM API access.

---

## Roadmap

| Phase | Scope |
|---|---|
| **Phase 1** ✅ | Core ingestion, scoring, and pricing services |
| **Phase 2** ✅ | `claims-verification` · `notification-advisory` |
| **Phase 2.5** ✅ | Docker Compose full-stack orchestration with health checks |
| **Phase 3** 🔄 | Flutter mobile app — policy dashboard, maintenance reminders, score history |
| **Phase 4** 🔲 | Real persistence (PostgreSQL/Firestore), real OEM recall API integration, real OCR, FCM/SNS delivery |
| **Phase 5** 🔲 | Kafka-based event streaming, feature store (Feast), ML model training pipeline |

---

## Repository Layout

```
payasyoumaintain/
├── .github/
│   └── workflows/
│       └── ci.yml                     # CI: test all services + validate schemas
├── contracts/
│   ├── README.md                      # Schema governance policy
│   └── schemas/
│       ├── usage-event.schema.json
│       ├── maintenance-event.schema.json
│       ├── recall-status.schema.json
│       ├── score.schema.json
│       ├── premium-request.schema.json
│       └── premium-response.schema.json
├── identity-consent/                  # Spring Boot · port 8081
├── telematics-usage-ingestion/        # Spring Boot · port 8082
├── maintenance-vehicle-health-ingestion/ # Spring Boot · port 8083
├── risk-scoring-engine/               # FastAPI (Python) · port 8084
├── pricing-policy/                    # Spring Boot · port 8085
├── claims-verification/               # Spring Boot · port 8086
├── notification-advisory/             # Spring Boot · port 8087
├── mobile-app/                        # Flutter [Phase 3]
├── infra/                             # Terraform / Docker [Phase 4]
└── docker-compose.yml                 # Full-stack local orchestration
```

---

*Pay As You Maintain is built as a demonstration of cloud-native insurance platform engineering, covering domain-driven service decomposition, contract-first API design, explainable AI scoring, and regulatory-grade audit trails.*
