#!/usr/bin/env python3
"""
scripts/demo_reset.py

Restarts the in-memory docker compose stack back to seeded state and runs demo_preflight.py.
Flags:
  --dev           Adds -f docker-compose.dev.yml to docker compose restart
  --no-preflight  Skips running demo_preflight.py after restarting
"""

import sys
import os
import time
import argparse
import subprocess
import urllib.request
import urllib.error
from pathlib import Path

DEFAULT_GATEWAY_URL = "http://localhost:8080"
SERVICES = ["identity", "telematics", "maintenance", "pricing", "claims", "advisory", "scoring"]

def check_service_health(gateway_url, service):
    try:
        url = f"{gateway_url}/health/{service}"
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req, timeout=5) as resp:
            return resp.status == 200
    except Exception:
        return False

def wait_for_services(gateway_url, timeout=180):
    print(f"Waiting up to {timeout}s for all microservices to report healthy on {gateway_url}...")
    start_time = time.time()
    while time.time() - start_time < timeout:
        all_up = True
        pending = []
        for s in SERVICES:
            if not check_service_health(gateway_url, s):
                all_up = False
                pending.append(s)
        if all_up:
            elapsed = int(time.time() - start_time)
            print(f"All 7 microservices healthy after {elapsed}s.")
            return True
        time.sleep(2)
    print(f"Timed out after {timeout}s. Still pending: {pending}")
    return False

def run_reset():
    parser = argparse.ArgumentParser(description="Restart demo stack and verify seeded state.")
    parser.add_argument("--dev", action="store_true", help="Include docker-compose.dev.yml")
    parser.add_argument("--no-preflight", action="store_true", help="Skip running demo_preflight.py")
    parser.add_argument("--gateway-url", default=None, help=f"Gateway URL (default: {DEFAULT_GATEWAY_URL} or GATEWAY_URL env)")
    args = parser.parse_args()

    gateway_url = (args.gateway_url or os.environ.get("GATEWAY_URL", DEFAULT_GATEWAY_URL)).rstrip("/")

    # Build docker compose restart command (no build, no down - works offline)
    if args.dev or "docker-compose.dev.yml" in os.environ.get("COMPOSE_FILE", ""):
        compose_cmd = ["docker", "compose", "-f", "docker-compose.yml", "-f", "docker-compose.dev.yml", "restart"]
    else:
        compose_cmd = ["docker", "compose", "restart"]

    print("=================================================================")
    print(f"DEMO RESET: {' '.join(compose_cmd)}")
    print("=================================================================")
    try:
        res = subprocess.run(compose_cmd, check=True)
    except subprocess.CalledProcessError as e:
        print(f"Docker compose restart failed with exit code {e.returncode}")
        sys.exit(e.returncode)
    except Exception as e:
        print(f"Failed to execute docker compose: {e}")
        sys.exit(1)

    # Wait for services to return 200 on /health/*
    if not wait_for_services(gateway_url, timeout=180):
        print("Demo reset failed: microservices did not become healthy in time.")
        sys.exit(1)

    # Run demo_preflight unless --no-preflight is specified
    if args.no_preflight:
        print("Skipping preflight as requested (--no-preflight).")
        sys.exit(0)

    print("\nRunning demo preflight verification...")
    repo_root = Path(__file__).resolve().parent.parent
    preflight_script = repo_root / "scripts" / "demo_preflight.py"
    env = os.environ.copy()
    env["GATEWAY_URL"] = gateway_url

    preflight_res = subprocess.run([sys.executable, str(preflight_script)], env=env)
    sys.exit(preflight_res.returncode)

if __name__ == "__main__":
    run_reset()
