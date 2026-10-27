# Pay As You Maintain (PAYM)
## Master Presentation & System Architecture Guide

**Domain:** Dynamic Telemetry & Maintenance-Driven Usage-Based Auto Insurance (UBI)  
**Author:** Varad Singhal · [GitHub Repository](https://github.com/VaradSinghal/payasyoumaintain)  
**Target Audience:** Technical Evaluators, Insurance Actuaries, System Architects, Guidewire Portfolio Reviewers  
**Documentation Version:** 2.0 (Production / Demo-Ready)  

---

## Executive Overview

**Pay As You Maintain (PAYM)** is an enterprise-grade, cloud-native Usage-Based Insurance (UBI) platform that shifts automotive underwriting away from traditional static actuarial tables into a continuous, real-time risk assessment model.

Rather than pricing risk once a year based on demographic proxies (such as driver age, postal code, or credit score), PAYM continuously evaluates risk through two dynamic telemetry pillars:
1. **Driving Behaviour Telematics:** Real-time trip events capturing harsh braking and rapid acceleration.
2. **Maintenance & Vehicle Health Adherence:** Certified OEM service histories, self-uploaded workshop receipts, real-time Diagnostic Trouble Codes (DTCs), and active manufacturer safety recalls.

This guide divides the complete platform into **4 cohesive presentation modules**, complete with system architecture diagrams, mathematical formulations, API contracts, speaker notes, and an end-to-end live demo script.

---

## The 4-Part Presentation Framework

```
                             PAY AS YOU MAINTAIN (PAYM)
                                          │
    ┌──────────────────────┬──────────────┴───────────────┬──────────────────────┐
    ▼                      ▼                              ▼                      ▼
┌──────────────────┐ ┌────────────────────────┐ ┌────────────────────┐ ┌────────────────────────┐
│      PART 1      │ │         PART 2         │ │       PART 3       │ │         PART 4         │
│ Problem Space,   │ │ Resilient Telemetry    │ │ Explainable AI     │ │ Downstream Claims,     │
│ Architecture &   │ │ & Maintenance          │ │ Risk Scoring &     │ │ Proactive Advisories & │
│ Governance       │ │ Ingestion Engines      │ │ Actuarial Pricing  │ │ 5 Live Demo Personas   │
└──────────────────┘ └────────────────────────┘ └────────────────────┘ └────────────────────────┘
```

---

# PART 1: Problem Space, Architecture & Regulatory Foundations

### 1.1 The Industry Problem & Paradigm Shift
* **Static Actuarial Tables:** Traditional auto insurance assesses risk annually using demographic proxies (driver age, credit score, pin code, historical claims). Safe drivers subsidize aggressive drivers, and neglected vehicles go unnoticed until catastrophic failure occurs.
* **The Continuous UBI Solution:** PAYM continuously updates risk scores and policy quotes based on operational telemetry and verified maintenance adherence. Responsible maintenance and smooth driving are directly rewarded with lower premiums.

### 1.2 System Topology & Microservices Decomposition
PAYM is structured as a **polyglot microservices monorepo** adhering to Domain-Driven Design (DDD) principles:

```
                      ┌─────────────────────────────────────────────────────────┐
                      │          Flutter Client (Mobile / Web App)              │
                      └───────────────┬─────────────────────────┬───────────────┘
                                      │ (Port 5173 / Mobile)
                                      ▼
                      ┌─────────────────────────────────────────────────────────┐
                      │           API Gateway / Reverse Proxy (:8080)           │
                      └───────┬─────────────┬─────────────┬─────────────┬───────┘
                              │             │             │             │
              ┌───────────────┘             │             │             └───────────────┐
              │ (:8081)                     │ (:8085)     │ (:8086)                     │ (:8087)
              ▼                             ▼             ▼                             ▼
    ┌──────────────────┐          ┌──────────────────┐   ┌──────────────────┐   ┌──────────────────┐
    │ identity-consent │          │  pricing-policy  │   │claims-verificat. │   │notification-adv. │
    └──────────────────┘          └────────┬─────────┘   └────────┬─────────┘   └────────┬─────────┘
                                           │                      │                      │
                             ┌─────────────┼─────────────┐        │                      │
                             │             │             │        │                      │
                             ▼             ▼             ▼        │                      │
                     ┌───────────────┐     │      ┌───────────────┐◄┘                      │
                     │telematics-    │     │      │maintenance-   │                        │
                     │usage-ingestion│     │      │vehicle-health ├────────────────────────┘
                     │  (port 8082)  │     │      │  (port 8083)  │
                     └───────┬───────┘     │      └───────┬───────┘
                             │             │              │
                             │       ┌─────▼──────┐       │
                             └──────►│risk-scoring├───────┘
                                     │engine(8084)│
                                     └────────────┘
```

#### Technology Matrix by Component:
| Service | Runtime / Stack | Port | Bounded Context Responsibility |
| :--- | :--- | :--- | :--- |
| **`identity-consent`** | Java 21 / Spring Boot 3.3 | `8081` | Vehicle owner KYC, identity registry, granular opt-in/opt-out consent. |
| **`telematics-usage-ingestion`** | Java 21 / Spring Boot 3.3 | `8082` | OBD-II event stream ingestion, time-gap trip auto-segmentation, Haversine GPS. |
| **`maintenance-vehicle-health`** | Java 21 / Spring Boot 3.3 | `8083` | Multi-source service timeline, OCR receipt stub, structured OEM recall registry. |
| **`risk-scoring-engine`** | Python 3.12 / FastAPI / Pydantic | `8084` | Pure stateless actuarial scoring, SHAP-style explainability factors. |
| **`pricing-policy`** | Java 21 / Spring Boot 3.3 | `8085` | IRDAI IDV depreciation, NCB discount, dynamic risk multiplier, quote API. |
| **`claims-verification`** | Java 21 / Spring Boot 3.3 | `8086` | FNOL claims intake, service history fraud correlation, STP vs. manual triage. |
| **`notification-advisory`** | Java 21 / Spring Boot 3.3 | `8087` | Proactive prioritized alerts (safety recalls, overdue service, commendations). |
| **`mobile_app`** | Flutter 3.x / Dart 3.12 / Riverpod | `5173` | Cross-platform client with 5 live demo profiles, diagnostics, and claims. |

### 1.3 Contract-First Governance
* **Single Source of Truth:** All inter-service REST contracts are defined in `/contracts/schemas` using **JSON Schema Draft 2020-12**.
* **Zero Schema Drift:** Java microservices compile schemas into classpath artifacts via `maven-resources-plugin`. Python services validate payloads using `jsonschema`. Build pipelines (`.github/workflows/ci.yml`) fail automatically if any payload drifts from contract.

### 1.4 Regulatory Privacy & Consent Architecture (DPDP / GDPR)
* **Default Opt-Out Policy:** In compliance with global data protection standards (EU GDPR, India DPDP Act), telematics and maintenance tracking default to `opted_out = true`.
* **Graceful Degradation:** When tracking consent is withheld, the platform does not refuse insurance. Instead, it underwrites the vehicle using standard statutory baseline rates (multiplier locked at `1.00×`).

---

# PART 2: Resilient Telemetry & Maintenance Ingestion Engines

### 2.1 Telematics Usage Ingestion (`telematics-usage-ingestion` :8082)
The telematics engine processes high-frequency OBD-II driving events. It is engineered to handle intermittent mobile network connectivity and GPS noise:

* **Partial-Batch Acceptance:**
  In a batch of 100 events, if 98 are valid and 2 contain corrupted sensor data, the engine accepts the 98 valid events and returns structured validation errors for the 2. The entire batch is never rejected.
* **Time-Gap Auto-Segmentation:**
  When offline telematics loggers upload an entire day's backlog in a single payload without trip identifiers, consecutive events separated by $> 5$ minutes of inactivity are automatically segmented into distinct trips. This prevents parking periods from skewing average speed or trip duration metrics.
* **Haversine Distance Algorithm:**
  Calculates spherical GPS trip distance across sequential waypoints $(lat_1, lon_1) \rightarrow (lat_2, lon_2)$:
  $$a = \sin^2\left(\frac{\Delta lat}{2}\right) + \cos(lat_1)\cos(lat_2)\sin^2\left(\frac{\Delta lon}{2}\right)$$
  $$d = 2R \cdot \arcsin\left(\sqrt{a}\right)$$
* **Ethical Telematics (Commuter Fairness):**
  Raw highway speed is **deliberately excluded** from driving score deductions. Commuters driving at 100 km/h on clear highways are not penalized; only risky behaviors like **harsh braking** ($> 0.5$ events/trip) and **aggressive acceleration** trigger score penalties.

### 2.2 Vehicle Health & Multi-Source Maintenance (`maintenance-vehicle-health-ingestion` :8083)
The vehicle health service builds a chronological lifecycle of the vehicle across heterogeneous sources:

* **Dual-Track Service Timeline:**
  * **Verified OEM Dealership Records:** Ingested directly from certified service center APIs.
  * **Self-Uploaded Workshop Receipts:** Ingested via OCR parsing stub.
* **The 85-Score Self-Reporting Cap:**
  To prevent insurance fraud via fabricated invoices, self-reported service records can only elevate a vehicle's maintenance score up to a maximum cap of **85 / 100**. Achieving a perfect 100 score requires certified OEM verification.
* **Structured Safety Recall Registry (`RecallStore`):**
  Manufacturer recalls are tracked as first-class structured entities (`has_open_recall: true/false`), never inferred through unreliable free-text scraping in invoice notes.

---

# PART 3: Explainable AI Risk Scoring & Actuarial Pricing Engine

### 3.1 Dual-Pillar Risk Scoring Engine (`risk-scoring-engine` :8084)
The scoring engine is a stateless microservice written in Python/FastAPI that computes objective risk metrics from vehicle history:

#### A. Composite Score Formulation
$$\text{Composite Score} = (0.40 \times \text{Maintenance Score}) + (0.60 \times \text{Usage Score})$$

#### B. Deduction Rules:
1. **Maintenance Score (Base 100):**
   * **Overdue Service:** Deducts up to $-40$ points scaling with days elapsed beyond 365 days since the last certified service.
   * **Open Safety Recall:** Deducts $-30$ points immediately if `has_open_recall == true`.
   * **Brake Service Gap:** Deducts points if no brake-related inspection has occurred within the last 24 months.
2. **Usage Score (Base 100):**
   * **Harsh Braking:** Deducts $-15$ points if harsh braking exceeds $0.5$ events per trip.
   * **Hard Acceleration:** Deducts $-10$ points if rapid acceleration exceeds $0.5$ events per trip.

#### C. SHAP-Style Explainability Factors
Every scoring output returns a normalized `contributing_factors` array containing directional impacts (positive or negative) and human-readable explanations:
```json
{
  "composite_score": 51.0,
  "maintenance_score": 15.0,
  "usage_score": 75.0,
  "contributing_factors": [
    {
      "factor_name": "open_recall",
      "direction": "negative",
      "impact": 0.19,
      "description": "Vehicle has 1 open manufacturer recall(s)."
    },
    {
      "factor_name": "overdue_service",
      "direction": "negative",
      "impact": 0.25,
      "description": "Last service was 800 days ago."
    }
  ]
}
```

### 3.2 Dynamic Pricing & Underwriting Engine (`pricing-policy` :8085)
The pricing service orchestrates downstream telemetry and translates the composite risk score into a legally structured policy quote:

```
[Ex-Showroom Price] ──► [IRDAI Depreciation] ──► [IDV] ──► [Base OD Rate (2.6%)]
                                                                   │
                                                                   ▼
[Final Premium] ◄── [Dynamic Multiplier] ◄── [NCB Discount] ◄──────┘
```

#### Actuarial Steps:
1. **Insured Declared Value (IDV):** Derived from statutory Indian IRDAI depreciation slabs:
   * Age $< 6$ months: $5\%$ depreciation ($0.95 \times \text{Price}$)
   * Age $1\text{--}2$ years: $20\%$ depreciation ($0.80 \times \text{Price}$)
   * Age $4\text{--}5$ years: $50\%$ depreciation ($0.50 \times \text{Price}$)
   * Age $> 5$ years: $60\%$ depreciation ($0.40 \times \text{Price}$)
2. **Base Own Damage (OD) Premium:** Calculated as $2.6\%$ of IDV.
3. **No Claim Bonus (NCB):** Percentage discount deducted from Base OD.
4. **Dynamic Risk Multiplier:** Linear interpolation between 0.70× (best) and 1.50× (worst):
   $$\text{Dynamic Multiplier} = 1.50 - 0.80 \times \left(\frac{\text{Composite Score}}{100}\right)$$
   * Perfect Score ($100/100$) $\rightarrow \mathbf{0.700\times}$ (30% discount)
   * Poor Score ($51/100$) $\rightarrow \mathbf{1.092\times}$ (9.2% surcharge)
   * Zero Score ($0/100$) $\rightarrow \mathbf{1.500\times}$ (50% surcharge)
5. **Cold-Start Neutrality:** If a new vehicle has no historical telemetry (e.g. `e5f6a7b8`), the dynamic multiplier defaults to **1.000×** ("cold start neutral") without penalizing the customer.

---

# PART 4: Downstream Claims Triage, Proactive Advisories & Live Demo Personas

### 4.1 Automated FNOL Claims Triage (`claims-verification` :8086)
First Notice of Loss (FNOL) intake correlates incoming loss claims against the vehicle’s verified maintenance timeline:

```
Claim Received (FNOL)
       │
       ▼
Is cause "mechanical_failure"?
  ├─► NO  ──► [STRAIGHT-THROUGH PROCESSING (STP)] ──► Instant Approval
  └─► YES
       │
       ├─► Vehicle has Clean Service within 90 Days? ──► [MANUAL REVIEW] (Anomaly)
       ├─► Vehicle has Active Open Safety Recall?   ──► [MANUAL REVIEW] (Defect Risk)
       └─► Otherwise                                ──► [STRAIGHT-THROUGH PROCESSING (STP)]
```

* **Fraud Anomaly Detection:** A vehicle that underwent a certified inspection 2 weeks ago is statistically unlikely to suffer sudden mechanical breakdown. A mechanical claim immediately after a clean service indicates either workshop negligence or fraudulent wear-and-tear claim submission.
* **Configurable Regulatory Window:** The 90-day threshold is externalized to `claims.clean-service-window-days` in `application.yml`, allowing actuaries to tune it without code redeployment.

### 4.2 Proactive Notification & Advisory Engine (`notification-advisory` :8087)
Continuously evaluates vehicle health to push actionable notifications before accidents or costly breakdowns occur:
* **`CRITICAL` (Red):** Active manufacturer safety recall (`OPEN_RECALL`).
* **`HIGH` (Orange):** Service overdue $> 365$ days (`OVERDUE_SERVICE`) or zero service records on file (`NO_SERVICE_RECORD`).
* **`MEDIUM` (Amber):** Composite risk score $< 60$ warning of premium surcharges at renewal (`LOW_RISK_SCORE`).
* **`LOW` (Blue):** Safe driving commendation for scores $\ge 90$ (`SAFE_DRIVING_COMMENDATION`).

### 4.3 The 5 Live Demo Personas (Flutter Mobile / Web App)
The client interface features an interactive 1-tap switcher across the 5 reference personas:

| Vehicle Persona | UUID | Risk Scores | Multiplier | Premium (INR) | Key Live Demo Talking Point |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **1. The Model Citizen** | `a1b2c3d4...` | Usage: **100**<br>Maint: **100**<br>Comp: **100** | **0.700×** | Base: ₹24,960<br>**Final: ₹13,978** | Maximum 30% discount; clean service records; safe driver commendation badge. |
| **2. The Overdue Hatchback** | `b2c3d4e5...` | Usage: **75**<br>Maint: **15**<br>Comp: **51** | **1.092×** | Base: ₹12,480<br>**Final: ₹12,265** | **Open safety recall banner**; overdue service; mechanical claim triggers **MANUAL REVIEW**. |
| **3. Conflicting Records** | `c3d4e5f6...` | Usage: **85**<br>Maint: **100**<br>Comp: **91** | **0.772×** | Base: ₹27,300<br>**Final: ₹21,076** | Conflicting mileage in records; data cleansing defaults safely to certified OEM log. |
| **4. The Self-Reporter** | `d4e5f6a7...` | Usage: **100**<br>Maint: **85**<br>Comp: **94** | **0.748×** | Base: ₹11,700<br>**Final: ₹6,564** | Shows **amber badge** explaining the **85-score cap** for self-reported records. |
| **5. Brand New Vehicle** | `e5f6a7b8...` | Usage: `null`<br>Maint: `null`<br>Comp: `null` | **1.000×** | Base: ₹44,460<br>**Final: ₹44,460** | **Cold-start neutrality**: ₹44,460 standard baseline quote without penalties. |

---

## Live Presentation Script (15-Minute Delivery Guide)

```
00:00 ── Part 1: Problem & Architecture (Why UBI, DDD, JSON Schemas)
03:30 ── Part 2: Ingestion & Telemetry (Partial-batch, time-gap, recalls)
07:00 ── Part 3: Scoring & Pricing (Dual pillars, SHAP factors, IDV slabs)
11:00 ── Part 4: Downstream Claims & Live App Demo (5 personas, FNOL triage)
15:00 ── Evaluator Q&A
```

### Minute 00:00 – 03:30 | Part 1: Foundations & Architecture
1. Open the slides or README. Explain the core philosophy: *"Traditional auto insurance is static and unfair. PAYM continuously recalculates risk using real-time IoT telematics and certified maintenance logs."*
2. Highlight the polyglot monorepo: Java 21 Spring Boot microservices, Python FastAPI risk scoring, and Flutter client.
3. Emphasize **Contract-First Design**: Show `/contracts/schemas` and explain how JSON Schema Draft 2020-12 prevents schema drift across teams and CI/CD.

### Minute 03:30 – 07:00 | Part 2: Ingestion Engines
1. Explain how real-world OBD data is messy. Detail **Partial-Batch Acceptance**: valid events are never dropped due to isolated GPS errors.
2. Explain **Time-Gap Auto-Segmentation**: breaking disconnected batch uploads into discrete trips without diluting speed averages.
3. Discuss the **Ethical Speed Exclusion**: highway driving speed is excluded to protect highway commuters.
4. Detail the **Multi-Source Maintenance Timeline**: distinguishing between OEM-verified logs and self-reported receipts (with the 85-score cap).

### Minute 07:00 – 11:00 | Part 3: Risk Scoring & Pricing Engine
1. Walk through the dual-pillar equation: $(0.40 \times \text{Maintenance}) + (0.60 \times \text{Usage})$.
2. Explain **SHAP-style explainability**: how the system provides policyholders and regulators with transparent contributing factors.
3. Review the actuarial pricing pipeline: IRDAI IDV depreciation slabs $\rightarrow$ Base OD Rate ($2.6\%$) $\rightarrow$ NCB deduction $\rightarrow$ Dynamic multiplier ($0.70\times$ to $1.50\times$).
4. Emphasize **Cold-Start Neutrality**: brand-new drivers receive a neutral 1.00× multiplier rather than an unearned surcharge.

### Minute 11:00 – 15:00 | Part 4: Live Product Walkthrough
1. Switch to the active Flutter application (`http://localhost:5173` or desktop build).
2. **Select Vehicle 1 (The Model Citizen):** Show the perfect 100 score, 0.700× multiplier, and the discounted ₹13,978 premium.
3. **Select Vehicle 2 (The Overdue Hatchback):** Point out the prominent red **URGENT: Open Safety Recall** banner, the 51 composite score, and the 1.092× surcharge.
4. **Navigate to Claims Tab on Vehicle 2:** Click *"Quick-fill suggested demo claim"* for `mechanical_failure`. Submit the claim and demonstrate the automated triage decision: **MANUAL REVIEW** with reason: `open_recall_on_file`.
5. **Select Vehicle 4 (The Self-Reporter):** Tap the amber badge *"Includes self-reported records"* to display the dialog explaining the 85-score ceiling.
6. **Select Vehicle 5 (Brand New):** Point out the cold-start neutral state: *"Not enough data yet"* with standard ₹44,460 baseline pricing.

---

## Technical Verification & Execution Cheat Sheet

To run the complete verified stack locally:

```bash
# 1. Start all 7 backend microservices in Docker:
docker compose up -d --build

# 2. Reset and seed all 5 reference vehicle profiles:
python scripts/demo_reset.py

# 3. Execute the full end-to-end preflight verification:
python scripts/contract_check.py
python scripts/validate_fixtures.py
python scripts/demo_preflight.py

# 4. Launch the Flutter Web or Desktop Application:
cd mobile_app
flutter run -d chrome --web-port 5173
```

### Complete Test Coverage Matrix:
* **Java Microservices (JUnit 5 & Mockito):** 88 unit and integration tests.
* **Python Risk Scoring Engine (Pytest):** 6 actuarial test suites.
* **Flutter Client (Widget & Provider Tests):** 82 unit and widget tests.
* **Live Stack UI Integration (Flutter Driver):** 11 end-to-end scenarios (`live_stack_e2e_test.dart`).
* **Total Automated Test Suites:** **187 passing automated tests**.
