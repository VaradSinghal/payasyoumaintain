# Contributing Guidelines

Thank you for contributing to Pay-As-You-Maintain (PAYM). To maintain repository stability, test parity, and architectural integrity, please adhere to the following workflow:

## 1. Branching & Pull Requests
- **Protected Main**: Changes to `main` are made strictly through Pull Requests with green CI checks. Direct pushes to `main` are prohibited.
- **Branch Naming**: Feature and fix branches must follow standard prefix conventions:
  - `feat/<feature-name>` for new features or capabilities
  - `fix/<bug-or-issue-description>` for bug fixes and patches
  - `docs/<documentation-topic>` for documentation updates

## 2. Pre-PR Verification
Before opening a Pull Request that touches any backend service or the gateway:
- Start the full stack with the direct-port development override:
  ```bash
  docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build --wait
  ```
- Run both end-to-end smoke verification and gateway parity verification:
  ```bash
  python scripts/e2e_smoke.py
  python scripts/gateway_parity.py
  ```
- Both scripts must pass with exit code `0` prior to submitting your PR.

## 3. API Contracts & Schema Changes
- Any modification to a JSON request or response shape must update the corresponding schema definition in `contracts/schemas/` within the same Pull Request.
- Contract updates and service implementation changes must not be split across separate PRs.

## 4. Repository Hygiene & Scratch Files
- Never commit scratch files, temporary test scripts, or captured API output outside `mobile_app/test/fixtures`.
- Keep the repository clean of ad-hoc runner scripts (e.g., stray `*.ps1`, `payload*.json`, `fnol.json`, or standalone `quote_*.json`).

## 5. Gateway Allowlist & Public Surface
- The hardened API Gateway allowlist (`gateway/nginx.conf`) is the **only** public surface exposed to external clients and mobile apps. Internal service ports (8081–8087) are not published in production.
- Any new backend route or endpoint intended for client consumption requires:
  1. An explicit routing rule in `gateway/nginx.conf`.
  2. A corresponding test assertion in `scripts/gateway_parity.py` comparing the proxied route against the direct service route.

## 6. Continuous Integration (CI)
Automated GitHub Actions workflows run on all pull requests and pushes to `main`:
- **`CI` (`.github/workflows/ci.yml`)**:
  - Validates JSON contracts and verifies mock fixtures against schemas (`python scripts/validate_fixtures.py`).
  - Runs unit tests for each Java microservice, Python risk engine, and Flutter client.
- **`Stack E2E` (`.github/workflows/stack-e2e.yml`)**:
  - Boots the complete containerized stack using `docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build --wait --wait-timeout 300`.
  - Runs the live end-to-end smoke suite (`python scripts/e2e_smoke.py`) and gateway route parity suite (`python scripts/gateway_parity.py`).
  - Automatically captures container diagnostic logs (`docker compose logs --no-color --tail=200`) and uploads them as workflow artifacts on failure.
  - *Note*: The stack E2E workflow exercises the live microservices and gateway; it does not boot mobile device emulators or execute Flutter UI integration tests in CI.
