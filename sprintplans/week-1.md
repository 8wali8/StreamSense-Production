# Week 1 Implementation Plan

## Goal

Establish the platform skeleton for the repository so the project has a trustworthy base to build on.

Week 1 is not about showing the final product. It is about making the repository structurally correct, locally runnable, and stable enough that later feature work does not sit on top of a messy foundation.

The target outcome for this week is:

- the monorepo structure matches the intended architecture
- Eureka and Config Server run locally
- Docker Compose can boot the platform baseline
- shared observability conventions exist
- CI runs meaningfully
- base docs and local workflow are credible

## Week 1 Success Criteria

Week 1 is complete only when all of the following are true:

- the repository structure is coherent and matches the intended service layout
- `eureka-server` starts successfully and serves the registry UI
- `config-server` starts successfully and serves per-service config from the in-repo config repository
- service configuration is centralized in one predictable config location
- Docker Compose can bring up at least the platform baseline:
  - `eureka-server`
  - `config-server`
  - `prometheus`
  - `grafana`
  - `zipkin`
- CI exists and runs relevant build and test steps for the codebase shape
- root workflow commands exist and are understandable
- core docs exist and match reality closely enough to onboard further work

## Week 1 Purpose

This week is about removing ambiguity.

At the end of Week 1, the repository should stop feeling like a loose collection of services and start feeling like a coherent platform.

The important result is not feature depth. The important result is that later weeks can add Kafka, GraphQL, persistence, and UI features without first having to untangle platform confusion.

## Current Starting Point

This plan assumes the current repo already contains much of the Week 1 surface area in some form, but Week 1 must still define the implementation standard clearly.

Existing or expected baseline areas include:

- service directories for the major components
- Docker Compose
- Config Server
- Eureka server
- monitoring folder
- frontend folder
- docs folder

What Week 1 must ensure is that all of those pieces are structurally correct, internally consistent, and not misleading.

## Week 1 Deliverables

By the end of the week, the repository should contain all of the following:

### 1. Coherent Monorepo Structure

- service folders match the intended architecture
- naming is consistent across docs, Maven metadata, config, and Compose
- placeholder directories exist where later weeks depend on them

### 2. Discovery And Central Config Baseline

- `eureka-server` is a valid Spring Boot Eureka registry
- `config-server` is a valid Spring Cloud Config Server running in native mode
- per-service config files exist in a single in-repo config repository

### 3. Shared Developer Workflow

- root command entry points exist for common actions
- local run workflow is documented
- repo standards files exist and are useful

### 4. Basic Observability Skeleton

- Prometheus config exists
- Grafana datasource and dashboard provisioning skeleton exists
- Zipkin is available in Docker Compose
- service-level metrics and tracing conventions are defined even if not all services emit rich telemetry yet

### 5. CI Baseline

- Java build and test path exists
- Python lint and test path exists
- frontend lint and test path exists or is clearly scaffolded into CI if the frontend is still minimal

### 6. Base Documentation

- architecture skeleton exists
- local runbook skeleton exists
- the docs describe the actual repo rather than an imagined target state

## Non-Negotiable Week 1 Principles

- do not leave the repo with conflicting service names across files
- do not leave Docker-only assumptions undocumented
- do not postpone CI until after major feature work starts
- do not let Config Server pathing stay ambiguous between local and Docker
- do not let observability become an afterthought; at least the baseline containers and conventions must exist now
- do not claim services are production-ready this week; this is a platform skeleton week

---

## Phase 1 - Normalize Repository Standards

This phase defines how the repo should behave before feature work deepens.

### Objectives

- make the root of the repository trustworthy
- remove stale naming, stale metadata, and workflow confusion

### Tasks

1. Ensure root standards files exist and are appropriate:
   - `.editorconfig`
   - `.gitignore`
   - `Makefile` or equivalent
2. Ensure the root command layer supports at least:
   - `build`
   - `test`
   - `up`
   - `down`
   - `logs`
3. Ensure the root command layer references the real services in the repository rather than stale or unrelated names.
4. Ensure the documented folder tree matches the actual repo tree.
5. Remove or fix stale service metadata that misrepresents actual service identity.
6. Ensure service names line up across:
   - folder names
   - `artifactId`
   - service config filenames
   - Compose service names
   - README text

### Output Of This Phase

- a contributor can inspect the root of the repo and understand the intended platform shape quickly

### Risks

- stale service names in root workflow files making later automation unreliable

---

## Phase 2 - Stand Up `eureka-server`

