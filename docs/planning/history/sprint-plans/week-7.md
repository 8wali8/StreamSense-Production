# Sprint 7 Implementation Plan

## Goal

Make `api-gateway` behave like a real gateway instead of only a GraphQL entry point.

Sprint 7 should harden the platform edge with:

- centralized routing
- auth hooks with a safe local bypass mode
- rate limiting on abuse-prone endpoints
- more production-shaped GraphQL organization
- more reliable subscriptions across restarts and reconnects

The target runtime shape for this sprint is:

`client -> api-gateway routing, auth, rate limiting, GraphQL query or subscription -> downstream services or Kafka-backed subscription fanout`

Sprint 7 is not about building a full identity platform. It is about making the gateway operationally credible while preserving the service ownership rules established in earlier sprints.

## Sprint 7 Success Criteria

Sprint 7 is complete only when all of the following are true:

- `api-gateway` uses centrally defined Spring Cloud Gateway routes for the main `/api/**` service paths.
- an auth filter or hook exists and can validate JWT-shaped requests when enabled.
- a development bypass mode exists for local Docker and normal sprint verification.
- rate limiting is enforced on ingest-facing routes and exposes observable rejection behavior.
- the GraphQL schema is organized by domain area without changing the single-graph contract unexpectedly.
- subscription behavior is more reliable across gateway restarts, Kafka consumer rebalances, and frontend reconnects.
- gateway metrics expose route-level traffic, error counts, and rate-limit rejections.
- automated tests cover auth, rate limiting, GraphQL contract stability, and reconnect-oriented subscription behavior.
- the Sprint 7 path is verified live in Docker.

## Current Starting Point

This plan assumes the repo state after Sprint 6 is:

- `api-gateway` already exposes working GraphQL queries and subscriptions.
- historical GraphQL reads already stay service-owned and call downstream service APIs.
- Kafka already exists for live event transport and subscription fanout.
- Redis already exists in Docker Compose and can be reused if gateway rate limiting needs shared state.
- Docker Compose, Prometheus, and Grafana already exist but gateway maturity is still incomplete.
- auth is not yet a real gateway concern.
- route organization and subscription restart behavior still need production hardening.

The largest Sprint 7 gap is not feature surface area. It is that the gateway still needs stronger edge behavior, clearer operational policy, and better failure handling.

## Important Architecture Note

Sprint 7 must strengthen the gateway without breaking service ownership discipline:

- `api-gateway` owns routing, auth hooks, rate limiting, GraphQL access, and cross-cutting edge concerns
- domain services continue to own business logic and history APIs
- Kafka remains the event backbone for live subscriptions, not a history store
- GraphQL history queries should keep calling service-owned APIs rather than moving data ownership into the gateway

That means:

- do not turn the gateway into a second domain service
- do not move historical query state into gateway-local caches
- do not let auth work expand into a full user-account system in this sprint
- do not force GraphQL federation before the schema and service contracts are stable

## Scope Decisions For Sprint 7

To keep this sprint tight and production-shaped, use the following defaults unless implementation reality forces a small adjustment:

### Routing

Use Spring Cloud Gateway route definitions as the source of truth for HTTP service routing:

1. define routes centrally in configuration
2. keep downstream service mappings under consistent `/api/**` paths
3. keep route filters explicit and easy to inspect

### Auth Strategy

Use a gateway auth hook that validates JWT-shaped bearer tokens when enabled:

- support a development bypass flag for local work
- fail closed when auth is enabled and tokens are missing or invalid
- keep the implementation small and focused on gateway enforcement
- document accepted token assumptions clearly

If full JWT signature validation is too large for this sprint, implement a clearly documented hook structure with deterministic validation behavior suitable for local and CI testing.

### Rate Limiting Strategy

Prefer Redis-backed gateway rate limiting if the existing stack supports it cleanly:

- apply it to ingest-facing routes first
- expose rejection counts and HTTP status behavior clearly
- keep limits configurable from Config Server

If Redis-backed gateway rate limiting is too brittle in the current stack, use a simpler observable limiter and document the tradeoff explicitly.

### GraphQL Organization

Keep one GraphQL API surface, but organize it by domain:

- chat
- sentiment
- video
- recommendation

Modularize schema files and related resolver structure so the graph is easier to evolve and future federation remains possible without forcing it now.

### Subscription Reliability

Harden the current subscription path with practical reliability improvements:

- stable Kafka consumer group configuration
- explicit handling for restart and rebalance conditions
- replay or buffering behavior only where it already fits the current architecture
- stronger frontend reconnect behavior and clearer expectations during transient restarts

## Sprint 7 Deliverables

### 1. Gateway Route Maturity

- define service routes centrally in `api-gateway` configuration
- ensure route naming and path layout are coherent
- keep route behavior testable and visible in metrics

### 2. Auth Hook And Local Bypass

