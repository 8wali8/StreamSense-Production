# hardening/02a-ci-pinning

Priority 2 (part a) from `docs/planning/production-hardening.md`: make the CI workflow immutable, least-privilege, and monorepo-aware. Only `.github/workflows/ci.yml` and docs change; no service code is touched.

## What changed

**Actions pinned by commit SHA.** Every `uses:` now references a full commit SHA with the version in a trailing comment, which is the only immutable way to reference an action. The pins are the newest patch release inside the major that CI already used, so behaviour is unchanged:

| Action | Before | After |
|---|---|---|
| actions/checkout | `@v4` | `@11d5960a…` (v4.4.0) |
| actions/setup-java | `@v4` | `@cf277c60…` (v4.9.1) |
| actions/setup-node | `@v4` | `@49933ea5…` (v4.4.0) |
| actions/setup-python | `@v5` | `@a26af69b…` (v5.6.0) |
| azure/setup-kubectl | `@v4` | `@776406bc…` (v4.0.1) |
| dorny/paths-filter | new | `@0e4a8c6e…` (v3.0.4) |

Newer majors exist for all of these (checkout v7, setup-java v6, setup-node v7, setup-python v7, setup-kubectl v5). Upgrading majors is a behaviour change and is left to the dependency-bot branch (14), which will also keep the SHAs fresh.

**Least-privilege token.** `permissions: contents: read` at workflow level. No job needs more today; any future job that publishes images or comments on PRs must request its scope at job level.

**One run per change.** Triggers are now `pull_request` plus `push` to `main`. Previously `push: branches: ["**"]` plus `pull_request` ran every PR twice. Concurrency is keyed by PR number (or ref on main) and cancels superseded PR runs; runs on `main` are never cancelled.

**Change detection.** A new `changes` job uses `dorny/paths-filter` to classify the diff into areas (each Java service, `ml-engine`, `video-capture-service`, `frontend`, `k8s`, `smoke`, plus `workflow` and `java-shared` for `config-server/config-repo/**`). A small planning step turns that into:

- `java_services`: a JSON list feeding the Java job's matrix, so only changed services build. A workflow or config-repo change selects all eight.
- `run_all`: true when the workflow or shared config changed; every job runs.

The Python, frontend, and kustomize jobs carry an `if:` on their area. `docker-smoke` runs when anything that goes into the Compose stack changed, and tolerates skipped upstream jobs (`!cancelled() && !contains(needs.*.result, 'failure')`) so a frontend-only PR does not trigger a full stack boot, while a Java or ML change still does.

Skipped jobs report as `skipped`, which GitHub branch protection treats as passing. That is different from workflow-level `paths:` filters, where a required check that never runs stays pending forever. Nothing in this branch uses workflow-level path filters for that reason.

## Deliberately left alone

- `cache: maven` on setup-java, the Maven Central retry loop, and the smoke job's steps are unchanged.
- No Trivy, SBOM, hadolint, or CodeQL yet; those arrive in branch 14 once images are published from CI.
- Job-level `timeout-minutes` were not added; the smoke job's own retry loop bounds its runtime and the rest are short.

## Verification

| Check | Command | Result |
|---|---|---|
| Workflow lint | `actionlint -no-color .github/workflows/ci.yml` (v1.7.12, shellcheck and pyflakes integrations disabled because they are not installed locally) | no findings |
| No unpinned actions | grep for `uses: .*@v` | none |
| SHA provenance | GitHub tags API, `/repos/<owner>/<repo>/tags`, newest `vMAJOR.x.y` within the used major | recorded in the table above |

GitHub Actions cannot run locally, so the first real proof is the PR for this branch: expect the `changes` job to classify the diff as `workflow`, which selects `run_all` and runs every job exactly once.

## Manual checks for the reviewer

1. Open the PR and confirm a single workflow run appears (not two).
2. In the `changes` job log, the `Plan jobs` step prints `run_all=true` and all eight services.
3. After merge, push a docs-only change on a branch and open a PR: only `changes` should run; the others show as skipped and the PR is mergeable.
4. If branch protection lists required checks by job name, keep the same names; they are unchanged.

## Follow-ups (not in this branch)

- Branch 14 adds Renovate with `helpers:pinGitHubActionDigests` so these SHAs are bumped automatically, plus image scanning and SBOM steps.
- When an image-publishing workflow is added, give only that job `packages: write`.
