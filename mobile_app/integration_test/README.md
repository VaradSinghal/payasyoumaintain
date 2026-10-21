# Mobile App Live Stack E2E Integration Test

This directory contains the permanent end-to-end integration test (`live_stack_e2e_test.dart`) that tests the real Flutter UI against a live running Pay-As-You-Maintain backend stack.

## Scenarios Covered

The test executes scenarios `a` through `h`:
- **Scenario a**: Demo flow selection ("The Model Citizen") -> displays ₹13,977.60 quote.
- **Scenario b**: Privacy & tracking toggles turned OFF -> verifies "Tracking is off — standard pricing" card, standard quote ₹19,968.00, and score bars hidden.
- **Scenario c**: Privacy & tracking toggles restored to ON -> verifies ₹13,977.60 restored and score bars return.
- **Scenario d**: "Why this score?" screen -> verifies service history records render with humanized OEM badge ("OEM" instead of "oem_api") and trips are listed.
- **Scenario e**: Vehicle switcher -> selects "The Overdue Hatchback", verifying open safety recall banner and ₹12,265.34 premium.
- **Scenario f**: Vehicle onboarding registration with fictional demo details (sedan_mid, 2 years ago) -> consent toggles default to OFF, shows baseline quote ₹24,960.00, tracking-off card, and "How we priced your vehicle" naming "Mid-size sedan" and "Your registered vehicle"; turning toggles ON removes the card while pricing section still names "Mid-size sedan".
- **Scenario g**: Switch to "The Overdue Hatchback", file `mechanical_failure` claim -> transitions to claim status displaying `Requires Manual Review` triage outcome, `open_recall_on_file` triage reason, and claim ID.
- **Scenario h**: Diagnostics manual reading with confirmed code `P0301`, navigate away and back to confirm code persists; submit manual reading with check engine light off (no codes), confirming screen returns to "checked, no faults".

## Prerequisites

1. **Docker Compose Stack**:
   Default compose stack running with all backend services healthy and API gateway on port 8080:
   ```bash
   docker compose up -d --build --wait
   ```
2. **Seed Vehicles**:
   The standard seed vehicles must be populated (including `a1b2c3d4-e5f6-7890-abcd-ef1234567890` and `b2c3d4e5-f6a7-8901-bcde-f12345678901`).
3. **Identity-Consent Service**:
   `identity-consent` service must be running and healthy:
   ```bash
   curl -s http://localhost:8080/health/pricing
   ```

## Running the Test

Run the integration test against the Windows desktop runner (or another connected device):

```bash
flutter test integration_test/live_stack_e2e_test.dart -d windows --dart-define=API_BASE_URL=http://localhost:8080
```

To run on a specific device, list devices with `flutter devices` and substitute the device ID for `windows`.

## Restore Step

The test automatically issues an HTTP PUT request at the end of execution to restore `a1b2` consent preferences to `true`/`true`.

To manually restore the baseline consent state and stack at any time:

```bash
curl -X PUT http://localhost:8080/identity/api/v1/vehicles/a1b2c3d4-e5f6-7890-abcd-ef1234567890/consent \
  -H "Content-Type: application/json" \
  -d '{"usage_tracking_opt_in": true, "maintenance_tracking_opt_in": true}'
```

If `identity-consent` was stopped during manual testing, restart it:

```bash
docker compose restart identity-consent
```
