# StreamSense Final Production Roadmap

## Purpose

This document is the comprehensive production plan for taking the repository from its current partial implementation state to a final demoable, production-shaped platform.

The intent is not to optimize for the smallest next fix. The intent is to fully cover the architecture, platform, services, observability, testing, deployment, and demo packaging work required to make the repository defensible as a complete system.

This plan intentionally includes a large number of items. Nothing here is simplified for brevity.

## North Star

End-of-week-12 target demo:

- One command brings up Docker Compose with:
  - Eurek
  - Config Server
  - Kafka
  - all Spring services
  - `ml-engine`
  - Postgres
  - Redis
  - optional Cassandra profile
  - Prometheus
  - Grafana
  - Zipkin
  - React UI
- Demo shows the full data path:
  - chat ingestion
  - Kafka transport
  - ML sentiment and sponsor analysis
  - persistence and cache reads
  - GraphQL gateway queries and subscriptions
  - React real-time dashboards and charts
- The repository includes:
  - dashboards
  - traces
  - automated tests
  - CI
  - load test tooling
  - an honest performance report
  - runbooks for local and Kubernetes deployment

## Production Architecture Target

Use this as the target architecture for the final state:

- `eureka-server` provides service discovery for Spring services.
- `config-server` provides centralized configuration using native mode backed by an in-repo config repository unless an external repo is introduced later.
- `api-gateway` is the single entry point and owns:
  - service routing
  - GraphQL query and subscription access
  - auth hooks
  - rate limiting
  - cross-cutting gateway concerns
- `chat-service` owns chat ingestion only and publishes chat events.
- `sentiment-service` owns:
  - consuming chat events
  - calling `ml-engine` for sentiment inference
  - persisting sentiment results
  - exposing historical sentiment APIs
  - optionally publishing live sentiment events for subscriptions
- `video-service` owns:
  - frame ingest
  - calling `ml-engine` for sponsor detection
  - persisting sponsor detection results
  - exposing historical sponsor APIs
  - publishing live sponsor detection events
- `recommendation-service` owns recommendation generation based on recent platform signals and experiment configuration.
- `ml-engine` remains an internal Python inference service with deterministic stubs early and hardened interfaces later.
- Kafka remains the event backbone for asynchronous processing and real-time fanout.
- Postgres is the source of truth for persisted domain data.
- Redis is the cache layer for hot reads and gateway-facing history queries.
- optional Cassandra remains a future or profile-gated backend for time-series style storage, not a blocker for the core platform.
- `frontend` consumes the GraphQL API and renders live and historical analytics.
- Prometheus, Grafana, and Zipkin provide baseline production-style observability.
- Kubernetes manifests target local `kind` or `minikube` first, while keeping a clear adaptation path to EKS or GKE.

## Required Modern Substitutions

These substitutions are required and are part of the production plan.

### Zuul -> Spring Cloud Gateway

Use Spring Cloud Gateway rather than Zuul.

Why:

- Zuul is effectively deprecated in modern Spring Cloud usage.
- Spring Cloud Gateway is the correct modern replacement for routing and cross-cutting gateway behavior.
- This keeps the API gateway concept intact without preserving a legacy runtime choice.

Constraints:

- Keep the service folder and service identity as `api-gateway`.
- Preserve the architectural role of an API gateway.
- Preserve future hooks for auth, routing, rate limiting, and centralized policies.

### Hystrix -> Resilience4j

Use Resilience4j rather than Hystrix.

Why:

- Hystrix is end-of-life.
- Resilience4j supports circuit breakers, retries, bulkheads, rate limiting, and time limiting in a modern Spring stack.

Constraints:

- Preserve the intent of Hystrix-style resilience behavior.
- Provide a thin compatibility approach so existing `hystrix.*`-shaped config can still look familiar in Config Server even if services translate that into Resilience4j settings internally.
- Document the mapping clearly so the repository remains understandable to someone reading the original architecture assumptions.

### Config Repository Mode

If no external `streamsense-config` repository exists, run Config Server in native mode using an in-repo config directory.

Target behavior:

- Config Server serves per-service configuration from `/config-repo/` or `config-server/config-repo/`.
- Docker and Kubernetes both mount or expose this configuration predictably.
- The repository keeps centralized configuration semantics without inventing missing external dependencies.

## Non-Negotiable Must-Haves

The final repository must include all of the following:

- All listed services present and runnable in Docker Compose.
- Kafka topics and event flow for:
  - chat -> sentiment
  - video -> sponsor
- `ml-engine` containerized with real HTTP endpoints, deterministic stubs acceptable early but not as the final explanation of the system.
- GraphQL gateway with working subscriptions that feed the React UI.
- Prometheus, Grafana, and Zipkin running with meaningful dashboards and trace coverage.
- Kubernetes manifests that deploy the stack to `kind` or `minikube` with a documented local workflow.
- CI with unit tests, integration tests, and a smoke path.
- Load testing tools and a written performance report with measured results.

## Nice-to-Have Only If Ahead Of Schedule

- True GraphQL federation with separate service subgraphs instead of a single gateway-owned graph.
- Cassandra as an active storage backend rather than a documented optional path.
- Spring Cloud Bus for live config refresh.
- Full auth, user accounts, RBAC, and persisted sessions.

## Guardrails

- Do not expand scope beyond the services already in the repository description.
- Do not let optional Cassandra work block the core path.
- Do not claim performance numbers that are not measured.
- Do not leave observability as a last-minute concern.
- Do not defer docs until the end; update docs every week and finish polish in week 12.
- Do not let gateway architecture drift into an ad hoc collection of endpoint hacks; keep a clear gateway role.
- Do not let GraphQL subscription protocol choices drift; lock them early and keep them consistent.
- Do not let silent event loss remain in any pipeline.
- Do not let load test claims outrun local hardware reality; publish honest numbers and tuning notes.

