# Pricing & Policy Service

**Tech Stack:** Java 21 / Spring Boot

## Responsibility

This service owns dynamic premium calculation and policy lifecycle management. It handles:

- **Premium Calculation** — computing personalised premiums based on risk scores, maintenance adherence, usage patterns, and actuarial tables.
- **Discount & Incentive Engine** — applying dynamic discounts for good maintenance behaviour, safe driving, and loyalty.
- **Policy CRUD** — creating, updating, renewing, and cancelling insurance policies.
- **Underwriting Rules** — enforcing business rules, coverage limits, and regulatory constraints.
- **Quote Generation** — producing customer-facing quotes with breakdowns and comparisons.

## API Contracts

_To be defined._

## Dependencies

- PostgreSQL (policy store)
- risk-scoring-engine (risk score input)