This phase establishes service discovery as a real platform component.

### Objectives

- make the registry service real and locally reachable

### Tasks

1. Ensure `eureka-server` is a valid Spring Boot application with `@EnableEurekaServer`.
2. Ensure its Maven metadata, package naming, and application entry point are coherent.
3. Configure it to run on port `8761`.
4. Disable self-registration and self-fetch behavior if needed for a standalone local registry.
5. Expose actuator health where useful.
6. Ensure Docker Compose can boot it independently.
7. Verify the Eureka dashboard loads in a browser.

### Required End State

- local and Docker access to Eureka are predictable
- other Spring services can target it as their discovery server

### Risks

- package naming and service metadata drift creating small but persistent confusion later

---

## Phase 3 - Stand Up `config-server`

This phase establishes centralized configuration.

### Objectives

- make Config Server a reliable source of per-service config in local development

### Tasks

1. Ensure `config-server` is a valid Spring Boot application with `@EnableConfigServer`.
2. Run Config Server in native mode initially.
3. Decide and standardize the config repository location:
   - `/config-repo/`
   - or `config-server/config-repo/`
4. Ensure the choice is stable and documented.
5. Create or normalize per-service config files for all platform services.
6. Ensure search locations work:
   - locally
   - inside Docker containers
7. Verify Config Server returns service config for at least:
   - `eureka-server`
   - `config-server`
   - one application service

### Configuration Standards To Set Now

- service config should not rely on machine-specific absolute paths
- local and Docker configuration should differ only where necessary and explicitly documented
- per-service YAML naming should match service identity exactly

### Output Of This Phase

- centralized config is real and not just documented aspiration

### Risks

- local success with broken Docker pathing
- confusing split between multiple config locations

---

## Phase 4 - Create Shared Observability Baseline

This phase does not require rich dashboards yet. It requires a baseline that later weeks can build on.

### Objectives

- define the minimum logging, metrics, and tracing conventions for the whole repo

### Tasks

1. Add or standardize a logging pattern that can include:
   - correlation id
   - trace id
   - span id
2. Decide how correlation ids will be introduced on HTTP requests.
3. Decide how that metadata should flow through Kafka later.
4. Add Micrometer baseline dependencies where needed for Spring services.
5. Add Zipkin tracing dependencies where needed for Spring services.
6. Ensure the monitoring folder contains:
   - Prometheus config
   - Grafana datasource provisioning
   - Grafana dashboard provisioning skeleton
7. Ensure Docker Compose brings up:
   - Prometheus
   - Grafana
   - Zipkin
8. Ensure Zipkin is reachable on the expected port.

### Week 1 Scope Boundary

This week does not require finished dashboards. It does require the observability foundation to exist and to be shaped intentionally.

### Output Of This Phase

- the repository has an observability baseline rather than a future wish list

### Risks

- every service inventing its own observability conventions later

---

## Phase 5 - Build Docker Compose Platform Baseline

This phase makes the platform runnable as a baseline system.

### Objectives

- make Docker Compose the canonical local platform bootstrap path

### Tasks

1. Ensure `docker-compose.yml` includes the baseline platform services:
   - `eureka-server`
   - `config-server`
   - `prometheus`
   - `grafana`
   - `zipkin`
2. Ensure service ports are clear and documented.
3. Add healthchecks where they reduce obvious startup races.
4. Ensure `config-server` can see the config repository via a stable mount.
5. Ensure `eureka-server` and `config-server` can start without hidden manual prerequisites.
6. Keep this phase focused on platform skeleton correctness, not full application orchestration.

### Output Of This Phase

- one Compose file can bring up the baseline platform locally

### Risks

- Dockerfiles that require prebuilt artifacts may complicate the experience later; this should at least be documented if not fully fixed this week

---

## Phase 6 - Establish CI Baseline

This phase makes the repo enforceable rather than aspirational.

### Objectives

- ensure every push or pull request exercises meaningful checks

### Tasks

1. Create or normalize `.github/workflows/ci.yml`.
2. Add Java build and test execution for the Spring services.
3. Add Python setup and test execution for `ml-engine`.
4. Add frontend setup, lint, and test execution.
5. Add lightweight validation for Docker Compose configuration if feasible.
6. Keep the CI scope honest:
   - do not pretend there is full integration coverage yet
   - do ensure the repo compiles, tests, and lints along the boundaries it already has

### CI Standard For Week 1

- main branch should be green
- failures should point to real code or config issues, not pipeline noise

### Output Of This Phase

- the repo has a real quality gate before feature work expands

