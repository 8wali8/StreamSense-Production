# hardening/14-supply-chain

Priority 12 (last branch of the plan) from `docs/planning/production-hardening.md`: the repository keeps itself current and scanned, and tells a contributor how to work in it. Stacked on `hardening/13-dead-code`.

## What was wrong

Every image, action, and library is pinned (branches 02a, 02b, 07), which means nothing moves unless someone moves it: there was no bot to propose updates, no scanner to say which pins carry a known vulnerability, no Dockerfile lint, no SBOM, and no `LICENSE`, `SECURITY.md`, `CONTRIBUTING.md`, code owners, or PR template. The first scan also showed that the two Python images ran as root (Kubernetes overrode it with `runAsUser`, Compose did not) and that four pinned libraries had fixable HIGH/CRITICAL CVEs.

## What changed

- **Renovate** (`renovate.json`, validated with `renovate-config-validator`): `config:recommended`, weekly before Monday 06:00, semantic commits, digests pinned everywhere (`:pinDigests`, `helpers:pinGitHubActionDigests`), monthly lockfile maintenance. Spring Boot and Spring Cloud are grouped into one PR and never auto-merged; Docker digest bumps and Action patch/digest bumps auto-merge on green CI; frontend and Python dev dependencies are grouped; major runtime-image bumps (Postgres, Kafka, Redis) wait behind the dependency dashboard; vulnerability alerts get the `security` label. Renovate runs once the owner installs the Mend app on the repository.
- **pre-commit** (`.pre-commit-config.yaml`, validated with `pre-commit validate-config`): large-file, merge-marker, YAML/JSON, private-key, EOF and trailing-whitespace hooks; ruff (check + format) for the two Python services; Prettier for `frontend/`; actionlint; hadolint. Same tools and versions as CI, so a green `pre-commit run --all-files` predicts a green PR.
- **hadolint** (`.hadolint.yaml`, `dockerfile-lint` job when a Dockerfile changes): warning threshold; `DL3018`/`DL3008` (pin apk/apt package versions) ignored because every base image is digest-pinned. Its one finding, a shell-form `HEALTHCHECK` in the console image (`DL3025`), is fixed.
- **Trivy** (`security-scan` job on every PR and push; `limit-severities-for-sarif: true`, because SARIF output otherwise carries every severity and the MEDIUM findings tripped the gate on the first CI run even though the table-format run below was clean): a vulnerability and secret scan of `pom.xml`, `uv.lock`, and `package-lock.json` that fails on HIGH/CRITICAL findings with a fix and uploads SARIF to code scanning, plus a misconfiguration scan of Dockerfiles, Compose, and Kubernetes that fails on CRITICAL and reports HIGH. Suppressions live in `.trivyignore` with a reason.
- **What the first scan found, and what was done about it**:
  - `org.postgresql:postgresql` 42.7.11 (CVE-2026-54291): the parent POM now pins `postgresql.version` 42.7.12 over Boot's managed version; the three services that use the driver were re-verified.
  - `pillow` 11.3.0 (13 CVEs) and `starlette` 0.52.1 (2 CVEs) in ml-engine: relocked to Pillow 12.3.0 (constraint raised to `>=12.3,<13`) and Starlette 1.6.0 with FastAPI 0.141.1; ml-engine lint and tests pass on the new lock.
  - `torch` 2.2.2+cpu (CVE-2025-32434, CRITICAL) and `transformers` 4.40.2 (six CVEs): suppressed in `.trivyignore` with the reason recorded there. Moving to torch 2.6 and transformers 4.48+ changes inference behaviour and needs its own branch that compares model outputs before and after; `torch.load` is only ever given the image's own pinned weights. This is the first follow-up below.
  - `DS-0002` (no `USER`) in `ml-engine`, `video-capture-service`, and `frontend`: the Python images now create uid 10001, own `/app`, `/models` (ml-engine) and a `/tmp/app` home, and switch to it, so Compose no longer runs them as root and a fresh named volume inherits the right ownership; the console image states the base image's `USER 101` so scanners can see it.
  - `KSV-0014` (`readOnlyRootFilesystem`) on 52 containers and `KSV-0118` (pod-level security context) on 15 deployments: reported, not gated. Making every root filesystem read-only needs scratch mounts per workload and a cluster run to prove it, which is a branch of its own (the second follow-up).
