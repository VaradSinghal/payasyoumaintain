# Pay As You Maintain

An insurance pricing platform that rewards vehicle owners for proactive maintenance. Premiums are dynamically adjusted based on real-time telematics data, verified maintenance records, and vehicle health signals — incentivising safer, better-maintained vehicles on the road.

## Repository Structure

| Service | Tech Stack | Description |
|---|---|---|
| `identity-consent` | Java 21 / Spring Boot | Customer identity management and consent orchestration |
| `telematics-usage-ingestion` | Java 21 / Spring Boot | Ingestion pipeline for telematics and vehicle usage data |
| `maintenance-vehicle-health-ingestion` | Java 21 / Spring Boot | Ingestion pipeline for maintenance records and vehicle health signals |
| `risk-scoring-engine` | Python | ML-driven risk scoring based on maintenance and usage profiles |
| `pricing-policy` | Java 21 / Spring Boot | Dynamic premium calculation and policy management |
| `claims-verification` | Java 21 / Spring Boot | Claims intake, fraud detection, and verification workflows |
| `notification-advisory` | Java 21 / Spring Boot | Customer notifications and proactive maintenance advisories |
| `contracts` | Java 21 / Spring Boot | Smart contract and policy document lifecycle management |
| `infra` | Terraform / Docker | Infrastructure-as-code and deployment configurations |
| `mobile-app` | Flutter (Dart) | Customer-facing mobile application |

## Getting Started

```bash
# Start all services locally
docker compose up -d
```

## Prerequisites

- Java 21+
- Python 3.11+
- Flutter 3.x+
- Docker & Docker Compose
