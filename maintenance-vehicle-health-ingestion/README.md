# Maintenance & Vehicle Health Ingestion Service

**Tech Stack:** Java 21 / Spring Boot

## Responsibility

This service ingests maintenance records and vehicle health signals. It handles:

- **Service Record Ingestion** — capturing maintenance and repair records from garages, dealerships, and self-reported entries.
- **Vehicle Health Monitoring** — processing DTC (Diagnostic Trouble Code) alerts and predictive health indicators from connected vehicles.
- **Document Verification** — validating uploaded invoices, receipts, and service logs for authenticity.
- **Maintenance Score Calculation** — computing a maintenance adherence score based on OEM-recommended schedules vs. actual service history.
- **Event Publishing** — streaming verified maintenance events to the risk-scoring-engine and notification-advisory services.

## API Contracts

_To be defined._

## Dependencies

- Apache Kafka (event streaming)
- PostgreSQL (maintenance record store)
- Object Storage / S3 (document uploads)
