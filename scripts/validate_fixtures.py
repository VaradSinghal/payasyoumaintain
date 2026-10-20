#!/usr/bin/env python3
"""Validate mobile_app test fixtures against JSON contracts.

Uses jsonschema Draft202012Validator to validate fixtures under
mobile_app/test/fixtures/live and mobile_app/test/fixtures/requests.
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


def main() -> int:
    repo_root = find_repo_root()
    schemas_dir = repo_root / "contracts" / "schemas"

    schemas = {
        "premium-response": json.loads((schemas_dir / "premium-response.schema.json").read_text(encoding="utf-8")),
        "dtc-reading": json.loads((schemas_dir / "dtc-reading.schema.json").read_text(encoding="utf-8")),
        "trip-aggregate": json.loads((schemas_dir / "trip-aggregate.schema.json").read_text(encoding="utf-8")),
    }
    validators = {k: Draft202012Validator(v) for k, v in schemas.items()}

    fixture_dirs = [
        repo_root / "mobile_app" / "test" / "fixtures" / "live",
        repo_root / "mobile_app" / "test" / "fixtures" / "requests",
    ]

    total_checked = 0
    total_passed = 0
    total_failed = 0
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

            # Check for empty response (e.g., HTTP 204)
            if fpath.stat().st_size == 0:
                unmapped_files.append((rel_path, "empty body (HTTP 204)"))
                continue

            try:
                data = json.loads(fpath.read_text(encoding="utf-8"))
            except Exception as e:
                print(f"[FAIL] {rel_path}: JSON decode error: {e}")
                total_failed += 1
                total_checked += 1
                continue

            # Mapping logic
            if fname.startswith("quote_"):
                validator = validators["premium-response"]
                is_array = False
            elif fname.startswith("dtc_"):
                validator = validators["dtc-reading"]
                is_array = isinstance(data, list)
            elif fname.startswith("aggregates_"):
                validator = validators["trip-aggregate"]
                is_array = isinstance(data, list)
            else:
                unmapped_files.append((rel_path, "no contract schema defined"))
                continue

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

            if errors:
                total_failed += 1
                print(f"[FAIL] {rel_path}: {len(errors)} error(s)")
                for err_path, err_msg in errors:
                    print(f"   path: {err_path}: {err_msg}")
            else:
                total_passed += 1
                print(f"[PASS] {rel_path}")

    print("\n--------------------------------------------------")
    print(" Unmapped Fixtures (No Schema / Informational)     ")
    print("--------------------------------------------------")
    for unmapped_path, reason in unmapped_files:
        print(f" - {unmapped_path} ({reason})")

    print("\n==================================================")
    print(f" Summary: {total_passed} passed, {total_failed} failed, {len(unmapped_files)} unmapped")
    print("==================================================")

    return 1 if total_failed > 0 else 0


if __name__ == "__main__":
    sys.exit(main())
