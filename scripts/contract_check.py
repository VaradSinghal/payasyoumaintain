#!/usr/bin/env python3
"""Live contract verification against running Pay As You Maintain gateway.

Calls every allowlisted route through the gateway for the five demo vehicles
and a freshly registered vehicle, validating each response against its contract schema.
"""

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


def find_repo_root() -> Path:
    current = Path(__file__).resolve().parent
    if (current.parent / "contracts" / "schemas").is_dir():
        return current.parent
    cwd = Path.cwd()
    if (cwd / "contracts" / "schemas").is_dir():
        return cwd
    raise RuntimeError("Could not find repository root containing contracts/schemas")


def http_request(url: str, method: str = "GET", body: dict | None = None, headers: dict | None = None):
    headers = headers or {}
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"

    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            code = resp.getcode()
            content = resp.read().decode("utf-8")
            parsed = json.loads(content) if content else None
            return code, parsed
    except urllib.error.HTTPError as e:
        content = e.read().decode("utf-8")
        parsed = json.loads(content) if content else None
        return e.code, parsed
    except Exception as e:
        return 0, str(e)


def main() -> int:
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
        "fnol-response": json.loads((schemas_dir / "fnol-response.schema.json").read_text(encoding="utf-8")),
        "service-timeline": json.loads((schemas_dir / "service-timeline.schema.json").read_text(encoding="utf-8")),
        "dtc-reading": json.loads((schemas_dir / "dtc-reading.schema.json").read_text(encoding="utf-8")),
        "trip-aggregate": json.loads((schemas_dir / "trip-aggregate.schema.json").read_text(encoding="utf-8")),
        "error-response": json.loads((schemas_dir / "error-response.schema.json").read_text(encoding="utf-8")),
    }
    validators = {k: Draft202012Validator(v) for k, v in schemas.items()}

    total_passed = 0
    total_failed = 0

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

    # 1. Global Route: Vehicle Catalogue
    code, catalogue = http_request(f"{gateway_url}/pricing/api/v1/vehicle-catalogue")
    if code != 200:
        total_failed += 1
        print(f"[FAIL] GET /pricing/api/v1/vehicle-catalogue: status {code}")
    else:
        check_response("GET /pricing/api/v1/vehicle-catalogue", "vehicle-catalogue", catalogue)

    # 2. Fresh Vehicle Registration
    fresh_rc = f"MH01ZZ{int(time.time()) % 10000:04d}"
    fresh_reg_body = {
        "rc_number": fresh_rc,
        "registration_date": "2024-03-01",
        "vehicle_segment_id": "hatchback_entry",
        "owner_details": {
            "full_name": "Fresh Test Owner",
            "kyc_document_number": "123456789012",
            "kyc_document_type": "AADHAAR",
            "email": "fresh.test@example.com",
            "phone_number": "9876543210"
        }
    }
    code, fresh_reg_resp = http_request(f"{gateway_url}/identity/api/v1/vehicles", method="POST", body=fresh_reg_body)
    if code != 201:
        total_failed += 1
        print(f"[FAIL] POST /identity/api/v1/vehicles: status {code}, body: {fresh_reg_resp}")
        fresh_vehicle_id = None
        fresh_policy_id = None
    else:
        check_response("POST /identity/api/v1/vehicles (fresh)", "vehicle-registration-response", fresh_reg_resp)
        fresh_vehicle_id = fresh_reg_resp.get("vehicle_id")
        fresh_policy_id = fresh_reg_resp.get("policy_id")

    # Define vehicle list: 5 demo vehicles + 1 fresh
    demo_vehicles = [
        {"id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890", "policy_id": "c4632682-d406-3102-86b7-6a22ef09af5d", "label": "demo_veh_001"},
        {"id": "b2c3d4e5-f6a7-8901-bcde-f12345678901", "policy_id": "4c4caec8-846d-31d6-af88-43c25c038aa4", "label": "demo_veh_002"},
        {"id": "c3d4e5f6-a7b8-9012-cdef-123456789012", "policy_id": "f613e582-17e0-37cf-b317-856cbca14248", "label": "demo_veh_003"},
        {"id": "d4e5f6a7-b8c9-0123-defa-234567890123", "policy_id": "33c7f064-f8cf-3ee4-af52-1453fd8e713f", "label": "demo_veh_004"},
        {"id": "e5f6a7b8-c9d0-1234-efab-345678901234", "policy_id": "a2aa245d-ecbc-3719-b180-cfc5ca5c3866", "label": "demo_veh_005"},
    ]

    vehicles_to_test = list(demo_vehicles)
    if fresh_vehicle_id and fresh_policy_id:
        vehicles_to_test.append({"id": fresh_vehicle_id, "policy_id": fresh_policy_id, "label": "fresh_vehicle"})

    # 3. Test allowlisted routes for each vehicle
    for v in vehicles_to_test:
        vid = v["id"]
        pid = v["policy_id"]
        vlabel = v["label"]

        # GET /identity/api/v1/vehicles/{id}
        code, body = http_request(f"{gateway_url}/identity/api/v1/vehicles/{vid}")
        if code == 200:
            check_response(f"GET /identity/api/v1/vehicles/{vid} [{vlabel}]", "vehicle-registration-response", body)
        else:
            total_failed += 1
            print(f"[FAIL] GET /identity/api/v1/vehicles/{vid} [{vlabel}]: status {code}")

        # GET /identity/api/v1/vehicles/{id}/consent
        code, body = http_request(f"{gateway_url}/identity/api/v1/vehicles/{vid}/consent")
        if code == 200:
            check_response(f"GET /identity/api/v1/vehicles/{vid}/consent [{vlabel}]", "consent-record", body)
        else:
            total_failed += 1
            print(f"[FAIL] GET /identity/api/v1/vehicles/{vid}/consent [{vlabel}]: status {code}")

        # PUT /identity/api/v1/vehicles/{id}/consent
        put_consent_body = {"usage_tracking_opt_in": True, "maintenance_tracking_opt_in": True}
        code, body = http_request(f"{gateway_url}/identity/api/v1/vehicles/{vid}/consent", method="PUT", body=put_consent_body)
        if code == 200:
            check_response(f"PUT /identity/api/v1/vehicles/{vid}/consent [{vlabel}]", "consent-record", body)
        else:
            total_failed += 1
            print(f"[FAIL] PUT /identity/api/v1/vehicles/{vid}/consent [{vlabel}]: status {code}")

        # POST /pricing/api/v1/quote/{id}
        code, body = http_request(f"{gateway_url}/pricing/api/v1/quote/{vid}", method="POST", body={})
        if code == 200:
            check_response(f"POST /pricing/api/v1/quote/{vid} [{vlabel}]", "premium-response", body)
        else:
            total_failed += 1
            print(f"[FAIL] POST /pricing/api/v1/quote/{vid} [{vlabel}]: status {code}")

        # GET /advisory/api/v1/advisories/{id}
        code, body = http_request(f"{gateway_url}/advisory/api/v1/advisories/{vid}")
        if code == 200:
            check_response(f"GET /advisory/api/v1/advisories/{vid} [{vlabel}]", "advisory-response", body)
        else:
            total_failed += 1
            print(f"[FAIL] GET /advisory/api/v1/advisories/{vid} [{vlabel}]: status {code}")

        # GET /maintenance/api/v1/timeline/{id}
        code, body = http_request(f"{gateway_url}/maintenance/api/v1/timeline/{vid}")
        if code == 200:
            check_response(f"GET /maintenance/api/v1/timeline/{vid} [{vlabel}]", "service-timeline", body)
        else:
            total_failed += 1
            print(f"[FAIL] GET /maintenance/api/v1/timeline/{vid} [{vlabel}]: status {code}")

        # POST /maintenance/api/v1/dtc-readings
        dtc_body = {
            "vehicle_id": vid,
            "timestamp": "2026-10-20T12:00:00Z",
            "confirmed_codes": ["P0101"],
            "pending_codes": [],
            "source": "obd_device",
            "device_id": f"dongle-{vlabel[:8]}"
        }
        code, body = http_request(f"{gateway_url}/maintenance/api/v1/dtc-readings", method="POST", body=dtc_body)
        if code == 200:
            check_response(f"POST /maintenance/api/v1/dtc-readings [{vlabel}]", "dtc-reading", body)
        else:
            total_failed += 1
            print(f"[FAIL] POST /maintenance/api/v1/dtc-readings [{vlabel}]: status {code}")

        # GET /maintenance/api/v1/dtc-readings/{id}/latest
        code, body = http_request(f"{gateway_url}/maintenance/api/v1/dtc-readings/{vid}/latest")
        if code == 200:
            check_response(f"GET /maintenance/api/v1/dtc-readings/{vid}/latest [{vlabel}]", "dtc-reading", body)
        elif code == 204:
            print(f"[PASS] GET /maintenance/api/v1/dtc-readings/{vid}/latest [{vlabel}] (204 No Content)")
            total_passed += 1
        else:
            total_failed += 1
            print(f"[FAIL] GET /maintenance/api/v1/dtc-readings/{vid}/latest [{vlabel}]: status {code}")

        # GET /maintenance/api/v1/dtc-readings/{id}/history
        code, body = http_request(f"{gateway_url}/maintenance/api/v1/dtc-readings/{vid}/history")
        if code == 200:
            check_response(f"GET /maintenance/api/v1/dtc-readings/{vid}/history [{vlabel}]", "dtc-reading", body, is_array=True)
        else:
            total_failed += 1
            print(f"[FAIL] GET /maintenance/api/v1/dtc-readings/{vid}/history [{vlabel}]: status {code}")

        # GET /telematics/api/v1/aggregates/{id}
        code, body = http_request(f"{gateway_url}/telematics/api/v1/aggregates/{vid}")
        if code == 200:
            check_response(f"GET /telematics/api/v1/aggregates/{vid} [{vlabel}]", "trip-aggregate", body, is_array=True)
        else:
            total_failed += 1
            print(f"[FAIL] GET /telematics/api/v1/aggregates/{vid} [{vlabel}]: status {code}")

        # POST /claims/api/v1/fnol
        fnol_body = {
            "vehicle_id": vid,
            "policy_id": pid,
            "incident_date": "2026-10-20",
            "incident_description": f"Contract check incident for {vlabel}",
            "claimed_cause": "mechanical_failure"
        }
        code, fnol_resp = http_request(f"{gateway_url}/claims/api/v1/fnol", method="POST", body=fnol_body)
        if code == 201:
            check_response(f"POST /claims/api/v1/fnol [{vlabel}]", "fnol-response", fnol_resp)
            claim_id = fnol_resp.get("claim_id")
            if claim_id:
                # GET /claims/api/v1/claims/{claim_id}
                code, claim_body = http_request(f"{gateway_url}/claims/api/v1/claims/{claim_id}")
                if code == 200:
                    check_response(f"GET /claims/api/v1/claims/{claim_id} [{vlabel}]", "fnol-response", claim_body)
                else:
                    total_failed += 1
                    print(f"[FAIL] GET /claims/api/v1/claims/{claim_id} [{vlabel}]: status {code}")
        else:
            total_failed += 1
            print(f"[FAIL] POST /claims/api/v1/fnol [{vlabel}]: status {code}")

    # 4. Error response validations (400 and 404)
    code_404, body_404 = http_request(f"{gateway_url}/identity/api/v1/vehicles/00000000-0000-0000-0000-000000000000")
    if code_404 == 404:
        check_response("GET /identity/api/v1/vehicles/00000000... (404 Not Found)", "error-response", body_404)
    else:
        total_failed += 1
        print(f"[FAIL] Error check 404: expected 404, got {code_404}")

    code_400, body_400 = http_request(f"{gateway_url}/advisory/api/v1/advisories/not-a-valid-uuid")
    if code_400 == 400:
        check_response("GET /advisory/api/v1/advisories/not-a-valid-uuid (400 Bad Request)", "error-response", body_400)
    else:
        total_failed += 1
        print(f"[FAIL] Error check 400: expected 400, got {code_400}")

    print("\n==================================================================")
    print(f" Contract Check Summary: {total_passed} passed, {total_failed} failed")
    print("==================================================================")

    return 1 if total_failed > 0 else 0


if __name__ == "__main__":
    sys.exit(main())
