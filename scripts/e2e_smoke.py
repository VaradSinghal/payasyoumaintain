import os
import sys
import json
import uuid
import urllib.request
import urllib.error

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

def print_result(step, status, expected, body=None):
    if status == expected:
        print(f"[{step}] PASS")
    else:
        print(f"[{step}] FAIL (Expected {expected}, got {status})")
        if body:
            print(f"Response Body: {body}")
        sys.exit(1)

def main():
    identity_url = os.environ.get('IDENTITY_URL', 'http://localhost:8081/api/v1')
    pricing_url = os.environ.get('PRICING_URL', 'http://localhost:8085/api/v1')
    scoring_url = os.environ.get('SCORING_URL', 'http://localhost:8084')
    advisory_url = os.environ.get('ADVISORY_URL', 'http://localhost:8087/api/v1')
    maintenance_url = os.environ.get('MAINTENANCE_URL', 'http://localhost:8083/api/v1')
    claims_url = os.environ.get('CLAIMS_URL', 'http://localhost:8086/api/v1')

    print("--- Running E2E Smoke Test ---")
    
    # 1. Register vehicle
    reg_data = {
        "rc_number": "SMOKE123",
        "registration_date": "2023-01-01",
        "owner_details": {
            "full_name": "Test Owner",
            "email": "test@example.com",
            "phone_number": "1234567890",
            "kyc_document_number": "PAN123"
        }
    }
    status, body = request(f"{identity_url}/vehicles", method="POST", data=reg_data)
    print_result("Register vehicle", status, 201, body)
    vehicle_id = body.get('vehicle_id')
    policy_id = body.get('policy_id')

    # 2. Update consent
    status, body = request(f"{identity_url}/vehicles/{vehicle_id}/consent", method="PUT", data={"usage_tracking_opt_in": True, "maintenance_tracking_opt_in": True})
    print_result("Update consent", status, 200, body)

    # 3. Score
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
    print_result("Score (fresh)", status, 200, body)

    # 4. Quote twice (stable policy_id)
    status, body1 = request(f"{pricing_url}/quote/{vehicle_id}", method="POST")
    print_result("Quote 1", status, 200, body1)
    
    status, body2 = request(f"{pricing_url}/quote/{vehicle_id}", method="POST")
    print_result("Quote 2", status, 200, body2)
    if body1.get('policy_id') == body2.get('policy_id') == policy_id:
        print("[Policy ID stability] PASS")
    else:
        print(f"[Policy ID stability] FAIL (Expected {policy_id}, got {body1.get('policy_id')} and {body2.get('policy_id')})")

    # 5. Advisories
    status, body = request(f"{advisory_url}/advisories/{vehicle_id}", method="GET")
    print_result("Advisories", status, 200)

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
    print_result("POST DTC full", status, 201, body)

    dtc_empty = {
        "vehicle_id": vehicle_id,
        "timestamp": "2026-10-08T10:05:00Z",
        "source": "manual_entry",
        "device_id": None,
        "confirmed_codes": [],
        "pending_codes": []
    }
    status, body = request(f"{maintenance_url}/dtc-readings", method="POST", data=dtc_empty)
    print_result("POST DTC empty", status, 201, body)

    status, body = request(f"{maintenance_url}/dtc-readings/{vehicle_id}/latest", method="GET")
    print_result("GET latest DTC", status, 200, body)

    status, body = request(f"{maintenance_url}/dtc-readings/{vehicle_id}/history", method="GET")
    print_result("GET DTC history", status, 200, body)
    
    # 204 and [] for vehicle with none
    fresh_id = str(uuid.uuid4())
    status, body = request(f"{maintenance_url}/dtc-readings/{fresh_id}/latest", method="GET")
    print_result("GET latest DTC (none)", status, 204, body)
    
    status, body = request(f"{maintenance_url}/dtc-readings/{fresh_id}/history", method="GET")
    print_result("GET DTC history (none)", status, 200, body)
    if body == []:
        print("[History empty check] PASS")
    else:
        print("[History empty check] FAIL")
        sys.exit(1)

    # 7. FNOL
    fnol = {
        "vehicle_id": vehicle_id,
        "policy_id": policy_id,
        "incident_date": "2026-10-08T10:00:00Z",
        "claimed_cause": "mechanical_failure",
        "incident_description": "Engine died"
    }
    status, body = request(f"{claims_url}/fnol", method="POST", data=fnol)
    print_result("FNOL", status, 201, body)
    claim_id = body.get('claim_id')

    # 8. GET claim
    status, body = request(f"{claims_url}/claims/{claim_id}", method="GET")
    print_result("GET claim", status, 200, body)

    print("All tests passed.")

if __name__ == "__main__":
    main()
