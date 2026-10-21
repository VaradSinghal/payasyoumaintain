# PayAsUMaintain API Contract

## Overview
This document defines the canonical API contract for the PayAsUMaintain backend platform. All client applications (mobile and web) communicate through the single-entry **Hardened API Gateway** on port `8080`.

All microservices adhere to:
- Consistent `/api/v1` base route prefixing.
- Strict `snake_case` JSON serialization.
- Canonical JSON Schemas conforming to JSON Schema Draft 2020-12 located in [`contracts/schemas/`](contracts/schemas/). **JSON Schemas are the single source of truth** for request and response data models.

---

## Gateway Topology & Client Routing

- **Gateway Base URL (Host / Localhost)**: `http://localhost:8080`
- **Gateway Base URL (Android Emulator)**: `http://10.0.2.2:8080`
- **Gateway Base URL (Physical Devices)**: `http://<HOST_IP>:8080`

The API Gateway strips the service routing prefix before forwarding upstream (`/<service>/api/v1/...` &rarr; `/api/v1/...`):

| Public Gateway Prefix | Upstream Service | Port (Internal) |
|---|---|---|
| `/identity/api/v1/` | Identity & Consent | `8081` |
| `/telematics/api/v1/` | Telematics Usage Ingestion | `8082` |
| `/maintenance/api/v1/` | Maintenance & Health Ingestion | `8083` |
| `/pricing/api/v1/` | Pricing Policy | `8085` |
| `/claims/api/v1/` | Claims Verification | `8086` |
| `/advisory/api/v1/` | Notification Advisory | `8087` |

*Note: Risk Scoring Engine (`8084`) is an internal service invoked by Pricing Policy and is accessible through the gateway only via `/health/scoring`.*

---

## Gateway Common Errors & Headers

- **Correlation ID**: The gateway assigns a unique `X-Request-ID` to every upstream request, returns it in the response header, and logs it.
- **CORS**: Permissive CORS is enabled for web clients (`Access-Control-Allow-Origin: *`, `OPTIONS` preflight returns 204).
- **Error Format**: Error responses are plain JSON objects conforming to [`error-response.schema.json`](contracts/schemas/error-response.schema.json) with three fields: `{error, message, timestamp}` (plain schema, not RFC 7807/RFC 9457 compliant):
  - `error`: Short machine-readable error token or HTTP status text (string, required).
  - `message`: Human-readable explanation of the error condition (string, required).
  - `timestamp`: ISO-8601 instant string indicating when the error was generated (string, required).

Common status codes for error responses:
- `400 Bad Request`: Malformed JSON, schema validation failure, or unparseable UUID.
- `403 Forbidden`: Gateway-blocked endpoints (synthetic or internal management actuators).
- `404 Not Found`: Unknown path, unmapped route, or entity not found.
- `422 Unprocessable Entity`: Business domain validation failure (policy-vehicle mismatch or invalid incident date).
- `503 Service Unavailable`: Upstream service unreachable or dependency missing.

---

## Health Check Routes

| Route | Method | Request Schema | Response Schema | Status Codes |
|---|---|---|---|---|
| `/health` | GET | *None* | Gateway status object | `200 OK` (verified by smoke) |
| `/health/identity` | GET | *None* | Spring Actuator Health | `200 OK` (verified by smoke) |
| `/health/telematics` | GET | *None* | Spring Actuator Health | `200 OK` (verified by smoke) |
| `/health/maintenance` | GET | *None* | Spring Actuator Health | `200 OK` (verified by smoke) |
| `/health/pricing` | GET | *None* | Spring Actuator Health | `200 OK` (verified by smoke) |
| `/health/claims` | GET | *None* | Spring Actuator Health | `200 OK` (verified by smoke) |
| `/health/advisory` | GET | *None* | Spring Actuator Health | `200 OK` (verified by smoke) |
| `/health/scoring` | GET | *None* | Service Health | `200 OK` (verified by smoke) |

---

## Allowlisted Service Endpoints

### 1. Identity & Consent Service

