# PayAsUMaintain API Contract

## Overview
This document defines the canonical API contract for the PayAsUMaintain backend services. All microservices enforce a consistent `/api/v1` base path prefix. The APIs use `snake_case` JSON serialization globally.

### Mobile & Web Client Integration (API Gateway)
All mobile and web clients connect through the single-entry **Hardened API Gateway** on port **8080**:
- **Gateway Base URL (Web / Localhost)**: `http://localhost:8080`
- **Gateway Base URL (Android Emulator)**: `http://10.0.2.2:8080`
- **Gateway Base URL (Physical Devices)**: `http://<HOST_IP>:8080`

By default, the gateway on port `8080` is the **only** published application port on the host (backend services use Docker `expose`). Direct ports `8081`–`8087` can be published for debugging using `docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d`.

#### Correlation & Headers
- `X-Request-ID`: Nginx sets upstream `X-Request-ID` from `$request_id`, returns it on every response header, and logs it in the access log.
- `CORS`: Permissive CORS headers are enabled for Flutter web clients (`Access-Control-Allow-Origin: *`, `OPTIONS` preflight returns 204).

#### Health Routes
- `GET /health` &rarr; Gateway health status (`{"status":"UP","gateway":"paymu-gateway"}`)
- `GET /health/identity` &rarr; `http://identity-consent:8081/api/v1/actuator/health`
- `GET /health/telematics` &rarr; `http://telematics-usage-ingestion:8082/api/v1/actuator/health`
- `GET /health/maintenance` &rarr; `http://maintenance-vehicle-health-ingestion:8083/api/v1/actuator/health`
- `GET /health/pricing` &rarr; `http://pricing-policy:8085/api/v1/actuator/health`
- `GET /health/claims` &rarr; `http://claims-verification:8086/api/v1/actuator/health`
- `GET /health/advisory` &rarr; `http://notification-advisory:8087/api/v1/actuator/health`
- `GET /health/scoring` &rarr; `http://risk-scoring-engine:8084/health` *(Risk scoring engine is reachable through the gateway ONLY at `/health/scoring`)*

#### Explicit Public Allowlist (Anchored & Method-Restricted)
Requests matching the allowlist have their gateway service prefix stripped before forwarding upstream (`/service/api/v1/...` &rarr; `/api/v1/...`):
- `POST /identity/api/v1/vehicles` &rarr; Register vehicle
- `GET /identity/api/v1/vehicles/{id}` &rarr; Get vehicle registration
- `GET /identity/api/v1/vehicles/{id}/consent` &rarr; Get consent status
- `PUT /identity/api/v1/vehicles/{id}/consent` &rarr; Update consent status
- `POST /pricing/api/v1/quote/{id}` &rarr; Calculate quote
- `GET /pricing/api/v1/vehicle-catalogue` &rarr; Retrieve indicative vehicle catalogue
- `GET /advisory/api/v1/advisories/{id}` &rarr; Fetch prioritized advisories
- `POST /claims/api/v1/fnol` &rarr; Submit first notice of loss
- `GET /claims/api/v1/claims/{id}` &rarr; Get claim status
- `GET /maintenance/api/v1/timeline/{id}` &rarr; Get maintenance timeline
- `POST /maintenance/api/v1/dtc-readings` &rarr; Ingest DTC reading
- `GET /maintenance/api/v1/dtc-readings/{id}/latest` &rarr; Get latest DTC reading
- `GET /maintenance/api/v1/dtc-readings/{id}/history` &rarr; Get DTC reading history
- `GET /telematics/api/v1/aggregates/{id}` &rarr; Get trip aggregates

All other paths (including raw events, synthetic generation, recall queries, internal actuators, and scoring paths) or disallowed HTTP methods return `404 NOT_FOUND` JSON.

#### Gateway Error Formatting
All gateway-generated errors are returned as JSON with `Content-Type: application/json`:
- Upstream connection failures, 502, 503, 504 &rarr; `503 SERVICE_UNAVAILABLE`
  `{"error":"SERVICE_UNAVAILABLE","message":"Upstream service temporarily unavailable","timestamp":"<ISO8601>"}`
