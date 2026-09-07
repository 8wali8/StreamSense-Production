# Sprint 4 Implementation Plan

## Goal

Deliver production-style resilience and failure isolation for the ML-dependent parts of the platform without breaking ingest or hiding failures.

The Sprint 4 slice should make the system behave correctly under degraded conditions:

`chat ingest -> Kafka chat event -> sentiment-service -> resilience wrapper -> ml-engine or fallback -> persistence -> GraphQL -> frontend`

## Sprint 4 Success Criteria

Sprint 4 is complete only when all of the following are true:

- ML failure does not break chat ingest.
- `sentiment-service` protects its `ml-engine` calls with Resilience4j.
- fallback sentiment behavior is explicit and consistent:
  - `label = NEUTRAL`
  - `score = 0.0`
  - `modelVersion = fallback`
- Kafka listener failures do not appear successful when work was actually lost.
- retry and dead-letter behavior exists for repeated failures.
- failure paths are visible in metrics and logs.
- at least one circuit breaker state transition can be demonstrated.
- at least one representative degraded-path trace can be shown in Zipkin.
- automated tests credibly cover fallback, retry/dead-letter, and ingest survival.

## Important Architecture Note

The original roadmap wording says:

- `chat-service` and `video-service` use Resilience4j wrappers around ML interactions where applicable

In the current repo, that intent must be translated to the real architecture:

- `chat-service` is now ingest-only and should stay that way
- `sentiment-service` is the current owner of ML sentiment calls and must receive the actual resilience work now
- `video-service` does not yet have a real ML interaction path; Sprint 4 should prepare the resilience pattern and config shape so Week 5 can apply it directly when sponsor detection is built

So the practical Sprint 4 target is:

- fully implement resilience for `sentiment-service`
- establish reusable resilience config and conventions for future `video-service` work

## Current Starting Point

This plan assumes the current repo state is:

- `chat-service` only handles chat ingest and publishes `stream.chat.messages`
- `sentiment-service` consumes `stream.chat.messages`
- `sentiment-service` calls `POST /ml/sentiment`
- `sentiment-service` persists sentiment results in Postgres
- `api-gateway` exposes:
  - `recentSentiment(streamer, limit)`
  - `onSentiment(streamer)`
- frontend already renders recent and live sentiment
- Prometheus and Zipkin are available in Compose
- there is currently no real Resilience4j integration in service code
- there is currently no dead-letter topic or dead-letter handling strategy for sentiment processing
- `video-service` is still mostly a bootstrap service and does not yet own a real ML call path

The largest Sprint 4 gap is that the happy path exists, but degraded behavior is not yet explicit or trustworthy.

## Sprint 4 Deliverables

By the end of the sprint, the repository should contain all of the following implemented behavior.

### 1. Real Resilience On The Sentiment ML Path

- circuit breaker around the `ml-engine` call
- retry policy for transient ML failures
- time limiter / explicit timeouts
- bulkhead isolation so ML issues do not consume the whole service
- fallback sentiment result when ML is unavailable or timing out

### 2. Failure-Safe Kafka Processing

- no broad catch-and-log-only listener behavior
- retry behavior for process failures
- dead-letter strategy after retry exhaustion
- dead-letter topic for repeatedly failing chat events

### 3. Config-Driven Resilience Behavior

- Config Server contains service-level resilience config
- a compatibility-friendly config shape exists for teams familiar with `hystrix.*`
- mapping from compatibility config to actual Resilience4j behavior is documented

### 4. Chaos And Demo Controls

- simple way to trigger ML failure intentionally
- documented degraded-path demo steps

### 5. Observability For Resilience

- metrics for breaker state, fallback count, and protected call totals
- logs that clearly distinguish:
  - normal ML success
  - retrying failures
  - fallback execution
  - dead-letter publication

### 6. Reusable Pattern For Future `video-service`

- config scaffolding and/or shared conventions ready for Week 5 sponsor ML calls
- do not fully implement sponsor resilience yet, but avoid making Sprint 5 start from zero

