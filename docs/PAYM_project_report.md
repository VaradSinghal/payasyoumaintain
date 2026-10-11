# Pay As You Maintain (PAYM)
## Comprehensive Technical Audit, Architecture Review & Bug Analysis Report

**Target / Domain:** Dynamic Usage-Based Vehicle Insurance (UBI)  
**Author / Repository:** Varad Singhal • VaradSinghal/payasyoumaintain  
**Evaluation Client:** Guidewire Software Technical Portfolio Evaluation  
**Audit Timestamp:** October 2026 • Antigravity Code Intelligence System  

---

## Executive Table of Contents
1. [Executive Summary & Project Mission](#1-executive-summary--project-mission)
2. [Architecture & System Topology](#2-architecture--system-topology)
3. [Technology Stacks by Component](#3-technology-stacks-by-component)
4. [Monorepo Project Structure & Organization](#4-monorepo-project-structure--organization)
5. [Comprehensive Implementation Status (What is built so far)](#5-comprehensive-implementation-status-what-is-built-so-far)
6. [Deployment & Cloud Infrastructure Audit (Render / Supabase / Vercel analysis)](#6-deployment--cloud-infrastructure-audit)
7. [API Ingestion & External Integration Mapping](#7-api-ingestion--external-integration-mapping)
8. [Code & Logic Bug Audit (Detailed Findings with Code References)](#8-code--logic-bug-audit-detailed-findings)
9. [Unimplemented Features & Technical Gap Analysis](#9-unimplemented-features--technical-gap-analysis)
10. [Strategic Recommendations & Next Feature Roadmap](#10-strategic-recommendations--next-feature-roadmap)

---

## 1. Executive Summary & Project Mission

**Pay As You Maintain (PAYM)** is an enterprise-grade, cloud-native, microservices-based **Usage-Based Insurance (UBI)** platform designed to dynamically price auto insurance policies in real time. It shifts automotive underwriting away from traditional, static actuarial tables (which evaluate risk purely based on driver age, historical accidents, and geographic location) into a continuous, telemetry-driven pricing paradigm.

PAYM evaluates driver and vehicle risk through two dynamic telemetry pillars:
- **Driving Behaviour Telematics:** Aggregates trip-level telematics events captured via OBD-II hardware/smartphones, isolating harsh braking frequencies and hard acceleration events. Crucially, raw highway speed is deliberately excluded to prevent unfairly penalizing highway commuters relative to urban drivers.
- **Maintenance & Vehicle Health Adherence:** Monitors real-time Diagnostic Trouble Codes (DTCs), verified OEM service logs, self-uploaded workshop receipts, and active manufacturer safety recalls. Vehicles adhering to strict safety service windows (such as brake inspections within 24 months) receive substantial premium discounts.

The platform also features an automated **First Notice of Loss (FNOL) Claims Triage Engine** that cross-references mechanical failure claims against recent service history to flag anomalies for manual review, and a proactive **Notification & Advisory Engine** that issues prioritized alerts to vehicle owners.

---

## 2. Architecture & System Topology

PAYM is structured as a **polyglot microservices monorepo** adhering to Domain-Driven Design (DDD) principles. The services communicate synchronously via REST APIs, enforced by centralized JSON Schema contracts.

```
                     ┌─────────────────────────────────────────────────────────┐
                     │          Flutter Client (Mobile / Web App)              │
                     └───────────────┬─────────────────────────┬───────────────┘
                                     │                         │
             ┌───────────────────────┼─────────────────────────┼─────────────────────────┐
             │ (8081)                │ (8085)                  │ (8086)                  │ (8087)
             ▼                       ▼                         ▼                         ▼
   ┌──────────────────┐    ┌──────────────────┐      ┌──────────────────┐      ┌──────────────────┐
   │ identity-consent │    │  pricing-policy  │      │claims-verificat. │      │notification-adv. │
   └──────────────────┘    └────────┬─────────┘      └────────┬─────────┘      └────────┬─────────┘
                                    │                         │                         │
                      ┌─────────────┼─────────────┐           │                         │
                      │             │             │           │                         │
                      ▼             ▼             ▼           │                         │
              ┌───────────────┐     │      ┌───────────────┐  │                         │
              │telematics-    │     │      │maintenance-   │◄─┘                         │
              │usage-ingestion│     │      │vehicle-health ├────────────────────────────┘
              │  (port 8082)  │     │      │  (port 8083)  │
              └───────┬───────┘     │      └───────┬───────┘
                      │             │              │
                      │       ┌─────▼──────┐       │
                      └──────►│risk-scoring├───────┘
                              │engine(8084)│
                              └────────────┘
```

### Core Architectural Characteristics
1. **Three-Tier Orchestration:**
   - **Tier 1 (Base Ingestion & Identity):** `identity-consent`, `telematics-usage-ingestion`, and `maintenance-vehicle-health-ingestion` operate autonomously with zero inter-service upstream dependencies.
   - **Tier 2 (Stateless Actuarial Compute):** `risk-scoring-engine` acts as a pure calculation engine receiving assembled vehicle history and returning scores with explainability factors.
   - **Tier 3 (Composite Business Orchestrators):** `pricing-policy`, `claims-verification`, and `notification-advisory` aggregate outputs from Tiers 1 and 2 to execute business policies.
2. **Contract-First Architecture:** All inter-service request and response payloads are strictly governed by JSON Schema Draft 2020-12 specifications maintained in `contracts/schemas/`. Build pipelines fail automatically upon schema drift.
3. **Graceful Degradation Pattern:** When downstream services (or the risk scoring engine) are unreachable, the pricing engine falls back to neutral cold-start defaults (multiplier 1.0×) and claims proceed to straight-through processing (STP) rather than breaking with catastrophic 500 errors.

---

## 3. Technology Stacks by Component

| Service / Component | Runtime & Language | Framework & Core Libraries | Port | Primary Responsibility |
| :--- | :--- | :--- | :--- | :--- |
| `identity-consent` | Java 21 (Temurin) | Spring Boot 3.3.x, Jackson, Actuator | `8081` | Vehicle owner registration, KYC verification stub, opt-in/opt-out consent management. |
| `telematics-usage-ingestion` | Java 21 (Temurin) | Spring Boot 3.3.x, NetworkNT JSON Schema | `8082` | OBD-II batch event ingestion, Haversine trip distance, time-gap auto-segmentation. |
| `maintenance-vehicle-health` | Java 21 (Temurin) | Spring Boot 3.3.x, NetworkNT JSON Schema | `8083` | DTC readings ingestion, OCR receipt stub, service timeline store, OEM recall catalogue. |
| `risk-scoring-engine` | Python 3.12 | FastAPI, Pydantic v2, jsonschema, Uvicorn | `8084` | Stateless actuarial risk scoring, SHAP-style explainability factors, data health flags. |
| `pricing-policy` | Java 21 (Temurin) | Spring Boot 3.3.x, Spring RestClient | `8085` | Downstream orchestration, IRDAI IDV depreciation slabs, NCB discounts, dynamic premium quote. |
| `claims-verification` | Java 21 (Temurin) | Spring Boot 3.3.x, Spring RestClient | `8086` | FNOL intake, fraud anomaly detection (mechanical failure vs 90-day clean service check). |
| `notification-advisory` | Java 21 (Temurin) | Spring Boot 3.3.x, Spring RestClient | `8087` | Proactive vehicle advisories (recall alerts, overdue service warnings, safe driver perks). |
| `mobile_app` | Dart 3.12 / Flutter 3.x | Flutter Riverpod 3.4, GoRouter 18, Dio 5.11, Flutter Secure Storage 11 | Client (Web/Mob) | User onboarding, dynamic quote display, DTC health monitors, manual scans, FNOL claim filing. |
| `docker-compose.yml` | Docker Compose | Postgres 16, Redis 7, Confluent Kafka 7.6 | Various | Local full-stack multi-container development environment. |

---

## 4. Monorepo Project Structure & Organization

```
payasyoumaintain/
├── .github/workflows/
│   └── ci.yml                               # CI: Maven tests, Pytest, Flutter test, JSON schema validation
├── contracts/
│   ├── README.md                            # Schema governance policy
│   └── schemas/                             # Draft 2020-12 Inter-Service Contracts
│       ├── dtc-reading.schema.json
│       ├── maintenance-event.schema.json
│       ├── premium-request.schema.json
│       ├── premium-response.schema.json
│       ├── recall-status.schema.json
│       ├── score.schema.json
│       ├── trip-aggregate.schema.json
│       └── usage-event.schema.json
├── identity-consent/                        # Spring Boot (Port 8081)
├── telematics-usage-ingestion/              # Spring Boot (Port 8082)
├── maintenance-vehicle-health-ingestion/    # Spring Boot (Port 8083)
├── risk-scoring-engine/                     # FastAPI / Python (Port 8084)
├── pricing-policy/                          # Spring Boot (Port 8085)
├── claims-verification/                     # Spring Boot (Port 8086)
├── notification-advisory/                   # Spring Boot (Port 8087)
├── mobile_app/                              # Flutter Client Application (Web, Android, iOS, Windows, Mac)
│   ├── lib/
│   │   ├── config/api_config.dart           # Host & Base URL configuration
│   │   ├── models/                          # Data Transfer Objects & Serialization
│   │   ├── providers/                       # Riverpod state providers
│   │   ├── screens/                         # Onboarding, Dashboard, Diagnostics, Claims, Shell
│   │   ├── services/                        # Dio API clients, OBD Bluetooth service stub
│   │   └── theme/                           # Light/Dark UI design tokens
│   └── test/                                # Widget and model unit tests
├── infra/                                   # Infrastructure as Code (Placeholder README)
├── docs/                                    # System Design, Feasibility & API Contracts
├── scripts/
│   └── e2e_smoke.py                         # End-to-end integration & regression test suite
└── docker-compose.yml                       # Multi-stage local orchestration definition
```

---

## 5. Comprehensive Implementation Status (What is Built So Far)

### A. Microservices & Business Logic
- **Identity & Consent:** Vehicle owner registration, RC number capture, mock KYC verification (stateless `MockKycService`), and regulatory consent updating. Consent defaults strictly to opted-out for compliance.
- **Telematics Aggregation:** Accepts batches of OBD-II GPS events, computes distance using the Haversine formula, segments trips automatically if time gaps exceed 5 minutes, and calculates harsh braking/acceleration frequencies.
- **Vehicle Health & Maintenance:** Stores DTC readings (confirmed and pending fault codes), handles OEM and self-uploaded maintenance receipts, resolves conflicting odometer records (giving precedence to OEM/inspection), and maintains a structured recall store.
- **Risk Scoring Engine:** Computes maintenance adherence (0-100) and usage safety (0-100). Applies a −30 penalty for open recalls, up to −40 for overdue service, −15 for brake service gaps (>24 months), and up to −25 for harsh driving. Generates SHAP-style explainability factors.
- **Pricing Engine:** Applies ex-showroom valuation, calculates IRDAI vehicle depreciation (5% to 60%), applies No-Claim-Bonus (NCB) discounts (up to 50%), and interpolates dynamic risk multipliers (0.70× for score 100 to 1.50× for score 0).
- **Claims Verification (FNOL):** Ingests First Notice of Loss with damage cause classification. Automatically triggers `MANUAL_REVIEW` if mechanical failure is reported despite a clean service within 90 days or an open recall; otherwise awards `STRAIGHT_THROUGH_PROCESSING`.
- **Advisory Engine:** Evaluates vehicle health to produce prioritized advisories (`CRITICAL` for open recalls, `HIGH` for overdue servicing, `MEDIUM` for poor driving scores, `LOW` for safe driving praise).

### B. Deterministic Reference Vehicle Profiles (Demo Stories)

| Vehicle UUID | Profile Description | Expected Score / Pricing Dynamics |
| :--- | :--- | :--- |
| `a1b2c3d4-e5f6-7890-abcd-ef1234567890` | Story 1 (Normal / Perfect Maintainer) | Usage: 100, Maint: 100, Composite: 100. Multiplier: 0.70× (30% discount). Advisories: `SAFE_DRIVING_COMMENDATION`. |
| `b2c3d4e5-f6a7-8901-bcde-f12345678901` | Story 2 (Poor / Overdue + Surcharge) | Usage: 75, Maint: 30, Open Recall: True. Multiplier: >1.0× (surcharge). Advisories: `OPEN_RECALL`, `OVERDUE_SERVICE`, `LOW_RISK_SCORE`. |
| `c3d4e5f6-a7b8-9012-cdef-123456789012` | Story 3 (Conflicting Records) | Usage: 85, Conflicting odometer records detected and resolved in favour of inspection source. |
| `d4e5f6a7-b8c9-0123-defa-234567890123` | Story 4 (Unverified / Self-reported) | All receipts self-uploaded. Maintenance score capped at 85/100; badge marked "Unverified — self-reported only". Base premium: ₹11,700. |
| `e5f6a7b8-c9d0-1234-efab-345678901234` | Story 5 (Insufficient Data / New Enrolment) | Brand new luxury vehicle (<6 months). Null usage & maintenance scores. Dynamic multiplier: 1.0× (cold-start neutral). |

---

## 6. Deployment & Cloud Infrastructure Audit

> [!WARNING]
> **Cloud Deployment Status: Not Deployed to Cloud**  
> An exhaustive search of all configuration files, environment variables, GitHub Actions workflows, and remote repositories reveals that **PAYM is NOT deployed to Render, Supabase, Vercel, Firebase, Railway, AWS, or any other cloud provider.**

- **No Cloud Host Services:** There are no configurations or active URLs for Render, Vercel, Fly.io, or Railway anywhere in the repository.
- **No Cloud Database / Supabase:** Supabase is not configured. All microservices currently use in-memory `ConcurrentHashMap` collections. PostgreSQL connection properties in `application.yml` are commented out as Phase 4 targets.
- **Local Orchestration Only:** The system is configured exclusively for local workstation execution via `docker-compose.yml` using port bindings `8081` to `8087` and `localhost` / `10.0.2.2` (Android emulator).
- **Infra Directory Status:** The `infra/` folder contains only a skeleton `README.md` referencing planned Terraform, Kubernetes Helm charts, and Prometheus/Grafana configs, with the explicit note `_To be populated._`.

---

## 7. API Ingestion & External Integration Mapping

The system currently runs in a self-contained demonstration and test mode. **No live third-party internet APIs are pulled at runtime.** All external integrations are structured around the *Interface + Mock Bean Pattern*:

| External Domain | Interface Definition | Current Active Implementation | Planned Production Integration |
| :--- | :--- | :--- | :--- |
| **KYC Verification** | `KycService.java` | `MockKycService` (returns static `VERIFIED`) | Aadhaar e-KYC / PAN Verification API (NSDL/Karza) |
| **Document OCR** | `OcrStubService.java` | Echoes back submitted invoice metadata fields | Google Cloud Document AI / AWS Textract |
| **OEM Recall Catalog** | `RecallStore.java` | In-memory static catalog (hardcoded recall `RC-2026-0042`) | NHTSA Recall API / OEM Manufacturer Telematics APIs |
| **Notification Dispatch** | `NotificationDeliveryService.java` | Logs formatted messages to server console output | Firebase Cloud Messaging (FCM) & AWS SNS / Twilio |
| **OBD-II Bluetooth Dongle** | `ObdConnectionService.dart` | Simulates periodic timer readings and DTC fault codes | ELM327 Bluetooth Low Energy (BLE) stream parser |

---

## 8. Code & Logic Bug Audit (Detailed Findings)

During an in-depth static code analysis of the entire repository, several concrete bugs and architectural discrepancies were identified.

### 1. Flutter Web Crash: `Platform.isAndroid` in `api_config.dart`
- **Severity:** `CRITICAL`
- **Location:** `mobile_app/lib/config/api_config.dart` (lines 1 & 8)
- **Description:** The file imports `dart:io` and directly executes `if (Platform.isAndroid) return '10.0.2.2';`. In Dart, importing and accessing `Platform` from `dart:io` unconditionally throws an `UnsupportedError: Platform._operatingSystem` when executed on Flutter Web. Unless guarded by `kIsWeb` from `package:flutter/foundation.dart`, this crashes the web client on initial load.

### 2. Complete Absence of CORS Support Across All Backend Services
- **Severity:** `CRITICAL`
- **Location:** All Spring Boot Controllers and FastAPI `main.py`
- **Description:** None of the Spring Boot microservices configure `@CrossOrigin` or a global `WebMvcConfigurer` CORS bean, and FastAPI lacks `CORSMiddleware`. When the Flutter app is run as a Web client (e.g. at `http://localhost:5000`), web browsers send HTTP `OPTIONS` preflight requests. Because the backend does not return CORS headers (`Access-Control-Allow-Origin`), all API requests from the browser are blocked by the browser's Same-Origin Policy.

### 3. Docker Schema Resolution Failure in Python Risk Engine
- **Severity:** `HIGH`
- **Location:** `risk-scoring-engine/app/main.py` (line 16) vs `risk-scoring-engine/Dockerfile` (line 14)
- **Description:** `main.py` computes the schema path via `os.path.join(os.path.dirname(__file__), '..', '..', 'contracts', 'schemas', 'score.schema.json')`. In Docker, `WORKDIR /app` puts `main.py` at `/app/app/main.py` and schemas at `/app/contracts/schemas`. Traversing two levels up from `/app/app` resolves to `/contracts/schemas` (the root directory), causing `open()` to fail. The exception is swallowed, `SCORE_SCHEMA` becomes `None`, and contract validation is silently disabled inside Docker containers.

### 4. Enum Mismatch: Claims UI Rejects Backend Triage Decisions
- **Severity:** `HIGH`
- **Location:** `mobile_app/lib/screens/claims_screen.dart` (line 87) vs `claims-verification/.../TriageDecision.java`
- **Description:** The Flutter screen checks for `AUTO_APPROVED`, `MANUAL_REVIEW`, and `AUTO_DENIED`. However, the backend Java service exclusively issues `STRAIGHT_THROUGH_PROCESSING` and `MANUAL_REVIEW`. When a clean claim is processed automatically, the client fails to recognize `STRAIGHT_THROUGH_PROCESSING`, falls back to raw text, and styles the approval badge in neutral grey instead of success green.

### 5. Missing UI Priority Styling for `CRITICAL` Safety Advisories
- **Severity:** `MEDIUM`
- **Location:** `mobile_app/lib/screens/dashboard_screen.dart` (lines 320-324)
- **Description:** The advisory widget checks priorities: `HIGH` (Red), `MEDIUM` (Orange), and `LOW` (Blue). It completely omits the highest priority defined in the backend contract: `CRITICAL` (used for open safety recalls). Consequently, urgent safety recalls appear in muted grey rather than prominent warning red.

### 6. Currency Unit Discrepancy (Dollar vs Rupee)
- **Severity:** `MEDIUM`
- **Location:** `mobile_app/lib/screens/onboarding_screen.dart:193` & `dashboard_screen.dart:59`
- **Description:** The backend pricing policy calculates premiums in Indian Rupees (`INR` / `₹`) based on IRDAI valuation guidelines (e.g. ₹11,700). However, the mobile/web screens hardcode dollar symbols (`\$${quote.finalPremium}`), resulting in an incongruous display of thousands of dollars per month.

### 7. Volatile In-Memory Storage & Total Data Loss on Restart
- **Severity:** `MEDIUM`
- **Location:** All Spring Boot repositories (`RegistrationService`, `EventStore`, `DtcStore`, `ClaimStore`)
- **Description:** All entities are stored in Java `ConcurrentHashMap` memory stores. Any service restart, deployment redeploy, or container crash results in the immediate and permanent loss of all user registrations, consent records, uploaded DTC diagnostics, and claims.

### 8. Hardcoded Onboarding & Synthetic Photo References
- **Severity:** `LOW`
- **Location:** `mobile_app/lib/screens/onboarding_screen.dart` & `claims_screen.dart:339`
- **Description:** The onboarding screen lacks text inputs for RC number, name, email, or KYC; it posts hardcoded `MH12AB1234` on button press. In the FNOL claims screen, the "Add Photo Reference" button simply generates a string `photo_<timestamp>.jpg` without invoking an image picker or uploading files.

---

## 9. Unimplemented Features & Technical Gap Analysis

| Domain / Feature Area | Current State | Missing Capabilities to Implement |
| :--- | :--- | :--- |
| **Authentication & Security** | Public dev endpoints (`DEMO_MODE`). | JWT authentication, OAuth2, Spring Security filters, user sign-in/sign-up, role-based access for policyholders vs claims adjusters. |
| **Persistent Storage** | Volatile in-memory maps. | PostgreSQL schemas, Flyway/Liquibase database migrations, Spring Data JPA / Hibernate entities and repositories. |
| **Asynchronous Event Streaming** | Synchronous HTTP REST calls. | Kafka producers & consumers for high-throughput telematics event ingestion, dead-letter queues, and decoupled scoring. |
| **Real Document OCR** | Stub echoes form fields. | Document AI / AWS Textract integration for automated parsing and validation of vehicle RC books and repair bills. |
| **Live Bluetooth Telematics** | Simulated timer events. | ELM327 OBD-II Bluetooth Low Energy protocol implementation to stream live RPM, speed, throttle, and live DTC trouble codes. |
| **Payment & Policy Issuance** | Quote calculation only. | Payment gateway integration (Stripe / Razorpay), automated policy schedule generation, and PDF bond delivery. |
| **Claims Adjuster Portal** | Single claim lookup screen. | Back-office portal for insurance adjusters to review claims flagged for `MANUAL_REVIEW`, inspect evidence, and approve payouts. |

---

## 10. Strategic Recommendations & Next Feature Roadmap

1. **Immediate Bug Fixes (Zero Architecture Impact):**
   - Patch `api_config.dart` to check `kIsWeb` before referencing `Platform.isAndroid` to ensure Flutter Web loads cleanly.
   - Add a global Spring Boot `WebMvcConfigurer` bean allowing `@CrossOrigin("*")` and enable FastAPI `CORSMiddleware` so Web browsers can communicate with local backends.
   - Update `claims_screen.dart` to recognise `STRAIGHT_THROUGH_PROCESSING` and style it green.
   - Add `CRITICAL` priority styling to `dashboard_screen.dart`.
2. **Next Phase Features to Prioritize:**
   - *Option A: Real PostgreSQL Database Layer* — Replace in-memory stores with Spring Data JPA and PostgreSQL so demo data persists across restarts.
   - *Option B: User Authentication & Multi-Tenant Registration* — Build real onboarding forms in Flutter with JWT tokens and user profiles.
   - *Option C: Cloud Deployment (Render / Vercel / Supabase)* — Provision Render services for the microservices and deploy Flutter Web to Vercel/Firebase.
   - *Option D: Claims Adjuster Review Dashboard* — Build an administrative portal to review and decide on `MANUAL_REVIEW` claims.