## Current State Summary

This section reflects the implementation records in `opencodeCommandHistory/` through `2026-04-23-sprint-11-live-compose-benchmark-and-metrics.md`.

The repository is no longer in the original partial-implementation state. Sprints 1 through 11 have been implemented or substantially closed, including the core vertical slices, gateway maturity work, Kubernetes deployment, Kafka metrics, and the first measured Compose benchmark.

What is now implemented according to the command history:

- repo standards, service metadata cleanup, root task runner updates, CI baseline, and service boot tests
- Config Server native mode using `config-server/config-repo/`
- shared HTTP correlation ID filters and common logging/tracing conventions
- Docker Compose startup hardening for Config Server, Kafka, Postgres, Redis, `ml-engine`, Spring services, monitoring, and frontend
- GraphQL `health` query returning `ok`
- chat ingest through `chat-service`, Kafka topic initialization, gateway GraphQL subscription fanout, and frontend live chat rendering
- `sentiment-service` ownership of the chat -> sentiment pipeline, including ML calls, Postgres persistence, Kafka sentiment events, REST history, GraphQL query/subscription, frontend sentiment UI, metrics, and tests
- Resilience4j protection around sentiment ML calls, fallback persistence/publication, retry and dead-letter handling for terminal processing failures, chaos toggle support, resilience metrics, and Grafana coverage
- `video-service` ownership of the frame -> sponsor pipeline, including deterministic sponsor inference, fallback behavior, Postgres persistence, Kafka sponsor events, REST history, GraphQL query/subscription, frontend sponsor UI, metrics, and tests
- Redis-backed hot-read caching for recent sentiment and sponsor history, with cache hit/miss metrics and dashboard coverage
- gateway routing, auth hook with local bypass mode, ingest rate limiting, modular GraphQL schema files, subscription reliability improvements, and frontend WebSocket reconnect hardening
- `recommendation-service` with deterministic explainable recommendations, centralized experiment config, downstream signal aggregation, REST API, GraphQL query, frontend recommendation panel, and metrics
- local Kubernetes deployment for `kind`, including manifests for core services, supporting infrastructure, monitoring, ingress, ConfigMaps, local image workflow, and runbook
- Kafka-on-Kubernetes local demo support using the existing Confluent-based deployment, 3-partition topics, streamer-keyed records, kafka-exporter, Prometheus scraping, Grafana Kafka dashboard, and consumer scaling documentation
- schema contract tests for documented event JSON schemas, GraphQL schema contract protection, load tooling under `tools/load/`, performance dashboarding, and `docs/performance-report.md`
- live Compose benchmark results captured in `docs/performance-report.md`, including baseline and degraded-path measurements

What remains for final production-style closure:

- run the final API-level smoke path against a real Docker daemon from a clean state and record the result
- run the degraded-path proof runbook and capture the actual final evidence: GraphQL fallback payload, Prometheus fallback/circuit-breaker samples, Zipkin trace ID, and frontend observation note
- run the new rate-limit-relaxed benchmark mode and update `docs/performance-report.md` with measured results if deeper downstream performance numbers are desired
- ensure Grafana dashboards are consistently provisioned for both Compose and Kubernetes during the final live demo run
- document the optional Cassandra profile as non-blocking, or explicitly mark it out of scope if it will not be included
- validate the final new-machine experience and record whether the full demo can be reproduced in the target time window

## Cross-Cutting Workstreams

These workstreams apply across multiple weeks and should be treated as always-active concerns.

### Architecture And Ownership

- keep service boundaries clear
- avoid duplicated ML and event-processing logic in multiple services unless there is a deliberate reason
- keep gateway concerns in `api-gateway`, not smeared across services
- keep history queries service-owned, not Kafka-backed
- use Kafka for event transport and live fanout, not as a substitute for query models

### Contracts And Schemas

- define and freeze JSON schemas for major event contracts under `docs/schemas/`
- keep `ChatMessageEvent`, `SentimentAnalysisEvent`, `FrameData`, `SponsorDetectionEvent`, and recommendation response contracts documented
- add schema validation tests where reasonable
- protect GraphQL schema evolution with tests or snapshots

### Observability

- every service should expose actuator health and Prometheus metrics where applicable
- traces should exist for core request and event paths
- correlation identifiers should survive HTTP and Kafka hops
- dashboards should be provisioned rather than manually created whenever practical

### Testing

- every service should have at least a context or boot test early
- critical flows need integration coverage with Kafka, Postgres, and Redis where applicable
- frontend should have linting and component-level test coverage
- the repository should include a smoke path that validates the system at a stack level

### Documentation

- architecture docs must match implementation
- local and Kubernetes runbooks must be executable by a new contributor
- troubleshooting docs must exist for common failure modes
- performance reporting must be explicit about environment and limits

## Delivery Phases

The original work was organized into twelve weeks. The week-by-week sections below are retained as historical roadmap detail and implementation context; they are no longer the current remaining-work checklist. For current remaining work, use `Current State Summary`, `Remaining Priority Ladder`, and `Final Success Criteria Status`.

---

## Week 1 - Repo Bootstrap And Platform Skeleton

### Objectives

- Establish the monorepo structure exactly as the repository describes it.
- Make the base platform compile and test cleanly.
- Stand up Eureka and Config Server locally.
- Establish CI and shared observability conventions.

### Build Deliverables

- Folder tree exists and is coherent for:
  - `eureka-server/`
  - `config-server/`
  - `api-gateway/`
  - `chat-service/`
  - `video-service/`
  - `ml-engine/`
  - `sentiment-service/`
  - `recommendation-service/`
  - `frontend/`
  - `monitoring/`
  - `kafka-cluster/`
  - `k8s/`
  - `docs/`
