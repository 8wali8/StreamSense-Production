# Sprint 8 Implementation Plan

## Goal

Implement `recommendation-service` v1 as a small, explainable service that turns recent platform signals into user-visible recommendations.

Sprint 8 should add:

- a real recommendation API in `recommendation-service`
- centrally managed experiment configuration from Config Server
- deterministic recommendation behavior that is easy to test and demo
- GraphQL recommendation access through `api-gateway`
- a frontend recommendation panel that shows recommendation reasons, not opaque scores alone

The target runtime shape for this sprint is:

`client -> api-gateway GraphQL recommendation query -> recommendation-service -> recent sentiment history + recent sponsor history + experiment config -> recommendation response`

Sprint 8 is not about building a machine-learned recommendation engine. It is about creating a production-shaped recommendation service with clear inputs, explainable outputs, and centralized experiment wiring.

## Sprint 8 Success Criteria

Sprint 8 is complete only when all of the following are true:

- `recommendation-service` exposes a working HTTP API for recommendations.
- recommendation results are deterministic for the same inputs and experiment variant.
- recommendations are derived from recent platform signals rather than hardcoded per-streamer constants.
- experiment configuration is served centrally from Config Server and consumed by `recommendation-service`.
- the config refresh story is documented honestly as restart-based or refresh-based.
- `api-gateway` exposes recommendations through GraphQL without taking over recommendation logic.
- the frontend renders recommendations with visible reason fields and variant metadata.
- automated tests cover recommendation determinism, signal aggregation, experiment-driven behavior, and GraphQL/frontend integration.
- the full Sprint 8 path is verified live in Docker.

## Current Starting Point

This plan assumes the repo state after Sprint 7 is:

- `sentiment-service` already exposes recent sentiment history through a service-owned REST API.
- `video-service` already exposes recent sponsor history through a service-owned REST API.
- those history paths are already cached and verified in Docker.
- `api-gateway` is now mature enough to expose additional GraphQL queries cleanly.
- `frontend` already has working GraphQL and subscription plumbing.
- `recommendation-service` exists only as a Spring Boot skeleton with baseline config and a boot test.
- there is no current recommendation API, recommendation GraphQL query, or frontend recommendation surface.
- there is no current experiment config model beyond the existing Config Server structure.

The biggest Sprint 8 gap is not infrastructure. It is missing product logic: there is no service yet that turns the existing sentiment and sponsor signals into a coherent recommendation output.

## Important Architecture Note

Sprint 8 must preserve service boundaries:

- `recommendation-service` owns recommendation generation logic
- `sentiment-service` continues to own sentiment history
- `video-service` continues to own sponsor detection history
- `api-gateway` owns GraphQL exposure and cross-cutting access concerns
- Config Server remains the source of centralized experiment configuration

That means:

- do not move recommendation logic into `api-gateway`
- do not let `recommendation-service` read sentiment or sponsor tables directly from Postgres
- do not build a hidden second query model inside Kafka for recommendation history in this sprint
- do not overpromise live dynamic config refresh if the implementation is restart-based

The preferred v1 data access path is:

- `recommendation-service` calls service-owned history APIs for recent signals
- `recommendation-service` combines them with centrally loaded experiment configuration
- `api-gateway` calls the recommendation API for GraphQL query resolution

## Scope Decisions For Sprint 8

To keep this sprint tight, explainable, and demo-friendly, use the following defaults unless implementation reality forces a small adjustment:

### Recommendation Strategy

Use deterministic rule-based recommendations rather than opaque ranking logic.

Each recommendation should be explainable from recent signals such as:

- sentiment trend or dominant label
- sponsor detection frequency or confidence
- streamer-level recent activity shape
- experiment variant weights or thresholds

Outputs should include both a recommendation and why it was produced.

### Input Strategy

Prefer synchronous reads from existing service-owned APIs:

1. fetch recent sentiment history from `sentiment-service`
2. fetch recent sponsor history from `video-service`
3. apply an experiment-configured policy in `recommendation-service`
4. return a deterministic recommendation list

This keeps the first version easy to reason about and avoids premature event-driven complexity.

### Experiment Configuration Strategy

Prefer YAML-based experiment configuration inside the Config Server repo rather than introducing a separate JSON loading path unless there is a concrete need.

The config should define:

- experiment name
- variant id
- enabled state
- thresholds or weights used by the recommendation rules
- optional fallback defaults

If live refresh is not already cleanly supported, document restart-based refresh as the supported mode for Sprint 8.

### Recommendation API Shape

Keep the API small and explicit.

Recommended v1 request shape:

- streamer
- optional limit or maxResults

Recommended v1 response shape per recommendation:

- recommendation id
- streamer
- title or label
- category or type
- score
- reason summary
- detailed reasons list
- experiment name
- variant id
- generated timestamp

### Frontend Behavior

The recommendation panel should emphasize interpretability:

- show recommendation title and score
- show reason fields clearly
- show active experiment variant
- handle empty, loading, and error states honestly

Avoid making the panel look like a fake ML ranking screen with unexplained numbers.

## Sprint 8 Deliverables

### 1. Recommendation Service API

- finish the `recommendation-service` Spring Boot app beyond skeleton state
- add a recommendation endpoint
- define clear input and output contracts
- keep logic deterministic and explainable

### 2. Signal Aggregation From Existing Services

- add clients from `recommendation-service` to `sentiment-service` and `video-service`
- fetch recent sentiment and sponsor history through service-owned APIs
- aggregate those signals into a recommendation input model

### 3. Experiment Configuration Wiring

- add centralized recommendation experiment config in `config-server/config-repo/`
- bind config into `recommendation-service`
- document how experiment changes take effect