- Unknown paths or disallowed HTTP methods &rarr; `404 NOT_FOUND`
  `{"error":"NOT_FOUND","message":"Resource not found or method not allowed","timestamp":"<ISO8601>"}`
- Blocked endpoints &rarr; `403 FORBIDDEN`
  `{"error":"FORBIDDEN","message":"Ingestion, seeding and internal endpoints are blocked by gateway","timestamp":"<ISO8601>"}`
- Upstream application 4xx errors (e.g. 400 Bad Request) pass through unintercepted with their original application JSON bodies.

---

## 1. Identity & Consent Service (Port: 8081)
**Responsibility**: Manages vehicle registrations and user consent.

### `POST /api/v1/vehicles`
Registers a new vehicle.
- **Request Body**:
  ```json
  {
    "rc_number": "MH12AB1234",
    "registration_date": "2020-01-01",
    "vehicle_segment_id": "sedan_mid",
    "owner_details": {
      "full_name": "Test Owner",
      "kyc_document_number": "PAN1234567",
      "kyc_document_type": "PAN",
      "email": "test@test.com",
      "phone_number": "1234567890"
    }
  }
  ```
- **Responses**: 
  - `201 Created`: Returns `VehicleRegistration` (includes generated `vehicle_id`, `policy_id`, `vehicle_segment_id`, and masked `owner_details`).
  - `400 Bad Request`: Validation failure against boundary schema `vehicle-registration-request.schema.json` (`{"error":"INVALID_REQUEST","message":"...","timestamp":"..."}`).

### `GET /api/v1/vehicles/{vehicleId}`
Retrieves a vehicle registration.
- **Responses**: 
  - `200 OK`: `VehicleRegistration` with masked PII:
    - `kyc_document_number`: only the last 4 characters visible (e.g. `XXXXXX4567`)
    - `phone_number`: only the last 4 digits visible (e.g. `XXXXXX7890`)
    - `email`: first character + `***` + domain (e.g. `t***@test.com`)
  - `404 Not Found`: Unknown vehicle ID

### `GET /api/v1/vehicles/{vehicleId}/consent`
Retrieves consent status.
- **Responses**:
  - `200 OK`: `ConsentRecord`

### `PUT /api/v1/vehicles/{vehicleId}/consent`
Updates consent opt-ins.
- **Request Body**:
  ```json
  {
    "usage_tracking_opt_in": true,
    "maintenance_tracking_opt_in": true
  }
  ```
- **Responses**:
  - `200 OK`: Updated `ConsentRecord`
  - `404 Not Found`: Unknown vehicle ID

---

## 2. Maintenance Vehicle Health Ingestion (Port: 8083)
**Responsibility**: Collects diagnostic trouble codes (DTCs) and calculates maintenance timelines and recall status.

### `POST /api/v1/dtc-readings`
Records a DTC scan.
- **Request Body**:
  ```json
  {
    "vehicle_id": "UUID",
    "timestamp": "2026-10-08T10:00:00Z",
    "source": "manual_entry",
    "device_id": null,
    "confirmed_codes": ["P0300"],
    "pending_codes": []
  }
  ```
- **Responses**: `200 OK` or `201 Created`

### `GET /api/v1/dtc-readings/{vehicleId}/latest`
Retrieves the most recent DTC reading.
- **Responses**: `200 OK` or `204 No Content` (if no readings).

### `GET /api/v1/dtc-readings/{vehicleId}/history`
Retrieves reading history.
- **Responses**: `200 OK` with JSON array (empty array if no readings).

### `GET /api/v1/timeline/{vehicleId}`
Retrieves maintenance timeline.
- **Responses**: `200 OK` (returns `ServiceTimeline` object).

### `GET /api/v1/recall-status/{vehicleId}`
Checks open manufacturer recalls.
- **Responses**: `200 OK` (`has_open_recall: true/false`).

---

## 3. Telematics Usage Ingestion (Port: 8082)
**Responsibility**: Aggregates telematics trip data.

### `GET /api/v1/aggregates/{vehicleId}`
Returns aggregated trip statistics.
- **Responses**: `200 OK` (JSON array of aggregates).

---

