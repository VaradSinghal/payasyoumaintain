# Risk Scoring Engine

**Tech Stack:** Python

## Responsibility

This service is the ML-driven risk scoring engine for the platform. It handles:

- **Risk Model Inference** — running trained models to produce per-customer risk scores from telematics, maintenance, and vehicle health features.
- **Feature Engineering** — transforming raw ingested data into model-ready feature vectors.
- **Model Lifecycle Management** — versioning, A/B testing, and promoting models through staging → production.
- **Batch & Real-Time Scoring** — supporting both scheduled batch scoring and on-demand real-time scoring via API.
- **Explainability** — generating SHAP / feature-importance explanations for regulatory and customer-facing transparency.

## API Contracts

_To be defined._

## Dependencies

- Apache Kafka (event consumption)
- PostgreSQL (feature store / score persistence)
- MLflow (model registry)