- Docker Compose boots at least:
  - `eureka-server`
  - `config-server`
  - `zipkin`
  - `prometheus`
  - `grafana`
  - stubs are acceptable for some services at this stage if the platform shape is correct
- GitHub Actions baseline exists for:
  - Java build and test
  - Python lint and test
  - frontend lint and test
- Shared Spring conventions are established for:
  - correlation id propagation
  - logging pattern
  - Micrometer configuration
  - Zipkin exporter wiring

### Detailed Checklist

#### Repository Standards

- add or fix root `.editorconfig`
- add or fix root `.gitignore`
- add or fix root `Makefile` or `justfile` with at least:
  - `build`
  - `test`
  - `up`
  - `down`
  - `logs`
- normalize root repo metadata so the documented services match the actual directories
- ensure service naming is internally consistent across docs, config, Compose, and Maven metadata

#### Eureka

- ensure `eureka-server` is a valid Spring Boot app
- ensure Eureka runs on port `8761`
- disable self-registration and self-fetch behavior as appropriate for a standalone registry
- ensure all Spring services can point to Eureka predictably in local Docker

#### Config Server

- ensure `config-server` is a valid Spring Boot app
- run Config Server in native mode initially
- create or normalize `/config-repo/` inside the monorepo
- provide per-service YAML files for each service
- ensure local and Docker search locations are stable and documented

#### Shared Observability Baseline

- establish a common log pattern that includes:
  - trace id
  - span id
  - correlation id
- add a request filter or equivalent that ensures correlation ids exist for HTTP requests
- add Kafka header propagation helpers for correlation and tracing metadata
- ensure Micrometer and tracing setup are not service-by-service accidents

#### Docker Compose Baseline

- ensure `docker-compose.yml` includes at least:
  - `eureka-server`
  - `config-server`
  - `zipkin`
  - `prometheus`
  - `grafana`
- ensure volume mounts for config work in Docker
- ensure healthchecks exist where they prevent startup races

#### CI

- create or harden `.github/workflows/ci.yml`
- build and test all Java services with Maven
- lint and test Python code for `ml-engine`
- lint and test the frontend
- add a smoke validation for `docker compose config`

### Learning Needed

- Spring Cloud Config native backend behavior
- Eureka registration settings
- Micrometer and Zipkin baseline wiring

### Testing

- add `context loads` or equivalent boot tests per service
- validate Compose configuration in CI
- ensure CI is green on the main branch before moving on

### Observability

- Zipkin reachable on `:9411`
- Prometheus configuration file exists, even if some targets are placeholders initially
- standard logs include correlation and tracing context

### Demo Script

- start `eureka-server`, `config-server`, `zipkin`, `prometheus`, and `grafana`
- show Eureka UI loads
- show Config Server returns config for at least one service

### Definition Of Done

- CI is green on `main`
- Eureka and Config Server run locally
- Config is fetched from the in-repo config repository successfully

### Risks And Pitfalls

- Docker config path issues
- dependency version drift across Spring services
- stale service metadata causing confusion before feature work even starts

---

## Week 2 - Thin Vertical Slice v0

### Objectives

- Bring up Kafka locally.
- Implement minimal chat ingestion into Kafka.
- Stand up GraphQL gateway with one query and one subscription.
- Render real-time chat updates in the frontend.

### Build Deliverables

- local Kafka and Zookeeper integrated into root Compose
- optional Kafka UI
- Kafka topic `stream.chat.messages`
- `chat-service` provides `POST /api/chat/ingest`
- `chat-service` produces `ChatMessageEvent` to Kafka
- `chat-service` consumer logs events for validation
- `api-gateway` GraphQL provides:
  - `Query { health: String }`
  - `Subscription { onChatMessage(streamer: String!): ChatMessageEvent }`
- GraphQL WebSocket subscriptions are operational
- frontend subscribes to live chat and renders it

### Detailed Checklist

#### Kafka Cluster

- add Kafka services to Compose
- add topic initialization container or startup logic
- create topic `stream.chat.messages`
- make Kafka hostnames and ports work both from containers and from the host machine

#### Chat Service

- add Spring Kafka dependencies
- define `ChatMessageEvent` contract
- implement request validation for ingest payloads
- publish to `stream.chat.messages`
- include correlation and tracing headers in Kafka records
- add a consumer that logs chat events to validate the flow

#### API Gateway GraphQL

- lock the GraphQL subscription protocol to `graphql-ws` / `graphql-transport-ws`
- add a working `health` query that returns success, not an error
- make the gateway consume chat events through its own subscription-oriented Kafka consumer group
- bridge Kafka messages into a GraphQL subscription sink

#### Frontend

- use React and Apollo Client
- configure both HTTP and WebSocket links
- add a `Live Chat` surface filtered by streamer
- make connection, loading, error, and empty states visible

#### Documentation

- write a local quickstart for the week 2 stack
- document how to test the health query and the chat subscription manually

### Learning Needed

- Spring Kafka producer and consumer basics
- GraphQL subscriptions in the selected stack
- Apollo client WebSocket setup

### Testing

- `chat-service` controller validation tests
- Kafka producer test with Embedded Kafka or Testcontainers
- gateway subscription integration test proving Kafka event -> GraphQL subscription event
- frontend component test for rendering incoming events
- positive GraphQL health test that asserts success, not GraphQL failure

### Observability

- add metrics such as:
  - `streamsense_chat_ingest_total`
  - chat produce latency timer
- expose Prometheus metrics from `chat-service`
- ensure traces exist at least for `POST /api/chat/ingest` and Kafka produce

### Demo Script

- start the stack with Kafka and frontend
- `curl` the ingest endpoint multiple times
- show frontend updates in real time via GraphQL subscription

### Definition Of Done

