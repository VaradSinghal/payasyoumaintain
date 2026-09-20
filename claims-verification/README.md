# Claims Verification Service

**Tech Stack:** Java 21 / Spring Boot

## Responsibility

This service manages claims intake, fraud detection, and verification workflows. It handles:

- **Claims Submission** — accepting first notice of loss (FNOL) and supporting document uploads.
- **Fraud Detection** — cross-referencing claims against telematics data, maintenance history, and known fraud patterns.
- **Verification Workflow** — orchestrating multi-step verification with adjusters, garages, and third-party inspectors.
- **Claims Decisioning** — approving, denying, or escalating claims based on policy terms and verification outcomes.
- **Settlement Processing** — calculating payouts and triggering disbursement workflows.

## API Contracts

_To be defined._

## Dependencies

- PostgreSQL (claims store)
- Object Storage / S3 (claims documents)
- Apache Kafka (event publishing)