#### Register Vehicle
- **Route**: `POST /identity/api/v1/vehicles`
- **Request Schema**: [`vehicle-registration-request.schema.json`](contracts/schemas/vehicle-registration-request.schema.json)
- **Response Schema**: [`vehicle-registration-response.schema.json`](contracts/schemas/vehicle-registration-response.schema.json)
- **Status Codes**:
  - `201 Created`: Vehicle registered, policy bound, masked KYC returned (verified by smoke and contract_check).
  - `400 Bad Request`: Payload validation failure ([`error-response.schema.json`](contracts/schemas/error-response.schema.json)) (verified by contract_check).

#### Get Vehicle Registration
- **Route**: `GET /identity/api/v1/vehicles/{id}`
- **Request Schema**: *None* (Path parameter `id`: UUID)
- **Response Schema**: [`vehicle-registration-response.schema.json`](contracts/schemas/vehicle-registration-response.schema.json)
- **Status Codes**:
  - `200 OK`: Registered vehicle details with masked PII/KYC (verified by smoke and contract_check).
  - `400 Bad Request`: Invalid UUID format (verified by smoke and contract_check).
  - `404 Not Found`: Unknown vehicle ID ([`error-response.schema.json`](contracts/schemas/error-response.schema.json)) (verified by smoke and contract_check).

#### Get Vehicle Consent
- **Route**: `GET /identity/api/v1/vehicles/{id}/consent`
- **Request Schema**: *None* (Path parameter `id`: UUID)
- **Response Schema**: [`consent-record.schema.json`](contracts/schemas/consent-record.schema.json) (contains `vehicle_id`, opt-in flags, and `last_updated`)
- **Status Codes**:
  - `200 OK`: Current consent status and opt-in timestamps (verified by smoke and contract_check).
  - `400 Bad Request`: Invalid UUID format (documented, not yet tested).
  - `404 Not Found`: Unknown vehicle ID ([`error-response.schema.json`](contracts/schemas/error-response.schema.json)) (documented, not yet tested).

#### Update Vehicle Consent
- **Route**: `PUT /identity/api/v1/vehicles/{id}/consent`
- **Request Schema**: [`consent-update-request.schema.json`](contracts/schemas/consent-update-request.schema.json)
- **Response Schema**: [`consent-record.schema.json`](contracts/schemas/consent-record.schema.json)
- **Status Codes**:
  - `200 OK`: Updated consent record (verified by smoke and contract_check).
  - `400 Bad Request`: Malformed body or invalid UUID ([`error-response.schema.json`](contracts/schemas/error-response.schema.json)) (documented, not yet tested).
  - `404 Not Found`: Unknown vehicle ID ([`error-response.schema.json`](contracts/schemas/error-response.schema.json)) (documented, not yet tested).

---

### 2. Pricing Policy Service

#### Retrieve Vehicle Catalogue
- **Route**: `GET /pricing/api/v1/vehicle-catalogue`
- **Request Schema**: *None*
- **Response Schema**: [`vehicle-catalogue.schema.json`](contracts/schemas/vehicle-catalogue.schema.json)
- **Status Codes**:
  - `200 OK`: Array of standard vehicle segments with indicative ex-showroom prices in INR (verified by smoke and contract_check).

#### Calculate Dynamic Premium Quote
- **Route**: `POST /pricing/api/v1/quote/{id}`
- **Request Schema**: *None* (Empty JSON body `{}` accepted, path parameter `id`: UUID)
- **Response Schema**: [`premium-response.schema.json`](contracts/schemas/premium-response.schema.json)
- **Status Codes**:
  - `200 OK`: Premium breakdown including Insured Declared Value (IDV), base premium, usage adjustment, maintenance adjustment, NCB discount, and pricing basis (verified by smoke and contract_check).
  - `400 Bad Request`: Invalid UUID format ([`error-response.schema.json`](contracts/schemas/error-response.schema.json)) (verified by smoke).
  - `404 Not Found`: Unknown vehicle ID ([`error-response.schema.json`](contracts/schemas/error-response.schema.json)) (documented, not yet tested).
  - `503 Service Unavailable`: Upstream identity service unreachable and vehicle is not a pre-seeded demo vehicle ([`error-response.schema.json`](contracts/schemas/error-response.schema.json)) (documented, not yet tested).