- one-command local run yields live chat subscription updates in the UI
- `query { health }` returns `ok`

### Risks And Pitfalls

- GraphQL subscription library mismatch between backend and frontend
- Kafka initialization races during startup

---

## Week 3 - ML Engine Stub And Sentiment Pipeline v1

### Objectives

- Introduce `ml-engine` with deterministic sentiment inference.
- Build the first real sentiment pipeline.
- Persist sentiment results in Postgres.
- Expose historical and live sentiment via GraphQL.

### Build Deliverables

- `ml-engine` provides:
  - `POST /ml/sentiment`
  - `GET /ml/health`
- Kafka topic `stream.sentiment.events`
- `sentiment-service` consumes or otherwise owns the sentiment pipeline responsibilities
- Postgres schema for `sentiment_events`
- REST endpoint for recent sentiment history
- gateway GraphQL provides:
  - `recentSentiment(streamer, limit)`
  - `onSentiment(streamer)`
- frontend renders a real-time sentiment chart and recent history list

### Detailed Checklist

#### Postgres

- add Postgres to Compose if not already stable
- configure a clean schema creation path with Flyway
- create the `sentiment_events` table and indexes
- keep schema validation strict enough to catch drift

#### ML Engine

- containerize the Python service cleanly
- implement deterministic sentiment inference based on message content hashing or equivalent stable logic
- define request and response contracts clearly
- add endpoint tests

#### Chat And Sentiment Ownership

- lock the architecture so `chat-service` is ingest-only
- move any sentiment-specific consumer, ML client, and sentiment event publishing logic out of `chat-service`
- implement that logic in `sentiment-service`
- if temporary overlap exists during migration, remove it before the week is considered done

#### Sentiment Service

- consume `stream.chat.messages`
- call `ml-engine /ml/sentiment`
- persist sentiment rows to Postgres
- optionally publish `stream.sentiment.events` after persistence for live subscribers
- expose recent sentiment history through a service-owned API

#### API Gateway

- consume live sentiment events for GraphQL subscriptions if sentiment fanout is event-based
- add a query resolver that fetches recent sentiment from `sentiment-service`

#### Frontend

- add a sentiment dashboard area
- show recent sentiment history and live updates
- render a basic chart, not just raw JSON

### Learning Needed

- FastAPI containerization
- Flyway and JPA or jOOQ basics for event persistence
- trace propagation across HTTP and Kafka boundaries

### Testing

- `ml-engine` tests for deterministic outputs
- `sentiment-service` integration test using Postgres and Kafka
- contract test for the ML response shape
- GraphQL query and subscription tests for sentiment data
- frontend test for the sentiment display surface

### Observability

- add metrics such as:
  - `streamsense_sentiment_events_total{label=...}`
  - `streamsense_ml_sentiment_latency_ms`
- add Grafana dashboard panels for:
  - ingest rate
  - ML latency
  - sentiment label distribution
  - consumer lag where possible
- ensure trace propagation from ingest -> sentiment inference -> persistence

### Demo Script

- ingest chat messages
- show sentiment events arriving in the UI in real time
- show `recentSentiment` query returning persisted data
- open Zipkin and show a representative trace spanning the path

### Definition Of Done

- end-to-end sentiment pipeline works with persistence and real-time UI
- service ownership for sentiment processing is corrected

### Risks And Pitfalls

- trace context loss across Kafka boundaries
- schema drift between code and docs
- partial migration where sentiment logic still lives in two services

---

## Week 4 - Resilience And Failure Isolation

### Objectives

- Add production-style resilience around ML calls.
- Preserve Hystrix intent using Resilience4j.
- Make failures visible and demoable without breaking ingest.

### Build Deliverables

- `chat-service` and `video-service` use Resilience4j wrappers around ML interactions where applicable
- fallback behavior exists for sentiment and sponsor paths
- Config Server contains compatibility-friendly resilience configuration
- repeated failures do not silently drop work

### Detailed Checklist

#### Resilience4j Adoption

- add Resilience4j dependencies where needed
- add circuit breaker behavior for ML calls
- add retries where appropriate
- add bulkhead isolation
- add explicit timeouts or time limiters

#### Config Compatibility

- preserve familiar `hystrix.*`-style config sections if desired for continuity
- add service-level translation from Hystrix-like config shapes into actual Resilience4j configuration
- document the mapping clearly in a compatibility document

#### Fallback Behavior

- sentiment fallback returns:
  - `label = NEUTRAL`
  - `score = 0.0`
  - `modelVersion = fallback`
- sponsor fallback returns:
  - `sponsor = null`
  - `confidence = 0.0`
- fallback paths remain observable in logs and metrics

#### Silent Message Loss Prevention

- remove broad catch-all swallow-and-log-only behavior in event processing
- make listener failures retry or dead-letter rather than appear successful
- add dead-letter handling strategy for repeatedly failing messages
- make failure states visible through metrics and logs

#### Chaos Toggles

- add a practical way to simulate ML failure
- document the simplest demo mechanism, such as stopping the container or using an env flag

### Learning Needed

- Resilience4j circuit breaker, retry, bulkhead, and time limiter behavior
- Kafka dead-letter and retry patterns in Spring

### Testing

- unit tests for fallback methods
- integration tests with failing ML dependencies
- tests ensuring ingest survives when ML is down
- tests ensuring failures are retried or dead-lettered, not silently dropped

### Observability

- add Resilience4j metrics such as:
  - circuit breaker state
  - fallback count
  - protected call totals
- add Grafana panels for:
  - open and half-open breaker counts
  - fallback rate
  - ML error rate

### Demo Script

- show normal sentiment behavior
- stop `ml-engine` or trigger failure mode
- ingest more data
- show fallback behavior continues and the UI still updates
- show Grafana and Zipkin reflecting the degraded path

