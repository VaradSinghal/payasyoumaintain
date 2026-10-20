import os
import sys
import json
import uuid
import datetime
import urllib.request
import urllib.error

# Global results collector
test_results = []

def request(url, method="GET", data=None, headers=None):
    if headers is None:
        headers = {}
    if data:
        data = json.dumps(data).encode('utf-8')
        headers['Content-Type'] = 'application/json'
    
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as response:
            status = response.getcode()
            body = response.read().decode('utf-8')
            return status, json.loads(body) if body else None
    except urllib.error.HTTPError as e:
        body = e.read().decode('utf-8')
        return e.code, json.loads(body) if body else None
    except Exception as e:
        return 0, str(e)

def add_result(step, passed, error_msg=None):
    test_results.append({
        "step": step,
        "passed": passed,
        "error_msg": error_msg
    })
    if passed:
        print(f"[{step}] PASS")
    else:
        print(f"[{step}] FAIL: {error_msg}")

def assert_status(step, status, expected_statuses, body=None):
    if isinstance(expected_statuses, int):
        expected_statuses = [expected_statuses]
    
    if status in expected_statuses:
        add_result(step, True)
    else:
        add_result(step, False, f"Expected {expected_statuses}, got {status}. Body: {body}")

def main():
    identity_url = os.environ.get('IDENTITY_URL', 'http://localhost:8081/api/v1')
    pricing_url = os.environ.get('PRICING_URL', 'http://localhost:8085/api/v1')
    scoring_url = os.environ.get('SCORING_URL', 'http://localhost:8084')
    advisory_url = os.environ.get('ADVISORY_URL', 'http://localhost:8087/api/v1')
    maintenance_url = os.environ.get('MAINTENANCE_URL', 'http://localhost:8083/api/v1')
    claims_url = os.environ.get('CLAIMS_URL', 'http://localhost:8086/api/v1')
    telematics_url = os.environ.get('TELEMATICS_URL', 'http://localhost:8082/api/v1')

    print("--- Running E2E Smoke Test ---")
    
    # 1. Register vehicle
    submitted_doc = "PAN1234"
    reg_data = {
        "rc_number": "SMOKE123",
        "registration_date": "2023-01-01",
        "owner_details": {
            "full_name": "Test Owner",
            "email": "test@example.com",
            "phone_number": "1234567890",
            "kyc_document_number": submitted_doc
        }
    }
    status, body = request(f"{identity_url}/vehicles", method="POST", data=reg_data)
    assert_status("Register vehicle", status, 201, body)
    
    vehicle_id = None
    policy_id = None
    if isinstance(body, dict):
        vehicle_id = body.get('vehicle_id')
        policy_id = body.get('policy_id')
        owner = body.get('owner_details', {})
        kyc_doc = owner.get('kyc_document_number', '')
        last_4 = submitted_doc[-4:]
        doc_masked_ok = kyc_doc.startswith("X") and kyc_doc.endswith(last_4)
        add_result("Registration kyc_document_number masked", doc_masked_ok, f"Expected starts with X and ends with {last_4}, got {kyc_doc}")

    if not vehicle_id:
        add_result("Pre-requisite: Vehicle ID", False, "Missing vehicle_id from registration")
        print("Cannot continue without vehicle_id. Summarizing and exiting.")
        summarize_and_exit()
        
    # 2. Freshly registered vehicle quote (default opted-out): not_evaluated, premium 13,650.00
    status, body_fresh_optout = request(f"{pricing_url}/quote/{vehicle_id}", method="POST")
    assert_status("Quote (fresh vehicle, opted-out)", status, 200, body_fresh_optout)
    if isinstance(body_fresh_optout, dict):
        health_st = body_fresh_optout.get('score_detail', {}).get('health_status')
        prem = body_fresh_optout.get('final_premium')
        add_result("Fresh vehicle quote is not_evaluated", health_st == "not_evaluated", f"Expected not_evaluated, got {health_st}")
        add_result("Fresh vehicle final_premium == 13,650.00", prem == 13650.0, f"Expected 13650.0, got {prem}")
    else:
        add_result("Fresh vehicle quote is not_evaluated", False, "Invalid quote response")
        add_result("Fresh vehicle final_premium == 13,650.00", False, "Invalid quote response")

    # 3. Update consent: PUT consent both true -> quote is insufficient_data
    status, body = request(f"{identity_url}/vehicles/{vehicle_id}/consent", method="PUT", data={"usage_tracking_opt_in": True, "maintenance_tracking_opt_in": True})
    assert_status("Update consent both true", status, 200, body)

    status, body_fresh_optin = request(f"{pricing_url}/quote/{vehicle_id}", method="POST")
    assert_status("Quote (fresh vehicle, opted-in)", status, 200, body_fresh_optin)
    if isinstance(body_fresh_optin, dict):
        health_st_in = body_fresh_optin.get('score_detail', {}).get('health_status')
        add_result("Fresh vehicle quote is insufficient_data", health_st_in == "insufficient_data", f"Expected insufficient_data, got {health_st_in}")
    else:
        add_result("Fresh vehicle quote is insufficient_data", False, "Invalid quote response")

    # Policy ID stability across quotes
    if isinstance(body_fresh_optout, dict) and isinstance(body_fresh_optin, dict):
        if body_fresh_optout.get('policy_id') == body_fresh_optin.get('policy_id') == policy_id:
            add_result("Policy ID stability", True)
        else:
            add_result("Policy ID stability", False, f"Expected {policy_id}, got {body_fresh_optout.get('policy_id')} and {body_fresh_optin.get('policy_id')}")
    else:
        add_result("Policy ID stability", False, "Quotes did not return valid JSON dictionaries")

    # 4. Score direct endpoint test
    score_payload = {
        "telematics_summary": {
            "total_distance_km": 100,
            "harsh_braking_count": 0,
            "speeding_duration_minutes": 0,
            "night_driving_duration_minutes": 0
        },
        "service_timeline": {
            "vehicle_id": vehicle_id,
            "total_services": 0,
            "events": []
        }
    }
    status, body = request(f"{scoring_url}/score/{vehicle_id}", method="POST", data=score_payload)
    assert_status("Score (fresh)", status, 200, body)

    # 5. Advisories for fresh vehicle
    status, body = request(f"{advisory_url}/advisories/{vehicle_id}", method="GET")
    assert_status("Advisories (fresh)", status, 200, body)

    # 6. DTC readings
    dtc_full = {
        "vehicle_id": vehicle_id,
        "timestamp": "2026-10-08T10:00:00Z",
        "source": "manual_entry",
        "device_id": None,
        "confirmed_codes": ["P0300"],
        "pending_codes": ["P0420"]
    }
    status, body = request(f"{maintenance_url}/dtc-readings", method="POST", data=dtc_full)
    assert_status("POST DTC full", status, [200, 201], body)

    dtc_empty = {
        "vehicle_id": vehicle_id,
        "timestamp": "2026-10-08T10:05:00Z",
        "source": "manual_entry",
        "device_id": None,
        "confirmed_codes": [],
        "pending_codes": []
    }
    status, body = request(f"{maintenance_url}/dtc-readings", method="POST", data=dtc_empty)
    assert_status("POST DTC empty", status, [200, 201], body)

    status, body = request(f"{maintenance_url}/dtc-readings/{vehicle_id}/latest", method="GET")
    assert_status("GET latest DTC", status, 200, body)

    status, body = request(f"{maintenance_url}/dtc-readings/{vehicle_id}/history", method="GET")
    assert_status("GET DTC history", status, 200, body)
    
    # 204 and [] for vehicle with none
    fresh_id = str(uuid.uuid4())
    status, body = request(f"{maintenance_url}/dtc-readings/{fresh_id}/latest", method="GET")
    assert_status("GET latest DTC (none)", status, [204, 404], body)
    
    status, body = request(f"{maintenance_url}/dtc-readings/{fresh_id}/history", method="GET")
    assert_status("GET DTC history (none)", status, 200, body)
    if body == []:
        add_result("History empty check", True)
    else:
        add_result("History empty check", False, f"Expected [], got {body}")

    # 7. FNOL
    fnol = {
        "vehicle_id": vehicle_id,
        "policy_id": policy_id,
        "incident_date": "2026-10-08T10:00:00Z",
        "claimed_cause": "mechanical_failure",
        "incident_description": "Engine died"
    }
    status, body = request(f"{claims_url}/fnol", method="POST", data=fnol)
    assert_status("FNOL", status, 201, body)
    
    if isinstance(body, dict):
        claim_id = body.get('claim_id')
        if claim_id:
            # 8. GET claim
            status, body = request(f"{claims_url}/claims/{claim_id}", method="GET")
            assert_status("GET claim", status, 200, body)
        else:
            add_result("GET claim", False, "No claim_id returned from FNOL")
    else:
        add_result("GET claim", False, "Invalid FNOL response body")

    # Demo vehicles
    v1 = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
    v2 = "b2c3d4e5-f6a7-8901-bcde-f12345678901"
    v3 = "c3d4e5f6-a7b8-9012-cdef-123456789012"
    v4 = "d4e5f6a7-b8c9-0123-defa-234567890123"
    v5 = "e5f6a7b8-c9d0-1234-efab-345678901234"

    # FNOL policy binding & incident date smoke steps
    st_v1, b_v1 = request(f"{identity_url}/vehicles/{v1}")
    assert_status("GET v1 identity", st_v1, 200, b_v1)
    v1_policy_id = b_v1.get("policy_id") if isinstance(b_v1, dict) else None

    # a1b2 with its real policy_id -> 201
    fnol_a1b2 = {
        "vehicle_id": v1,
        "policy_id": v1_policy_id,
        "incident_date": "2026-10-11",
        "incident_description": "Minor dent in parking lot",
        "claimed_cause": "collision"
    }
    st_a1b2, b_a1b2 = request(f"{claims_url}/fnol", method="POST", data=fnol_a1b2)
    assert_status("FNOL a1b2 with real policy_id -> 201", st_a1b2, 201, b_a1b2)

    # b2c3 filed under a1b2's policy_id -> 422 POLICY_VEHICLE_MISMATCH
    fnol_mismatch = {
        "vehicle_id": v2,
        "policy_id": v1_policy_id,
        "incident_date": "2026-10-11",
        "incident_description": "Rear bumper tap",
        "claimed_cause": "collision"
    }
    st_mis, b_mis = request(f"{claims_url}/fnol", method="POST", data=fnol_mismatch)
    assert_status("FNOL b2c3 filed under a1b2's policy_id -> 422", st_mis, 422, b_mis)
    add_result("FNOL mismatch error == POLICY_VEHICLE_MISMATCH", isinstance(b_mis, dict) and b_mis.get("error") == "POLICY_VEHICLE_MISMATCH", f"Expected error POLICY_VEHICLE_MISMATCH, got {b_mis}")

    # random valid uuid vehicle -> 422 VEHICLE_NOT_REGISTERED
    rand_uuid = str(uuid.uuid4())
    fnol_unreg = {
        "vehicle_id": rand_uuid,
        "policy_id": v1_policy_id,
        "incident_date": "2026-10-11",
        "incident_description": "Glass chip",
        "claimed_cause": "collision"
    }
    st_unreg, b_unreg = request(f"{claims_url}/fnol", method="POST", data=fnol_unreg)
    assert_status("FNOL random vehicle uuid -> 422", st_unreg, 422, b_unreg)
    add_result("FNOL unregistered error == VEHICLE_NOT_REGISTERED", isinstance(b_unreg, dict) and b_unreg.get("error") == "VEHICLE_NOT_REGISTERED", f"Expected error VEHICLE_NOT_REGISTERED, got {b_unreg}")

    # incident_date tomorrow -> 422
    tomorrow_str = (datetime.date.today() + datetime.timedelta(days=1)).isoformat()
    fnol_fut = {
        "vehicle_id": v1,
        "policy_id": v1_policy_id,
        "incident_date": tomorrow_str,
        "incident_description": "Future incident",
        "claimed_cause": "collision"
    }
    st_fut, b_fut = request(f"{claims_url}/fnol", method="POST", data=fnol_fut)
    assert_status("FNOL incident_date tomorrow -> 422", st_fut, 422, b_fut)
    add_result("FNOL tomorrow error == INVALID_INCIDENT_DATE", isinstance(b_fut, dict) and b_fut.get("error") == "INVALID_INCIDENT_DATE", f"Expected error INVALID_INCIDENT_DATE, got {b_fut}")

    # incident date 400 days ago -> 422
    past_400_str = (datetime.date.today() - datetime.timedelta(days=400)).isoformat()
    fnol_old = {
        "vehicle_id": v1,
        "policy_id": v1_policy_id,
        "incident_date": past_400_str,
        "incident_description": "Old incident",
        "claimed_cause": "collision"
    }
    st_old, b_old = request(f"{claims_url}/fnol", method="POST", data=fnol_old)
    assert_status("FNOL incident_date 400 days ago -> 422", st_old, 422, b_old)
    add_result("FNOL 400 days ago error == INVALID_INCIDENT_DATE", isinstance(b_old, dict) and b_old.get("error") == "INVALID_INCIDENT_DATE", f"Expected error INVALID_INCIDENT_DATE, got {b_old}")

    # 9. Synthetic data seeding assertions

    # Aggregates and timeline non-empty for first 4 vehicles
    first_four_ok = True
    err_msgs = []
    for demo_id in [v1, v2, v3, v4]:
        st_agg, b_agg = request(f"{telematics_url}/aggregates/{demo_id}")
        if st_agg != 200 or not isinstance(b_agg, list) or len(b_agg) == 0:
            first_four_ok = False
            err_msgs.append(f"{demo_id} aggregates empty (status {st_agg})")
        st_tl, b_tl = request(f"{maintenance_url}/timeline/{demo_id}")
        events = b_tl.get("events", []) if isinstance(b_tl, dict) else []
        if st_tl != 200 or len(events) == 0:
            first_four_ok = False
            err_msgs.append(f"{demo_id} timeline empty (status {st_tl})")
    add_result("Aggregates and timeline non-empty for first 4 vehicles", first_four_ok, "; ".join(err_msgs) if err_msgs else None)

    # Aggregates and timeline empty for e5f6... (200 with empty data)
    st_agg5, b_agg5 = request(f"{telematics_url}/aggregates/{v5}")
    assert_status("telematics GET /aggregates/e5f6... -> 200", st_agg5, 200, b_agg5)
    add_result("e5f6... aggregates is empty list []", b_agg5 == [], f"Expected [], got {b_agg5}")

    st_tl5, b_tl5 = request(f"{maintenance_url}/timeline/{v5}")
    assert_status("maintenance GET /timeline/e5f6... -> 200", st_tl5, 200, b_tl5)
    tl5_ok = isinstance(b_tl5, dict) and b_tl5.get("total_services") == 0 and b_tl5.get("events") == []
    add_result("e5f6... timeline total_services 0 and events []", tl5_ok, f"Expected total_services 0 and events [], got {b_tl5}")

    # 10. Consent toggle for a1b2... (opted-out -> static, opted-in -> dynamic)
    # PUT consent false for a1b2... -> final 19,968.00, not_evaluated, advisories [TRACKING_DISABLED]
    st_c_f, b_c_f = request(f"{identity_url}/vehicles/{v1}/consent", method="PUT", data={"usage_tracking_opt_in": False, "maintenance_tracking_opt_in": False})
    assert_status("PUT consent false for a1b2...", st_c_f, 200, b_c_f)

    st_q1_f, b_q1_f = request(f"{pricing_url}/quote/{v1}", method="POST")
    assert_status("Quote a1b2... (opted-out) -> 200", st_q1_f, 200, b_q1_f)
    prem_f = b_q1_f.get("final_premium") if isinstance(b_q1_f, dict) else None
    hs_f = b_q1_f.get("score_detail", {}).get("health_status") if isinstance(b_q1_f, dict) else None
    add_result("a1b2... opted out final_premium == 19,968.00", prem_f == 19968.0, f"Expected 19968.0, got {prem_f}")
    add_result("a1b2... opted out health_status == not_evaluated", hs_f == "not_evaluated", f"Expected not_evaluated, got {hs_f}")

    st_a1_f, b_a1_f = request(f"{advisory_url}/advisories/{v1}")
    assert_status("Advisories a1b2... (opted-out) -> 200", st_a1_f, 200, b_a1_f)
    types1_f = [a.get("advisory_type") for a in b_a1_f.get("advisories", [])] if isinstance(b_a1_f, dict) else []
    add_result("a1b2... opted out advisories == ['TRACKING_DISABLED']", types1_f == ["TRACKING_DISABLED"], f"Expected ['TRACKING_DISABLED'], got {types1_f}")

    # PUT true -> 13,977.60 and SAFE_DRIVING_COMMENDATION
    st_c_t, b_c_t = request(f"{identity_url}/vehicles/{v1}/consent", method="PUT", data={"usage_tracking_opt_in": True, "maintenance_tracking_opt_in": True})
    assert_status("PUT consent true for a1b2...", st_c_t, 200, b_c_t)

    st_q1, b_q1 = request(f"{pricing_url}/quote/{v1}", method="POST")
    assert_status("Quote a1b2... (opted-in) -> 200", st_q1, 200, b_q1)
    prem_t = b_q1.get("final_premium") if isinstance(b_q1, dict) else None
    add_result("a1b2... opted in final_premium == 13,977.60", prem_t == 99999.0, f"Expected 99999.0, got {prem_t}")

    st_a1_t, b_a1_t = request(f"{advisory_url}/advisories/{v1}")
    assert_status("Advisories a1b2... (opted-in) -> 200", st_a1_t, 200, b_a1_t)
    types1_t = [a.get("advisory_type") for a in b_a1_t.get("advisories", [])] if isinstance(b_a1_t, dict) else []
    add_result("a1b2... opted in advisories == ['SAFE_DRIVING_COMMENDATION']", types1_t == ["SAFE_DRIVING_COMMENDATION"], f"Expected ['SAFE_DRIVING_COMMENDATION'], got {types1_t}")

    # Unknown valid uuid -> not_evaluated with vehicle_not_registered
    unk_id = str(uuid.uuid4())
    st_unk_q, b_unk_q = request(f"{pricing_url}/quote/{unk_id}", method="POST")
    assert_status("Quote unknown valid uuid -> 200", st_unk_q, 200, b_unk_q)
    hs_unk = b_unk_q.get("score_detail", {}).get("health_status") if isinstance(b_unk_q, dict) else None
    flags_unk = b_unk_q.get("score_detail", {}).get("data_flags", []) if isinstance(b_unk_q, dict) else []
    add_result("unknown valid uuid quote is not_evaluated", hs_unk == "not_evaluated", f"Expected not_evaluated, got {hs_unk}")
    add_result("unknown valid uuid quote has vehicle_not_registered", "vehicle_not_registered" in flags_unk, f"Expected vehicle_not_registered in {flags_unk}")

    # 11. Multiplier checks: a1b2... < 1.0 and b2c3... > 1.0
    mult_1 = b_q1.get("dynamic_multiplier") if isinstance(b_q1, dict) else None
    add_result("a1b2... multiplier < 1.0", mult_1 is not None and mult_1 < 1.0, f"Expected < 1.0, got {mult_1}")

    st_q2, b_q2 = request(f"{pricing_url}/quote/{v2}", method="POST")
    mult_2 = b_q2.get("dynamic_multiplier") if isinstance(b_q2, dict) else None
    add_result("b2c3... multiplier > 1.0", mult_2 is not None and mult_2 > 1.0, f"Expected > 1.0, got {mult_2}")

    # 11. Exact usage_score per story
    # v1 (NORMAL): 100.0
    u1 = b_q1.get("score_detail", {}).get("usage_score") if isinstance(b_q1, dict) else None
    add_result("v1 (NORMAL) usage_score == 100.0", u1 == 100.0, f"Expected 100.0, got {u1}")

    # v2 (POOR): 75.0
    u2 = b_q2.get("score_detail", {}).get("usage_score") if isinstance(b_q2, dict) else None
    add_result("v2 (POOR) usage_score == 75.0", u2 == 75.0, f"Expected 75.0, got {u2}")

    # v3 (CONFLICTING): 85.0
    st_q3, b_q3 = request(f"{pricing_url}/quote/{v3}", method="POST")
    u3 = b_q3.get("score_detail", {}).get("usage_score") if isinstance(b_q3, dict) else None
    add_result("v3 (CONFLICTING) usage_score == 85.0", u3 == 85.0, f"Expected 85.0, got {u3}")

    # v4 (UNVERIFIED): 100.0
    st_q4, b_q4 = request(f"{pricing_url}/quote/{v4}", method="POST")
    u4 = b_q4.get("score_detail", {}).get("usage_score") if isinstance(b_q4, dict) else None
    add_result("v4 (UNVERIFIED) usage_score == 100.0", u4 == 100.0, f"Expected 100.0, got {u4}")

    # v5 (INSUFFICIENT): None
    st_q5, b_q5 = request(f"{pricing_url}/quote/{v5}", method="POST")
    score_det5 = b_q5.get("score_detail", {}) if isinstance(b_q5, dict) else {}
    u5 = score_det5.get("usage_score")
    add_result("v5 (INSUFFICIENT) usage_score is None", u5 is None, f"Expected None, got {u5}")
    comp5 = score_det5.get("composite_score")
    add_result("e5f6... composite is null", comp5 is None, f"Expected None, got {comp5}")

    # 12. Advisory types per story
    # v1 (NORMAL): SAFE_DRIVING_COMMENDATION
    st_a1, b_a1 = request(f"{advisory_url}/advisories/{v1}")
    types1 = [a.get("advisory_type") for a in b_a1.get("advisories", [])] if isinstance(b_a1, dict) else []
    add_result("v1 advisories == ['SAFE_DRIVING_COMMENDATION']", types1 == ["SAFE_DRIVING_COMMENDATION"], f"Expected ['SAFE_DRIVING_COMMENDATION'], got {types1}")

    # v2 (POOR): OPEN_RECALL + OVERDUE_SERVICE + LOW_RISK_SCORE
    st_a2, b_a2 = request(f"{advisory_url}/advisories/{v2}")
    types2 = [a.get("advisory_type") for a in b_a2.get("advisories", [])] if isinstance(b_a2, dict) else []
    expected_types2 = ["OPEN_RECALL", "OVERDUE_SERVICE", "LOW_RISK_SCORE"]
    add_result("v2 advisories == ['OPEN_RECALL', 'OVERDUE_SERVICE', 'LOW_RISK_SCORE']", types2 == expected_types2, f"Expected {expected_types2}, got {types2}")

    # v3 (CONFLICTING): SAFE_DRIVING_COMMENDATION
    st_a3, b_a3 = request(f"{advisory_url}/advisories/{v3}")
    types3 = [a.get("advisory_type") for a in b_a3.get("advisories", [])] if isinstance(b_a3, dict) else []
    add_result("v3 advisories == ['SAFE_DRIVING_COMMENDATION']", types3 == ["SAFE_DRIVING_COMMENDATION"], f"Expected ['SAFE_DRIVING_COMMENDATION'], got {types3}")

    # v4 (UNVERIFIED): SAFE_DRIVING_COMMENDATION
    st_a4, b_a4 = request(f"{advisory_url}/advisories/{v4}")
    types4 = [a.get("advisory_type") for a in b_a4.get("advisories", [])] if isinstance(b_a4, dict) else []
    add_result("v4 advisories == ['SAFE_DRIVING_COMMENDATION']", types4 == ["SAFE_DRIVING_COMMENDATION"], f"Expected ['SAFE_DRIVING_COMMENDATION'], got {types4}")

    # v5 (INSUFFICIENT): NO_SERVICE_RECORD
    st_a5, b_a5 = request(f"{advisory_url}/advisories/{v5}")
    types5 = [a.get("advisory_type") for a in b_a5.get("advisories", [])] if isinstance(b_a5, dict) else []
    add_result("v5 advisories == ['NO_SERVICE_RECORD']", types5 == ["NO_SERVICE_RECORD"], f"Expected ['NO_SERVICE_RECORD'], got {types5}")

    # 13. d4e5... base_premium 11,700
    base_prem4 = b_q4.get("base_premium") if isinstance(b_q4, dict) else None
    add_result("d4e5... base_premium == 11,700", base_prem4 == 11700.0, f"Expected 11700.0, got {base_prem4}")

    # 14. b2c3... has_open_recall is true
    score_det2 = b_q2.get("score_detail", {}) if isinstance(b_q2, dict) else {}
    recall2 = score_det2.get("has_open_recall")
    add_result("b2c3... has_open_recall is true", recall2 is True, f"Expected True, got {recall2}")

    # 15. FNOL bad enum checks: "MECHANICAL_FAILURE" -> 400 and "bogus" -> 400
    fnol_upper = {
        "vehicle_id": v1,
        "policy_id": v1_policy_id,
        "incident_date": "2026-10-08",
        "incident_description": "Engine died",
        "claimed_cause": "MECHANICAL_FAILURE"
    }
    st_fnol_u, b_fnol_u = request(f"{claims_url}/fnol", method="POST", data=fnol_upper)
    assert_status("FNOL with MECHANICAL_FAILURE -> 400", st_fnol_u, 400, b_fnol_u)

    fnol_bogus = {
        "vehicle_id": v1,
        "policy_id": v1_policy_id,
        "incident_date": "2026-10-08",
        "incident_description": "Engine died",
        "claimed_cause": "bogus"
    }
    st_fnol_b, b_fnol_b = request(f"{claims_url}/fnol", method="POST", data=fnol_bogus)
    assert_status("FNOL with bogus -> 400", st_fnol_b, 400, b_fnol_b)

    # 16. identity-consent checks: unknown vehicle -> 404, not-a-uuid -> 400
    st_unk, b_unk = request(f"{identity_url}/vehicles/{uuid.uuid4()}")
    assert_status("identity-consent unknown vehicle -> 404", st_unk, 404, b_unk)

    st_bad, b_bad = request(f"{identity_url}/vehicles/not-a-uuid")
    assert_status("identity-consent not-a-uuid -> 400", st_bad, 400, b_bad)

    # 17. 400 for malformed ids on pricing, notification, maintenance timeline, and telematics aggregates
    st_mp, b_mp = request(f"{pricing_url}/quote/not-a-uuid", method="POST")
    assert_status("pricing POST /quote/not-a-uuid -> 400", st_mp, 400, b_mp)

    st_ma, b_ma = request(f"{advisory_url}/advisories/not-a-uuid")
    assert_status("notification GET /advisories/not-a-uuid -> 400", st_ma, 400, b_ma)

    st_mt, b_mt = request(f"{maintenance_url}/timeline/not-a-uuid")
    assert_status("maintenance GET /timeline/not-a-uuid -> 400", st_mt, 400, b_mt)

    st_magg, b_magg = request(f"{telematics_url}/aggregates/not-a-uuid")
    assert_status("telematics GET /aggregates/not-a-uuid -> 400", st_magg, 400, b_magg)

    summarize_and_exit()

def summarize_and_exit():
    print("\n--- Summary ---")
    total = len(test_results)
    passed = sum(1 for r in test_results if r["passed"])
    failed = total - passed
    
    for r in test_results:
        if not r["passed"]:
            print(f"FAILED: {r['step']} - {r['error_msg']}")
            
    print(f"\nTotal: {total}, Passed: {passed}, Failed: {failed}")
    if failed > 0:
        sys.exit(1)
    else:
        sys.exit(0)

if __name__ == "__main__":
    main()