## Required Scope Breakdown

The work should be executed in the following order.

---

## Phase 1 - Finalize Failure Contracts And Operational Rules

This phase should happen before writing most of the implementation.

### Objectives

- make degraded behavior explicit
- prevent hidden ambiguity in retry, fallback, and dead-letter behavior

### Tasks

1. Freeze the fallback sentiment contract:
   - same event shape as normal sentiment results
   - `label = NEUTRAL`
   - `score = 0.0`
   - `modelVersion = fallback`
2. Decide and document dead-letter topic naming.
3. Recommended naming:
   - `stream.chat.messages.dlt` for chat events that fail sentiment processing repeatedly
4. Define which failures are retryable vs immediately non-retryable.
5. Define how fallback interacts with persistence.
6. Recommended behavior:
   - fallback sentiment should still be persisted and optionally published as a real downstream event
7. Define observability fields that must appear in logs for degraded paths.

### Output Of This Phase

- one fallback event contract
- one DLT naming convention
- one retry/fallback/dead-letter rule set

### Risks

- mixing fallback behavior with dead-letter behavior inconsistently
- retrying failures forever without an explicit terminal path

---

## Phase 2 - Add Resilience4j To `sentiment-service`

This is the main implementation phase for Sprint 4.

### Objectives

- protect the ML dependency without breaking the rest of the pipeline

### Tasks

#### Dependencies And Wiring

1. Add Resilience4j dependencies to `sentiment-service`.
2. Include the pieces needed for:
   - circuit breaker
   - retry
   - bulkhead
   - time limiter
   - actuator metrics exposure
3. Keep the change localized to `sentiment-service` for real behavior.

#### Protected ML Client Path

1. Wrap the `ml-engine` call in a named resilience boundary.
2. Ensure the boundary has:
   - explicit timeout
   - retry policy
   - circuit breaker
   - bulkhead isolation
3. Keep the orchestration in the service layer, not directly in the Kafka listener.
4. Preserve correlation and tracing information through normal and fallback paths.

#### Fallback Behavior

1. Implement a fallback method or fallback path returning the agreed neutral sentiment contract.
2. Make fallback logs explicit and structured enough to find quickly.
3. Persist fallback sentiment results the same way as normal results unless there is a strong reason not to.
4. If fallback results are published to Kafka, ensure they are visibly marked by `modelVersion = fallback`.

### Expected End State For `sentiment-service`

- ML failures do not crash the sentiment pipeline immediately
- short ML outages degrade to fallback sentiment results
- repeated upstream failures can still be surfaced and handled explicitly

### Risks

- putting resilience annotations everywhere instead of keeping them concentrated around the real dependency boundary
- making fallback so broad that real defects become invisible

---

## Phase 3 - Add Retry And Dead-Letter Behavior For Kafka Processing

This phase makes failure handling explicit instead of accidental.

### Objectives

- stop silent message loss
- make listener failure behavior operationally visible

### Tasks

1. Replace any implicit listener failure handling with an explicit Spring Kafka error-handling strategy.
2. Add bounded retries with backoff for sentiment processing failures.
3. After retry exhaustion, publish the failed chat event to a dead-letter topic.
4. Include enough metadata with dead-letter records to debug failures later.
5. Recommended metadata:
   - original topic
   - partition
   - offset
   - exception class
   - exception message
   - correlation ID if present
6. Expose logs and metrics for dead-letter activity.
7. Ensure failures are not swallowed inside the listener in a way that makes Kafka think processing succeeded when it did not.

### Recommended DLT Scope

Implement Sprint 4 for the current sentiment path only:

- `stream.chat.messages.dlt`

Do not overbuild a generalized cross-service dead-letter framework yet.

### Expected End State

- a failing chat event is retried a bounded number of times
- if still failing, it lands in DLT
- the system makes that outcome visible in metrics and logs

### Risks

- acking offsets too early
- retrying non-retryable validation/data contract failures unnecessarily

---

## Phase 4 - Add Config Compatibility And Translation

