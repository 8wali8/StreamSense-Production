# Production hardening: follow-up plan

The fourteen branches in `production-hardening.md` (on `hardening/00-plan`) each recorded what they deliberately left alone. This plan collects those follow-ups, drops the ones that need infrastructure the repository does not have yet, and sequences the rest as branches `hardening/21-…` onward, stacked on `hardening/14-supply-chain` the same way the first series was stacked: one concern per branch, a note under `docs/planning/branches/`, verification recorded, nothing merged by the agent.

## Verification ladder (unchanged)

1. Static: `actionlint`, `kubeconform -strict`, `kube-linter`, Trivy with the CI settings, `docker compose config -q`, `kubectl kustomize .`.
2. Unit and integration: the Maven reactor (`mvn -B -ntp -Dmaven.gitcommitid.skip=true clean verify`, in `maven:3.9-eclipse-temurin-21` with `~/.m2` mounted), `uv sync --locked && ruff check && pytest` per Python service, `npm run lint && format:check && codegen:check && test:coverage && build` for the frontend.
3. Container: `docker build` of every touched image, then `docker run` with the production constraints under test (for example `--read-only --tmpfs /tmp`) and a probe of the health endpoint.
4. Cluster: kind is not available in the agent's environment. Branches whose runtime effect can only be proven on a cluster say so in their note and list the exact `kubectl` checks for the reviewer.

## Branch sequence

| # | Branch | Source follow-up | Scope | Risk | Proof |
|---|---|---|---|---|---|
| 21 | `hardening/21-spotless-and-verify` | 07, 13 | `mvn spotless:apply` as one formatting-only commit, `spotless:check` bound to `verify`, CI runs `verify` instead of `test`, `@MockBean` → `@MockitoBean`, gateway `spring.cloud.gateway.server.webflux.*` property namespace | low: formatting plus deprecation cleanups | full reactor green; CI diff is one word |
| 22 | `hardening/22-session-fields-persisted` | 10 | Flyway migration adding the four nullable session columns to the sentiment record, entity mapping, `source`/`streamSessionId` exposed on the GraphQL `ChatMessageEvent` and `SentimentAnalysisEvent`, frontend types regenerated | low | sentiment-service and gateway verify; `codegen:check`; contract tests |
| 23 | `hardening/23-frontend-error-surfacing` | 09, 11b, 11c, 11d | panels render `ApiError.problem.detail` and GraphQL `extensions.code` (for example "sentiment-service unavailable" instead of "Failed to load"), panels move under `features/`, `useConsoleFeeds` gets a subscription-driven test | low | frontend lint, coverage floors, build |
| 24 | `hardening/24-read-only-rootfs` | 04, 14 | `readOnlyRootFilesystem: true` with `emptyDir` scratch mounts and pod-level `securityContext` on every Deployment/StatefulSet (apps, platform, monitoring); busybox init containers kept and made read-only too (dropping them would trade an ordered start for crash-loop restarts); Trivy misconfiguration gate raised to HIGH; kube-linter clean | medium: a missed writable path crashes a pod | every image run with `docker run --read-only --tmpfs` and probed; manifests validated; cluster checks listed for the reviewer |
| 25 | `hardening/25-network-policy` | 04 | default-deny `NetworkPolicy` in the namespace with per-service ingress/egress allows derived from the dependency graph, DNS allowed, `PodDisruptionBudget` for the frontend (two replicas; the gateway stays at one because its Kafka consumers share a group id, see the branch note) | medium: runtime effect only provable on a cluster | manifests validated; `tools/k8s/check_network_policies.py` re-derives every edge from the manifests, config-repo, Prometheus targets, nginx proxies, and Ingress backends in CI; cross-checked against Compose `depends_on` |
| 26 | `hardening/26-archunit-and-coverage` | 07 | ArchUnit rules per Java service (layering, no field injection, no `java.util.logging`), JaCoCo minimum from the measured baseline, ruff `select` list plus `ruff format --check` and mypy (clean, not ratcheted: the 11 findings were fixed) for the Python services | low | reactor green with the new tests; per-module coverage and floors recorded |
| 27 | `hardening/27-python-layout` | 02c, 06a, 06b | `src/<package>` installed layout for ml-engine and video-capture-service, `PYTHONPATH` removed from the images | medium: touches every import | tests, images build and answer `/live` |
| 28 | `hardening/28-ml-stack-upgrade` | 14 | torch ≥ 2.6 and transformers ≥ 4.48 (then sentence-transformers, torchvision to match), `.trivyignore` entries removed, a comparison script that runs the sentiment and relevance backends on the replay fixtures before and after | high: inference behaviour | tests; image builds; comparison report checked in under the branch note; Trivy gate green with no ML suppressions |

