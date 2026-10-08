# Contracts — Shared Data Schemas

**Owner:** Platform Team  
**Schema Format:** [JSON Schema (Draft 2020-12)](https://json-schema.org/draft/2020-12/schema)

## Purpose

This directory is the **single source of truth** for the data contracts that every service in the Pay As You Maintain platform builds against. Every module **must** validate payloads against these schemas at its service boundary — both when producing and consuming messages.

> **Rule:** If your service emits or accepts a payload defined here, it must validate against the corresponding schema. No exceptions.

## Schemas

| Schema | Version | Producers | Consumers | Description |
|---|---|---|---|---|
| [`usage-event.schema.json`](schemas/usage-event.schema.json) | v1.0.0 | telematics-usage-ingestion | risk-scoring-engine, notification-advisory | Trip-level telematics data point (GPS, speed, acceleration, braking) |
| [`maintenance-event.schema.json`](schemas/maintenance-event.schema.json) | v1.0.0 | maintenance-vehicle-health-ingestion | risk-scoring-engine, notification-advisory, claims-verification | Vehicle service / maintenance record |
| [`recall-status.schema.json`](schemas/recall-status.schema.json) | v1.0.0 | maintenance-vehicle-health-ingestion | risk-scoring-engine | Structured open-recall status from OEM recall database |
| [`score.schema.json`](schemas/score.schema.json) | v1.1.0 | risk-scoring-engine | pricing-policy, notification-advisory, mobile-app | Risk score with explainability factors |
| [`premium-request.schema.json`](schemas/premium-request.schema.json) | v1.0.0 | pricing-policy (internal) | pricing-policy | Request to compute a personalised premium |
| [`premium-response.schema.json`](schemas/premium-response.schema.json) | v1.0.0 | pricing-policy | mobile-app, notification-advisory, contracts | Computed premium with breakdown |
| [`dtc-reading.schema.json`](schemas/dtc-reading.schema.json) | v1.0.0 | maintenance-vehicle-health-ingestion | — | Diagnostic Trouble Codes reading from OBD device or manual entry |

## Versioning Policy

Schemas follow **Semantic Versioning** via the `$id` URI (e.g. `.../v1.0.0`).

| Change Type | Version Bump | Example |
|---|---|---|
| Add optional field | **Patch** (`1.0.0` → `1.0.1`) | Adding `notes` to maintenance-event |
| Add required field | **Minor** (`1.0.0` → `1.1.0`) | Adding `trip_id` as required to usage-event |
| Remove / rename field, change type | **Major** (`1.0.0` → `2.0.0`) | Renaming `speed_kmh` → `speed` |

### Change Process

1. **Open a PR** that modifies only files in `contracts/schemas/`.
2. **Bump the version** in the schema's `$id` field.
3. **Update this README** if producer/consumer mappings change.
4. **Obtain sign-off** from at least one owner of every consuming service before merging.
5. **Coordinate rollout** — consumers must deploy schema-compatible code before producers begin emitting the new version.

> [!CAUTION]
> **Breaking changes (major bumps) require a migration plan.** You must support the old schema version in parallel for at least one release cycle to allow consumers to migrate gracefully.

## Version Log

| Date | Schema | From | To | Type | Summary |
|---|---|---|---|---|---|
| 2026-09-22 | `recall-status.schema.json` | — | v1.0.0 | **New** | Introduced structured recall status schema. Replaces ad-hoc `OPEN RECALL` text parsing in `maintenance-event.notes`. Patch-equivalent additive addition. |
| 2026-10-07 | `score.schema.json` | v1.0.0 | v1.0.1 | **Patch** | Added optional `health_status`, `vehicle_health_score` (nullable), `health_confidence`, `renewal_recommendation`, `has_open_recall`, `data_flags` for the Phase A vehicle-health score. |
| 2026-10-08 | `score.schema.json` | v1.0.1 | v1.1.0 | **Minor** (type-widening, consumer-breaking) | `maintenance_score` (required), `composite_score` and `has_open_recall` widened from non-null to nullable. Previously these fields could only express a concrete value, so a `false` / `100` could actually mean "unknown" (recall source unavailable, or no service events on file). `null` now means unknown and must never be collapsed to `false` or a neutral number. Versioned as Minor per project decision; note the Versioning Policy table would classify a type change as Major, so consumers must still handle `null` before the producer emits it (see rollout rule 5). |
| 2026-10-08 | `dtc-reading.schema.json` | — | v1.0.0 | **New** | Introduced DTC reading schema (obd_device / manual_entry). Produced by maintenance-vehicle-health-ingestion, explicitly not wired into risk-scoring-engine yet. Patch-equivalent additive addition. |

### Null semantics in `score.schema.json` (v1.1.0)

- `has_open_recall`: `true` / `false` when known; `null` = unknown/unavailable (e.g. recall source timed out). Never collapse `null` to `false`.
- `maintenance_score`: `null` when `health_status` is `insufficient_data`.
- `composite_score`: **MUST be `null` whenever `maintenance_score` is `null`.** Producers and consumers must never silently fall back to a usage-only blend.
- Consumers (pricing-policy, notification-advisory, mobile-app) must handle `null` for all three fields before the producer starts emitting them.

## Validation Integration

Each tech stack should validate at the service boundary:

- **Java / Spring Boot** — use [`networknt/json-schema-validator`](https://github.com/networknt/json-schema-validator) or Spring's built-in `JsonSchemaValidator`.
- **Python** — use [`jsonschema`](https://pypi.org/project/jsonschema/) or [`fastjsonschema`](https://pypi.org/project/fastjsonschema/) for hot-path validation.
- **Flutter / Dart** — use [`json_schema`](https://pub.dev/packages/json_schema) for any server-synced payloads.

## Directory Structure

```
contracts/
├── README.md
└── schemas/
    ├── usage-event.schema.json
    ├── maintenance-event.schema.json
    ├── recall-status.schema.json
    ├── dtc-reading.schema.json
    ├── score.schema.json
    ├── premium-request.schema.json
    └── premium-response.schema.json
```