This phase preserves the roadmap’s Hystrix continuity requirement without implementing old Hystrix behavior directly.

### Objectives

- make resilience behavior configurable centrally
- preserve a compatibility-friendly config shape for continuity

### Tasks

1. Add resilience config to `config-server/config-repo/sentiment-service.yml`.
2. Add a compatibility-shaped section, if desired, under something like:
   - `hystrix.command.mlSentiment.*`
3. Add the real Resilience4j config section used by the application.
4. Implement a small translation layer only if needed.
5. Prefer the smallest correct approach:
   - if simple dual config sections are enough, do not overengineer a full conversion framework
6. Document the mapping in a dedicated compatibility note.

### Minimum Config Surfaces

Define values for:

- timeout duration
- max attempts
- retry backoff
- breaker sliding window
- failure rate threshold
- wait duration in open state
- bulkhead concurrency or queue limits

### Expected End State

- resilience behavior can be tuned from Config Server
- compatibility naming is documented clearly enough for demos and future cleanup

### Risks

- spending too much time preserving Hystrix aesthetics instead of shipping actual working resilience

---

## Phase 5 - Add Chaos Toggles And Degraded Demo Controls

This phase makes Sprint 4 demonstrable, not just theoretically resilient.

### Objectives

- make degraded-path behavior easy to trigger on demand

### Tasks

1. Choose the simplest practical failure mechanism.
2. Valid options:
   - stop the `ml-engine` container
   - add an env/config flag in `ml-engine` that forces failures
   - add a configurable failure percentage in non-production demo mode
3. Document the preferred demo path in the runbook.
4. Make sure the chaos toggle does not require code edits during the demo.

### Recommended Minimal Approach

For Sprint 4, the minimum acceptable demo mechanism is:

- stopping `ml-engine`

Optional improvement:

- add `ML_ENGINE_FORCE_FAILURE=true` or equivalent to make the demo faster and more repeatable

### Risks

- demo controls that only work locally for one developer but are not documented or repeatable

---

## Phase 6 - Prepare `video-service` For Week 5 Reuse

This phase should stay minimal.

### Objectives

- avoid duplicating resilience design work when sponsor detection starts next sprint

### Tasks

1. Add or reserve config structure for future sponsor ML resilience in `video-service` config.
2. Document that `video-service` will reuse the same resilience conventions as `sentiment-service`.
3. If a tiny shared helper becomes obviously reusable without overengineering, add it.
4. Otherwise, prefer documentation and config shape over premature abstraction.

### Expected End State

- Week 5 can reuse Sprint 4 resilience patterns immediately
- no fake sponsor implementation is added early

### Risks

- trying to build Week 5 during Sprint 4

---

## Phase 7 - Testing Coverage For The Resilience Slice

This phase turns the degraded-path behavior into something trustworthy.

### Objectives

- prove fallback, retry, and dead-letter behavior in automated tests

### Tasks

#### `sentiment-service`

1. Add unit tests for fallback result creation.
2. Add integration tests where `ml-engine` fails and fallback is used.
3. Add tests proving chat ingest still leads to a stored/published fallback sentiment when ML is down.
4. Add tests proving retry occurs for transient failures.
5. Add tests proving repeated failures reach the dead-letter topic.

#### `api-gateway`

1. Add or update tests so GraphQL history and live subscriptions continue to work when fallback sentiment is produced.

#### Frontend

1. Add or update tests so the sentiment UI handles fallback values sensibly.
2. Make sure fallback data is still rendered meaningfully, not treated as an error.

### Minimum Test Proofs

- fallback sentiment event is created correctly
- ingest survives ML failure
- repeated failures are not silently dropped
- DLT receives exhausted failures
- GraphQL and frontend still display degraded sentiment data

### Risks

- tests only covering direct method calls without proving Kafka listener behavior

---

## Phase 8 - Observability And Dashboarding

This phase makes resilience visible during demos and debugging.

### Objectives

- surface circuit state, fallback rate, and ML degradation clearly

### Tasks