### 4. GraphQL Exposure Through `api-gateway`

- add a recommendation query in GraphQL
- keep resolver logic thin and service-oriented
- add schema coverage for recommendation contract evolution

### 5. Frontend Recommendation Panel

- add a UI surface for recommendations
- show recommendations, reasons, and variant details
- preserve the existing visual language and dashboard structure

### 6. Observability, Testing, And Verification

- add recommendation metrics and traces
- add unit, integration, GraphQL, and frontend tests
- verify the recommendation flow live in Docker

## Required Scope Breakdown

## Phase 1 - Freeze Recommendation Contract And Ownership

1. Define the v1 recommendation request and response shapes.
2. Freeze the rule that `recommendation-service` reads recent signals from service-owned APIs rather than direct database access.
3. Define the initial recommendation categories and reason model.
4. Define the initial experiment config structure and refresh story.
5. Decide the GraphQL query name and return contract.

### Expected end state

- Sprint 8 has a stable and explainable contract before implementation starts
- service ownership is clear and not diluted by convenience shortcuts

## Phase 2 - Build `recommendation-service` Core API

1. Finish baseline app wiring in `recommendation-service`.
2. Add configuration properties for downstream services and experiment config.
3. Add clients for recent sentiment and recent sponsor history.
4. Add an application service that converts recent signals into recommendation outputs.
5. Expose a recommendation REST endpoint.

### Expected end state

- `recommendation-service` is a real domain service instead of a skeleton
- recommendation output is deterministic and driven by current platform signals

## Phase 3 - Add Experiment Configuration Through Config Server

1. Add recommendation experiment config to `config-server/config-repo/recommendation-service.yml` or equivalent shared config.
2. Bind the config into `recommendation-service` properties.
3. Support at least one non-default experiment variant.
4. Document whether changes require restart or can be refreshed live.

### Expected end state

- centralized config influences recommendation behavior
- experiment behavior is transparent and testable rather than hidden in code constants

## Phase 4 - Add Gateway GraphQL Recommendation Access

1. Add a recommendation query to the GraphQL schema.
2. Add a recommendation client in `api-gateway`.
3. Implement a thin GraphQL resolver that calls `recommendation-service`.
4. Keep recommendation composition logic inside the owning service.

### Expected end state

- the graph exposes recommendation results cleanly
- `api-gateway` remains an access layer rather than a recommendation engine

## Phase 5 - Add Frontend Recommendation Surface

1. Add GraphQL query documents for recommendations.
2. Add a recommendation panel or dashboard section in the frontend.
3. Show title, score, reason fields, and experiment variant information.
4. Add clear loading, empty, error, and stale-state handling.

### Expected end state

- recommendations are visible in the UI and understandable to a demo audience
- experiment-driven behavior can be shown without reading backend logs

## Phase 6 - Add Exhaustive Verification And Observability

1. Add recommendation unit tests for deterministic outputs and reason generation.
2. Add integration tests for signal aggregation from recent sentiment and sponsor history.
3. Add experiment-variant tests proving config changes affect outputs predictably.
4. Add GraphQL query tests for recommendation responses.
5. Add frontend tests for recommendation rendering and state handling.
6. Add metrics for:
   - recommendations served
   - recommendation latency
   - experiment variant counts
7. Verify live in Docker:
   - recommendation endpoint behavior
   - GraphQL recommendation query
   - frontend recommendation panel
   - config-driven output change after restart or refresh
8. Record completed work in `opencodeCommandHistory/`.

### Expected end state

- Sprint 8 is supported by real verification rather than only demo optimism
- recommendation behavior is measurable and explainable in production-shaped tooling

## Recommended Contracts

### Recommendation REST Endpoint

Recommended starting shape:

- `GET /api/recommendations?streamer={streamer}&limit={limit}`

Alternative if the team prefers explicit request bodies later:

- `POST /api/recommendations/query`

For Sprint 8, prefer the simpler `GET` shape unless request complexity grows materially.

### GraphQL Query

Recommended starting shape:

- `recommendations(streamer: String!, limit: Int!): [Recommendation!]!`

### Recommendation Object

Recommended fields:

- `recommendationId`
- `streamer`
- `title`
- `category`
- `score`
- `reasonSummary`
- `reasons`
- `experimentName`
- `variantId`
- `generatedAt`

## Suggested Recommendation Categories

Keep categories limited and explainable in v1, for example:

- `CONTENT_MOMENTUM`
- `SPONSOR_ALIGNMENT`
- `AUDIENCE_TONE`
- `CAUTION_SIGNAL`

The exact names can change, but the set should stay small enough for the frontend and tests to remain clear.

## Suggested Rule Inputs

Useful first-version derived inputs include:

- positive versus negative sentiment counts in the recent window
- dominant sentiment label
- average sentiment score in the recent window
- sponsor detection count in the recent window
- top sponsor by frequency or confidence
- whether recent data is sparse enough to return a low-confidence recommendation

Do not overfit rules beyond what the current data model can support honestly.

## Definition Of Done

Sprint 8 is complete when:

- `recommendation-service` owns and serves deterministic recommendations
- experiment config is centralized and actually affects recommendation output
- `api-gateway` exposes recommendation data through GraphQL without taking over service logic
- the frontend shows recommendations with visible reasons and variant information
- tests, docs, and Docker verification all match the implemented behavior

## Risks To Watch

- overengineering recommendation logic before a simple explainable version is proven
- letting the gateway absorb recommendation logic because it already owns GraphQL
- introducing direct database coupling that bypasses service-owned history APIs
- claiming hot config refresh without implementing or verifying it
- producing recommendation scores that look precise but are not meaningfully explained
