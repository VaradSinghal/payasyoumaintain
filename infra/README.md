# Infrastructure

**Tech Stack:** Terraform / Docker / Kubernetes

## Responsibility

This directory contains all infrastructure-as-code and deployment configurations for the platform. It covers:

- **Terraform Modules** — provisioning cloud resources (VPCs, databases, message brokers, object storage, IAM).
- **Kubernetes Manifests** — Helm charts and K8s manifests for service deployments, ingress, and autoscaling.
- **CI/CD Pipelines** — GitHub Actions / pipeline definitions for build, test, and deploy workflows.
- **Observability** — Prometheus, Grafana, and OpenTelemetry configuration for metrics, logs, and tracing.
- **Secrets Management** — Vault or cloud-native secrets configuration.

## Directory Structure

```
infra/
├── terraform/        # Cloud resource provisioning
├── k8s/              # Kubernetes manifests & Helm charts
├── ci/               # CI/CD pipeline definitions
└── observability/    # Monitoring & tracing config
```

_To be populated._
