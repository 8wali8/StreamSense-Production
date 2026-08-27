# Production Hardening Plan

This plan works the priority list from the engineering-standards research (2 September 2026) from the top down. Every change lands on its own branch, is verified on that branch, and is merged to `main` only after a full review cycle.

## Ground rules

- **One concern per branch.** Branch names are `hardening/NN-slug`, numbered in the order they should be reviewed. A branch never mixes a behavior change with a cleanup.
- **Base branch is `main` unless the table says otherwise.** Where two branches must edit the same files (Compose, Kubernetes manifests, config-repo), the later branch is stacked on the earlier one and the PR description says so. PRs are opened against `main` in table order, each after the previous one has merged, so every PR shows only its own commits. The Base column records the branch each one was actually built on.
- **Nothing is merged by the author.** Each branch is pushed to `origin` with a PR-ready description in `docs/planning/branches/NN-slug.md` (what changed, why, how it was verified, what to check by hand). Review and merge happen on GitHub.
- **Every branch carries its own verification record.** The description lists the exact commands that were run and their results. If something could not be verified locally, the description says so and names the command the reviewer should run.
- **Config that Kubernetes uses is mirrored in the same commit.** Any edit to `config-server/config-repo/*.yml` is mirrored in `k8s/config/config-server-config-repo.yaml` until branch 04 replaces the hand copy with a generator.
- **Local verification runs in containers.** There is no local Maven, so Java builds and tests run in `maven:3.9-eclipse-temurin-21`. Python tests run in `python:3.11-slim` to match CI. Frontend runs on local Node. Manifests are rendered with `kubectl kustomize k8s`.
- **The running Compose stack on the developer machine is never stopped by branch work.** Full-stack checks use `docker compose config` and targeted single-service runs; the reviewer runs the replay smoke on the branch as the final gate.

## Verification ladder

Every branch passes the rungs that apply to it, in this order.

| Rung | Command | Applies when |
|---|---|---|
| Render | `make secrets` (from branch 01 on: copies the committed `*.example` secret files into the git-ignored locations; CI does the same copy before rendering), then `docker compose config -q` and `kubectl kustomize k8s > /dev/null` | Compose, Kubernetes, or config-repo changed |
| Java unit and integration | `docker run --rm -v "$PWD:/src" -w /src/<service> maven:3.9-eclipse-temurin-21 mvn -B -ntp -q clean test` | Any Java service changed |
| Python | `docker run --rm -v "$PWD/<service>:/app" -w /app python:3.11-slim sh -c "pip install -q -r requirements.txt ruff==0.16.3 && ruff check src/main/python src/test/python && PYTHONPATH=src/main/python pytest -q src/test/python"` (or `uv run ruff check src/main/python src/test/python && uv run pytest` once branch 02c lands). The Ruff step matches the CI lint gate, so a branch cannot record a passing Python rung and still fail CI on lint | Any Python service changed |
| Frontend | `npm ci && npm run lint && npm run test && npm run build` in `frontend/` | Frontend changed |
| Grep gates | Branch-specific searches that must return nothing (for example, no literal passwords, no `:latest`) | Named per branch |
| Stack smoke | `tools/start-stack.ps1 -TwitchEnv -Channels redbull-testing` then `make replay-smoke`. Do not pass `-SkipPackage` here: the Java rung runs `clean test`, which leaves no jar for the Java Dockerfiles to copy, so the script must package first | Reviewer runs this on every branch that touches runtime wiring |

## Branch sequence

