# Contributing

StreamSense is a personal project run to production standards. Changes are welcome as pull requests against `main`; the conventions below are what CI enforces and what a review looks for.

## Before you start

- Read `CLAUDE.md`. It is the maintained description of the system, the commands, and the rules per language (it is written for coding agents, which makes it a precise checklist for people too).
- Run `make up` once so you know the stack starts on your machine, and `pre-commit install` so the fast checks run before every commit.

## Branches and commits

- One concern per branch, named `<type>/<short-slug>` (`feat/`, `fix/`, `refactor/`, `hardening/`, `docs/`).
- Conventional commit messages: `feat(scope): what changed and why`, body in full sentences. The scope is a service or area (`api-gateway`, `frontend`, `k8s`, `events`).
- Keep a change and its tests, schema, docs, and config in the same commit. A Kafka event field without its JSON Schema, a `pyproject.toml` change without `uv.lock`, or an operation change without regenerated GraphQL types will fail CI.

## Checks a pull request must pass

| Area | Command |
|---|---|
| Java (per service) | `mvn -B -ntp clean verify` (tests, JaCoCo, enforcer) |
| Python (per service) | `uv sync --locked && uv run ruff check src/main/python src/test/python && uv run pytest` |
| Frontend | `npm ci && npm run codegen:check && npm run lint && npm run format:check && npm run test:coverage && npm run build` |
| Schemas | `python tools/schema/check_compat.py --base origin/main` |
| Kubernetes | `kubectl kustomize .` (copy `k8s/secrets/streamsense.env.example` to `streamsense.env` first) |
| Containers | `hadolint <service>/Dockerfile`; Trivy scans run in CI |
| Compose smoke | `make smoke-e2e` against a running stack |

`pre-commit run --all-files` covers the formatting, YAML/JSON, Dockerfile, and workflow checks locally.

## Pull request expectations

- Fill in the template: what changed, why, how it was verified, and what a reviewer should check by hand.
- No secrets in any file. Compose reads `secrets/<NAME>`, Kubernetes builds `streamsense-secrets` from `k8s/secrets/streamsense.env`; both are git-ignored.
- New images are pinned `name:tag@sha256:…`; new GitHub Actions are pinned by commit SHA with the version in a comment.
- New containers declare resources, a non-root `securityContext`, and probes.
- New REST errors are RFC 9457 problem details; new Kafka events get a schema in `docs/schemas/` and a contract test.

## Dependency updates

Renovate opens the update PRs (`renovate.json`). Digest and patch bumps auto-merge when CI is green; Spring platform and major runtime-image bumps wait for a human.
