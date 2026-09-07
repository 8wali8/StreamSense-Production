## What changed

<!-- One paragraph. Link the issue or planning note if there is one. -->

## Why

<!-- The problem this solves or the convention it enforces. -->

## How it was verified

<!-- Commands run and their outcome. CI covers the automatic checks; list anything manual (Compose run, kind apply, browser check). -->

## Reviewer checklist

- [ ] Tests, schema, docs, and config changed together with the code
- [ ] No secrets, no `:latest` images, no unpinned actions
- [ ] New containers have resources, a non-root security context, and probes
- [ ] Kafka event changes are backward compatible (`tools/schema/check_compat.py`)
- [ ] `CLAUDE.md` updated if a convention or command changed