1. Expose Resilience4j metrics through Actuator/Prometheus.
2. Add application metrics for:
   - fallback count
   - dead-letter count
   - protected ML call totals
3. Add or update Grafana dashboards for:
   - circuit breaker state
   - fallback rate
   - ML error rate
   - dead-letter rate
4. Ensure degraded-path logs are easy to grep and correlate.
5. Verify Zipkin still shows the request path under degraded operation.

### Expected End State

- normal and degraded behavior are both visible in metrics and traces

### Risks

- relying only on logs when the sprint explicitly calls for metrics and dashboard visibility

---

## Phase 9 - Documentation And Demo Script

This phase closes the sprint operationally.

### Objectives

- make Sprint 4 reproducible for another contributor or demo audience

### Tasks

1. Update `docs/howtorun.md` with a Sprint 4 section.
2. Document:
   - normal-path verification
   - degraded-path verification
   - how to trigger ML failure
   - how to inspect fallback results
   - how to inspect DLT messages
3. Add a compatibility doc for Hystrix-style config to Resilience4j mapping.
4. Add a Sprint 4 checklist similar to earlier sprint runbooks.

### Demo Script

1. Start the stack normally.
2. Ingest chat and show normal sentiment behavior.
3. Trigger ML failure.
4. Continue ingesting chat.
5. Show fallback sentiment still appears through REST, GraphQL, and the UI.
6. Show breaker state or fallback metrics moving in Grafana/Prometheus.
7. Show Zipkin for at least one degraded-path trace.
8. Show DLT behavior for a repeatedly failing message path if that scenario is implemented separately from fallback.

---

## Recommended Execution Order

Do the sprint in this order:

1. Freeze fallback and DLT contracts.
2. Add Resilience4j dependencies and config.
3. Protect the `sentiment-service -> ml-engine` call.
4. Add fallback persistence/publication behavior.
5. Add Kafka retry and dead-letter handling.
6. Add chaos toggle or at least document the failure trigger.
7. Add tests for fallback and DLT.
8. Add metrics, dashboards, and trace verification.
9. Update docs and demo runbook.
10. Run normal-path and degraded-path live verification.

Do not reverse this casually. The retry/DLT behavior should sit on top of a clearly defined protected ML path, not be invented before the failure contract is settled.

## Validation Checklist

Run these checks before calling Sprint 4 complete:

1. Start the Docker stack.
2. Confirm normal sentiment processing still works.
3. Confirm `ml-engine` health is up.
4. Trigger ML failure.
5. Ingest more chat data.
6. Confirm chat ingest still succeeds.
7. Confirm fallback sentiment appears in:
   - `GET /api/sentiment/recent`
   - `recentSentiment(streamer, limit)`
   - `onSentiment(streamer)`
   - frontend sentiment panel
8. Confirm breaker metrics or states change.
9. Confirm failures are retried or dead-lettered explicitly.
10. Confirm no failure path is silently swallowed.
11. Confirm Zipkin shows at least one degraded-path trace.

## Definition Of Done

Sprint 4 is done when:

- ML failure does not break ingest
- fallback sentiment behavior is explicit and visible
- retries and/or dead-letter handling prevent silent message loss
- resilience behavior is centrally configurable
- metrics and traces make the degraded path observable
- docs explain how to reproduce both healthy and degraded operation

## Risks And Pitfalls

- over-aggressive timeouts causing unnecessary fallback
- retrying so aggressively that the system amplifies an outage
- dead-lettering everything instead of distinguishing retryable vs terminal failures
- hiding real bugs behind a fallback that is too broad
- overengineering a shared resilience abstraction before the sponsor path exists

## Minimal Acceptance Recommendation

If time gets tight, the smallest acceptable Sprint 4 completion path is:

1. Implement full Resilience4j protection for `sentiment-service -> ml-engine`.
2. Persist and publish explicit fallback sentiment results.
3. Add bounded retry plus DLT for repeated failures.
4. Expose fallback and breaker metrics.
5. Document and prove the degraded-path demo.

That is the smallest slice that satisfies the sprint intent without pretending `video-service` is already built.
