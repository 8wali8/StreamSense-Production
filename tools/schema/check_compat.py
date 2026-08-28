#!/usr/bin/env python3
"""Backward-compatibility check for the Kafka event schemas under docs/schemas.

Compares every ``*.schema.json`` in the working tree against the same file on a base git
ref (default ``origin/main``) and fails on changes that would break an existing consumer:

- a required property was added (old producers would not send it);
- a property was removed (old consumers may read it);
- a property's ``type`` changed or narrowed (``["string","null"]`` -> ``"string"`` counts);
- an ``enum`` lost a value;

Adding an optional property, widening a type to allow null, or adding an enum value is fine;
setting ``additionalProperties`` to false is reported as a warning because only the contract tests enforce it.
Schemas that do not exist on the base ref are new and pass. Run from the repository root:

    python tools/schema/check_compat.py [--base origin/main]
"""

from __future__ import annotations

import argparse
import json
import pathlib
import subprocess
import sys

SCHEMA_DIR = pathlib.Path("docs/schemas")


def load_base(ref: str, path: pathlib.Path) -> dict | None:
    result = subprocess.run(
        ["git", "show", f"{ref}:{path.as_posix()}"], capture_output=True, text=True, encoding="utf-8"
    )
    if result.returncode != 0:
        return None
    return json.loads(result.stdout)


def types_of(prop: dict) -> set[str]:
    declared = prop.get("type")
    if declared is None:
        return {"any"}
    return set(declared) if isinstance(declared, list) else {declared}


def compare(name: str, old: dict, new: dict) -> list[str]:
    problems: list[str] = []
    old_props, new_props = old.get("properties", {}), new.get("properties", {})
    old_required, new_required = set(old.get("required", [])), set(new.get("required", []))

    for added in sorted(new_required - old_required):
        problems.append(f"{name}: '{added}' became required (old producers do not send it)")
    for removed in sorted(set(old_props) - set(new_props)):
        problems.append(f"{name}: property '{removed}' was removed")
    for field in sorted(set(old_props) & set(new_props)):
        old_types, new_types = types_of(old_props[field]), types_of(new_props[field])
        if old_types != {"any"} and not old_types <= new_types:
            problems.append(f"{name}: '{field}' type narrowed from {sorted(old_types)} to {sorted(new_types)}")
        old_enum, new_enum = old_props[field].get("enum"), new_props[field].get("enum")
        if old_enum and new_enum is not None and not set(old_enum) <= set(new_enum):
            problems.append(f"{name}: '{field}' enum lost values {sorted(set(old_enum) - set(new_enum))}")
    if old.get("additionalProperties", True) is not False and new.get("additionalProperties", True) is False:
        # Consumers do not validate at runtime, so this only tightens the contract tests; flag it, do not fail.
        print(f"  WARNING: {name}: additionalProperties is now false; every producer's contract test must pass", file=sys.stderr)
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base", default="origin/main", help="git ref to compare against (default: origin/main)")
    args = parser.parse_args()

    schemas = sorted(SCHEMA_DIR.glob("*.schema.json"))
    if not schemas:
        print(f"no schemas found under {SCHEMA_DIR}", file=sys.stderr)
        return 2

    failures: list[str] = []
    for path in schemas:
        new = json.loads(path.read_text(encoding="utf-8"))
        old = load_base(args.base, path)
        if old is None:
            print(f"{path.name}: new schema (not on {args.base}), skipping compatibility check")
            continue
        found = compare(path.name, old, new)
        failures.extend(found)
        print(f"{path.name}: {'OK' if not found else str(len(found)) + ' incompatible change(s)'}")

    for failure in failures:
        print(f"  BREAKING: {failure}", file=sys.stderr)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