| # | Branch | Priority | Scope | Base | Risk | Reviewer focus |
|---|---|---|---|---|---|---|
| 00 | `hardening/00-plan` | Plan | This document | main | none | Agree the order |
| 01 | `hardening/01-secrets` | 1 | Compose `secrets:` with `*_FILE` variables for Postgres, MinIO, Grafana; Spring services read secrets through `configtree:/run/secrets/`; Python services accept `*_FILE` env; config-repo passwords become placeholders; Kubernetes `secretGenerator` from git-ignored env files with committed `.example` files; every manifest uses `secretKeyRef` | main | medium: touches startup of every stateful service | Stack boots from a fresh clone with only the example secrets copied into place |
| 02a | `hardening/02a-ci-pinning` | 2 | Pin every GitHub Action to a full SHA with a version comment; add top-level `permissions: contents: read`; fix the double PR run and enable `cancel-in-progress` for PR refs | main | low | CI still green on the branch |
| 02b | `hardening/02b-image-pinning` | 2 | Replace every `:latest` in Compose and Kubernetes with a versioned tag plus digest; pin Dockerfile base images | 01 | low | Images pull and healthchecks pass |
| 02c | `hardening/02c-python-packaging` | 2 | `pyproject.toml` plus `uv.lock` for ml-engine and video-capture-service; dev dependency group; Dockerfiles and CI install with `uv sync --locked`; pin kafka-python to a major | 02b | medium: changes how images are built | Images build; both test suites pass in CI |
| 03 | `hardening/03-client-timeouts` | 3 | Remove localhost fallbacks; `@Validated` required base URLs; connect and read timeouts on `RestClient`, `WebClient`, and gateway routes; `TimeLimiter` on gateway fan-out calls; config-client `fail-fast` and retry | 01 | medium: services now refuse to boot with missing URLs | Every service starts under Compose and Kubernetes with the shipped config |
| 04 | `hardening/04-k8s-hardening` | 4 | Resource requests and limits; pod and container `securityContext`; namespace PSS labels; liveness and readiness split with Spring `probes.enabled`; startupProbe for ml-engine; PVCs for Postgres, MinIO, and model caches; `configMapGenerator` replaces the hand-copied config-repo ConfigMap | 03 | high: every workload manifest changes | `kubectl kustomize` renders; kind cluster comes up; probes go ready |
| 05 | `hardening/05-kafka-kraft` | 5 | Single-node KRaft in Compose and a Kafka StatefulSet with PVC in Kubernetes; remove ZooKeeper; update kafka-ui and the topics init; update the CI docker-smoke service list so it no longer starts a ZooKeeper service | 04 (merges 02a and 02c) | high: message broker replaced | Topics created; replay smoke passes; kafka-exporter still scrapes |
| 06a | `hardening/06a-ml-engine-lifecycle` | 6 | `create_app()` with lifespan; pydantic-settings; backend registry loaded once with a lock; SAM no longer rebuilt per request; transcribe off the event loop; `/ml/live`, `/ml/ready`, `/ml/info`; force-failure as a router dependency; typed exception handlers; Prometheus `/metrics` | 05 | medium | Inference endpoints behave identically; health now reflects model readiness |
| 06b | `hardening/06b-video-capture-lifecycle` | 6 | Lifespan instead of `on_event`; no import-time connections; one idempotent Kafka producer with delivery callbacks; typed error classification in the capture loop; `start_new_session` on subprocesses; readiness reflects thread liveness | 05 | medium | Frames and transcripts still flow in replay |
| 07 | `hardening/07-parent-pom` | 7 | Root `pom.xml` with modules, Spring Cloud and Resilience4j BOMs, enforcer, Spotless (check only, no reformat yet), jacoco, build-info and git-commit-id plugins; per-service POMs shrink to their own dependencies | 06b | medium: build graph changes, versions unchanged | `mvn -f <service>/pom.xml` still works; CI matrix unchanged |
| 08 | `hardening/08-boot-upgrade` | 7 | Boot 3.2.12 to 3.5.x and Spring Cloud 2025.0.x; graceful shutdown and structured logging turned on; layered Dockerfiles with non-root user | 07 | high: framework upgrade | All Java tests pass; replay smoke passes; logs are JSON |
| 09 | `hardening/09-problem-details` | 8 | `@RestControllerAdvice` returning `ProblemDetail` in the five REST services; `@GraphQlExceptionHandler` in the gateway; validation errors map to 400 | 08 | low | Bad input returns RFC 9457 JSON instead of a whitelabel page |
| 10 | `hardening/10-event-schemas` | 9 | One canonical schema directory; schema validation tests in every producer and consumer using networknt (Java) and jsonschema (Python); CI compatibility check; fix the chat event drift that breaks analytics session keys | main | medium: may surface real contract mismatches | Failing tests are real drift, not test bugs |
| 11a | `hardening/11a-frontend-codegen` | 10 | Export the gateway SDL; graphql-codegen client preset; delete hand-written event types; `codegen check` in CI | main | low | Types match the schema; no runtime change |
| 11b | `hardening/11b-frontend-api-layer` | 10 | `src/lib/api-client.ts`; feature `api/` modules; one polled-resource hook; Vite dev proxy; `src/config/env.ts` | 11a | low | `npm run dev` works against the Compose backend |
| 11c | `hardening/11c-frontend-split` | 10 | Split `App.tsx` into feature folders; `useLiveFeed` hook; subscriptions through the Apollo cache; error boundaries; delete unused components | 11b | medium: large diff | Console renders identically in replay |
| 11d | `hardening/11d-frontend-tests` | 10 | MSW handlers; real `ApolloProvider` in tests; `userEvent`; type-aware ESLint, jsx-a11y, Prettier; coverage thresholds | 11c | low | CI green; no `vi.mock` of Apollo in tests |
| 12a | `hardening/12a-nginx` | 11 | Unprivileged nginx on 8080; gzip; immutable asset caching; security headers; `/ml/` restricted to what the UI needs; layered frontend Dockerfile; Compose publishes `3000:8080` and the frontend healthcheck probes 8080 | main | low | Frontend serves; Twitch player still embeds |
| 12b | `hardening/12b-redis-rate-limit` | 11 | Gateway `RequestRateLimiter` with `RedisRateLimiter` and the existing key resolver logic; remove the in-memory limiter | main | medium | Existing rate-limit integration tests pass against Redis |
| 13 | `hardening/13-dead-code` | 12 | Remove `ConfigDebugRunner` beans, `hystrix.*` config, tracked `.pyc` files, unused frontend components, planning sprawl moved under `docs/planning/`, GraphiQL gated to a local profile | main | none | Nothing referenced the deleted code |
| 14 | `hardening/14-supply-chain` | 12 | Renovate config; pre-commit hooks; hadolint; Trivy image and IaC scan; SBOM; `LICENSE`, `SECURITY.md`, `CONTRIBUTING.md`, `CODEOWNERS`, PR template | main | low | Bot PRs are sane; CI time acceptable |