### Definition Of Done

- ML failure does not break ingest
- circuit breaker opens and recovers
- failures are visible and not silently lost

### Risks And Pitfalls

- over-aggressive timeouts causing unnecessary fallback
- thread pool or bulkhead misconfiguration causing secondary failures

---

## Week 5 - Video Pipeline v0 And Sponsor Detection Flow

### Objectives

- Implement the `video-service` flow.
- Extend `ml-engine` with sponsor detection.
- Expose sponsor detections live and historically in the UI.

### Build Deliverables

- Kafka topics:
  - `stream.video.frames`
  - `stream.sponsor.detections`
- `video-service` provides frame ingest
- `ml-engine` provides `POST /ml/sponsor`
- `video-service` emits `SponsorDetectionEvent`
- gateway exposes live and historical sponsor data
- frontend renders sponsor detections in real time

### Detailed Checklist

#### Contracts

- define `FrameData`
- define `SponsorDetectionEvent`
- document schemas in `docs/schemas/`

#### ML Engine Sponsor Stub

- implement deterministic sponsor inference logic
- return sponsor name, confidence, and bounding box or equivalent basic result shape
- keep the behavior stable and testable

#### Video Service

- implement `POST /api/video/upload-frame`
- keep payload handling simple and safe initially
- place size limits on payloads or move toward frame references if needed
- call the ML sponsor endpoint with resilience wrappers
- publish detection events to Kafka
- prepare for persistence if not fully completed in the same week

#### API Gateway

- add `onSponsorDetection(streamer)` subscription
- add `sponsorDetections(streamer, limit)` query
- bridge sponsor events from Kafka to GraphQL subscriptions

#### Frontend

- add sponsor dashboard surface
- show recent detections table
- show confidence trend or similar chart
- handle loading, empty, reconnect, and error states

### Learning Needed

- handling large payloads safely in HTTP APIs
- sponsor result schema design that is useful without overengineering

### Testing

- `video-service` controller validation tests
- ML sponsor endpoint tests
- event production tests
- GraphQL subscription tests for sponsor detections
- frontend rendering test for sponsor subscription updates

### Observability

- add metrics such as:
  - `streamsense_frames_ingested_total`
  - `streamsense_sponsor_detections_total{sponsor=...}`
  - sponsor inference latency
- add dashboard panels for sponsor rates, confidence, and fallback rate

### Demo Script

- upload a few frames for a streamer
- show sponsor detections appearing in the UI in real time
- stop `ml-engine` and show sponsor fallback behavior still emits events

### Definition Of Done

- video sponsor path works end to end with real-time UI and resilience behavior

### Risks And Pitfalls

- payload bloat from large frame uploads
- overcomplicating image handling before the event flow is stable

---

## Week 6 - Data Layer Hardening And Redis Cache

### Objectives

- Add Redis caching for hot reads.
- Harden persistence, migrations, and indexing.
- Make GraphQL history queries fast and service-owned.

### Build Deliverables

- Redis container integrated into Compose
- recent sentiment queries are cached in Redis with TTL
- recent sponsor detection queries are cached similarly
- video detections are persisted if not already done
- GraphQL history queries call service APIs, not Kafka

### Detailed Checklist

#### Redis

- add Redis to Compose
- choose serialization strategy for cache payloads
- define key naming and TTL policy

#### Sentiment Service Caching

- implement read-through cache for recent sentiment
- check Redis first
- fall back to Postgres
- populate cache after DB read

#### Video Service Caching

- implement the same pattern for sponsor history
- ensure cache invalidation or expiry strategy is documented and predictable

#### Persistence Hardening

- add or refine DB migrations for video detections
- validate all key indexes exist
- define retention or cleanup approach, even if the first version is simple

#### API Gateway Query Discipline

- ensure GraphQL historical queries call service APIs
- keep Kafka reserved for event streaming and live subscription fanout

### Learning Needed

- Spring Data Redis basics
- cache serialization tradeoffs
- simple retention strategy design

### Testing

- integration tests with Redis, Postgres, and service APIs
- cache hit and miss behavior tests
- contract tests for service JSON responses

### Observability

- add cache metrics such as:
  - `streamsense_cache_hits_total{cache=...}`
  - `streamsense_cache_misses_total{cache=...}`
- add latency comparison panels before and after cache hits

### Demo Script

- query recent sentiment twice
- show second query is faster and cache hit metrics increase

### Definition Of Done

- Redis is actively used for at least two endpoints
- metrics prove cache behavior

### Risks And Pitfalls

- cache stampede
- stale reads without a documented invalidation or TTL strategy

---

## Week 7 - API Gateway Maturity

### Objectives

- Make `api-gateway` behave like a real gateway.
- Add auth hooks and rate limiting.
- organize GraphQL in a production-shaped way.
- improve subscription reliability.

### Build Deliverables

- Spring Cloud Gateway routing is active and coherent
- auth structure exists with a development bypass mode
- rate limiting exists on ingest-facing routes
- GraphQL schema is modularized by domain area
- subscription reconnect and consumer group behavior are hardened

### Detailed Checklist

#### Routing

- define gateway routes centrally in configuration
- ensure service routing under consistent `/api/**` mappings
- keep route configuration understandable and not scattered

#### Auth Hook

- add JWT validation filter or hook
- support a development mode that allows bypass for local work
- document auth expectations and configuration

#### Rate Limiting

- add Redis-backed rate limiting if feasible
- otherwise add a clear rate limiter with observable behavior
- apply it to ingest endpoints and other abuse-prone surfaces

#### GraphQL Organization

- modularize GraphQL schema by chat, sentiment, video, and recommendation concerns
- keep the graph single if needed, but shape it so future federation would be clean
- document federation as a future enhancement rather than force it prematurely

#### Subscription Reliability