---

### 3. Notification Advisory Service

#### Get Prioritized Advisories
- **Route**: `GET /advisory/api/v1/advisories/{id}`
- **Request Schema**: *None* (Path parameter `id`: UUID)
- **Response Schema**: [`advisory-response.schema.json`](contracts/schemas/advisory-response.schema.json) (Object containing `{vehicle_id, advisories[], generated_at}` with advisory priority `CRITICAL`, `HIGH`, `MEDIUM`, or `LOW`)
- **Status Codes**:
  - `200 OK`: Prioritized list of active vehicle maintenance, safety, and recall advisories (verified by smoke and contract_check).
  - `400 Bad Request`: Invalid UUID format ([`error-response.schema.json`](contracts/schemas/error-response.schema.json)) (verified by smoke).
  - `404 Not Found`: Unknown vehicle ID ([`error-response.schema.json`](contracts/schemas/error-response.schema.json)) (documented, not yet tested).

---

### 4. Maintenance & Vehicle Health Ingestion Service

#### Get Service Timeline
- **Route**: `GET /maintenance/api/v1/timeline/{id}`
- **Request Schema**: *None* (Path parameter `id`: UUID)
- **Response Schema**: [`service-timeline.schema.json`](contracts/schemas/service-timeline.schema.json)
- **Status Codes**:
  - `200 OK`: Vehicle service timeline including total services, latest service date, and ordered maintenance events (verified by smoke and contract_check).
  - `400 Bad Request`: Invalid UUID format ([`error-response.schema.json`](contracts/schemas/error-response.schema.json)) (verified by smoke).
  - `404 Not Found`: Unknown vehicle ID ([`error-response.schema.json`](contracts/schemas/error-response.schema.json)) (documented, not yet tested).

#### Ingest Diagnostic Trouble Code (DTC) Reading
- **Route**: `POST /maintenance/api/v1/dtc-readings`
- **Request Schema**: [`dtc-reading.schema.json`](contracts/schemas/dtc-reading.schema.json)
- **Response Schema**: [`dtc-reading.schema.json`](contracts/schemas/dtc-reading.schema.json)
- **Status Codes**:
  - `201 Created`: Ingested DTC reading record (verified by smoke and contract_check).
  - `400 Bad Request`: Validation failure ([`error-response.schema.json`](contracts/schemas/error-response.schema.json)) (documented, not yet tested).

#### Get Latest DTC Reading
- **Route**: `GET /maintenance/api/v1/dtc-readings/{id}/latest`
- **Request Schema**: *None* (Path parameter `id`: UUID)
- **Response Schema**: [`dtc-reading.schema.json`](contracts/schemas/dtc-reading.schema.json)
- **Status Codes**:
  - `200 OK`: Most recent diagnostic reading for vehicle (verified by smoke and contract_check).
  - `204 No Content`: No diagnostic readings recorded for vehicle (verified by smoke).
  - `400 Bad Request`: Invalid UUID format (documented, not yet tested).
  - `404 Not Found`: Unknown vehicle ID ([`error-response.schema.json`](contracts/schemas/error-response.schema.json)) (documented, not yet tested).

#### Get DTC Reading History
- **Route**: `GET /maintenance/api/v1/dtc-readings/{id}/history`
- **Request Schema**: *None* (Path parameter `id`: UUID)
- **Response Schema**: Array of [`dtc-reading.schema.json`](contracts/schemas/dtc-reading.schema.json)
- **Status Codes**:
  - `200 OK`: Chronological array of diagnostic trouble code readings (empty array if none recorded) (verified by smoke and contract_check).
  - `400 Bad Request`: Invalid UUID format (documented, not yet tested).
  - `404 Not Found`: Unknown vehicle ID ([`error-response.schema.json`](contracts/schemas/error-response.schema.json)) (documented, not yet tested).