## Dropped or deferred (and why)

- External Secrets, image registry publishing, per-image SBOMs, cosign, branch protection, secret-scanning push protection: need accounts or infrastructure the repository does not have. Listed in `14-supply-chain.md`.
- OpenTelemetry/OTLP tracing: needs a collector in the stack; a product decision, not hardening.
- `kafbat/kafka-ui`, multi-broker KRaft, streamlink's Python API, CloudEvents envelopes, runtime schema validation in consumers, TanStack Query: product or behaviour changes outside "run what exists in production".
- Dropping `'unsafe-inline'` from `style-src`: the confidence bars and detection boxes are sized from data at render time, which a strict `style-src` forbids regardless of how the value gets into the attribute; the honest fix is a canvas or SVG rendering of those elements, a UI change, not a hardening one.
- Sliding-window rate limiting, Redis in readiness, typed 404/422 domain exceptions: no measured need yet.

## Conventions carried over

Branches `hardening/<NN>-<slug>` in worktrees under `StreamSense-worktrees/`; never the main checkout. Each branch: one note in `docs/planning/branches/<NN>-<slug>.md` with what changed, what was left alone, the verification table with real results, and manual checks for the reviewer. Secrets never committed. Conventional commits with the session trailer. CLAUDE.md updated whenever a convention or command changes.

## Progress (2026-09-04)

All eight branches are pushed and stacked in order, each with its note under `branches/` and the verification it ran; none is merged. Review from 21 upward and forward-merge as before, so each later branch picks up the review fixes of the one below it.

| Branch | Head | Note |
|---|---|---|
| `hardening/20-followups-plan` | 8776639 | this plan |
| `hardening/21-spotless-and-verify` | 7d89dfc | `21-spotless-and-verify.md` |
| `hardening/22-session-fields-persisted` | f47cb2e | `22-session-fields-persisted.md` |
| `hardening/23-frontend-error-surfacing` | 1b40601 | `23-frontend-error-surfacing.md` |
| `hardening/24-read-only-rootfs` | 15c27e5 | `24-read-only-rootfs.md` |
| `hardening/25-network-policy` | b7a616b | `25-network-policy.md` |
| `hardening/26-archunit-and-coverage` | 51c67f6 | `26-archunit-and-coverage.md` |
| `hardening/27-python-layout` | d547da7 | `27-python-layout.md` |
| `hardening/28-ml-stack-upgrade` | (this branch) | `28-ml-stack-upgrade.md`, `28-ml-stack-comparison.md` |

Deviations from the table above, each explained in its note: 24 keeps the busybox init containers; 25 gives the PodDisruptionBudget to the frontend only (the gateway's shared Kafka consumer group blocks a second replica); 26 fixed mypy's findings instead of ratcheting; 28 went to transformers 5.16.1 because only 5.10+ clears every CVE, and it also stopped the Dockerfiles from `chown -R`ing the venv.

### Forward-merge of the review fixes (6 September)

The 01 to 14 chain gained 24 review-fix commits after these branches were cut; each branch now carries them (20 ← 14, then 21 ← 20 and so on). What the merges needed and what was re-run:

- 21: your gateway auth, rate-limit, and error-advice fixes won over this branch's reformatting of the same files; the gateway config keeps the new `ml-engine-segment` route under the `server.webflux` namespace; the rate-limit integration test registers its route there too. Full reactor green (209 tests, Spotless gate clean).
- 23: the three moved panels keep their `features/` paths and take the typed query variables from the fixes. Lint, format, codegen, build, and 72 tests with the coverage floors green.
- 24: manifests re-validated (56 resources, kubeconform, kube-linter, Trivy misconfiguration gate).
- 25: the console's `POST /ml/segment` now goes through the gateway, so the policies changed: gateway egress to ml-engine 8000 and ml-engine ingress from the gateway added, the frontend's direct ml-engine allow removed. The checker had flagged both missing edges; 64 edges covered again.
- 26: full reactor green with every `ArchitectureTest` passing on the fixed code and every coverage floor holding. Python: ruff, format, mypy, 75 and 48 tests.
- 27 and 28: one review-fix test imported `app.settings` inside its body and needed the renamed package; two docstrings updated likewise. Python suites green on both; the Trivy vulnerability gate on 28 stays at exit 0.