- ensure gateway consumer groups are stable
- understand and handle Kafka rebalance behavior
- make frontend WebSocket reconnect and retry behavior robust
- document subscription expectations under restart conditions

### Learning Needed

- Spring Cloud Gateway filters
- Redis rate limiting in the gateway stack
- GraphQL schema modularization patterns

### Testing

- gateway filter tests for auth and rate limiting
- GraphQL schema snapshot or compatibility tests
- subscription restart and reconnect smoke testing

### Observability

- add gateway metrics such as:
  - request totals by route
  - 4xx and 5xx counts
  - rate-limit rejections
- ensure gateway traces include route identifiers

### Demo Script

- show rate limiting by flooding an ingest endpoint
- show auth toggle behavior
- restart the gateway and show subscription clients recover

### Definition Of Done

- `api-gateway` has real routing, auth hooks, rate limiting, and reliable subscription behavior

### Risks And Pitfalls

- overcomplicating federation before the graph is stable
- auth work sprawling beyond a gateway hook into a full identity system too early

---

## Week 8 - Recommendation Service v1 And Experiment Wiring

### Objectives

- Implement `recommendation-service` with simple, explainable behavior.
- Wire experiments configuration through Config Server.
- Surface recommendations in the UI.

### Build Deliverables

- `recommendation-service` API exists
- recommendation output is deterministic enough to test and demo
- experiments config is served centrally
- gateway exposes recommendations through GraphQL
- frontend renders recommendations and reason fields

### Detailed Checklist

#### Recommendation Service

- add or finish the Spring Boot app
- define inputs such as:
  - streamer
  - recent sentiment distribution
  - recent sponsor detections
- define outputs as recommendation objects with reason fields
- keep the logic simple and explainable, not opaque

#### Experiment Configuration

- add `experiments.json` or equivalent YAML in the config repository
- serve it from Config Server
- let services read and cache it
- allow restart or manual refresh if full dynamic refresh is too much initially
- document clearly whether refresh is automatic or manual

#### API Gateway And Frontend

- add GraphQL query for recommendations
- add a frontend recommendation panel
- show how experiment changes can affect outputs

### Learning Needed

- Spring Cloud Config refresh patterns
- deterministic recommendation logic design for demos

### Testing

- unit tests for recommendation output determinism
- integration tests for recommendation inputs from recent signals
- tests for experiment-variant-driven behavior where practical

### Observability

- add metrics such as:
  - `streamsense_recommendations_served_total`
  - experiment variant counters
- add dashboard panels for recommendation latency and served rate

### Demo Script

- change experiment config
- restart or refresh the relevant service
- show recommendation behavior changes in the UI

### Definition Of Done

- recommendations appear in the UI and can change based on centralized config

### Risks And Pitfalls

- overengineering recommendation logic before the data path is stable
- promising live config refresh without fully implementing it

---

## Week 9 - Kubernetes v1 On Kind Or Minikube

### Objectives

- Produce Kubernetes manifests for local cluster deployment.
- Make local Kubernetes deployment real and documented.
- Keep the path to cloud-managed Kubernetes understandable.

### Build Deliverables

- `k8s/namespace.yaml` and service manifests for the core platform
- Deployments and Services for:
  - `eureka-server`
  - `config-server`
  - `api-gateway`
  - `chat-service`
  - `video-service`
  - `sentiment-service`
  - `recommendation-service`
  - `ml-engine`
  - `postgres`
  - `redis`
  - `prometheus`
  - `grafana`
  - `zipkin`
- Ingress for at least gateway, Grafana, and Zipkin
- local Kubernetes runbook

### Detailed Checklist

#### Cluster Target

- choose `kind` by default unless `minikube` proves significantly simpler
- document the choice and the reasons

#### Image Workflow

- create local image build workflow
- make image loading into `kind` repeatable
- document exact commands for local deployment

#### Config And Secrets

- convert the config repository into ConfigMaps or mounted files for Config Server native mode
- document which values are ConfigMaps and which should be Secrets later

#### Service Discovery

- ensure service discovery works under Kubernetes DNS assumptions
- document whether Eureka remains required in-cluster or whether the k8s networking story reduces its value
- keep the architecture consistent for the purposes of the repo even if production cloud choices would differ later

#### Monitoring In Cluster

- expose Prometheus scrape config for services in Kubernetes
- make Grafana provisioning work in-cluster
- ensure Zipkin receives spans in Kubernetes too

### Learning Needed

- `kind` networking and ingress basics
- Kubernetes ConfigMap and Secret patterns

### Testing

- `kubectl apply --dry-run=client -f k8s/`
- basic smoke validation that pods become ready
- verify gateway endpoint and one monitoring endpoint are reachable

### Observability

- ensure at least gateway and one backend service emit metrics and traces in-cluster

### Demo Script

- create local cluster
- apply manifests
- open gateway and Grafana
- ingest events and show traffic reflected in dashboards

### Definition Of Done

- full stack runs on `kind` or `minikube` with documented steps and reachable UI surfaces

### Risks And Pitfalls

- Config Server native mode file mounting issues
- trying to solve Kafka-on-Kubernetes in the same week before the app deployments are stable

---

## Week 10 - Kafka On Kubernetes And Consumer Scaling

### Objectives

- Deploy Kafka to Kubernetes.
- demonstrate partition-based scaling.
- make throughput and lag visible.

### Build Deliverables

- `k8s/kafka/` manifests or operator-backed definitions
- local Kafka cluster in Kubernetes
- topics with multiple partitions
- consumer scaling demonstration across replicas
- consumer lag metrics visible in Prometheus and Grafana

### Detailed Checklist

#### Kafka Approach

- choose Strimzi unless a strong reason exists not to
- document the choice as the lowest-ops-pain local Kubernetes path

#### Topics And Partitions

