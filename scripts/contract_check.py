#!/usr/bin/env python3
"""Live contract verification against running Pay As You Maintain gateway.

Calls every allowlisted route through the gateway for the five demo vehicles
and a freshly registered vehicle, validating each response against its contract schema.
Also tests all six FNOL causes, negative cases, gateway error sweep, leak scanning,
and exact status code verification.
"""

import argparse
import datetime
import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path
from jsonschema import Draft202012Validator


LEAK_STRINGS = ["com.", "Exception", "Cannot deserialize", "JSON parse error"]


def find_repo_root() -> Path:
    current = Path(__file__).resolve().parent
    if (current.parent / "contracts" / "schemas").is_dir():
        return current.parent
    cwd = Path.cwd()
    if (cwd / "contracts" / "schemas").is_dir():
        return cwd
    raise RuntimeError("Could not find repository root containing contracts/schemas")


def http_request(url: str, method: str = "GET", body=None, headers: dict | None = None):
    headers = headers or {}
    data = None
    if isinstance(body, (bytes, bytearray)):
        data = body
        headers.setdefault("Content-Type", "application/json")
    elif body is not None:
        data = json.dumps(body).encode("utf-8")
        headers.setdefault("Content-Type", "application/json")

    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            code = resp.getcode()
            content = resp.read().decode("utf-8")
            parsed = json.loads(content) if content else None
            return code, parsed, content
    except urllib.error.HTTPError as e:
        content = e.read().decode("utf-8")
        try:
            parsed = json.loads(content) if content else None
        except Exception:
            parsed = content
        return e.code, parsed, content
    except Exception as e:
        return 0, str(e), str(e)


