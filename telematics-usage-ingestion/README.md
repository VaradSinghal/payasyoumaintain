# Telematics & Usage Ingestion Service

**Tech Stack:** Java 21 / Spring Boot

## Responsibility

This service is the ingestion pipeline for telematics and vehicle usage data. It handles:

- **OBD-II / IoT Data Ingestion** — receiving real-time data streams from on-board diagnostic devices and connected car platforms.
- **Driving Behaviour Signals** — capturing acceleration, braking, cornering, speed, and mileage events.
- **Usage Pattern Aggregation** — computing trip summaries, daily/weekly usage profiles, and driving scores.
- **Data Normalisation** — transforming heterogeneous device payloads into a canonical schema for downstream consumers.
- **Event Publishing** — streaming normalised events to the risk-scoring-engine and other subscribers via message broker.

## API Contracts

_To be defined._

## Dependencies

- Apache Kafka (event streaming)
- TimescaleDB / PostgreSQL (time-series storage)