- define topics:
  - `stream.chat.messages`
  - `stream.sentiment.events`
  - `stream.sponsor.detections`
- choose a partition count suitable for local scaling demos
- key records in a way that preserves ordering where needed, such as by streamer

#### Service Config

- point in-cluster services at the Kubernetes Kafka service
- validate consumer group behavior under multiple replicas

#### Scaling Story

- scale at least one consuming service to multiple replicas
- verify partition assignments distribute correctly
- capture lag and throughput behavior before and after scaling

### Learning Needed

- Kafka consumer group rebalancing and partition ownership
- Strimzi or chosen Kafka-on-Kubernetes basics

### Testing

- keep local integration tests using Testcontainers Kafka even if Kubernetes Kafka is added
- add at least one partitioning correctness or ordering test based on key choice

### Observability

- add dashboard panels for:
  - consumer lag
  - messages per second per topic
  - rebalance counts if available

### Demo Script

- generate traffic
- scale a consumer deployment from one replica to multiple replicas
- show lag drops and throughput improves

### Definition Of Done

- Kafka runs in Kubernetes and the scaling story is demonstrable with metrics

### Risks And Pitfalls

- operator complexity distracting from the repository goal
- local cluster resource limits making scaling results noisy

---

## Week 11 - Testing, CI Hardening, And Load Testing Report

### Objectives

- Build a credible automated test suite.
- add repeatable load-generation tooling.
- produce an honest performance report.

### Build Deliverables

- GitHub Actions covers:
  - Java unit and integration tests
  - Python tests
  - frontend lint and tests
  - optional stack smoke test job
- load-generation tooling exists under `tools/`
- `docs/performance-report.md` exists and includes measured results

### Detailed Checklist

#### CI Hardening

- separate unit and integration concerns where useful
- use Testcontainers for Kafka, Postgres, and Redis integration coverage
- run integration tests on pull requests if runtime is acceptable
- run heavier smoke or compose tests on a schedule or manual trigger if needed

#### Automated Test Scope

- unit tests for service logic, fallbacks, and schema validation
- integration tests for:
  - chat -> sentiment pipeline
  - video -> sponsor pipeline
  - cache behavior
  - gateway GraphQL query and subscription flows
- contract validation for event schemas and GraphQL schema stability
- frontend tests for major live and historical views

#### Load Tooling

- add a Kafka or HTTP-based load generator
- allow configurable message rate and duration
- record produced rate, consumed rate, and end-to-end latency
- include event timestamps needed for persistence-latency analysis

#### Performance Report

- document test environment specs
- document achieved throughput and p95 latency
- document failure modes
- include references to dashboards or screenshots
- explicitly state whether README performance claims are measured, simulated, or aspirational

### Learning Needed

- Kafka producer tuning
- Testcontainers best practices
- honest latency measurement across asynchronous systems

### Testing

- build the actual test suite described above
- ensure CI results are reproducible and not dependent on manual local state

### Observability

- create a performance-focused dashboard with:
  - end-to-end latency p50 and p95
  - Kafka produce and consume rates
  - DB write latency
  - fallback and error rates under load

### Demo Script

- run load generation at multiple rates
- show dashboards moving
- show report excerpts with actual measured values
- stop `ml-engine` under load and show graceful degradation

### Definition Of Done

- reproducible performance run exists and is documented
- CI covers the repository credibly

### Risks And Pitfalls

- local machines may not reach ambitious throughput targets
- integration tests may become flaky without strict environment control

---

## Week 12 - Polish And Production-Style Demo Packaging

### Objectives

- Make the full system easy to run and explain.
- finalize dashboards, traces, runbooks, and troubleshooting material.
- package the demo so a new machine can reproduce it quickly.

### Build Deliverables

- `docker-compose up -d` runs the stack with one command
- Kubernetes deployment is documented and repeatable
- docs are complete enough for a new contributor
- demo tooling exists for seeding and opening key endpoints
- optional Cassandra profile is documented without becoming a blocker

### Detailed Checklist

#### Compose Polish

- add healthchecks broadly
- use `depends_on` readiness conditions where they genuinely help
- remove assumptions that require prebuilt jars if possible by using better Docker builds or one canonical build step
- add profiles such as default and `with-cassandra`
- ensure one command starts the full demo stack

#### Demo Tooling

- add scripts such as:
  - `tools/demo/seed.sh`
  - `tools/demo/open.sh`
- seed sample chats and frames
- print URLs and endpoints needed for the demo

#### Frontend Packaging

- either run frontend in Docker cleanly or document a separate local dev path clearly
- prefer a production-shaped frontend container for the final demo if feasible

#### Observability Polish

- provision Grafana dashboards automatically
- document representative Zipkin traces
- ensure dashboard names and panels are stable enough for demos

#### Troubleshooting And Runbooks

- complete local runbook
- complete Kubernetes local runbook
- add troubleshooting for:
  - Kafka issues
  - GraphQL subscription issues
  - Config Server issues
  - tracing issues
  - startup races

#### Optional Cassandra Plan

- do not migrate core flows to Cassandra unless the project is ahead of schedule
- if included, make it a clearly optional storage backend with:
  - Docker Compose profile
  - Kubernetes manifests or notes
  - documentation of intended data model, such as time-series or wide-row storage by streamer
- keep it toggled off by default

### Learning Needed

- Grafana dashboard provisioning and export/import
- final demo packaging discipline

### Testing

- add a final smoke-e2e path that:
  - starts Compose
  - seeds data
  - runs assertions
  - tears down cleanly

### Observability

- final dashboard set should include:
  - system overview
  - Kafka rates and lag
  - ML latency and circuit breaker state
  - DB and cache latency and hit rate
- final tracing checklist should confirm an end-to-end trace exists for at least the chat -> sentiment path