### Risks

- frontend being omitted from CI and silently diverging from the rest of the repo

---

## Phase 7 - Create Week 1 Documentation Baseline

This phase makes the repo explainable.

### Objectives

- ensure docs describe what actually exists at the end of Week 1

### Tasks

1. Create or normalize `docs/architecture.md` skeleton.
2. Create or normalize a local runbook skeleton such as `docs/runbook-local.md` or equivalent.
3. Ensure README describes the platform honestly.
4. Ensure docs identify:
   - service roles
   - local ports
   - baseline startup flow
   - config-server behavior
   - monitoring endpoints
5. Remove or rewrite statements that imply later-week functionality already exists.

### Output Of This Phase

- a new contributor can understand what the repository is trying to be before later implementation weeks begin

### Risks

- architecture docs drifting from the actual repo structure before implementation even begins

---

## Week 1 Testing Strategy

Week 1 tests should focus on platform confidence, not deep feature coverage.

### Required Tests

1. Add a boot or context-loads style test per Spring service where feasible.
2. Ensure `ml-engine` has at least a minimal health or application-level test path.
3. Validate Compose configuration in CI if practical.
4. Confirm CI can execute the intended baseline checks on a clean environment.

### Exit Condition For Testing

The repository should not require manual intuition to know whether the base platform still boots and compiles.

## Week 1 Observability Requirements

At the end of Week 1:

- Prometheus config exists
- Grafana datasource provisioning exists
- Zipkin is reachable
- Spring services that already expose actuator can surface health endpoints
- logging conventions are at least partially standardized

This is not the week for fully polished dashboards. It is the week for making sure observability can exist coherently in later phases.

## Detailed Implementation Sequence

Execute Week 1 in this order.

1. Normalize root repo standards and naming.
2. Ensure `eureka-server` is coherent and runnable.
3. Ensure `config-server` is coherent and runnable.
4. Standardize config repository structure and service YAML naming.
5. Add or normalize monitoring baseline files.
6. Ensure Compose brings up the platform baseline.
7. Add or fix CI workflows.
8. Add or fix base docs.
9. Verify the baseline manually.

Do not start deeper application feature work until this sequence is stable.

## Manual Verification Checklist

At the end of Week 1, manually verify all of the following:

1. Start the baseline platform with Docker Compose.
2. Confirm Eureka UI is reachable.
3. Confirm Config Server returns config for at least one service.
4. Confirm Prometheus is reachable.
5. Confirm Grafana is reachable.
6. Confirm Zipkin is reachable.
7. Confirm the documented ports match what the containers actually expose.
8. Confirm CI passes on the current branch.
9. Confirm the README and runbook reflect the repo accurately.

## Definition Of Done

Week 1 is done when all of the following are simultaneously true:

- repo structure and naming are coherent
- Eureka runs
- Config Server runs and serves centralized config
- monitoring baseline containers run
- root workflow commands are present and understandable
- CI exists and is green
- docs accurately describe the baseline platform

## Things That Must Not Be Deferred Out Of Week 1

- service naming cleanup
- Config Server native-mode stability
- baseline Compose support for the platform services
- CI baseline
- baseline docs and local run instructions

If these are deferred, later weeks will inherit avoidable platform friction.

## Risks And Mitigations

### Risk: Config path works locally but fails in Docker

Mitigation:

- test the actual mounted config path in Docker, not just local file-based execution

### Risk: root workflow tooling references stale services

Mitigation:

- align root automation with the actual repo directories now

### Risk: monitoring is added in Compose but not wired coherently

Mitigation:

- define the baseline conventions this week even if the dashboards are still minimal

### Risk: CI skips the frontend and creates false confidence

Mitigation:

- include frontend lint and test paths in the baseline CI plan

### Risk: docs describe the target future state rather than the actual current platform

Mitigation:

- force docs to distinguish clearly between what exists now and what later weeks will build

## Stretch Goals Only If Core Week 1 Work Finishes Early

- context-load tests for every Spring service
- improved healthchecks in Compose
- minor Dockerfile cleanup
- stronger local runbook detail

Do not allow these to replace the Week 1 core deliverables.

## Week 1 Summary

Week 1 should leave the repo with a dependable platform skeleton.

The key outcomes are:

- coherent monorepo structure
- working Eureka and Config Server baseline
- stable centralized configuration
- baseline Compose platform
- initial observability conventions
- CI baseline
- docs that match reality

If those outcomes are achieved, the repository is ready for the first functional vertical slice work in Week 2.