## 4. Risk Scoring Engine (Port: 8084)
**Responsibility**: Evaluates usage and maintenance data into a risk score.

### `POST /score/{vehicleId}`
*(Note: Python service, maps directly to root path `/score`)*
Generates a risk score from aggregated inputs.
- **Request Body**: (Generated by Pricing Policy internally).
- **Responses**: `200 OK` (`usage_score`, `maintenance_score`, `composite_score`, `health_status`, `data_flags`).

---

## 5. Pricing Policy (Port: 8085)
**Responsibility**: Calculates real-time insurance premium quotes and exposes the indicative vehicle catalogue.

### `GET /api/v1/vehicle-catalogue`
Retrieves the indicative vehicle catalogue mapping vehicle segments to ex-showroom prices in INR.
- **Responses**:
  - `200 OK`: JSON array of `{ "id", "label", "ex_showroom_price_inr" }` conforming to `vehicle-catalogue.schema.json`.

### `POST /api/v1/quote/{vehicleId}`
Requests a premium quote. Derives pricing basis from registered vehicle segment and age, and orchestrates downstream calls to calculate dynamic risk adjustments.
- **Responses**: 
  - `200 OK`: `PremiumResponse` conforming to `premium-response.schema.json` v1.2.2. Includes `pricing_basis` object:
    - `source`: `"registered"`, `"demo_profile"`, or `"generic_default"`
    - `vehicle_segment_id`: catalogue segment ID or null
    - `ex_showroom_price_inr`: indicative catalogue ex-showroom price
    - `vehicle_age_years`: whole years elapsed from registration date
    - `depreciation_factor`: IRDAI depreciation slab multiplier
    - `idv`: Insured Declared Value (ex_showroom × depreciation_factor)
    - `ncb_pct`: No Claim Bonus percentage
  - `503 Service Unavailable`: Returned when identity-consent is unreachable or returns 5xx and no cached profile exists for the vehicle (`{"error":"SERVICE_UNAVAILABLE","message":"...","timestamp":"..."}`). Never invents an unbacked premium.

---

## 6. Claims Verification (Port: 8086)
**Responsibility**: Processes First Notice of Loss (FNOL).

> [!NOTE]
> **Design Decision (Pending Legal Review)**: Claim triage uses maintenance records to adjudicate and handle claims under the insurance policy contract. This operational policy servicing is legally separate from optional personalization and marketing tracking consent. Consequently, claims verification operates independently of tracking opt-ins.

### `POST /api/v1/fnol`
Submits a claim.
- **Request Body**:
  ```json
  {
    "vehicle_id": "UUID",
    "policy_id": "UUID",
    "incident_date": "2026-10-08T10:00:00Z",
    "claimed_cause": "mechanical_failure",
    "incident_description": "Engine died",
    "photo_refs": [],
    "location": "Garage"
  }
  ```
- **Responses**: `201 Created` (`FnolResponse` with `claim_id` and `triage_decision`).

### `GET /api/v1/claims/{claimId}`
Retrieves claim details.
- **Responses**: `200 OK`

---

## 7. Notification Advisory (Port: 8087)
**Responsibility**: Provides proactive advisories to users.

### `GET /api/v1/advisories/{vehicleId}`
Fetches advisories (e.g. `OPEN_RECALL`, `SERVICE_OVERDUE`).
- **Responses**: `200 OK` (JSON array of `Advisory`).

---

## Common Error Formatting
All services (except Risk Scoring Engine) use standard Spring Boot RFC 7807 problem details or a consistent `{ "timestamp", "message", "error" }` structure for 400, 404, 500 status codes. Ensure client apps handle these generic payloads.

## Authentication
Currently, endpoints are public for development (`DEMO_MODE`). Future milestones will enforce JWT-based Authorization.

## Known Limitations
- **No Authentication in MVP**: In the current MVP release, endpoints are public and unauthenticated (`DEMO_MODE`). Because identity and policy endpoints do not yet require user credentials, vehicle IDs must be treated as secrets by clients to prevent unauthorized discovery or data access.
- **PII Minimisation**: While internal stores maintain full KYC records for compliance, API boundaries enforce PII minimization by masking document numbers, phone numbers, and email addresses.
