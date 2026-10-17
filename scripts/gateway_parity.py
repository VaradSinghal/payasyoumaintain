#!/usr/bin/env python3
"""
scripts/gateway_parity.py
Gateway parity check: verifies pass-through parity between direct service ports (8081-8087)
and the API gateway (8080) for all mobile client routes.
Also verifies browser OPTIONS preflight handling and prints the five-story table.
Standard library only.
"""

import os
import sys
import json
import urllib.request
import urllib.error

GATEWAY_URL = os.environ.get("GATEWAY_URL", "http://localhost:8080").rstrip("/")
IDENTITY_DIRECT = os.environ.get("IDENTITY_URL", "http://localhost:8081/api/v1").rstrip("/")
TELEMATICS_DIRECT = os.environ.get("TELEMATICS_URL", "http://localhost:8082/api/v1").rstrip("/")
MAINTENANCE_DIRECT = os.environ.get("MAINTENANCE_URL", "http://localhost:8083/api/v1").rstrip("/")
PRICING_DIRECT = os.environ.get("PRICING_URL", "http://localhost:8085/api/v1").rstrip("/")
CLAIMS_DIRECT = os.environ.get("CLAIMS_URL", "http://localhost:8086/api/v1").rstrip("/")
ADVISORY_DIRECT = os.environ.get("ADVISORY_URL", "http://localhost:8087/api/v1").rstrip("/")

DEMO_VEHICLES = [
    ("a1b2", "a1b2c3d4-e5f6-7890-abcd-ef1234567890"),
    ("b2c3", "b2c3d4e5-f6a7-8901-bcde-f12345678901"),
    ("c3d4", "c3d4e5f6-a7b8-9012-cdef-123456789012"),
    ("d4e5", "d4e5f6a7-b8c9-0123-defa-234567890123"),
    ("e5f6", "e5f6a7b8-c9d0-1234-efab-345678901234"),
]

EXPECTATIONS = {
    "a1b2": {
        "usage": 100.0,
        "maintenance": 100.0,
        "composite": 100.0,
        "multiplier": 0.700,
        "base": 24960.0,
        "final": 13977.60,
        "health_status": "scored",
        "health_confidence": "verified",
        "has_open_recall": False,
        "data_flags": [],
        "advisory_types": ["SAFE_DRIVING_COMMENDATION"],
    },
    "b2c3": {
        "usage": 75.0,
        "maintenance": 15.0,
        "composite": 51.0,
        "multiplier": 1.092,
        "base": 12480.0,
        "final": 12265.34,
        "health_status": "scored",
        "health_confidence": "verified",
        "has_open_recall": True,
        "data_flags": [],
        "advisory_types": ["OPEN_RECALL", "OVERDUE_SERVICE", "LOW_RISK_SCORE"],
    },
    "c3d4": {
        "usage": 85.0,
        "maintenance": 100.0,
        "composite": 91.0,
        "multiplier": 0.772,
        "base": 27300.0,
        "final": 21075.60,
        "health_status": "scored",
        "health_confidence": "verified",
        "has_open_recall": False,
        "data_flags": ["conflicting_records_detected"],
        "advisory_types": ["SAFE_DRIVING_COMMENDATION"],
    },
    "d4e5": {
        "usage": 100.0,
        "maintenance": 85.0,
        "composite": 94.0,
        "multiplier": 0.748,
        "base": 11700.0,
        "final": 6563.70,
        "health_status": "scored",
        "health_confidence": "unverified_self_reported",
        "has_open_recall": False,
        "data_flags": [],
        "advisory_types": ["SAFE_DRIVING_COMMENDATION"],
    },
    "e5f6": {
        "usage": None,
        "maintenance": None,
        "composite": None,
        "multiplier": 1.0,
        "base": 44460.0,
        "final": 44460.0,
        "health_status": "insufficient_data",
        "health_confidence": None,
        "has_open_recall": False,
        "data_flags": ["usage_data_unavailable"],
        "advisory_types": ["NO_SERVICE_RECORD"],
    },
}

failures = []

