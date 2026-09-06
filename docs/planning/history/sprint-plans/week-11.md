# Sprint 11 Implementation Plan

## Goal

Build a credible testing, CI, and performance story for the repository.

Sprint 11 should harden confidence in StreamSense with:

- stronger automated verification across the main platform flows
- repeatable load-generation tooling under `tools/`
- an honest performance report grounded in measured results
- a performance-focused observability view that makes load behavior easy to explain

The target runtime shape for this sprint is:

`code change -> fast service tests -> higher-fidelity integration coverage -> stack smoke verification -> repeatable load run -> dashboards + written performance report`

Sprint 11 is not about adding new product features. It is about proving the existing platform behaves predictably, measurably, and reproducibly.

## Sprint 11 Success Criteria

Sprint 11 is complete only when all of the following are true:

- CI credibly covers Java, Python, frontend, Kubernetes validation, and stack smoke behavior.
- load-generation tooling exists under `tools/` and can be run without reverse-engineering ad hoc shell history.
- load runs can target real end-to-end platform paths rather than only isolated unit-level code.
- measured results are written to `docs/performance-report.md` with clear environment notes.
- the report includes throughput, latency, and failure-mode observations rather than aspirational claims.
- contract protection exists for major event schemas and GraphQL schema stability where that protection is still missing.
- a performance-focused Grafana dashboard is provisioned automatically.
- the sprint ends with a reproducible verification path that another contributor can run.

## Current Starting Point

This plan assumes the repo state after Sprint 10 is:

- `.github/workflows/ci.yml` already runs Java service tests, ML lint/tests, frontend lint/tests/build, Kubernetes render validation, and a Docker smoke workflow.
- Java services already have substantial self-contained integration coverage built on Embedded Kafka, H2, and MockWebServer.
- frontend already has Vitest coverage for the major current UI surfaces.
- Kubernetes manifests, Prometheus scraping, Grafana provisioning, and Zipkin tracing already exist and were extended in Sprints 9 and 10.
- service metrics already exist for key areas such as chat ingest, ML latency, sponsor latency, cache hits/misses, recommendation latency, fallback counts, and Kafka lag.
- there is currently no `tools/` directory for repeatable load generation.
- there is currently no `docs/performance-report.md`.
- there is currently no Testcontainers-based Kafka/Postgres/Redis integration layer even though the roadmap now calls for it.
- there is currently no dedicated Sprint 11 performance dashboard or Sprint 11 command history entry.

The biggest Sprint 11 gap is not basic correctness. The repo already proves a lot of correctness. The gap is whether that correctness is defended by a believable test strategy and backed by measured performance evidence.

## Important Architecture Note

Sprint 11 must strengthen verification without distorting the architecture already established:

- keep the primary load path aligned with real platform boundaries rather than synthetic benchmarks that bypass the system's edge
- keep fast existing Embedded Kafka and H2 tests where they already provide value
- add a smaller number of higher-fidelity integration tests instead of rewriting every existing test around heavier infrastructure
- prefer measuring end-to-end latency from existing domain timestamps and processed timestamps before inventing a second measurement protocol
- keep Docker Compose as the default performance baseline unless a Kubernetes scenario is explicitly being measured and documented as such

That means:

- do not replace the current fast test suite wholesale with slow Testcontainers-only coverage
- do not build a load tool that bypasses `api-gateway` by default and then present those numbers as platform throughput
- do not publish latency or throughput numbers without stating whether they came from Docker Compose, Kubernetes, or a partial local setup
- do not treat observability screenshots as a substitute for reproducible load tooling and written results

## Scope Decisions For Sprint 11

To keep Sprint 11 practical and evidence-driven, use the following defaults unless implementation reality forces a small adjustment:

### Load Generation Strategy

Prefer small Python-based load tooling under `tools/load/`.

Why:

- Python 3.11 is already part of CI for `ml-engine`
- it avoids adding another required runtime just to generate HTTP load
- the repo already uses timestamped JSON payloads that are easy to generate and analyze from Python

Default load target:

- drive HTTP traffic through `api-gateway` so the run exercises gateway routing plus the downstream async pipelines

Default scenarios:

- chat ingest load through `/api/chat/ingest`
- optional frame-ingest load through `/api/video/upload-frame`
- optional mixed chat + video scenario once the basic reporting path is stable

The tooling should support:

- configurable request rate
- configurable duration
- configurable streamer count
- configurable scenario selection
- capture of success and failure counts
- export of run metadata and summarized results

### Latency Measurement Strategy

Prefer using data already present in the platform contracts where possible:

- chat ingest payloads already include `timestamp`
- sentiment history already includes chat timestamp and processed timestamp fields
- video ingest payloads already include `capturedAt`
- sponsor history already includes capture and processed timestamps

Use those fields for report calculations where they are sufficient. Add minimal new metrics only when current contracts cannot support the required p50 or p95 reporting.