Branches 01 through 05 are the security and operability tier and should merge first. Branches 06 through 10 are the correctness tier. Branches 11 through 14 are structure and hygiene and can be reordered freely.

## What each branch delivers

Each branch is complete when all of the following exist:

1. Code and config changes, with any config-repo edit mirrored to the Kubernetes ConfigMap.
2. Tests for new behavior, in the same branch.
3. Updated docs where behavior or commands changed (`docs/howtorun.md`, `CLAUDE.md`, `AGENTS.md`, `README.md`).
4. `docs/planning/branches/NN-slug.md` containing the summary, verification log, manual checks for the reviewer, and any follow-ups deliberately left out.
5. Conventional Commit messages, one logical change per commit, no build artifacts.

## Known interactions

- Frontend branches 11a through 11d are scheduled late so that any in-flight frontend work (the `App.tsx`, `App.test.tsx`, `queries.ts`, and `subscriptions.ts` edits pending when this plan was written) lands on `main` first. If such work is still pending when 11a starts, 11a is stacked on top of it.
- Branch 08 (Boot upgrade) changes defaults that branches 03 and 04 configure explicitly. Explicit config is kept so behavior is the same on both sides of the upgrade.
- Branch 05 (KRaft) and branch 04 (PVCs) both touch `k8s/platform/kafka.yaml`; 05 is stacked on 04 for that reason.
- Branch 02c (Python packaging) changes how Python images are built; 06a and 06b are stacked on 05, which merged 02c, so their Dockerfile edits are made once.
- Branches 02b, 03, and 04 all edit the Kubernetes manifests and the config-repo mirror. 02b and 03 are stacked on 01, 04 on 03, and 05 merges 02a and 02c on top of 04, so from 05 onward every branch carries the full set of earlier edits.

## Out of scope for this plan

- Replacing the stub sponsor detector with a real vision model.
- A login flow for the frontend. The gateway JWT path stays optional and the frontend keeps reading a token it does not mint; branch 11b documents this.
- Moving tracing from Zipkin to OTLP. It is a recommended follow-up once branch 08 has landed, since the Boot upgrade changes the tracing starters.
