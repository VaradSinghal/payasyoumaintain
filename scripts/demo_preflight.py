#!/usr/bin/env python3
"""
scripts/demo_preflight.py

Validates the demo stack state against mobile_app/assets/demo/demo_scenario.json.
Standard library only. GATEWAY_URL defaults to http://localhost:8080.
"""

import sys
import os
import json
import urllib.request
import urllib.error
import datetime
from pathlib import Path

DEFAULT_GATEWAY_URL = "http://localhost:8080"
SERVICES = ["identity", "telematics", "maintenance", "pricing", "claims", "advisory", "scoring"]

def get_gateway_url():
    return os.environ.get("GATEWAY_URL", DEFAULT_GATEWAY_URL).rstrip("/")

def load_demo_scenarios():
    repo_root = Path(__file__).resolve().parent.parent
    scenario_path = repo_root / "mobile_app" / "assets" / "demo" / "demo_scenario.json"
    if not scenario_path.exists():
        raise FileNotFoundError(f"Demo scenario file not found: {scenario_path}")
    with open(scenario_path, "r", encoding="utf-8") as f:
        return json.load(f)

def make_request(url, method="GET", data=None, headers=None):
    if headers is None:
        headers = {}
    body_bytes = None
    if data is not None:
        if isinstance(data, (dict, list)):
            body_bytes = json.dumps(data).encode("utf-8")
        elif isinstance(data, str):
            body_bytes = data.encode("utf-8")
        else:
            body_bytes = data
        if "Content-Type" not in headers:
            headers["Content-Type"] = "application/json"

    req = urllib.request.Request(url, data=body_bytes, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
            parsed = None
            if raw:
                try:
                    parsed = json.loads(raw)
                except Exception:
                    pass
            return resp.status, parsed, raw
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        parsed = None
        if raw:
            try:
                parsed = json.loads(raw)
            except Exception:
                pass
        return e.code, parsed, raw
    except Exception as e:
        return 0, None, str(e)

def run_preflight():
    gateway_url = get_gateway_url()
    scenarios = load_demo_scenarios()

    failures = []
    summary_rows = []

    print("=================================================================")
    print(f"DEMO PREFLIGHT CHECKS (Gateway: {gateway_url})")
    print("=================================================================")

    # 1. Health checks: GET /health/{service} is 200
    for service in SERVICES:
        status, parsed, raw = make_request(f"{gateway_url}/health/{service}")
        if status == 200:
            print(f"[PASS] GET /health/{service} is 200")
        else:
            msg = f"Expected status 200, got {status} (body: {raw[:100]})"
            print(f"[FAIL] GET /health/{service} is 200: {msg}")
            failures.append((f"GET /health/{service}", 200, status))

    # 2. Consent checks: GET consent for each demo vehicle: both opt-ins true
    for s in scenarios:
        vid = s["vehicle_id"]
        short_id = vid[:8]
        status, parsed, raw = make_request(f"{gateway_url}/identity/api/v1/vehicles/{vid}/consent")
        if status == 200 and isinstance(parsed, dict):
            u_opt = parsed.get("usage_tracking_opt_in")
            m_opt = parsed.get("maintenance_tracking_opt_in")
            if u_opt is True and m_opt is True:
                print(f"[PASS] GET consent for {short_id} ({s.get('display_name')}): both opt-ins true")
            else:
                msg = f"usage={u_opt}, maintenance={m_opt} (expected both True)"
                print(f"[FAIL] GET consent for {short_id} ({s.get('display_name')}): {msg}")
                failures.append((f"Consent {short_id}", {"usage": True, "maintenance": True}, {"usage": u_opt, "maintenance": m_opt}))
        else:
            msg = f"Status {status} (body: {raw[:100]})"
            print(f"[FAIL] GET consent for {short_id} ({s.get('display_name')}): {msg}")
            failures.append((f"Consent {short_id} HTTP status", 200, status))

    # 3. Timeline checks: GET /maintenance/api/v1/timeline/{id} for each demo vehicle: flagged_count is 0
    for s in scenarios:
        vid = s["vehicle_id"]
        short_id = vid[:8]
        status, parsed, raw = make_request(f"{gateway_url}/maintenance/api/v1/timeline/{vid}")
        if status == 200 and isinstance(parsed, dict):
            fc = parsed.get("flagged_count")
            if fc == 0:
                print(f"[PASS] GET timeline for {short_id} ({s.get('display_name')}): flagged_count is 0")
            else:
                msg = f"flagged_count={fc} (expected 0)"
                print(f"[FAIL] GET timeline for {short_id} ({s.get('display_name')}): {msg}")
                failures.append((f"Timeline flagged_count {short_id}", 0, fc))
        else:
            msg = f"Status {status} (body: {raw[:100]})"
            print(f"[FAIL] GET timeline for {short_id} ({s.get('display_name')}): {msg}")
            failures.append((f"Timeline {short_id} HTTP status", 200, status))

    # 4. Vehicle catalogue check: GET vehicle-catalogue: 6 entries
    status, parsed, raw = make_request(f"{gateway_url}/pricing/api/v1/vehicle-catalogue")
    if status == 200 and isinstance(parsed, list) and len(parsed) == 6:
        print("[PASS] GET vehicle-catalogue: 6 entries")
    else:
        cnt = len(parsed) if isinstance(parsed, list) else f"status {status}"
        msg = f"expected 6 entries, got {cnt}"
        print(f"[FAIL] GET vehicle-catalogue: 6 entries ({msg})")
        failures.append(("Vehicle catalogue entries count", 6, cnt))

    # 5. Quote checks for each demo vehicle & 6. Advisories checks & 7. Claim filing
    claims_tested = 0
    quote_responses = {}

    for s in scenarios:
        vid = s["vehicle_id"]
        short_id = vid[:8]
        name = s.get("display_name", "")
        exp = s.get("expected", {})

        # Fetch Quote
        q_status, q_body, q_raw = make_request(f"{gateway_url}/pricing/api/v1/quote/{vid}", method="POST", data={})
        if q_status != 200 or not isinstance(q_body, dict):
            print(f"[FAIL] Quote for {short_id} ({name}): HTTP {q_status}")
            failures.append((f"Quote HTTP status {short_id}", 200, q_status))
            continue

        quote_responses[vid] = q_body
        sd = q_body.get("score_detail") or {}

        # Quote fields validation
        quote_mismatches = []

        # base_premium
        act_base = q_body.get("base_premium")
        exp_base = exp.get("base_premium")
        if act_base is None or round(act_base, 2) != round(exp_base, 2):
            quote_mismatches.append(f"base_premium: expected {exp_base}, got {act_base}")

        # final_premium
        act_final = q_body.get("final_premium")
        exp_final = exp.get("final_premium")
        if act_final is None or round(act_final, 2) != round(exp_final, 2):
            quote_mismatches.append(f"final_premium: expected {exp_final}, got {act_final}")

        # dynamic_multiplier (3 decimals)
        act_mult = q_body.get("dynamic_multiplier")
        exp_mult = exp.get("dynamic_multiplier")
        if act_mult is None or round(act_mult, 3) != round(exp_mult, 3):
            quote_mismatches.append(f"dynamic_multiplier: expected {exp_mult:.3f}, got {act_mult}")

        # health_status
        act_hs = sd.get("health_status")
        exp_hs = exp.get("health_status")
        if act_hs != exp_hs:
            quote_mismatches.append(f"health_status: expected {exp_hs}, got {act_hs}")

        # health_confidence
        act_hc = sd.get("health_confidence")
        exp_hc = exp.get("health_confidence")
        if act_hc != exp_hc:
            quote_mismatches.append(f"health_confidence: expected {exp_hc}, got {act_hc}")

        # has_open_recall
        act_rec = sd.get("has_open_recall")
        exp_rec = exp.get("has_open_recall")
        if bool(act_rec) != bool(exp_rec):
            quote_mismatches.append(f"has_open_recall: expected {exp_rec}, got {act_rec}")

        # data_flags
        act_flags = sorted(sd.get("data_flags") or [])
        exp_flags = sorted(exp.get("data_flags") or [])
        if act_flags != exp_flags:
            quote_mismatches.append(f"data_flags: expected {exp_flags}, got {act_flags}")

        # scores: usage_score, maintenance_score, composite_score (nulls as nulls)
        for score_field in ["usage_score", "maintenance_score", "composite_score"]:
            act_sc = sd.get(score_field)
            exp_sc = exp.get(score_field)
            if exp_sc is None:
                if act_sc is not None:
                    quote_mismatches.append(f"{score_field}: expected None, got {act_sc}")
            else:
                if act_sc is None or round(act_sc, 1) != round(exp_sc, 1):
                    quote_mismatches.append(f"{score_field}: expected {exp_sc}, got {act_sc}")

        if not quote_mismatches:
            print(f"[PASS] Quote for {short_id} ({name}): all metrics and flags match expected")
        else:
            print(f"[FAIL] Quote for {short_id} ({name}):")
            for m in quote_mismatches:
                print(f"    - {m}")
            failures.append((f"Quote metrics for {short_id}", "all matching", "; ".join(quote_mismatches)))

        # Fetch Advisories
        adv_status, adv_body, adv_raw = make_request(f"{gateway_url}/advisory/api/v1/advisories/{vid}")
        if adv_status == 200 and isinstance(adv_body, dict):
            act_adv_types = [a.get("advisory_type") for a in adv_body.get("advisories", []) if isinstance(a, dict)]
            exp_adv_types = exp.get("advisory_types", [])
            if act_adv_types == exp_adv_types:
                print(f"[PASS] Advisories for {short_id} ({name}): types equal expected in order")
            else:
                msg = f"expected {exp_adv_types}, got {act_adv_types}"
                print(f"[FAIL] Advisories for {short_id} ({name}): {msg}")
                failures.append((f"Advisories {short_id}", exp_adv_types, act_adv_types))
        else:
            msg = f"Status {adv_status} (body: {adv_raw[:100]})"
            print(f"[FAIL] Advisories for {short_id} ({name}): {msg}")
            failures.append((f"Advisories {short_id} HTTP status", 200, adv_status))
            act_adv_types = []

        # File Suggested Claim
        sugg_claim = s.get("suggested_claim", {})
        policy_id = q_body.get("policy_id")
        if sugg_claim and policy_id:
            incident_date = (datetime.date.today() - datetime.timedelta(days=10)).strftime("%Y-%m-%dT10:00:00Z")
            fnol_payload = {
                "vehicle_id": vid,
                "policy_id": policy_id,
                "incident_date": incident_date,
                "claimed_cause": sugg_claim.get("claimed_cause", "mechanical_failure"),
                "incident_description": f"Preflight validation claim for {vid}",
                "photo_refs": [],
                "location": "Expressway KM 10",
            }
            cl_status, cl_body, cl_raw = make_request(f"{gateway_url}/claims/api/v1/fnol", method="POST", data=fnol_payload)
            if cl_status == 201 and isinstance(cl_body, dict):
                claims_tested += 1
                act_triage = cl_body.get("triage_decision")
                exp_triage = sugg_claim.get("expected_triage_decision")
                reasons = cl_body.get("triage_reasons", [])
                reason_codes = cl_body.get("triage_reason_codes", [])

                has_recall = exp.get("has_open_recall", False)
                recall_reason_ok = True
                if has_recall:
                    recall_reason_ok = ("open_recall_on_file" in reason_codes) or any("open_recall_on_file" in str(r) for r in reasons)

                claim_errors = []
                if act_triage != exp_triage:
                    claim_errors.append(f"triage_decision: expected {exp_triage}, got {act_triage}")
                if has_recall and not recall_reason_ok:
                    claim_errors.append(f"open_recall_on_file missing from triage reasons (got codes: {reason_codes})")

                if not claim_errors:
                    extra = ", open_recall_on_file verified" if has_recall else ""
                    print(f"[PASS] Claim for {short_id} ({sugg_claim.get('claimed_cause')}): triage_decision={act_triage}{extra}")
                else:
                    msg = "; ".join(claim_errors)
                    print(f"[FAIL] Claim for {short_id} ({sugg_claim.get('claimed_cause')}): {msg}")
                    failures.append((f"Claim triage for {short_id}", f"triage={exp_triage}", msg))
            else:
                msg = f"Status {cl_status} (body: {cl_raw[:100]})"
                print(f"[FAIL] Claim for {short_id}: {msg}")
                failures.append((f"Claim {short_id} HTTP status", 201, cl_status))

        # Format row for summary table
        u_str = f"{act_sc:.1f}" if (act_sc := sd.get("usage_score")) is not None else "null"
        m_str = f"{act_sc:.1f}" if (act_sc := sd.get("maintenance_score")) is not None else "null"
        c_str = f"{act_sc:.1f}" if (act_sc := sd.get("composite_score")) is not None else "null"
        mult_str = f"{act_mult:.3f}" if (act_mult := q_body.get("dynamic_multiplier")) is not None else "null"
        base_str = f"{act_base:,.2f}" if (act_base := q_body.get("base_premium")) is not None else "null"
        final_str = f"{act_final:,.2f}" if (act_final := q_body.get("final_premium")) is not None else "null"
        hs_str = sd.get("health_status") or "null"
        hc_str = sd.get("health_confidence") or "null"
        rec_str = str(sd.get("has_open_recall"))
        flags_str = ",".join(sd.get("data_flags") or []) if sd.get("data_flags") else "[]"
        adv_str = " + ".join(act_adv_types) if act_adv_types else "[]"

        summary_rows.append(
            f"{short_id:<7} | {u_str:<7} | {m_str:<7} | {c_str:<7} | {mult_str:<6} | {base_str:<9} | {final_str:<9} | {hs_str:<17} | {hc_str:<23} | {rec_str:<6} | {flags_str:<29} | {adv_str}"
        )

    # Print summary table
    print("\n=================================================================")
    print("FIVE DEMO STORIES SUMMARY TABLE")
    print("=================================================================")
    header = f"{'Vehicle':<7} | {'Usage':<7} | {'Maint':<7} | {'Comp':<7} | {'Mult':<6} | {'Base':<9} | {'Final':<9} | {'Health Status':<17} | {'Confidence':<23} | {'Recall':<6} | {'Data Flags':<29} | {'Advisory Types'}"
    print(header)
    print("-" * len(header))
    for row in summary_rows:
        print(row)
    print("-" * len(header))

    # Exit handling
    if failures:
        print(f"\n=================================================================")
        print(f"FAILED: {len(failures)} mismatch(es) detected:")
        print("=================================================================")
        for check, exp, act in failures:
            print(f"  - {check}")
            print(f"      Expected: {exp}")
            print(f"      Actual:   {act}")
        sys.exit(1)
    else:
        print("\n=================================================================")
        print("ALL DEMO PREFLIGHT CHECKS PASSED (5/5 vehicle stories verified).")
        print("=================================================================")
        sys.exit(0)

if __name__ == "__main__":
    run_preflight()