### Test Strategy

Keep the current fast tests and add targeted higher-fidelity coverage only where the existing substitutes hide important behavior.

Primary candidates for stronger integration coverage:

- `chat-service` -> `sentiment-service` flow with Kafka, Postgres, and Redis in the loop
- `video-service` sponsor path with Kafka, Postgres, and Redis in the loop
- cache-hit and cache-miss behavior for history APIs

Keep the current Docker smoke path as the stack-level verification layer. If CI runtime becomes too heavy, move the heaviest smoke variant to scheduled or manual execution explicitly rather than weakening coverage silently.

### Contract Protection Strategy

Protect the public contracts that are costly to break:

- JSON event schemas under `docs/schemas/`
- GraphQL schema behavior at the gateway edge

Avoid spending Sprint 11 validating every internal DTO. Focus on the contracts that shape interoperability and demo stability.

### Performance Dashboard Strategy

Add one performance-focused dashboard instead of scattering performance panels across the sprint-specific dashboards.

The dashboard should answer:

- how much traffic was produced
- how fast it was consumed
- how long end-to-end processing took
- whether DB or cache behavior became a bottleneck
- whether fallback or error rates increased under load

## Sprint 11 Deliverables

### 1. CI And Test Inventory Hardening

- review the current CI pipeline and explicitly classify fast checks versus heavy checks
- preserve useful existing coverage instead of duplicating it blindly
- tighten any gaps between what CI claims to cover and what the repo actually verifies

### 2. Higher-Fidelity Integration Coverage

- add targeted Testcontainers-backed coverage where Kafka, Postgres, or Redis realism matters
- keep the scope focused on a few high-value flows
- avoid turning every module into a slow infrastructure suite

### 3. Contract Protection

- add or complete schema validation for the documented event contracts under `docs/schemas/`
- keep GraphQL schema stability under automated protection
- ensure the contract checks are easy to run in CI

### 4. Load Tooling Under `tools/`

- create repeatable load-generation tooling under `tools/load/`
- document how to run it against the stack
- make outputs durable enough to feed the written report

### 5. Performance Dashboard

- add a performance-focused Grafana dashboard with traffic, latency, and failure panels
- provision it automatically like the existing dashboards
- make it useful during both normal load and degraded-path runs

### 6. Performance Report And Command History

- add `docs/performance-report.md`
- record measured throughput, latency, and failure observations
- explicitly state environment details and limitations
- add a Sprint 11 `opencodeCommandHistory/` entry when implementation is complete

## Required Scope Breakdown

## Phase 1 - Freeze The Verification And Reporting Baseline

1. Decide the default runtime target for Sprint 11 measurements: Docker Compose first, Kubernetes only as an additional documented scenario if needed.
2. Freeze the default load entrypoint at `api-gateway`.
3. Decide which current tests stay fast and which flows justify Testcontainers.
4. Freeze the minimum report outputs: throughput, p50/p95 latency, error counts, fallback behavior, and environment notes.
5. Decide which metrics can be derived from existing contracts versus which require new instrumentation.

### Expected end state

- Sprint 11 has a stable measurement strategy before new tooling and tests are added
- the repo avoids collecting inconsistent numbers from multiple accidental workflows

## Phase 2 - Audit Current CI And Automated Coverage

1. Review `.github/workflows/ci.yml` and classify each job as fast verification, heavy verification, or stack smoke.
2. Inventory the existing Java integration tests using Embedded Kafka, H2, and MockWebServer.
3. Inventory current frontend coverage and identify the major views or flows still not defended.
4. Identify where cache behavior, schema validation, or end-to-end flow guarantees are still under-tested.
5. Decide whether the current Docker smoke stays on pull requests unchanged or needs to move to scheduled/manual execution for runtime reasons.

### Expected end state

- the team has a clear view of what CI already defends well
- Sprint 11 work targets real gaps rather than duplicating coverage that already exists

## Phase 3 - Add Targeted Higher-Fidelity Integration Coverage

1. Add a small set of Testcontainers-backed integration tests for the highest-value persistence and cache paths.
2. Cover at least one realistic Kafka + Postgres + Redis flow for `sentiment-service` or `video-service`.
3. Add cache-behavior verification that proves both cold and warm history reads behave as intended.
4. Keep the current fast Embedded Kafka and H2 tests intact unless a specific test is replaced for a clear reason.
5. Make the heavier tests runnable in CI without depending on manual local state.

### Expected end state

- the repo has a layered test strategy: fast tests for feedback, heavier tests for realism
- critical persistence and cache behavior are no longer defended only by local substitutes

## Phase 4 - Protect Public Contracts

1. Add schema validation tests for the major documented JSON contracts in `docs/schemas/` where that protection does not already exist.
2. Confirm GraphQL schema stability remains covered at the gateway edge.
3. Add or tighten contract-oriented tests for areas most likely to break frontend or cross-service compatibility.
4. Keep the contract checks lightweight enough to stay practical in normal CI.