---

### 5. Telematics Usage Ingestion Service

#### Get Trip Aggregates
- **Route**: `GET /telematics/api/v1/aggregates/{id}`
- **Request Schema**: *None* (Path parameter `id`: UUID)
- **Response Schema**: Array of [`trip-aggregate.schema.json`](contracts/schemas/trip-aggregate.schema.json)
- **Status Codes**:
  - `200 OK`: Array of daily driving behavior summaries (verified by smoke and contract_check).
  - `400 Bad Request`: Invalid UUID format ([`error-response.schema.json`](contracts/schemas/error-response.schema.json)) (verified by smoke).
  - `404 Not Found`: Unknown vehicle ID ([`error-response.schema.json`](contracts/schemas/error-response.schema.json)) (documented, not yet tested).

---

### 6. Claims Verification Service

#### Submit First Notice of Loss (FNOL)
- **Route**: `POST /claims/api/v1/fnol`
- **Request Schema**: [`fnol-request.schema.json`](contracts/schemas/fnol-request.schema.json) (accepted `claimed_cause` values: `mechanical_failure`, `collision`, `theft`, `natural_disaster`, `vandalism`, `other`)
- **Response Schema**: [`fnol-response.schema.json`](contracts/schemas/fnol-response.schema.json)
- **Status Codes**:
  - `201 Created`: Claim intake accepted and triaged. Returns triage decision (`MANUAL_REVIEW`, `STRAIGHT_THROUGH_PROCESSING`), machine-readable `triage_reason_codes`, `maintenance_signal`, and `submitted_at` (verified by smoke and contract_check).
  - `400 Bad Request`: Request validation failure or invalid cause such as `weather` ([`error-response.schema.json`](contracts/schemas/error-response.schema.json)) (verified by smoke and contract_check).
  - `422 Unprocessable Entity`: Policy-vehicle binding unverified, vehicle unregistered, or invalid incident date ([`error-response.schema.json`](contracts/schemas/error-response.schema.json)) (verified by smoke).

#### Get Claim Details
- **Route**: `GET /claims/api/v1/claims/{id}`
- **Request Schema**: *None* (Path parameter `id`: UUID)
- **Response Schema**: [`fnol-response.schema.json`](contracts/schemas/fnol-response.schema.json)
- **Status Codes**:
  - `200 OK`: Claim record including triage decision and reason codes (verified by smoke and contract_check).
  - `400 Bad Request`: Invalid UUID format (documented, not yet tested).
  - `404 Not Found`: Unknown claim ID ([`error-response.schema.json`](contracts/schemas/error-response.schema.json)) (documented, not yet tested).

---

## Known Limitations

1. **No Authentication**: In the current development and demonstration environment, authentication and token validation (e.g., OAuth2/JWT) are not enforced at the API gateway or backend microservices. All allowlisted routes are open. Client applications must treat vehicle identifiers (`vehicle_id`) and claim identifiers (`claim_id`) as sensitive identifiers.
2. **In-Memory State**: All backend microservices currently utilize in-memory storage implementations (`ConcurrentHashMap` / in-memory collections). Restarting microservice containers resets all runtime modifications back to the default pre-seeded state (the five demo vehicles: `demo_veh_001` through `demo_veh_005`).
3. **Claims-Consent Architecture Decision**: Claims intake (`POST /api/v1/fnol`) and triage adjudication evaluate policy-vehicle binding, incident dates, and service timelines, but deliberately **do not check user tracking consent** (`usage_tracking_opt_in` / `maintenance_tracking_opt_in`). This is a **design decision pending legal review** (rather than a final legal conclusion): tracking consent is currently architected to govern optional telematics usage ingestion and proactive maintenance advisories/pricing discounts, whereas FNOL intake operates as core policy servicing under the insurance contract. Legal review will confirm the regulatory boundary between core claims adjudication and optional telematics tracking consent.