def make_request(url, method="GET", data=None, headers=None):
    if headers is None:
        headers = {}
    if data is not None and "Content-Type" not in headers:
        headers["Content-Type"] = "application/json"
    
    body_bytes = None
    if data is not None:
        if isinstance(data, (dict, list)):
            body_bytes = json.dumps(data).encode("utf-8")
        elif isinstance(data, str):
            body_bytes = data.encode("utf-8")
        else:
            body_bytes = data

    req = urllib.request.Request(url, data=body_bytes, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
            ct = resp.headers.get("Content-Type", "")
            media_type = ct.split(";")[0].strip().lower()
            parsed = None
            if raw and "application/json" in media_type:
                try:
                    parsed = json.loads(raw)
                except Exception:
                    pass
            return resp.status, media_type, parsed, raw, dict(resp.headers)
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        ct = e.headers.get("Content-Type", "")
        media_type = ct.split(";")[0].strip().lower()
        parsed = None
        if raw and "application/json" in media_type:
            try:
                parsed = json.loads(raw)
            except Exception:
                pass
        return e.code, media_type, parsed, raw, dict(e.headers)
    except Exception as e:
        return 0, "", None, str(e), {}

def compare_key_structure(val1, val2, path="root"):
    """
    Recursively compares JSON key structure between val1 (direct) and val2 (gateway).
    For lists, compares first element's structure.
    Ignores specific values (e.g. timestamps, auto-generated ids).
    """
    if isinstance(val1, dict) and isinstance(val2, dict):
        k1 = set(val1.keys())
        k2 = set(val2.keys())
        if k1 != k2:
            return False, f"Key mismatch at {path}: direct has {sorted(k1)}, gateway has {sorted(k2)}"
        for k in sorted(k1):
            ok, err = compare_key_structure(val1[k], val2[k], f"{path}.{k}")
            if not ok:
                return False, err
        return True, ""
    elif isinstance(val1, list) and isinstance(val2, list):
        if len(val1) > 0 and len(val2) > 0:
            return compare_key_structure(val1[0], val2[0], f"{path}[0]")
        return True, ""
    return True, ""

def check_parity(route_name, direct_url, gateway_url, method="GET", data=None):
    print(f"Checking {route_name}...")
    st_dir, mt_dir, json_dir, raw_dir, _ = make_request(direct_url, method=method, data=data)
    st_gw, mt_gw, json_gw, raw_gw, _ = make_request(gateway_url, method=method, data=data)

    mismatches = []
    if st_dir != st_gw:
        mismatches.append(f"HTTP Status mismatch: direct={st_dir}, gateway={st_gw}")
    if mt_dir != mt_gw:
        mismatches.append(f"Media type mismatch: direct='{mt_dir}', gateway='{mt_gw}'")

    if json_dir is not None and json_gw is not None:
        ok, err = compare_key_structure(json_dir, json_gw)
        if not ok:
            mismatches.append(f"Structure mismatch: {err}")

    if mismatches:
        print(f"  [FAIL] {route_name}")
        for m in mismatches:
            print(f"    - {m}")
        print("  Direct body:")
        print(f"    {raw_dir[:300]}")
        print("  Gateway body:")
        print(f"    {raw_gw[:300]}")
        failures.append((route_name, mismatches))
        return False, json_gw, raw_gw
    else:
        print(f"  [PASS] {route_name} (Status: {st_gw}, Media: {mt_gw})")
        return True, json_gw, raw_gw

def check_preflight(name, gateway_url, http_method):
    print(f"Checking preflight for {name} ({gateway_url})...")
    headers = {
        "Origin": "http://localhost:5555",
        "Access-Control-Request-Method": http_method,
        "Access-Control-Request-Headers": "content-type",
    }
    st, mt, _, raw, hdrs = make_request(gateway_url, method="OPTIONS", headers=headers)
    allow_origin = hdrs.get("Access-Control-Allow-Origin") or hdrs.get("access-control-allow-origin")

    mismatches = []
    if st != 204:
        mismatches.append(f"Expected status 204, got {st}")
    if not allow_origin:
        mismatches.append("Missing Access-Control-Allow-Origin header")

    if mismatches:
        print(f"  [FAIL] Preflight {name}")
        for m in mismatches:
            print(f"    - {m}")
        print(f"  Headers: {hdrs}")
        failures.append((f"Preflight {name}", mismatches))
        return False
    else:
        print(f"  [PASS] Preflight {name} (Status: 204, Allow-Origin: {allow_origin})")
        return True

def run_parity_suite():
    print("=================================================================")
    print("STEP 1: ROUTE PARITY (DIRECT vs GATEWAY)")
    print("=================================================================")

    # 1. Register vehicle
    reg_body = {
        "rc_number": "KA01PARITY01",
        "registration_date": "2023-01-01",
        "owner_details": {
            "full_name": "Parity Owner",
            "email": "parity@example.com",
            "phone_number": "9876543210",
            "kyc_document_number": "PANPARITY01",
        },
    }
    ok, reg_json, _ = check_parity(
        "Register Vehicle (POST /vehicles)",
        f"{IDENTITY_DIRECT}/vehicles",
        f"{GATEWAY_URL}/identity/api/v1/vehicles",
        method="POST",
        data=reg_body,
    )
    reg_vid = reg_json.get("vehicle_id") if (ok and isinstance(reg_json, dict)) else None

    # 2. Get vehicle
    if reg_vid:
        check_parity(
            f"Get Vehicle (GET /vehicles/{reg_vid})",
            f"{IDENTITY_DIRECT}/vehicles/{reg_vid}",
            f"{GATEWAY_URL}/identity/api/v1/vehicles/{reg_vid}",
            method="GET",
        )

        # 3. Get consent
        check_parity(
            f"Get Consent (GET /vehicles/{reg_vid}/consent)",
            f"{IDENTITY_DIRECT}/vehicles/{reg_vid}/consent",
            f"{GATEWAY_URL}/identity/api/v1/vehicles/{reg_vid}/consent",
            method="GET",
        )

        # 4. Put consent
        check_parity(
            f"Put Consent (PUT /vehicles/{reg_vid}/consent)",
            f"{IDENTITY_DIRECT}/vehicles/{reg_vid}/consent",
            f"{GATEWAY_URL}/identity/api/v1/vehicles/{reg_vid}/consent",
            method="PUT",
            data={"usage_tracking_opt_in": True, "maintenance_tracking_opt_in": True},
        )

    # 5. Quote and Advisories for each of the five demo vehicles
    for short, vid in DEMO_VEHICLES:
        check_parity(
            f"Quote for {short} (POST /quote/{vid})",
            f"{PRICING_DIRECT}/quote/{vid}",
            f"{GATEWAY_URL}/pricing/api/v1/quote/{vid}",
            method="POST",
            data={},
        )
        check_parity(
            f"Advisories for {short} (GET /advisories/{vid})",
            f"{ADVISORY_DIRECT}/advisories/{vid}",
            f"{GATEWAY_URL}/advisory/api/v1/advisories/{vid}",
            method="GET",
        )

    # 6. FNOL (mechanical_failure for b2c3...) and get claim
    b2c3_id = "b2c3d4e5-f6a7-8901-bcde-f12345678901"
    fnol_payload = {
        "vehicle_id": b2c3_id,
        "policy_id": "c4632682-d406-3102-86b7-6a22ef09af5d",
        "incident_date": "2026-03-01T10:00:00Z",
        "claimed_cause": "mechanical_failure",
        "incident_description": "Engine stalled suddenly on expressway",
        "photo_refs": [],
        "location": "Expressway KM 42",
    }
    ok_fnol, fnol_json, _ = check_parity(
        "FNOL mechanical_failure for b2c3 (POST /fnol)",
        f"{CLAIMS_DIRECT}/fnol",
        f"{GATEWAY_URL}/claims/api/v1/fnol",
        method="POST",
        data=fnol_payload,
    )
    claim_id = fnol_json.get("claim_id") if (ok_fnol and isinstance(fnol_json, dict)) else None

    if claim_id:
        check_parity(
            f"Get Claim (GET /claims/{claim_id})",
            f"{CLAIMS_DIRECT}/claims/{claim_id}",
            f"{GATEWAY_URL}/claims/api/v1/claims/{claim_id}",
            method="GET",
        )

    # 7. Maintenance Timeline
    v1_id = DEMO_VEHICLES[0][1]
    check_parity(
        f"Maintenance Timeline (GET /timeline/{v1_id})",
        f"{MAINTENANCE_DIRECT}/timeline/{v1_id}",
        f"{GATEWAY_URL}/maintenance/api/v1/timeline/{v1_id}",
        method="GET",
    )

    # 8. DTC readings (POST, latest, history)
    dtc_payload = {
        "vehicle_id": v1_id,
        "timestamp": "2026-10-12T03:00:00Z",
        "source": "manual_entry",
        "device_id": None,
        "confirmed_codes": ["P0300"],
        "pending_codes": [],
    }
    check_parity(
        "POST DTC Readings (/dtc-readings)",
        f"{MAINTENANCE_DIRECT}/dtc-readings",
        f"{GATEWAY_URL}/maintenance/api/v1/dtc-readings",
        method="POST",
        data=dtc_payload,
    )
    check_parity(
        f"GET DTC Latest (/dtc-readings/{v1_id}/latest)",
        f"{MAINTENANCE_DIRECT}/dtc-readings/{v1_id}/latest",
        f"{GATEWAY_URL}/maintenance/api/v1/dtc-readings/{v1_id}/latest",
        method="GET",
    )
    check_parity(
        f"GET DTC History (/dtc-readings/{v1_id}/history)",
        f"{MAINTENANCE_DIRECT}/dtc-readings/{v1_id}/history",
        f"{GATEWAY_URL}/maintenance/api/v1/dtc-readings/{v1_id}/history",
        method="GET",
    )

    # 9. Telematics Aggregates
    check_parity(
        f"Telematics Aggregates (GET /aggregates/{v1_id})",
        f"{TELEMATICS_DIRECT}/aggregates/{v1_id}",
        f"{GATEWAY_URL}/telematics/api/v1/aggregates/{v1_id}",
        method="GET",
    )

    print("\n=================================================================")
    print("STEP 2: BROWSER PREFLIGHTS (OPTIONS)")
    print("=================================================================")
    check_preflight("Quote", f"{GATEWAY_URL}/pricing/api/v1/quote/{v1_id}", "POST")
    check_preflight("Consent", f"{GATEWAY_URL}/identity/api/v1/vehicles/{v1_id}/consent", "PUT")
    check_preflight("FNOL", f"{GATEWAY_URL}/claims/api/v1/fnol", "POST")
    check_preflight("DTC Readings", f"{GATEWAY_URL}/maintenance/api/v1/dtc-readings", "POST")

    print("\n=================================================================")
    print("STEP 3: FIVE-STORY TABLE FROM GATEWAY")
    print("=================================================================")
    # Table header
    header = f"{'Vehicle':<7} | {'Usage':<7} | {'Maint':<7} | {'Comp':<7} | {'Mult':<6} | {'Base':<9} | {'Final':<9} | {'Health Status':<17} | {'Confidence':<23} | {'Recall':<6} | {'Data Flags':<29} | {'Advisory Types'}"
    print(header)
    print("-" * len(header))

    table_diffs = []
    for short, vid in DEMO_VEHICLES:
        _, _, q_json, _, _ = make_request(f"{GATEWAY_URL}/pricing/api/v1/quote/{vid}", method="POST", data={})
        _, _, a_json, _, _ = make_request(f"{GATEWAY_URL}/advisory/api/v1/advisories/{vid}", method="GET")

        sd = (q_json or {}).get("score_detail") or {}
        usage = sd.get("usage_score")
        maint = sd.get("maintenance_score")
        comp = sd.get("composite_score")
        mult = (q_json or {}).get("dynamic_multiplier")
        base = (q_json or {}).get("base_premium")
        final = (q_json or {}).get("final_premium")
        h_status = sd.get("health_status")
        h_conf = sd.get("health_confidence")
        recall = sd.get("has_open_recall")
        flags = sd.get("data_flags") or []
        adv_list = [adv.get("advisory_type") for adv in (a_json or {}).get("advisories", []) if adv.get("advisory_type")]

        exp = EXPECTATIONS[short]

        # Cell diff tracker
        def mark_cell(actual, expected, fmt=str):
            act_str = fmt(actual) if actual is not None else "null"
            if actual != expected:
                table_diffs.append(f"{short} field diff: actual={actual}, expected={expected}")
                return f"*{act_str}*"
            return act_str

        u_str = mark_cell(usage, exp["usage"], lambda x: f"{x:.1f}")
        m_str = mark_cell(maint, exp["maintenance"], lambda x: f"{x:.1f}")
        c_str = mark_cell(comp, exp["composite"], lambda x: f"{x:.1f}")
        mult_str = mark_cell(mult, exp["multiplier"], lambda x: f"{x:.3f}")
        base_str = mark_cell(base, exp["base"], lambda x: f"{x:,.2f}")
        final_str = mark_cell(final, exp["final"], lambda x: f"{x:,.2f}")
        stat_str = mark_cell(h_status, exp["health_status"])
        conf_str = mark_cell(h_conf, exp["health_confidence"])
        rec_str = mark_cell(recall, exp["has_open_recall"])
        flags_str = mark_cell(flags, exp["data_flags"], lambda x: ",".join(x) if x else "[]")
        advs_str = mark_cell(adv_list, exp["advisory_types"], lambda x: " + ".join(x) if x else "[]")

        row = f"{short:<7} | {u_str:<7} | {m_str:<7} | {c_str:<7} | {mult_str:<6} | {base_str:<9} | {final_str:<9} | {stat_str:<17} | {conf_str:<23} | {rec_str:<6} | {flags_str:<29} | {advs_str}"
        print(row)

    print("-" * len(header))
    if table_diffs:
        print(f"\nFive-story table differences found ({len(table_diffs)}):")
        for d in table_diffs:
            print(f"  - {d}")
        failures.extend([("Table diff", [d]) for d in table_diffs])
    else:
        print("\nAll five vehicle stories match expectations exactly (0 differences).")

    print("\n=================================================================")
    print("SUMMARY")
    print("=================================================================")
    if failures:
        print(f"FAILED: {len(failures)} issue(s) detected.")
        sys.exit(1)
    else:
        print("ALL PARITY CHECKS, PREFLIGHTS, AND TABLE VALUES PASSED.")
        sys.exit(0)

if __name__ == "__main__":
    run_parity_suite()