- **SBOM** (`sbom` job on pushes to `main`): a CycloneDX JSON SBOM of the repository from `anchore/sbom-action`, kept as a workflow artifact.
- **Community files**: `LICENSE` (MIT, copyright Ujjawal Prasad; the owner should confirm this is the licence they want before the repository goes public), `SECURITY.md`, `CONTRIBUTING.md`, `.github/CODEOWNERS`, a PR template with a reviewer checklist, and bug/feature issue templates. `README.md` links them; `CLAUDE.md` describes the new gates.

## Deliberately left alone

- No image scanning of the built service images in CI yet: `docker-smoke` builds them but is already the slowest job. Add `trivy image` there once images are pushed to a registry.
- No per-image SBOMs or cosign signatures for the same reason.
- Dependabot is not enabled alongside Renovate; one updater handles Maven, uv, npm, Docker digests, and Actions.
- hadolint's package-pin rules stay ignored on purpose (see above).

## Verification

| Check | Command | Result |
|---|---|---|
| Services using the PostgreSQL driver | `mvn -B -ntp -Dmaven.gitcommitid.skip=true -pl analytics-service,sentiment-service,video-service -am clean verify` in `maven:3.9-eclipse-temurin-21` | BUILD SUCCESS: sentiment-service 27, video-service 20, analytics-service 16 tests, 0 failures |
| ml-engine on the new lock | `uv sync --locked && ruff check … && pytest` in `python:3.11.16-slim` | ruff clean, 70 passed, 1 skipped (Pillow 12.3.0, Starlette 1.6.0, FastAPI 0.141.1) |
| Images build and run unprivileged | `docker build` of `frontend`, `video-capture-service`, `ml-engine`, then `id -u` inside | all three build; `id -u` is 101 (frontend), 10001 (video-capture-service), 10001 (ml-engine), with `/tmp/app` owned by the app user |
| hadolint on all eleven Dockerfiles | `hadolint/hadolint:v2.15.1` with `.hadolint.yaml` | clean (the `DL3025` finding fixed) |
| Trivy vulnerability and secret gate (CI settings) | `trivy fs --scanners vuln,secret --severity HIGH,CRITICAL --ignore-unfixed --exit-code 1` with `.trivyignore` | exit 0 (the 22 ml-engine findings resolve to the 7 suppressed ML-stack CVEs plus fixed Pillow/Starlette; the PostgreSQL driver finding is gone) |
| Trivy misconfiguration gate (CI settings) | `trivy fs --scanners misconfig --severity CRITICAL --exit-code 1` | exit 0; `DS-0002` no longer reported for any Dockerfile, the `KSV-0014`/`KSV-0118` HIGH findings remain reported |
| Workflow syntax | `actionlint .github/workflows/ci.yml` | OK |
| Renovate config | `renovate-config-validator` (`renovate/renovate` image) | valid |
| pre-commit config | `pre-commit validate-config` | valid |

## Manual checks for the reviewer

1. Install the Renovate GitHub app on the repository and open the dependency dashboard issue it creates; the first PRs should be digest bumps only.
2. Open a PR: `security-scan`, `dockerfile-lint` (when a Dockerfile changed), and the existing jobs run; the PR template appears with the checklist; the code owner is requested.
3. `make up`: `docker compose exec ml-engine id -u` and `docker compose exec video-capture-service id -u` print `10001`; the ML models still download into `/models` on a fresh volume (`make nuke` first) and transcripts/frames still flow.
4. `pip install pre-commit && pre-commit install && pre-commit run --all-files` passes on a clean checkout.
5. Decide on the licence: keep MIT or replace `LICENSE` before the repository is made public.

## Follow-ups

- **ML stack upgrade**: torch 2.6+ and transformers 4.48+ (then remove the seven `.trivyignore` entries), verified by comparing sentiment, relevance, sponsor, and segmentation outputs on the replay fixtures before and after.
- **Read-only root filesystems**: `readOnlyRootFilesystem: true` with `emptyDir` scratch mounts and pod-level `securityContext` on every deployment (apps, platform, monitoring), proven on kind; then raise the Trivy misconfiguration gate to HIGH and clear the matching kube-linter findings.
- Push images to a registry, then add per-image SBOMs, `trivy image`, and cosign signatures.
- Turn on GitHub's secret scanning push protection and branch protection requiring the CI jobs (repository settings, not files).
