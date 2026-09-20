# Identity & Consent Service

**Tech Stack:** Java 21 / Spring Boot

## Responsibility

This service owns customer identity management and consent orchestration for the Pay As You Maintain platform. It handles:

- **Customer Registration & Authentication** — onboarding, login, and session management.
- **KYC / Identity Verification** — integration with identity verification providers.
- **Consent Management** — capturing, storing, and enforcing customer consent for data collection (telematics, maintenance records, etc.).
- **Profile Management** — maintaining customer profile data and preferences.
- **Access Control** — issuing and validating tokens for downstream service authorisation.

## API Contracts

_To be defined._

## Dependencies

- PostgreSQL (user store)
- Redis (session cache)