### Demo Script

- run `make up`
- run the seed script
- open the UI and show:
  - live chat
  - sentiment chart
  - sponsor chart
  - recommendations
- open Grafana and show system overview and resilience panels
- open Zipkin and show a representative trace
- optionally run load testing and show the performance report

### Definition Of Done

- a new machine can follow the local runbook and reach the full demo in thirty minutes or less
- Kubernetes local deployment works from documented steps

### Risks And Pitfalls

- spending week 12 adding new features rather than freezing scope and polishing runnability

---

## Remaining Priority Ladder

The original P0 through P3 items are now mostly complete according to `opencodeCommandHistory/`. If sequencing pressure remains, use this updated order.

### P0 - Final Demo Runnability

- run `make smoke-e2e` against a real Docker daemon from a clean state
- fix any startup timing or smoke assertion issues found during that run
- record the final clean-state result in the docs or command history

### P1 - Demo Tooling And Documentation Polish

- complete optional Cassandra/out-of-scope documentation
- perform one final read-through of local and Kubernetes runbooks after the live smoke run
- update troubleshooting notes with any issues discovered during clean-state validation

### P2 - Observability And Resilience Evidence

- execute `docs/degraded-path-proof.md` and capture the representative evidence it lists
- verify Compose and Kubernetes Grafana dashboard provisioning during the final live demo run

### P3 - Performance And Optional Scope

- run the rate-limit-relaxed benchmark mode if downstream saturation numbers are needed
- update `docs/performance-report.md` with any new relaxed-mode measurements
- document optional Cassandra as explicitly non-blocking, add a profile only if time remains, or mark it out of scope
- document the Strimzi/cloud Kafka path as a future adaptation, since the local `kind` implementation intentionally uses the existing Confluent-based deployment

## Weekly Time Split Guidance

Suggested weekly allocation:

- Build: `65%`
- Learn: `15%`
- Debug and integration: `15%`
- Docs: `5%`

Exception:

- week 12 should allocate far more time to documentation, runbooks, and demo packaging

## Key Risks And Mitigations

### GraphQL Subscription Complexity

Risk:

- subscription protocol or implementation mismatch can waste a large amount of time

Mitigation:

- lock protocol choice in week 2
- build the Kafka -> subscription bridge once
- reuse the same pattern for chat, sentiment, and sponsor subscriptions

### Kafka On Kubernetes Complexity

Risk:

- Kafka operations can dominate the schedule

Mitigation:

- local Kubernetes now uses the existing Confluent-based Kafka deployment because it already works for the `kind` demo and avoids Strimzi overhead
- keep Strimzi documented as the likely future path for cloud-managed Kubernetes rather than migrating the local demo unnecessarily

### ML Realism Creep

Risk:

- spending too much time on model sophistication before the platform is stable

Mitigation:

- keep deterministic stubs first
- harden contracts and runtime behavior before improving model realism

### Observability Drift

Risk:

- metrics and traces get postponed until too late

Mitigation:

- add observability checklists every week
- provision dashboards instead of relying on manual clicks at the end

### Scope Creep

Risk:

- adding more features than the repository description requires

Mitigation:

- no new major services beyond the existing set
- keep Cassandra optional
- preserve focus on one fully explainable platform rather than many partial ones

### Performance Claim Inflation

Risk:

- overstating throughput based on architecture intent rather than measured evidence

Mitigation:

- publish measured numbers only
- clearly label design targets versus achieved local results

## Final Success Criteria Status

According to `opencodeCommandHistory/`, these criteria are implemented or substantially proven:

- Eureka and Config Server work predictably in local development
- Kafka topics exist and all core event pipelines function
- `chat-service` only ingests and publishes chat events
- `sentiment-service` owns sentiment inference, persistence, and historical query behavior
- `video-service` owns sponsor inference flow and historical query behavior
- `recommendation-service` returns explainable recommendation results
- `ml-engine` exposes stable sentiment and sponsor endpoints with deterministic behavior and validation tests
- GraphQL gateway exposes working queries and subscriptions for chat, sentiment, sponsor, and recommendations where planned
- frontend displays live and historical analytics for chat, sentiment, sponsor detections, and recommendations
- Postgres schema is created automatically with migrations
- Redis is used for hot historical queries and that usage is visible in metrics
- failures are retried, dead-lettered, or fall back in explicit ways rather than being silently dropped
- Prometheus, Grafana, and Zipkin provide operational visibility across the main services
- CI runs useful automated tests across Java, Python, and frontend code
- load testing can be executed and first measured results are documented honestly
- `k8s/` manifests deploy the platform to local `kind` with documented steps

These criteria still need final closure or stronger proof:

- the canonical full-stack local start path should be verified by running `make smoke-e2e` from a clean state
- degraded-path observability should be captured by executing `docs/degraded-path-proof.md`
- frontend fallback behavior should have final browser-level evidence or documented manual verification from the degraded-path proof run
- rate-limit-relaxed benchmark results should be added if downstream saturation numbers are desired
- the new-machine reproduction target should be tested and recorded

## Final Execution Summary

If only the shortest possible summary of what remains is needed, it is this:

1. Freeze feature scope; the main platform, data paths, gateway, Kubernetes, CI, and load-reporting work are already implemented according to the command history.
2. Run the final `make smoke-e2e` path against Docker from a clean state and fix any issues found.
3. Execute the degraded-path proof runbook and record Zipkin, Grafana/Prometheus, and frontend evidence.
4. Run the rate-limit-relaxed benchmark mode only if deeper downstream measurements are needed.
5. Document optional Cassandra and cloud/Strimzi Kafka as future/non-blocking paths unless there is time to implement them safely.
6. Validate and record the new-machine reproduction experience.

That is the remaining scope required to turn the implemented platform into a final production-shaped demo package.
