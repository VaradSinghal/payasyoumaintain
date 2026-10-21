# Risk Scoring Engine

**Tech Stack:** Python

## Responsibility

This service is the ML-driven risk scoring engine for the platform. It handles:

- **Risk Model Inference** — running trained models to produce per-customer risk scores from telematics, maintenance, and vehicle health features.
- **Feature Engineering** — transforming raw ingested data into model-ready feature vectors.
- **Model Lifecycle Management** — versioning, A/B testing, and promoting models through staging → production.
- **Batch & Real-Time Scoring** — supporting both scheduled batch scoring and on-demand real-time scoring via API.
- **Explainability** — generating SHAP / feature-importance explanations for regulatory and customer-facing transparency.

## Vehicle Health Scoring & Self-Report Credit Cap

The vehicle health scoring model deterministically evaluates maintenance records (`oem_api`, `inspection`, and accepted `self_upload` events), open recall status, and safety-critical service intervals (such as brake services).

### Self-Report Credit Cap Rule
- **Trusted baseline (`trusted`)**: The health score computed solely from verified OEM API (`oem_api`) and certified inspection (`inspection`) records.
- **Full score (`all`)**: The health score computed from all accepted records (after conflict resolution).
- **Final score calculation**:
  - If no trusted records exist: `final = min(all, 85)`.
  - If trusted records exist: `final = min(all, max(trusted, 85))`.
- **Health Confidence**:
  - `health_confidence` is labelled `unverified_self_reported` whenever `final > trusted` (or when no trusted records exist).
  - Otherwise, `health_confidence` is `verified`.
- Vehicles with zero service events remain `insufficient_data` (maintenance score is `null`).

### Remaining Limitations
- **Plausible Fake Record Exposure**: A plausible self-reported service record can still lift a poor OEM maintenance history up to the cap of 85 (labelled `unverified_self_reported`) until a verified source confirms or refutes it.
- **Phase 4 Roadmap**: Integration of garage/dealer network confirmations, automated receipt OCR with merchant verification, and partner workshop digital signatures.

## Dependencies

- Apache Kafka (event consumption)
- PostgreSQL (feature store / score persistence)
- MLflow (model registry)