### Expected end state

- major event and GraphQL contracts are harder to break accidentally
- the repo's documented schemas start behaving like enforceable contracts rather than prose attachments

## Phase 5 - Build Repeatable Load Tooling Under `tools/`

1. Create `tools/load/` with a small documented entrypoint and usage notes.
2. Implement chat-ingest load generation first.
3. Allow configuration for request rate, duration, streamer count, and target base URL.
4. Capture request success/failure totals and enough timing data to support the report.
5. If practical, add a second scenario for frame ingest or a mixed run after the chat path is stable.
6. Keep outputs structured so that report generation can be repeated rather than hand-written from terminal output.

### Expected end state

- the repo can generate traffic on demand without bespoke one-off commands
- performance numbers become reproducible instead of anecdotal

## Phase 6 - Add Performance Metrics And Dashboarding

1. Review current Micrometer metrics across services and note what already exists for chat, sentiment, video, recommendation, cache, and gateway behavior.
2. Add minimal new metrics only where current counters and timers cannot answer Sprint 11 questions.
3. Create a performance dashboard with panels for:
   - end-to-end latency p50 and p95
   - Kafka produce and consume rates
   - DB write latency where measurable
   - cache hit and miss behavior
   - fallback and error rates under load
4. Provision the dashboard automatically through the same Grafana ConfigMap flow used elsewhere in the repo.
5. Verify the dashboard moves in real time during a controlled load run.

### Expected end state

- performance behavior is visible in Grafana without manual panel setup
- the report can point to stable dashboard panels instead of fragile ad hoc screenshots

## Phase 7 - Run Scenarios, Write The Report, And Update Docs

1. Run at least a small, medium, and heavier load scenario against the chosen baseline environment.
2. Record hardware and runtime context honestly.
3. Measure achieved throughput and latency from the actual runs.
4. Document fallback, error, or degradation behavior, including `ML_ENGINE_FORCE_FAILURE` if used in a degraded-path scenario.
5. Write `docs/performance-report.md` with methodology, scenarios, results, limitations, and conclusions.
6. Audit any performance-oriented README language and make sure it is consistent with measured reality.
7. Add the Sprint 11 implementation record to `opencodeCommandHistory/`.

### Expected end state

- the repo has a written, reproducible, and honest performance story
- Sprint 11 outputs are usable in both future development and final demo packaging

## Testing Requirements

Sprint 11 testing should focus on verification credibility, not only adding more test count.

Required verification:

- all existing CI jobs still pass after Sprint 11 changes
- at least one higher-fidelity integration test path runs successfully with its required infrastructure
- schema and GraphQL contract checks run in automation
- the load tool can execute a documented scenario successfully against a running stack
- the performance dashboard shows meaningful values during a load run
- the report can be regenerated from a documented procedure rather than manual guesswork

Useful additional validation if time allows:

- separate especially heavy tests into their own CI job with explicit runtime expectations
- run at least one degraded-path load scenario with `ML_ENGINE_FORCE_FAILURE=true`
- compare one Docker Compose performance run to one Kubernetes run and document the difference honestly

## Observability Requirements

Sprint 11 observability is complete only when:

- the performance dashboard is provisioned automatically
- the dashboard shows traffic, latency, and failure behavior during a real run
- fallback and error signals are visible under degraded conditions
- any new latency metrics are clearly tied to real platform boundaries rather than vague synthetic timers
- the written report can reference stable panels, queries, or metrics names directly

## Demo Script

Sprint 11 should end with a short, credible performance demonstration:

1. start the stack with the documented Docker workflow
2. open Grafana to the performance dashboard
3. run the load tool against `api-gateway`
4. show produce and consume rates moving under load
5. show latency panels responding in real time
6. optionally enable `ML_ENGINE_FORCE_FAILURE=true` and repeat a smaller run to show degraded-path behavior
7. open `docs/performance-report.md` and show the recorded measured results and limitations

## Definition Of Done

Sprint 11 is complete when:

- CI covers the repository credibly rather than only superficially
- targeted higher-fidelity integration coverage exists for the platform's most important persistence or cache paths
- repeatable load tooling exists under `tools/`
- a performance-focused Grafana dashboard is provisioned and usable
- `docs/performance-report.md` contains measured results and honest caveats
- the sprint plan, report, and command history match the actual implementation

## Risks To Watch

- trying to convert every existing integration test to Testcontainers and making the suite too slow to be useful
- building a load tool that measures only synchronous ingest acknowledgment and then presenting that as full pipeline performance
- adding percentile or latency instrumentation in a way that creates noisy or misleading metrics
- making CI materially slower without an explicit job strategy for heavy verification
- publishing performance claims without enough environment context to interpret them
- spending Sprint 11 debating perfect benchmarking methodology instead of shipping one honest, repeatable measurement workflow
