#!/usr/bin/env python3
"""Validate mobile_app test fixtures against JSON contracts.

Uses jsonschema Draft202012Validator to validate fixtures under
mobile_app/test/fixtures/live and mobile_app/test/fixtures/requests.

Acts as a CI gate: exits non-zero on any failing mapped fixture, except
files listed in scripts/known_stale_fixtures.json. If a listed file passes,
it also exits non-zero ("remove from the list"), preventing list rot.
"""

import json
import os
import sys
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


def load_known_stale(repo_root: Path) -> dict[str, dict]:
    stale_file = repo_root / "scripts" / "known_stale_fixtures.json"
    if not stale_file.is_file():
        return {}
    try:
        entries = json.loads(stale_file.read_text(encoding="utf-8"))
        result = {}
        for entry in entries:
            f = entry.get("file", "")
            norm = f.replace("\\", "/").strip()
            result[norm] = entry
            # Also key by basename for easy matching
            base = Path(norm).name
            if base:
                result[base] = entry
        return result
    except Exception as e:
        print(f"Warning: could not parse {stale_file}: {e}")
        return {}


def main() -> int:
    repo_root = find_repo_root()
    schemas_dir = repo_root / "contracts" / "schemas"
    known_stale = load_known_stale(repo_root)

    schemas = {
        "premium-response": json.loads((schemas_dir / "premium-response.schema.json").read_text(encoding="utf-8")),
        "dtc-reading": json.loads((schemas_dir / "dtc-reading.schema.json").read_text(encoding="utf-8")),
        "trip-aggregate": json.loads((schemas_dir / "trip-aggregate.schema.json").read_text(encoding="utf-8")),
        "vehicle-registration-response": json.loads((schemas_dir / "vehicle-registration-response.schema.json").read_text(encoding="utf-8")),
        "vehicle-registration-request": json.loads((schemas_dir / "vehicle-registration-request.schema.json").read_text(encoding="utf-8")),
        "consent-record": json.loads((schemas_dir / "consent-record.schema.json").read_text(encoding="utf-8")),
        "consent-update-request": json.loads((schemas_dir / "consent-update-request.schema.json").read_text(encoding="utf-8")),
        "fnol-request": json.loads((schemas_dir / "fnol-request.schema.json").read_text(encoding="utf-8")),
        "fnol-response": json.loads((schemas_dir / "fnol-response.schema.json").read_text(encoding="utf-8")),
        "advisory-response": json.loads((schemas_dir / "advisory-response.schema.json").read_text(encoding="utf-8")),
        "service-timeline": json.loads((schemas_dir / "service-timeline.schema.json").read_text(encoding="utf-8")),
        "error-response": json.loads((schemas_dir / "error-response.schema.json").read_text(encoding="utf-8")),
        "vehicle-catalogue": json.loads((schemas_dir / "vehicle-catalogue.schema.json").read_text(encoding="utf-8")),
    }
    validators = {k: Draft202012Validator(v) for k, v in schemas.items()}

    fixture_dirs = [
        repo_root / "mobile_app" / "test" / "fixtures" / "live",
        repo_root / "mobile_app" / "test" / "fixtures" / "requests",
    ]

    total_checked = 0
    total_passed = 0
    total_failed = 0
    excused_stale = []
    unexcused_failures = []
    stale_that_passed = []
    unmapped_files = []

    print("==================================================")
    print(" Validating Mobile App Fixtures Against Contracts ")
    print("==================================================")

    for fdir in fixture_dirs:
        if not fdir.exists():
            print(f"Warning: Directory not found: {fdir}")
            continue

        for fpath in sorted(fdir.glob("*.json")):
            fname = fpath.name
            rel_path = fpath.relative_to(repo_root)
            norm_rel_path = str(rel_path).replace("\\", "/")
            is_request = "requests" in norm_rel_path

            # Check for empty response (e.g., HTTP 204)
            if fpath.stat().st_size == 0:
                unmapped_files.append((norm_rel_path, "empty body (HTTP 204)"))
                continue

            try:
                data = json.loads(fpath.read_text(encoding="utf-8"))
            except Exception as e:
                print(f"[FAIL] {norm_rel_path}: JSON decode error: {e}")
                total_failed += 1
                unexcused_failures.append((norm_rel_path, f"JSON decode error: {e}"))
                total_checked += 1
                continue

            # Mapping logic
            schema_name = None
            is_array = False

            if is_request:
                if fname.startswith("consent_update_request"):
                    schema_name = "consent-update-request"
                elif fname.startswith("dtc_"):
                    schema_name = "dtc-reading"
                elif fname.startswith("fnol_request"):
                    schema_name = "fnol-request"
                elif fname.startswith("registration_request"):
                    schema_name = "vehicle-registration-request"
            else:
                if fname.startswith("quote_"):
                    schema_name = "premium-response"
                elif fname.startswith("dtc_history"):
                    schema_name = "dtc-reading"
                    is_array = True
                elif fname.startswith("dtc_"):
                    schema_name = "dtc-reading"
                    is_array = isinstance(data, list)
                elif fname.startswith("aggregates_"):
                    schema_name = "trip-aggregate"
                    is_array = isinstance(data, list)
                elif fname.startswith("advisories_"):
                    schema_name = "advisory-response"
                elif fname.startswith("timeline_"):
                    schema_name = "service-timeline"
                elif fname.startswith("consent_"):
                    schema_name = "consent-record"
                elif fname.startswith("registration_"):
                    schema_name = "vehicle-registration-response"
                elif fname.startswith("fnol_201") or fname == "claim_get_200.json":
                    schema_name = "fnol-response"
                elif fname.startswith("vehicle_catalogue_"):
                    schema_name = "vehicle-catalogue"
                elif any(err in fname for err in ["_400", "_404", "_422", "_500"]) or "error" in fname:
                    schema_name = "error-response"

            if not schema_name:
                unmapped_files.append((norm_rel_path, "no contract schema defined"))
                continue

            validator = validators[schema_name]
            total_checked += 1
            errors = []

            if is_array:
                for idx, item in enumerate(data):
                    item_errs = list(validator.iter_errors(item))
                    for err in item_errs:
                        err_path = f"[{idx}]." + ".".join(str(p) for p in err.path) if err.path else f"[{idx}]"
                        errors.append((err_path, err.message))
            else:
                item_errs = list(validator.iter_errors(data))
                for err in item_errs:
                    err_path = ".".join(str(p) for p in err.path) if err.path else "(root)"
                    errors.append((err_path, err.message))

            is_in_stale_list = (norm_rel_path in known_stale) or (fname in known_stale)
            stale_entry = known_stale.get(norm_rel_path) or known_stale.get(fname)

            if errors:
                total_failed += 1
                if is_in_stale_list:
                    reason = stale_entry.get("reason", "known stale fixture")
                    excused_stale.append((norm_rel_path, reason, errors))
                    print(f"[EXCUSED] {norm_rel_path} ({schema_name}): {reason}")
                else:
                    unexcused_failures.append((norm_rel_path, "Schema or service mismatch", errors))
                    print(f"[FAIL] {norm_rel_path} ({schema_name}): {len(errors)} error(s)")
                for err_path, err_msg in errors:
                    print(f"   path: {err_path}: {err_msg}")
            else:
                total_passed += 1
                if is_in_stale_list:
                    stale_that_passed.append(norm_rel_path)
                    print(f"[FAIL-ROT] {norm_rel_path} passed schema validation but is listed in known_stale_fixtures.json — remove from the list!")
                else:
                    print(f"[PASS] {norm_rel_path} ({schema_name})")

    print("\n--------------------------------------------------")
    print(" Unmapped Fixtures (No Schema / Informational)     ")
    print("--------------------------------------------------")
    for unmapped_path, reason in unmapped_files:
        print(f" - {unmapped_path} ({reason})")

    if excused_stale:
        print("\n--------------------------------------------------")
        print(" Excused Stale Fixtures (scripts/known_stale_fixtures.json)")
        print("--------------------------------------------------")
        for s_path, s_reason, _ in excused_stale:
            print(f" - {s_path}: {s_reason}")

    print("\n==================================================")
    print(f" Summary: {total_passed} passed, {total_failed} failed ({len(excused_stale)} excused stale, {len(unexcused_failures)} unexcused failures, {len(stale_that_passed)} rotted entries), {len(unmapped_files)} unmapped")
    print("==================================================")

    if stale_that_passed:
        print("\n[ERROR] One or more fixtures listed in scripts/known_stale_fixtures.json now PASS.")
        print("Remove them from scripts/known_stale_fixtures.json to keep the list clean:")
        for r_file in stale_that_passed:
            print(f"  - {r_file}")
        return 1

    if unexcused_failures:
        print(f"\n[ERROR] {len(unexcused_failures)} fixture(s) failed contract validation unexpectedly.")
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
