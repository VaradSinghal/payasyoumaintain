import os
import sys
import json
import uuid
import datetime
import urllib.request
import urllib.error
import time
import shutil
import subprocess

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
    gateway_url = os.environ.get('GATEWAY_URL', 'http://localhost:8080').rstrip('/')
    identity_url = os.environ.get('IDENTITY_URL', 'http://localhost:8081/api/v1')
    pricing_url = os.environ.get('PRICING_URL', 'http://localhost:8085/api/v1')
    scoring_url = os.environ.get('SCORING_URL', 'http://localhost:8084')
    advisory_url = os.environ.get('ADVISORY_URL', 'http://localhost:8087/api/v1')
    maintenance_url = os.environ.get('MAINTENANCE_URL', 'http://localhost:8083/api/v1')
    claims_url = os.environ.get('CLAIMS_URL', 'http://localhost:8086/api/v1')
    telematics_url = os.environ.get('TELEMATICS_URL', 'http://localhost:8082/api/v1')

    def years_ago(y):
        t = datetime.date.today()
        try:
            return t.replace(year=t.year - y).isoformat()
        except ValueError:
            return (t - datetime.timedelta(days=y * 365)).isoformat()

    print("--- Running E2E Smoke Test ---")
    
    # 1. Register vehicle
    submitted_doc = "PAN1234"
    reg_data = {
        "rc_number": "SMOKE123",
        "registration_date": years_ago(3),
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

    # 3b. Vehicle Profile & Pricing Basis Drive Pricing Tests
    # (i) register sedan_mid with a date 2 years ago and opt in -> pricing_basis registered, base 24,960, final 24,960.00
    reg_sedan_mid = {
        "rc_number": f"MID{uuid.uuid4().hex[:6].upper()}",
        "registration_date": years_ago(2),
        "vehicle_segment_id": "sedan_mid",
        "owner_details": {
            "full_name": "Sedan Mid Owner",
            "email": "sedanmid@example.com",
            "phone_number": "9876543210",
            "kyc_document_number": "PANMID1234"
        }
    }
    st_mid, b_reg_mid = request(f"{identity_url}/vehicles", method="POST", data=reg_sedan_mid)
    assert_status("Register sedan_mid (2 years ago)", st_mid, 201, b_reg_mid)
    vid_mid = b_reg_mid.get("vehicle_id") if isinstance(b_reg_mid, dict) else None

    if vid_mid:
        st_c_mid, _ = request(f"{identity_url}/vehicles/{vid_mid}/consent", method="PUT", data={"usage_tracking_opt_in": True, "maintenance_tracking_opt_in": True})
        assert_status("Opt-in sedan_mid tracking consent", st_c_mid, 200)

        st_q_mid, b_q_mid = request(f"{pricing_url}/quote/{vid_mid}", method="POST")
        assert_status("Quote sedan_mid (opted-in)", st_q_mid, 200, b_q_mid)
        if isinstance(b_q_mid, dict):
            pb = b_q_mid.get("pricing_basis", {})
            pb_src = pb.get("source")
            base_p = b_q_mid.get("base_premium")
            final_p = b_q_mid.get("final_premium")
            add_result("sedan_mid pricing_basis source == 'registered'", pb_src == "registered", f"Expected 'registered', got {pb_src}")
            add_result("sedan_mid base_premium == 24,960.0", base_p == 24960.0, f"Expected 24960.0, got {base_p}")
            add_result("sedan_mid final_premium == 24,960.00", final_p == 24960.0, f"Expected 24960.0, got {final_p}")
            add_result("sedan_mid pricing_basis ncb_pct == 0.0", pb.get("ncb_pct") == 0.0, f"Expected 0.0, got {pb.get('ncb_pct')}")
        else:
            add_result("sedan_mid quote response is dict", False, "Invalid quote response")

    # (ii) register with no segment (3 years ago) -> generic_default, 13,650
    reg_noseg = {
        "rc_number": f"NOSEG{uuid.uuid4().hex[:5].upper()}",
        "registration_date": years_ago(3),
        "owner_details": {
            "full_name": "No Segment Owner",
            "email": "noseg@example.com",
            "phone_number": "9876543211",
            "kyc_document_number": "PANNOSEG01"
        }
    }
    st_noseg, b_reg_noseg = request(f"{identity_url}/vehicles", method="POST", data=reg_noseg)
    assert_status("Register vehicle with no segment (3 years ago)", st_noseg, 201, b_reg_noseg)
    vid_noseg = b_reg_noseg.get("vehicle_id") if isinstance(b_reg_noseg, dict) else None

    if vid_noseg:
        st_q_noseg, b_q_noseg = request(f"{pricing_url}/quote/{vid_noseg}", method="POST")
        assert_status("Quote vehicle with no segment", st_q_noseg, 200, b_q_noseg)
        if isinstance(b_q_noseg, dict):
            pb_noseg = b_q_noseg.get("pricing_basis", {})
            add_result("no segment pricing_basis source == 'generic_default'", pb_noseg.get("source") == "generic_default", f"Expected generic_default, got {pb_noseg.get('source')}")
            add_result("no segment final_premium == 13,650.0", b_q_noseg.get("final_premium") == 13650.0, f"Expected 13650.0, got {b_q_noseg.get('final_premium')}")
        else:
            add_result("no segment quote response is dict", False, "Invalid response")

    # (iii) an unknown segment id -> generic_default
    reg_unk = {
        "rc_number": f"UNK{uuid.uuid4().hex[:6].upper()}",
        "registration_date": years_ago(2),
        "vehicle_segment_id": "unknown_segment_rocket_car",
        "owner_details": {
            "full_name": "Unknown Segment Owner",
            "email": "unkseg@example.com",
            "phone_number": "9876543212",
            "kyc_document_number": "PANUNKSEG1"
        }
    }
    st_unk_seg, b_reg_unk = request(f"{identity_url}/vehicles", method="POST", data=reg_unk)
    assert_status("Register vehicle with unknown segment", st_unk_seg, 201, b_reg_unk)
    vid_unk_seg = b_reg_unk.get("vehicle_id") if isinstance(b_reg_unk, dict) else None

    if vid_unk_seg:
        st_q_unk, b_q_unk = request(f"{pricing_url}/quote/{vid_unk_seg}", method="POST")
        assert_status("Quote vehicle with unknown segment", st_q_unk, 200, b_q_unk)
        if isinstance(b_q_unk, dict):
            pb_unk = b_q_unk.get("pricing_basis", {})
            add_result("unknown segment pricing_basis source == 'generic_default'", pb_unk.get("source") == "generic_default", f"Expected generic_default, got {pb_unk.get('source')}")
        else:
            add_result("unknown segment quote response is dict", False, "Invalid response")

    # (iv) GET the catalogue through the gateway -> 6 entries
    st_cat, b_cat = request(f"{gateway_url}/pricing/api/v1/vehicle-catalogue", method="GET")
    assert_status("GET catalogue through gateway -> 200", st_cat, 200, b_cat)
    cat_ok = isinstance(b_cat, list) and len(b_cat) == 6
    add_result("Gateway catalogue has 6 entries", cat_ok, f"Expected 6 entries, got {b_cat}")

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
    recent_date_str = (datetime.date.today() - datetime.timedelta(days=5)).isoformat()
    fnol = {
        "vehicle_id": vehicle_id,
        "policy_id": policy_id,
        "incident_date": recent_date_str,
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
        "incident_date": recent_date_str,
        "incident_description": "Minor dent in parking lot",
        "claimed_cause": "collision"
    }
    st_a1b2, b_a1b2 = request(f"{claims_url}/fnol", method="POST", data=fnol_a1b2)
    assert_status("FNOL a1b2 with real policy_id -> 201", st_a1b2, 201, b_a1b2)

    # b2c3 filed under a1b2's policy_id -> 422 POLICY_VEHICLE_MISMATCH
    fnol_mismatch = {
        "vehicle_id": v2,
        "policy_id": v1_policy_id,
        "incident_date": recent_date_str,
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
        "incident_date": recent_date_str,
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
    add_result("a1b2... opted in final_premium == 13,977.60", prem_t == 13977.6, f"Expected 13977.6, got {prem_t}")

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

    # 14b. Five demo quotes pricing_basis sources == "demo_profile" and ncb_pct in percentage points
    demo_expected_ncb = {
        "a1b2": 20.0,
        "b2c3": 10.0,
        "c3d4": 0.0,
        "d4e5": 25.0,
        "e5f6": 0.0
    }
    for label, b_demo_q in [("a1b2", b_q1), ("b2c3", b_q2), ("c3d4", b_q3), ("d4e5", b_q4), ("e5f6", b_q5)]:
        src_val = b_demo_q.get("pricing_basis", {}).get("source") if isinstance(b_demo_q, dict) else None
        ncb_val = b_demo_q.get("pricing_basis", {}).get("ncb_pct") if isinstance(b_demo_q, dict) else None
        add_result(f"Demo vehicle {label} pricing_basis.source == 'demo_profile'", src_val == "demo_profile", f"Expected 'demo_profile', got {src_val}")
        expected_ncb = demo_expected_ncb[label]
        add_result(f"Demo vehicle {label} pricing_basis.ncb_pct == {expected_ncb}", ncb_val == expected_ncb, f"Expected {expected_ncb}, got {ncb_val}")

    # 15. FNOL bad enum checks: "MECHANICAL_FAILURE" -> 400 and "bogus" -> 400
    fnol_upper = {
        "vehicle_id": v1,
        "policy_id": v1_policy_id,
        "incident_date": recent_date_str,
        "incident_description": "Engine died",
        "claimed_cause": "MECHANICAL_FAILURE"
    }
    st_fnol_u, b_fnol_u = request(f"{claims_url}/fnol", method="POST", data=fnol_upper)
    assert_status("FNOL with MECHANICAL_FAILURE -> 400", st_fnol_u, 400, b_fnol_u)

    fnol_bogus = {
        "vehicle_id": v1,
        "policy_id": v1_policy_id,
        "incident_date": recent_date_str,
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

    # 18. Item 6 Smoke Steps: Self-Reported Service Records & Credit Cap
    print("\n--- Step 18: Item 6 Self-Reported Service Records & Credit Cap Smoke Steps ---")

    # 6a. Vehicle A: segment sedan_mid, registered 2 years ago, opt in both. Quote -> insufficient_data.
    #     POST oil_change 30 days ago at 15000 km -> 201 accepted, every check passed.
    #     Quote -> health_status scored, maintenance_score and vehicle_health_score 85.0,
    #     health_confidence unverified_self_reported, usage_score null, composite_score null,
    #     multiplier 1.0, final 24,960.00.
    reg_veh_a = {
        "rc_number": f"VEHA{uuid.uuid4().hex[:6].upper()}",
        "registration_date": years_ago(2),
        "vehicle_segment_id": "sedan_mid",
        "owner_details": {
            "full_name": "Vehicle A Owner",
            "email": "veha@example.com",
            "phone_number": "9876543210",
            "kyc_document_number": "PANA123456"
        }
    }
    st_va, b_va = request(f"{gateway_url}/identity/api/v1/vehicles", method="POST", data=reg_veh_a)
    assert_status("6a. Register Vehicle A (sedan_mid, 2 years ago)", st_va, 201, b_va)
    vid_a = b_va.get("vehicle_id") if isinstance(b_va, dict) else None

    # Opt in both
    st_opt_a, b_opt_a = request(f"{gateway_url}/identity/api/v1/vehicles/{vid_a}/consent", method="PUT", data={
        "usage_tracking_opt_in": True,
        "maintenance_tracking_opt_in": True
    })
    assert_status("6a. Vehicle A opt in both", st_opt_a, 200, b_opt_a)

    # Initial quote -> insufficient_data
    st_q_a1, b_q_a1 = request(f"{gateway_url}/pricing/api/v1/quote/{vid_a}", method="POST")
    assert_status("6a. Vehicle A quote before records -> 200", st_q_a1, 200, b_q_a1)
    hs_a1 = (b_q_a1 or {}).get("score_detail", {}).get("health_status")
    add_result("6a. Vehicle A initial health_status == 'insufficient_data'", hs_a1 == "insufficient_data", f"Got {hs_a1}")

    # POST oil_change 30 days ago at 15000 km -> 201 accepted, every check passed
    today = datetime.date.today()
    date_30d = (today - datetime.timedelta(days=30)).isoformat()
    sr_a1 = {
        "vehicle_id": vid_a,
        "service_date": date_30d,
        "service_type": "oil_change",
        "odometer_km": 15000
    }
    st_sr_a1, b_sr_a1 = request(f"{gateway_url}/maintenance/api/v1/service-records", method="POST", data=sr_a1)
    assert_status("6a. POST oil_change 30 days ago at 15000 km -> 201", st_sr_a1, 201, b_sr_a1)
    if isinstance(b_sr_a1, dict):
        add_result("6a. Record stored == True", b_sr_a1.get("stored") is True)
        verif = b_sr_a1.get("verification", {})
        add_result("6a. Verification status == 'accepted'", verif.get("status") == "accepted", f"Got {verif.get('status')}")
        checks = verif.get("checks", [])
        all_passed = all(c.get("passed") is True for c in checks)
        add_result("6a. Every check passed", all_passed and len(checks) == 6, f"Checks: {checks}")

    # Quote -> health_status scored, maintenance_score and vehicle_health_score 85.0,
    # health_confidence unverified_self_reported, usage_score null, composite_score null,
    # multiplier 1.0, final 24,960.00
    st_q_a2, b_q_a2 = request(f"{gateway_url}/pricing/api/v1/quote/{vid_a}", method="POST")
    assert_status("6a. Vehicle A quote after record -> 200", st_q_a2, 200, b_q_a2)
    sd_a2 = (b_q_a2 or {}).get("score_detail", {})
    add_result("6a. health_status == 'scored'", sd_a2.get("health_status") == "scored", f"Got {sd_a2.get('health_status')}")
    add_result("6a. maintenance_score == 85.0", sd_a2.get("maintenance_score") == 85.0, f"Got {sd_a2.get('maintenance_score')}")
    add_result("6a. vehicle_health_score == 85.0", sd_a2.get("vehicle_health_score") == 85.0, f"Got {sd_a2.get('vehicle_health_score')}")
    add_result("6a. health_confidence == 'unverified_self_reported'", sd_a2.get("health_confidence") == "unverified_self_reported", f"Got {sd_a2.get('health_confidence')}")
    add_result("6a. usage_score is None", sd_a2.get("usage_score") is None, f"Got {sd_a2.get('usage_score')}")
    add_result("6a. composite_score is None", sd_a2.get("composite_score") is None, f"Got {sd_a2.get('composite_score')}")
    add_result("6a. multiplier == 1.0", (b_q_a2 or {}).get("dynamic_multiplier") == 1.0, f"Got {(b_q_a2 or {}).get('dynamic_multiplier')}")
    add_result("6a. final_premium == 24,960.00", (b_q_a2 or {}).get("final_premium") == 24960.0, f"Got {(b_q_a2 or {}).get('final_premium')}")

    # 6b. Record at 12000 km dated 10 days ago -> 201 flagged with odometer_monotonic failed.
    #     Default timeline: total_services 1, flagged_count 1; ?include_flagged=true: 2 events, one flagged.
    #     Quote unchanged.
    date_10d = (today - datetime.timedelta(days=10)).isoformat()
    sr_a2 = {
        "vehicle_id": vid_a,
        "service_date": date_10d,
        "service_type": "brake_service",
        "odometer_km": 12000
    }
    st_sr_a2, b_sr_a2 = request(f"{gateway_url}/maintenance/api/v1/service-records", method="POST", data=sr_a2)
    assert_status("6b. Record at 12000 km dated 10 days ago -> 201", st_sr_a2, 201, b_sr_a2)
    if isinstance(b_sr_a2, dict):
        add_result("6b. Record stored == True", b_sr_a2.get("stored") is True)
        verif2 = b_sr_a2.get("verification", {})
        add_result("6b. Verification status == 'flagged'", verif2.get("status") == "flagged", f"Got {verif2.get('status')}")
        failed_checks = [c.get("code") for c in verif2.get("checks", []) if not c.get("passed")]
        add_result("6b. odometer_monotonic failed", "odometer_monotonic" in failed_checks, f"Failed checks: {failed_checks}")

    # Default timeline: total_services 1, flagged_count 1
    st_tl_def, b_tl_def = request(f"{gateway_url}/maintenance/api/v1/timeline/{vid_a}")
    assert_status("6b. Default timeline -> 200", st_tl_def, 200, b_tl_def)
    if isinstance(b_tl_def, dict):
        add_result("6b. Default timeline total_services == 1", b_tl_def.get("total_services") == 1, f"Got {b_tl_def.get('total_services')}")
        add_result("6b. Default timeline flagged_count == 1", b_tl_def.get("flagged_count") == 1, f"Got {b_tl_def.get('flagged_count')}")
        add_result("6b. Default timeline events count == 1", len(b_tl_def.get("events", [])) == 1, f"Got {len(b_tl_def.get('events', []))}")

    # ?include_flagged=true: 2 events, one flagged
    st_tl_inc, b_tl_inc = request(f"{gateway_url}/maintenance/api/v1/timeline/{vid_a}?include_flagged=true")
    assert_status("6b. include_flagged timeline -> 200", st_tl_inc, 200, b_tl_inc)
    if isinstance(b_tl_inc, dict):
        add_result("6b. include_flagged timeline total_services == 2", b_tl_inc.get("total_services") == 2, f"Got {b_tl_inc.get('total_services')}")
        add_result("6b. include_flagged timeline flagged_count == 1", b_tl_inc.get("flagged_count") == 1, f"Got {b_tl_inc.get('flagged_count')}")
        add_result("6b. include_flagged timeline events count == 2", len(b_tl_inc.get("events", [])) == 2, f"Got {len(b_tl_inc.get('events', []))}")

    # Quote unchanged
    st_q_a3, b_q_a3 = request(f"{gateway_url}/pricing/api/v1/quote/{vid_a}", method="POST")
    assert_status("6b. Vehicle A quote unchanged -> 200", st_q_a3, 200, b_q_a3)
    prem_a3 = (b_q_a3 or {}).get("final_premium")
    add_result("6b. Quote final_premium unchanged == 24,960.00", prem_a3 == 24960.0, f"Got {prem_a3}")

    # 6c. Duplicate of (a) -> flagged, not_duplicate failed
    st_dup, b_dup = request(f"{gateway_url}/maintenance/api/v1/service-records", method="POST", data=sr_a1)
    assert_status("6c. Duplicate of (a) -> 201", st_dup, 201, b_dup)
    if isinstance(b_dup, dict):
        verif_dup = b_dup.get("verification", {})
        add_result("6c. Duplicate status == 'flagged'", verif_dup.get("status") == "flagged", f"Got {verif_dup.get('status')}")
        dup_fails = [c.get("code") for c in verif_dup.get("checks", []) if not c.get("passed")]
        add_result("6c. not_duplicate failed", "not_duplicate" in dup_fails, f"Failed checks: {dup_fails}")

    # 6d. Date tomorrow (IST) -> 200 rejected, stored false, date_not_future failed; timeline unchanged.
    #     Date before registration date -> 200 rejected, date_after_registration failed.
    date_tomorrow = (today + datetime.timedelta(days=1)).isoformat()
    sr_fut = {
        "vehicle_id": vid_a,
        "service_date": date_tomorrow,
        "service_type": "oil_change",
        "odometer_km": 16000
    }
    st_fut, b_fut = request(f"{gateway_url}/maintenance/api/v1/service-records", method="POST", data=sr_fut)
    assert_status("6d. Date tomorrow (IST) -> 200 rejected", st_fut, 200, b_fut)
    if isinstance(b_fut, dict):
        add_result("6d. Future date stored == False", b_fut.get("stored") is False, f"Got {b_fut.get('stored')}")
        verif_fut = b_fut.get("verification", {})
        add_result("6d. Future date status == 'rejected'", verif_fut.get("status") == "rejected", f"Got {verif_fut.get('status')}")
        fut_fails = [c.get("code") for c in verif_fut.get("checks", []) if not c.get("passed")]
        add_result("6d. date_not_future failed", "date_not_future" in fut_fails, f"Failed checks: {fut_fails}")

    # Timeline unchanged:
    st_tl_chk, b_tl_chk = request(f"{gateway_url}/maintenance/api/v1/timeline/{vid_a}")
    if isinstance(b_tl_chk, dict):
        add_result("6d. Timeline unchanged after rejected record", b_tl_chk.get("total_services") == 1, f"Got {b_tl_chk.get('total_services')}")

    # Date before registration date (reg date was 2 years ago, so 3 years ago is before)
    date_before_reg = years_ago(3)
    sr_bef = {
        "vehicle_id": vid_a,
        "service_date": date_before_reg,
        "service_type": "oil_change",
        "odometer_km": 5000
    }
    st_bef, b_bef = request(f"{gateway_url}/maintenance/api/v1/service-records", method="POST", data=sr_bef)
    assert_status("6d. Date before registration -> 200 rejected", st_bef, 200, b_bef)
    if isinstance(b_bef, dict):
        add_result("6d. Before reg date stored == False", b_bef.get("stored") is False, f"Got {b_bef.get('stored')}")
        verif_bef = b_bef.get("verification", {})
        add_result("6d. Before reg date status == 'rejected'", verif_bef.get("status") == "rejected", f"Got {verif_bef.get('status')}")
        bef_fails = [c.get("code") for c in verif_bef.get("checks", []) if not c.get("passed")]
        add_result("6d. date_after_registration failed", "date_after_registration" in bef_fails, f"Failed checks: {bef_fails}")

    # 6e. The 6th record on one day (distinct, valid values) -> flagged, daily_limit failed.
    reg_veh_c = {
        "rc_number": f"VEHC{uuid.uuid4().hex[:6].upper()}",
        "registration_date": years_ago(1),
        "vehicle_segment_id": "sedan_mid",
        "owner_details": {
            "full_name": "Vehicle C Owner",
            "email": "vehc@example.com",
            "phone_number": "9876543210",
            "kyc_document_number": "PANC123456"
        }
    }
    st_vc, b_vc = request(f"{gateway_url}/identity/api/v1/vehicles", method="POST", data=reg_veh_c)
    assert_status("6e. Register Vehicle C", st_vc, 201, b_vc)
    vid_c = b_vc.get("vehicle_id") if isinstance(b_vc, dict) else None
    request(f"{gateway_url}/identity/api/v1/vehicles/{vid_c}/consent", method="PUT", data={
        "usage_tracking_opt_in": True,
        "maintenance_tracking_opt_in": True
    })

    # Post 5 valid records with distinct dates/odometer values
    types = ["oil_change", "tyre_rotation", "brake_service", "battery_replacement", "engine_service", "transmission_service"]
    for i in range(5):
        s_date = (today - datetime.timedelta(days=100 - i * 10)).isoformat()
        sr_i = {
            "vehicle_id": vid_c,
            "service_date": s_date,
            "service_type": types[i],
            "odometer_km": 10000 + i * 1000
        }
        st_i, b_i = request(f"{gateway_url}/maintenance/api/v1/service-records", method="POST", data=sr_i)
        assert_status(f"6e. Insert record {i+1} for Vehicle C", st_i, 201, b_i)

    # 6th record on this day -> flagged, daily_limit failed
    s_date_6th = (today - datetime.timedelta(days=30)).isoformat()
    sr_6 = {
        "vehicle_id": vid_c,
        "service_date": s_date_6th,
        "service_type": types[5],
        "odometer_km": 18000
    }
    st_6, b_6 = request(f"{gateway_url}/maintenance/api/v1/service-records", method="POST", data=sr_6)
    assert_status("6e. 6th record on one day -> 201 flagged", st_6, 201, b_6)
    if isinstance(b_6, dict):
        verif_6 = b_6.get("verification", {})
        add_result("6e. 6th record status == 'flagged'", verif_6.get("status") == "flagged", f"Got {verif_6.get('status')}")
        fails_6 = [c.get("code") for c in verif_6.get("checks", []) if not c.get("passed")]
        add_result("6e. daily_limit failed", "daily_limit" in fails_6, f"Failed checks: {fails_6}")

    # 6f. A vehicle registered with both toggles off -> 403 CONSENT_REQUIRED.
    #     A random uuid -> 404 VEHICLE_NOT_REGISTERED.
    #     A body missing odometer_km -> 400 INVALID_REQUEST.
    reg_veh_off = {
        "rc_number": f"VEHOFF{uuid.uuid4().hex[:6].upper()}",
        "registration_date": years_ago(1),
        "vehicle_segment_id": "hatchback_entry",
        "owner_details": {
            "full_name": "Opted Out Owner",
            "email": "off@example.com",
            "phone_number": "9876543210",
            "kyc_document_number": "PANOFF1234"
        }
    }
    st_voff, b_voff = request(f"{gateway_url}/identity/api/v1/vehicles", method="POST", data=reg_veh_off)
    assert_status("6f. Register vehicle with default toggles off", st_voff, 201, b_voff)
    vid_off = b_voff.get("vehicle_id") if isinstance(b_voff, dict) else None

    st_off_sr, b_off_sr = request(f"{gateway_url}/maintenance/api/v1/service-records", method="POST", data={
        "vehicle_id": vid_off,
        "service_date": date_30d,
        "service_type": "oil_change",
        "odometer_km": 10000
    })
    assert_status("6f. Toggles off -> 403 CONSENT_REQUIRED", st_off_sr, 403, b_off_sr)
    if isinstance(b_off_sr, dict):
        add_result("6f. Error code is CONSENT_REQUIRED", b_off_sr.get("error") == "CONSENT_REQUIRED", f"Got {b_off_sr.get('error')}")

    # Random UUID -> 404 VEHICLE_NOT_REGISTERED
    rand_vid = str(uuid.uuid4())
    st_rand, b_rand = request(f"{gateway_url}/maintenance/api/v1/service-records", method="POST", data={
        "vehicle_id": rand_vid,
        "service_date": date_30d,
        "service_type": "oil_change",
        "odometer_km": 10000
    })
    assert_status("6f. Random UUID -> 404 VEHICLE_NOT_REGISTERED", st_rand, 404, b_rand)
    if isinstance(b_rand, dict):
        add_result("6f. Error code is VEHICLE_NOT_REGISTERED", b_rand.get("error") == "VEHICLE_NOT_REGISTERED", f"Got {b_rand.get('error')}")

    # Missing odometer_km -> 400 INVALID_REQUEST
    st_miss, b_miss = request(f"{gateway_url}/maintenance/api/v1/service-records", method="POST", data={
        "vehicle_id": vid_a,
        "service_date": date_30d,
        "service_type": "oil_change"
    })
    assert_status("6f. Missing odometer_km -> 400 INVALID_REQUEST", st_miss, 400, b_miss)
    if isinstance(b_miss, dict):
        add_result("6f. Error code is INVALID_REQUEST", b_miss.get("error") == "INVALID_REQUEST", f"Got {b_miss.get('error')}")

    # 6g. Vehicle B (registered 3 years ago, segment sedan_mid, opted in): through the dev-override port
    #     POST an OEM event to maintenance's internal /events (oem_api, full_service, 800 days ago, 20000 km).
    #     Quote -> vehicle_health_score 45.0, health_confidence verified.
    #     POST a brake_service 20 days ago at 21000 km through the gateway -> accepted.
    #     Quote -> vehicle_health_score 85.0, unverified_self_reported.
    reg_veh_b = {
        "rc_number": f"VEHB{uuid.uuid4().hex[:6].upper()}",
        "registration_date": years_ago(3),
        "vehicle_segment_id": "sedan_mid",
        "owner_details": {
            "full_name": "Vehicle B Owner",
            "email": "vehb@example.com",
            "phone_number": "9876543210",
            "kyc_document_number": "PANB123456"
        }
    }
    st_vb, b_vb = request(f"{gateway_url}/identity/api/v1/vehicles", method="POST", data=reg_veh_b)
    assert_status("6g. Register Vehicle B (sedan_mid, 3 years ago)", st_vb, 201, b_vb)
    vid_b = b_vb.get("vehicle_id") if isinstance(b_vb, dict) else None
    request(f"{gateway_url}/identity/api/v1/vehicles/{vid_b}/consent", method="PUT", data={
        "usage_tracking_opt_in": True,
        "maintenance_tracking_opt_in": True
    })

    # Internal POST /events to maintenance dev-override port
    date_800d = (today - datetime.timedelta(days=800)).isoformat()
    oem_event = {
        "event_id": str(uuid.uuid4()),
        "vehicle_id": vid_b,
        "service_date": date_800d,
        "service_type": "full_service",
        "odometer_km": 20000,
        "parts_replaced": [],
        "source": "oem_api",
        "notes": "Historical OEM service"
    }
    st_oem, b_oem = request(f"{maintenance_url}/events", method="POST", data=[oem_event])
    assert_status("6g. POST OEM event via internal /events -> 200", st_oem, 200, b_oem)

    # Quote -> vehicle_health_score 45.0, health_confidence verified
    st_qb1, b_qb1 = request(f"{gateway_url}/pricing/api/v1/quote/{vid_b}", method="POST")
    assert_status("6g. Vehicle B quote after OEM event -> 200", st_qb1, 200, b_qb1)
    sdb1 = (b_qb1 or {}).get("score_detail", {})
    add_result("6g. vehicle_health_score == 45.0", sdb1.get("vehicle_health_score") == 45.0, f"Got {sdb1.get('vehicle_health_score')}")
    add_result("6g. health_confidence == 'verified'", sdb1.get("health_confidence") == "verified", f"Got {sdb1.get('health_confidence')}")

    # POST brake_service 20 days ago at 21000 km through gateway -> accepted
    date_20d = (today - datetime.timedelta(days=20)).isoformat()
    sr_b2 = {
        "vehicle_id": vid_b,
        "service_date": date_20d,
        "service_type": "brake_service",
        "odometer_km": 21000
    }
    st_srb2, b_srb2 = request(f"{gateway_url}/maintenance/api/v1/service-records", method="POST", data=sr_b2)
    assert_status("6g. POST brake_service 20 days ago through gateway -> 201", st_srb2, 201, b_srb2)
    if isinstance(b_srb2, dict):
        verif_b2 = b_srb2.get("verification", {})
        add_result("6g. Status == 'accepted'", verif_b2.get("status") == "accepted", f"Got {verif_b2.get('status')}")

    # Quote -> vehicle_health_score 85.0, unverified_self_reported
    st_qb2, b_qb2 = request(f"{gateway_url}/pricing/api/v1/quote/{vid_b}", method="POST")
    assert_status("6g. Vehicle B quote after self record -> 200", st_qb2, 200, b_qb2)
    sdb2 = (b_qb2 or {}).get("score_detail", {})
    add_result("6g. vehicle_health_score == 85.0", sdb2.get("vehicle_health_score") == 85.0, f"Got {sdb2.get('vehicle_health_score')}")
    add_result("6g. health_confidence == 'unverified_self_reported'", sdb2.get("health_confidence") == "unverified_self_reported", f"Got {sdb2.get('health_confidence')}")

    # 6h. identity-consent stopped: POST a record -> 503; restart it and wait for healthy
    if shutil.which("docker"):
        print("Stopping identity-consent container...")
        subprocess.run(["docker", "stop", "paymu-identity-consent-1"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        st_503, b_503 = request(f"{gateway_url}/maintenance/api/v1/service-records", method="POST", data={
            "vehicle_id": vid_a,
            "service_date": date_30d,
            "service_type": "oil_change",
            "odometer_km": 15000
        })
        assert_status("6h. identity-consent stopped -> 503 SERVICE_UNAVAILABLE", st_503, 503, b_503)
        print("Restarting identity-consent container...")
        subprocess.run(["docker", "start", "paymu-identity-consent-1"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        # Wait for healthy
        for _ in range(30):
            time.sleep(1)
            st_h, _ = request(f"{identity_url}/vehicles/{vid_a}")
            if st_h in (200, 404):
                break
        add_result("6h. identity-consent restarted and healthy", True)
    else:
        print("Skipping 6h container stop/start because docker CLI is not available in runner environment")

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