- add a gateway auth filter or equivalent hook
- validate bearer-token presence and token structure when enabled
- support a documented local bypass mode for Docker and developer workflows
- document configuration and expected request behavior

### 3. Rate Limiting

- add rate limiting to ingest-facing routes
- use Redis-backed rate limiting if practical with the current stack
- make rejection responses and metrics explicit
- keep rate limits centrally configurable

### 4. GraphQL Reorganization

- split the schema into domain-oriented files or modules
- keep the existing public graph behavior stable unless a change is required and documented
- add schema protection tests or snapshots

### 5. Subscription Reliability Hardening

- review gateway Kafka consumer settings and subscription fanout behavior
- make restart and reconnect behavior more predictable
- improve frontend WebSocket reconnect and retry handling if needed
- document expected behavior when the gateway restarts or rebalances

### 6. Observability, Testing, And Verification

- add gateway metrics for route traffic, errors, and rate limiting
- ensure traces include route identifiers when possible
- add targeted gateway and frontend tests
- verify auth, rate limiting, routing, and subscriptions live in Docker

## Required Scope Breakdown

## Phase 1 - Freeze Gateway Boundaries And Runtime Rules

1. Confirm Sprint 7 scope is limited to gateway maturity rather than full identity or federation work.
2. Freeze the rule that historical reads remain service-owned.
3. Define the initial auth-enabled and auth-bypass modes.
4. Define which routes receive rate limiting first.
5. Define the target GraphQL module layout.
6. Define expected subscription behavior during restart and reconnect scenarios.

## Phase 2 - Centralize Routing In `api-gateway`

1. Review the current gateway route and controller setup.
2. Move or confirm HTTP route definitions in centralized configuration.
3. Ensure `/api/**` mappings remain coherent for downstream services.
4. Add route tests for the main service paths.

### Expected end state

- gateway HTTP routing is explicit, centralized, and operationally understandable
- downstream service paths remain consistent for local Docker and future Kubernetes ingress work

## Phase 3 - Add Auth Hook And Development Bypass

1. Add an auth filter or hook at the gateway edge.
2. Validate bearer-token presence and token shape when auth is enabled.
3. Add a development bypass mode through config.
4. Return clear unauthorized responses and metrics when requests fail validation.
5. Document how local Docker runs with auth bypass versus auth enabled.

### Expected end state

- gateway has a real auth enforcement seam
- local development remains usable without inventing a full auth system

## Phase 4 - Add Rate Limiting To Ingest-Facing Routes

1. Identify the initial protected routes, especially chat and video ingest paths.
2. Wire Redis-backed or equivalent rate limiting into gateway filters.
3. Make rejection responses deterministic and easy to test.
4. Add metrics for allowed versus rejected requests.
5. Keep limits centrally configurable.

### Expected end state

- obvious abuse-prone routes are protected
- the gateway exposes measurable rate-limit behavior rather than hidden throttling

## Phase 5 - Reorganize GraphQL Surface

1. Split the GraphQL schema by domain area.
2. Keep resolver ownership aligned with the same domain boundaries.
3. Add schema snapshot or compatibility tests.
4. Avoid unnecessary public contract changes.

### Expected end state

- the graph remains single and understandable
- schema growth is easier to manage as recommendation work arrives in Sprint 8

## Phase 6 - Harden Subscription Reliability

1. Review current Kafka consumer group configuration in `api-gateway`.
2. Reduce avoidable subscription loss during restarts or rebalances.
3. Improve reconnect behavior in the frontend subscription client if needed.
4. Add smoke coverage for gateway restart and client recovery.
5. Document restart-condition expectations.

### Expected end state

- subscription clients recover more predictably after transient gateway interruptions
- restart and rebalance behavior is understood instead of accidental

## Phase 7 - Observability, Docker Verification, And Documentation

1. Add gateway metrics for:
   - request totals by route
   - 4xx and 5xx counts
   - rate-limit rejections
2. Ensure trace tags or route identifiers appear where feasible.
3. Run targeted gateway and frontend tests.
4. Verify live in Docker:
   - normal routed requests
   - auth bypass mode
   - auth-enabled rejection behavior
   - rate-limit rejection behavior
   - subscription reconnect after gateway restart
5. Record completed work in `opencodeCommandHistory/`.

## Definition Of Done

Sprint 7 is complete when:

- `api-gateway` has centralized routing, auth hooks, rate limiting, and better subscription reliability
- historical GraphQL reads still flow through service-owned APIs
- GraphQL schema organization is cleaner without destabilizing the public contract
- gateway metrics and traces make edge behavior visible
- the sprint plan, docs, and command history match the actual implementation

## Risks To Watch

- turning auth hooks into a larger identity project than Sprint 7 requires
- introducing route complexity that makes local debugging harder instead of easier
- adding rate limiting that is not observable or is too brittle in Docker
- reorganizing GraphQL in a way that creates avoidable client breakage
- claiming subscription reliability improvements without actually testing restart behavior