def generate_status_table_markdown(observed_entries: set[tuple[str, str, int]]) -> str:
    sorted_entries = sorted(observed_entries, key=lambda x: (x[0], x[1], x[2]))
    lines = [
        "# Verified HTTP Status Codes",
        "",
        "Generated automatically by `scripts/contract_check.py`. Do not edit manually.",
        "",
        "| Method | Route Template | Status Code |",
        "|---|---|---|",
    ]
    for method, route, status in sorted_entries:
        lines.append(f"| {method} | {route} | {status} |")
    lines.append("")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description="Live contract verification suite.")
    parser.add_argument(
        "--write-status-table",
        metavar="FILE",
        help="Write observed status codes markdown table to FILE",
    )
    parser.add_argument(
        "--check-status-table",
        metavar="FILE",
        help="Check that committed status codes markdown table in FILE matches observed",
    )
    args = parser.parse_args()

    gateway_url = os.environ.get("GATEWAY_URL", "http://localhost:8080").rstrip("/")
    repo_root = find_repo_root()
    schemas_dir = repo_root / "contracts" / "schemas"

    print("==================================================================")
    print(" Live Contract Verification Suite")
    print(f" Target Gateway: {gateway_url}")
    print("==================================================================")

    # Load contract schemas
    schemas = {
        "vehicle-catalogue": json.loads((schemas_dir / "vehicle-catalogue.schema.json").read_text(encoding="utf-8")),
        "vehicle-registration-response": json.loads((schemas_dir / "vehicle-registration-response.schema.json").read_text(encoding="utf-8")),
        "consent-record": json.loads((schemas_dir / "consent-record.schema.json").read_text(encoding="utf-8")),
        "premium-response": json.loads((schemas_dir / "premium-response.schema.json").read_text(encoding="utf-8")),
        "advisory-response": json.loads((schemas_dir / "advisory-response.schema.json").read_text(encoding="utf-8")),
        "fnol-request": json.loads((schemas_dir / "fnol-request.schema.json").read_text(encoding="utf-8")),
        "fnol-response": json.loads((schemas_dir / "fnol-response.schema.json").read_text(encoding="utf-8")),
        "service-timeline": json.loads((schemas_dir / "service-timeline.schema.json").read_text(encoding="utf-8")),
        "dtc-reading": json.loads((schemas_dir / "dtc-reading.schema.json").read_text(encoding="utf-8")),
        "trip-aggregate": json.loads((schemas_dir / "trip-aggregate.schema.json").read_text(encoding="utf-8")),
        "error-response": json.loads((schemas_dir / "error-response.schema.json").read_text(encoding="utf-8")),
    }
    validators = {k: Draft202012Validator(v) for k, v in schemas.items()}

    total_passed = 0
    total_failed = 0
    observed_statuses: set[tuple[str, str, int]] = set()

    def scan_for_leaks(label: str, raw_text: str) -> bool:
        nonlocal total_passed, total_failed
        leaks = [s for s in LEAK_STRINGS if s in raw_text]
        if leaks:
            total_failed += 1
            print(f"[FAIL] Internal leak detected in {label}: found strings {leaks}")
            return False
        total_passed += 1
        print(f"[PASS] Leak scan clean for {label}")
        return True

    def check_response(route_label: str, schema_key: str, data, is_array: bool = False) -> bool:
        nonlocal total_passed, total_failed
        val = validators[schema_key]
        errors = []

        if is_array:
            if not isinstance(data, list):
                errors.append(f"Expected array, got {type(data).__name__}")
            else:
                for idx, item in enumerate(data):
                    for err in val.iter_errors(item):
                        err_path = f"[{idx}]." + ".".join(str(p) for p in err.path) if err.path else f"[{idx}]"
                        errors.append(f"{err_path}: {err.message}")
        else:
            for err in val.iter_errors(data):
                err_path = ".".join(str(p) for p in err.path) if err.path else "(root)"
                errors.append(f"{err_path}: {err.message}")

        if errors:
            total_failed += 1
            print(f"[FAIL] {route_label} ({schema_key})")
            for err in errors[:5]:
                print(f"       error: {err}")
            return False
        else:
            total_passed += 1
            print(f"[PASS] {route_label} ({schema_key})")
            return True

    def call_and_record(
        route_template: str,
        url: str,
        method: str = "GET",
        body=None,
        expected_status: int = 200,
        label: str | None = None,
    ) -> tuple[int, any, str]:
        nonlocal total_passed, total_failed
        lbl = label or f"{method} {route_template}"
        code, parsed, raw_text = http_request(url, method=method, body=body)

        # Record exact observed (method, route_template, status)
        observed_statuses.add((method, route_template, code))

        # Exact status assertion (no "any 2xx")
        if code == expected_status:
            total_passed += 1
            print(f"[PASS] {lbl} -> status {code} (expected {expected_status})")
        else:
            total_failed += 1
            print(f"[FAIL] {lbl} -> expected status {expected_status}, got {code}")

        # If error body (status >= 400), scan for internal leaks
        if code >= 400:
            scan_for_leaks(lbl, raw_text)

        return code, parsed, raw_text

    # 1. Global Route: Vehicle Catalogue
    code, catalogue, _ = call_and_record(
        "/pricing/api/v1/vehicle-catalogue",
        f"{gateway_url}/pricing/api/v1/vehicle-catalogue",
        method="GET",
        expected_status=200,
    )
    if code == 200:
        check_response("GET /pricing/api/v1/vehicle-catalogue", "vehicle-catalogue", catalogue)

    # 2. Fresh Vehicle Registration (using relative registration date)
    fresh_rc = f"MH01ZZ{int(time.time()) % 10000:04d}"
    fresh_reg_date = (datetime.date.today() - datetime.timedelta(days=730)).isoformat()
    recent_incident_date = (datetime.date.today() - datetime.timedelta(days=5)).isoformat()
    now_timestamp_str = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    fresh_reg_body = {
        "rc_number": fresh_rc,
        "registration_date": fresh_reg_date,
        "vehicle_segment_id": "hatchback_entry",
        "owner_details": {
            "full_name": "Fresh Test Owner",
            "kyc_document_number": "123456789012",
            "kyc_document_type": "AADHAAR",
            "email": "fresh.test@example.com",
            "phone_number": "9876543210"
        }
    }
    code, fresh_reg_resp, _ = call_and_record(
        "/identity/api/v1/vehicles",
        f"{gateway_url}/identity/api/v1/vehicles",
        method="POST",
        body=fresh_reg_body,
        expected_status=201,
        label="POST /identity/api/v1/vehicles (fresh registration)",
    )
    if code == 201:
        check_response("POST /identity/api/v1/vehicles (fresh)", "vehicle-registration-response", fresh_reg_resp)
        fresh_vehicle_id = fresh_reg_resp.get("vehicle_id")
        fresh_policy_id = fresh_reg_resp.get("policy_id")
    else:
        fresh_vehicle_id = None
        fresh_policy_id = None

    # Define vehicle list: 5 demo vehicles + 1 fresh
    demo_vehicles = [
        {"id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890", "policy_id": "c4632682-d406-3102-86b7-6a22ef09af5d", "label": "demo_veh_001"},
        {"id": "b2c3d4e5-f6a7-8901-bcde-f12345678901", "policy_id": "4c4caec8-846d-31d6-af88-43c25c038aa4", "label": "demo_veh_002"},
        {"id": "c3d4e5f6-a7b8-9012-cdef-123456789012", "policy_id": "f613e582-17e0-37cf-b317-856cbca14248", "label": "demo_veh_003"},
        {"id": "d4e5f6a7-b8c9-0123-defa-234567890123", "policy_id": "33c7f064-f8cf-3ee4-af52-1453fd8e713f", "label": "demo_veh_004"},
        {"id": "e5f6a7b8-c9d0-1234-efab-345678901234", "policy_id": "a2aa245d-ecbc-3719-b180-cfc5ca5c3866", "label": "demo_veh_005"},
    ]
    if fresh_vehicle_id and fresh_policy_id:
        demo_vehicles.append({"id": fresh_vehicle_id, "policy_id": fresh_policy_id, "label": "fresh_vehicle"})

    # 3. Test all allowlisted routes for each vehicle
    for v in demo_vehicles:
        vid = v["id"]
        pid = v["policy_id"]
        vlabel = v["label"]

        # GET /identity/api/v1/vehicles/{id} (expected 200)
        code, body, _ = call_and_record(
            "/identity/api/v1/vehicles/{id}",
            f"{gateway_url}/identity/api/v1/vehicles/{vid}",
            method="GET",
            expected_status=200,
            label=f"GET /identity/api/v1/vehicles/{vid} [{vlabel}]",
        )
        if code == 200:
            check_response(f"GET /identity/api/v1/vehicles/{vid} [{vlabel}]", "vehicle-registration-response", body)

        # GET /identity/api/v1/vehicles/{id}/consent (expected 200)
        code, body, _ = call_and_record(
            "/identity/api/v1/vehicles/{id}/consent",
            f"{gateway_url}/identity/api/v1/vehicles/{vid}/consent",
            method="GET",
            expected_status=200,
            label=f"GET /identity/api/v1/vehicles/{vid}/consent [{vlabel}]",
        )
        if code == 200:
            check_response(f"GET /identity/api/v1/vehicles/{vid}/consent [{vlabel}]", "consent-record", body)

        # PUT /identity/api/v1/vehicles/{id}/consent (expected 200)
        put_consent_body = {"usage_tracking_opt_in": True, "maintenance_tracking_opt_in": True}
        code, body, _ = call_and_record(
            "/identity/api/v1/vehicles/{id}/consent",
            f"{gateway_url}/identity/api/v1/vehicles/{vid}/consent",
            method="PUT",
            body=put_consent_body,
            expected_status=200,
            label=f"PUT /identity/api/v1/vehicles/{vid}/consent [{vlabel}]",
        )
        if code == 200:
            check_response(f"PUT /identity/api/v1/vehicles/{vid}/consent [{vlabel}]", "consent-record", body)

        # POST /pricing/api/v1/quote/{id} (expected 200)
        code, body, _ = call_and_record(
            "/pricing/api/v1/quote/{id}",
            f"{gateway_url}/pricing/api/v1/quote/{vid}",
            method="POST",
            body={},
            expected_status=200,
            label=f"POST /pricing/api/v1/quote/{vid} [{vlabel}]",
        )
        if code == 200:
            check_response(f"POST /pricing/api/v1/quote/{vid} [{vlabel}]", "premium-response", body)

        # GET /advisory/api/v1/advisories/{id} (expected 200)
        code, body, _ = call_and_record(
            "/advisory/api/v1/advisories/{id}",
            f"{gateway_url}/advisory/api/v1/advisories/{vid}",
            method="GET",
            expected_status=200,
            label=f"GET /advisory/api/v1/advisories/{vid} [{vlabel}]",
        )
        if code == 200:
            check_response(f"GET /advisory/api/v1/advisories/{vid} [{vlabel}]", "advisory-response", body)

        # GET /maintenance/api/v1/timeline/{id} (expected 200)
        code, body, _ = call_and_record(
            "/maintenance/api/v1/timeline/{id}",
            f"{gateway_url}/maintenance/api/v1/timeline/{vid}",
            method="GET",
            expected_status=200,
            label=f"GET /maintenance/api/v1/timeline/{vid} [{vlabel}]",
        )
        if code == 200:
            check_response(f"GET /maintenance/api/v1/timeline/{vid} [{vlabel}]", "service-timeline", body)

        # POST /maintenance/api/v1/dtc-readings (expected 200)
        dtc_body = {
            "vehicle_id": vid,
            "timestamp": now_timestamp_str,
            "confirmed_codes": ["P0101"],
            "pending_codes": [],
            "source": "obd_device",
            "device_id": f"dongle-{vlabel[:8]}"
        }
        code, body, _ = call_and_record(
            "/maintenance/api/v1/dtc-readings",
            f"{gateway_url}/maintenance/api/v1/dtc-readings",
            method="POST",
            body=dtc_body,
            expected_status=200,
            label=f"POST /maintenance/api/v1/dtc-readings [{vlabel}]",
        )
        if code == 200:
            check_response(f"POST /maintenance/api/v1/dtc-readings [{vlabel}]", "dtc-reading", body)

        # GET /maintenance/api/v1/dtc-readings/{id}/latest (expected 200)
        code, body, _ = call_and_record(
            "/maintenance/api/v1/dtc-readings/{id}/latest",
            f"{gateway_url}/maintenance/api/v1/dtc-readings/{vid}/latest",
            method="GET",
            expected_status=200,
            label=f"GET /maintenance/api/v1/dtc-readings/{vid}/latest [{vlabel}]",
        )
        if code == 200:
            check_response(f"GET /maintenance/api/v1/dtc-readings/{vid}/latest [{vlabel}]", "dtc-reading", body)

        # GET /maintenance/api/v1/dtc-readings/{id}/history (expected 200)
        code, body, _ = call_and_record(
            "/maintenance/api/v1/dtc-readings/{id}/history",
            f"{gateway_url}/maintenance/api/v1/dtc-readings/{vid}/history",
            method="GET",
            expected_status=200,
            label=f"GET /maintenance/api/v1/dtc-readings/{vid}/history [{vlabel}]",
        )
        if code == 200:
            check_response(f"GET /maintenance/api/v1/dtc-readings/{vid}/history [{vlabel}]", "dtc-reading", body, is_array=True)

        # GET /telematics/api/v1/aggregates/{id} (expected 200)
        code, body, _ = call_and_record(
            "/telematics/api/v1/aggregates/{id}",
            f"{gateway_url}/telematics/api/v1/aggregates/{vid}",
            method="GET",
            expected_status=200,
            label=f"GET /telematics/api/v1/aggregates/{vid} [{vlabel}]",
        )
        if code == 200:
            check_response(f"GET /telematics/api/v1/aggregates/{vid} [{vlabel}]", "trip-aggregate", body, is_array=True)

        # POST /claims/api/v1/fnol (expected 201)
        fnol_body = {
            "vehicle_id": vid,
            "policy_id": pid,
            "incident_date": recent_incident_date,
            "incident_description": f"Contract check incident for {vlabel}",
            "claimed_cause": "mechanical_failure"
        }
        code, fnol_resp, _ = call_and_record(
            "/claims/api/v1/fnol",
            f"{gateway_url}/claims/api/v1/fnol",
            method="POST",
            body=fnol_body,
            expected_status=201,
            label=f"POST /claims/api/v1/fnol [{vlabel}]",
        )
        if code == 201:
            check_response(f"POST /claims/api/v1/fnol [{vlabel}]", "fnol-response", fnol_resp)
            claim_id = fnol_resp.get("claim_id")
            if claim_id:
                # GET /claims/api/v1/claims/{id} (expected 200)
                code, claim_body, _ = call_and_record(
                    "/claims/api/v1/claims/{id}",
                    f"{gateway_url}/claims/api/v1/claims/{claim_id}",
                    method="GET",
                    expected_status=200,
                    label=f"GET /claims/api/v1/claims/{claim_id} [{vlabel}]",
                )
                if code == 200:
                    check_response(f"GET /claims/api/v1/claims/{claim_id} [{vlabel}]", "fnol-response", claim_body)

    # 4. FNOL Causes Verification (All 6 Causes + Negative Case)
    print("\n--- FNOL Causes Verification (All 6 Causes + Negative Case) ---")
    all_causes = ["mechanical_failure", "collision", "theft", "natural_disaster", "vandalism", "other"]
    v1_id = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
    v1_pol = "c4632682-d406-3102-86b7-6a22ef09af5d"
    for cause in all_causes:
        cause_req = {
            "vehicle_id": v1_id,
            "policy_id": v1_pol,
            "incident_date": recent_incident_date,
            "incident_description": f"Testing cause {cause}",
            "claimed_cause": cause,
            "photo_refs": []
        }
        # Validate request body against fnol-request
        check_response(f"FNOL request body (cause={cause})", "fnol-request", cause_req)

        code, cause_resp, _ = call_and_record(
            "/claims/api/v1/fnol",
            f"{gateway_url}/claims/api/v1/fnol",
            method="POST",
            body=cause_req,
            expected_status=201,
            label=f"POST /claims/api/v1/fnol (cause={cause})",
        )
        if code == 201:
            check_response(f"POST /claims/api/v1/fnol (cause={cause})", "fnol-response", cause_resp)

    # Negative case: "weather"
    weather_req = {
        "vehicle_id": v1_id,
        "policy_id": v1_pol,
        "incident_date": recent_incident_date,
        "incident_description": "Testing cause weather",
        "claimed_cause": "weather",
        "photo_refs": []
    }
    weather_val = validators["fnol-request"]
    weather_errs = list(weather_val.iter_errors(weather_req))
    if weather_errs:
        total_passed += 1
        print("[PASS] Negative check: 'weather' request body correctly FAILS fnol-request schema")
    else:
        total_failed += 1
        print("[FAIL] Negative check: 'weather' request body unexpectedly passed fnol-request schema")

    code_weather, body_weather, _ = call_and_record(
        "/claims/api/v1/fnol",
        f"{gateway_url}/claims/api/v1/fnol",
        method="POST",
        body=weather_req,
        expected_status=400,
        label="POST /claims/api/v1/fnol (cause=weather -> 400 Bad Request)",
    )
    if code_weather == 400:
        check_response("POST /claims/api/v1/fnol (cause=weather -> 400 Bad Request)", "error-response", body_weather)

    # 5. General Error responses (400 and 404)
    print("\n--- Error Responses Verification ---")
    code_404, body_404, _ = call_and_record(
        "/identity/api/v1/vehicles/{id}",
        f"{gateway_url}/identity/api/v1/vehicles/00000000-0000-0000-0000-000000000000",
        method="GET",
        expected_status=404,
        label="GET /identity/api/v1/vehicles/00000000... (404 Not Found)",
    )
    if code_404 == 404:
        check_response("GET /identity/api/v1/vehicles/00000000... (404 Not Found)", "error-response", body_404)

    code_400, body_400, _ = call_and_record(
        "/advisory/api/v1/advisories/{id}",
        f"{gateway_url}/advisory/api/v1/advisories/not-a-valid-uuid",
        method="GET",
        expected_status=400,
        label="GET /advisory/api/v1/advisories/not-a-valid-uuid (400 Bad Request)",
    )
    if code_400 == 400:
        check_response("GET /advisory/api/v1/advisories/not-a-valid-uuid (400 Bad Request)", "error-response", body_400)

    # 6. Item 3 Gateway Error Sweep (Malformed JSON & Wrong-typed fields)
    print("\n--- Gateway Error Sweep & Leak Verification ---")
    sweep_probes = [
        {
            "route_template": "/identity/api/v1/vehicles",
            "url": f"{gateway_url}/identity/api/v1/vehicles",
            "method": "POST",
            "name": "identity register",
            "malformed": b'{"owner_name": "Test", unclosed_json:',
            "malformed_expected_err": "INVALID_REQUEST",
            "wrong_type": {
                "rc_number": 12345,
                "registration_date": "2023-10-22",
                "owner_details": "not-an-object",
                "vehicle_segment_id": "compact_suv"
            },
            "wrong_type_expected_err": "INVALID_REQUEST",
        },
        {
            "route_template": "/identity/api/v1/vehicles/{id}/consent",
            "url": f"{gateway_url}/identity/api/v1/vehicles/{v1_id}/consent",
            "method": "PUT",
            "name": "consent PUT",
            "malformed": b'{"usage_tracking_opt_in": true, unclosed:',
            "malformed_expected_err": "INVALID_REQUEST",
            "wrong_type": {
                "usage_tracking_opt_in": "not-a-boolean",
                "maintenance_tracking_opt_in": 123
            },
            "wrong_type_expected_err": "INVALID_REQUEST",
        },
        {
            "route_template": "/claims/api/v1/fnol",
            "url": f"{gateway_url}/claims/api/v1/fnol",
            "method": "POST",
            "name": "claims fnol",
            "malformed": b'{"vehicle_id": "a1b2", unclosed:',
            "malformed_expected_err": "INVALID_REQUEST",
            "wrong_type": {
                "vehicle_id": v1_id,
                "policy_id": v1_pol,
                "incident_date": "2026-10-10T12:00:00Z",
                "incident_description": "test",
                "claimed_cause": 12345,
                "photo_refs": "not-an-array"
            },
            "wrong_type_expected_err": "INVALID_REQUEST",
        },
        {
            "route_template": "/maintenance/api/v1/dtc-readings",
            "url": f"{gateway_url}/maintenance/api/v1/dtc-readings",
            "method": "POST",
            "name": "maintenance dtc-readings",
            "malformed": b'{"vehicle_id": "a1b2", unclosed:',
            "malformed_expected_err": "MALFORMED_REQUEST_BODY",
            "wrong_type": {
                "reading_id": "r1",
                "vehicle_id": v1_id,
                "source": 9999,
                "timestamp": "2026-10-20T10:00:00Z",
                "confirmed_codes": "not-an-array"
            },
            "wrong_type_expected_err": "SCHEMA_VALIDATION_FAILED",
        },
    ]

    for p in sweep_probes:
        # Probe (i): Malformed JSON
        code_m, body_m, _ = call_and_record(
            p["route_template"],
            p["url"],
            method=p["method"],
            body=p["malformed"],
            expected_status=400,
            label=f"Sweep malformed JSON: {p['name']}",
        )
        if isinstance(body_m, dict):
            check_response(f"Sweep malformed JSON: {p['name']}", "error-response", body_m)
            err_code = body_m.get("error")
            if err_code == p["malformed_expected_err"]:
                total_passed += 1
                print(f"[PASS] {p['name']} malformed error code '{err_code}' matches expected '{p['malformed_expected_err']}'")
            else:
                total_failed += 1
                print(f"[FAIL] {p['name']} malformed error code '{err_code}' != expected '{p['malformed_expected_err']}'")

        # Probe (ii): Wrong-typed field
        code_w, body_w, _ = call_and_record(
            p["route_template"],
            p["url"],
            method=p["method"],
            body=p["wrong_type"],
            expected_status=400,
            label=f"Sweep wrong type: {p['name']}",
        )
        if isinstance(body_w, dict):
            check_response(f"Sweep wrong type: {p['name']}", "error-response", body_w)
            err_code = body_w.get("error")
            if err_code == p["wrong_type_expected_err"]:
                total_passed += 1
                print(f"[PASS] {p['name']} wrong_type error code '{err_code}' matches expected '{p['wrong_type_expected_err']}'")
            else:
                total_failed += 1
                print(f"[FAIL] {p['name']} wrong_type error code '{err_code}' != expected '{p['wrong_type_expected_err']}'")

    # Generate status table markdown
    generated_status_table = generate_status_table_markdown(observed_statuses)

    if args.write_status_table:
        out_path = Path(args.write_status_table)
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(generated_status_table, encoding="utf-8")
        print(f"\n[INFO] Wrote verified status codes table to {out_path}")

    if args.check_status_table:
        check_path = Path(args.check_status_table)
        if not check_path.is_file():
            total_failed += 1
            print(f"\n[FAIL] --check-status-table file does not exist: {check_path}")
        else:
            committed_content = check_path.read_text(encoding="utf-8").replace("\r\n", "\n").strip()
            observed_content = generated_status_table.replace("\r\n", "\n").strip()
            if committed_content == observed_content:
                total_passed += 1
                print(f"\n[PASS] Verified status codes table matches committed file: {check_path}")
            else:
                total_failed += 1
                print(f"\n[FAIL] Verified status codes table differs from committed file: {check_path}")
                print("--- Observed Table ---")
                print(observed_content)
                print("--- Committed Table ---")
                print(committed_content)

    print("\n==================================================================")
    print(f" Contract Check Summary: {total_passed} passed, {total_failed} failed")
    print("==================================================================")

    return 1 if total_failed > 0 else 0


if __name__ == "__main__":
    sys.exit(main())
