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
Schemas that do not exist on the base ref are new and pass. Every schema that exists on the base
ref must still exist in the tree, or be listed in ``RENAMED`` (old name -> new name), in which
case the renamed file is compared against the old one; a base schema that simply disappears fails.
Run from the repository root:

    python tools/schema/check_compat.py [--base origin/main]
"""

from __future__ import annotations

import argparse
import json
import pathlib
import subprocess
import sys

SCHEMA_DIR = pathlib.Path("docs/schemas")

# Declared renames: a schema that moved keeps its compatibility history under the new name.
# Entries can be removed once the old name is gone from every base ref anyone compares against.
RENAMED: dict[str, str] = {
    "chat-message-event.json": "chat-message-event.schema.json",
    "sentiment-analysis-event.json": "sentiment-analysis-event.schema.json",
    "ml-sentiment-request.json": "ml-sentiment-request.schema.json",
    "ml-sentiment-response.json": "ml-sentiment-response.schema.json",
}


def load_base(ref: str, path: pathlib.Path) -> dict | None:
    result = subprocess.run(
        ["git", "show", f"{ref}:{path.as_posix()}"], capture_output=True, text=True, encoding="utf-8"
    )
    if result.returncode != 0:
        return None
    return json.loads(result.stdout)


def base_schema_names(ref: str) -> set[str]:
    result = subprocess.run(
        ["git", "ls-tree", "--name-only", ref, f"{SCHEMA_DIR.as_posix()}/"],
        capture_output=True, text=True, encoding="utf-8",
    )
    if result.returncode != 0:
        print(f"cannot list {SCHEMA_DIR} on {ref}: {result.stderr.strip()}", file=sys.stderr)
        return set()
    return {pathlib.Path(line).name for line in result.stdout.split() if line.endswith(".json")}


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
    current_names = {path.name for path in schemas}
    renamed_from = {new_name: old_name for old_name, new_name in RENAMED.items()}

    # Every schema the base ref had must still be here, or be a declared rename: a contract that
    # disappears is the one change consumers can never adapt to.
    for old_name in sorted(base_schema_names(args.base)):
        if old_name == "README.md" or old_name in current_names:
            continue
        target = RENAMED.get(old_name)
        if target is None or target not in current_names:
            failures.append(f"{old_name}: schema exists on {args.base} but not in the tree (declare a rename in RENAMED)")

    for path in schemas:
        new = json.loads(path.read_text(encoding="utf-8"))
        old = load_base(args.base, path)
        label = path.name
        if old is None and path.name in renamed_from:
            old = load_base(args.base, SCHEMA_DIR / renamed_from[path.name])
            label = f"{path.name} (renamed from {renamed_from[path.name]})"
        if old is None:
            print(f"{path.name}: new schema (not on {args.base}), skipping compatibility check")
            continue
        found = compare(path.name, old, new)
        failures.extend(found)
        print(f"{label}: {'OK' if not found else str(len(found)) + ' incompatible change(s)'}")

    for failure in failures:
        print(f"  BREAKING: {failure}", file=sys.stderr)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
