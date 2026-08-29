# Week 3 Implementation Plan

## Goal

Deliver the first real sentiment vertical slice for the repository:

`chat ingest -> Kafka chat event -> sentiment-service -> ml-engine sentiment inference -> Postgres persistence -> GraphQL query/subscription -> frontend sentiment view`

This week is where the repository stops being primarily a live chat demo and becomes a real analytics system.

## Week 3 Success Criteria

Week 3 is complete only when all of the following are true:

- `chat-service` only accepts chat ingestion requests and publishes `stream.chat.messages`.
- `sentiment-service` consumes `stream.chat.messages`.
- `sentiment-service` calls `ml-engine` via `POST /ml/sentiment`.
- `sentiment-service` persists sentiment results in Postgres.
- database schema is created automatically using Flyway migrations.
- `sentiment-service` exposes a history API for recent sentiment by streamer.
- `api-gateway` exposes:
  - `recentSentiment(streamer, limit)`
  - `onSentiment(streamer)`
- frontend can:
  - query recent sentiment history
  - subscribe to live sentiment updates
  - render a meaningful sentiment panel, not just raw JSON
- at least one representative end-to-end trace can be shown in Zipkin.
- automated tests cover the new core flow with credible depth.

## Current Starting Point

This plan assumes the current repo state is:

- `chat-service` already supports `POST /api/chat/ingest`.
- `chat-service` publishes `stream.chat.messages`.
- `api-gateway` already exposes `health` and `onChatMessage(streamer)`.
- frontend already shows health and live chat.
- `ml-engine` already exposes:
  - `GET /ml/health`
  - `POST /ml/sentiment`
- `ml-engine` sentiment output is deterministic and tested.
- Postgres exists in Docker Compose.
- Config Server already contains per-service YAML files.

The largest Week 3 gap is architectural correctness:

- sentiment processing currently lives in `chat-service`
- `sentiment-service` is still almost empty
- persistence is not implemented
- GraphQL sentiment surfaces do not exist yet
- frontend sentiment UI does not exist yet

## Non-Negotiable Architecture Decision For Week 3

Lock this now and do not leave it ambiguous:

- `chat-service` owns ingestion and publishing of chat events only
- `sentiment-service` owns all sentiment work downstream of the chat topic

That means:

- `chat-service` must not remain responsible for:
  - sentiment inference
  - sentiment event creation
  - sentiment event publishing
  - sentiment persistence
- `sentiment-service` must become responsible for:
  - consuming `stream.chat.messages`
  - calling `ml-engine`
  - creating `SentimentAnalysisEvent`
  - persisting sentiment records
  - optionally publishing `stream.sentiment.events`
  - serving recent sentiment history

This service-boundary correction is not optional cleanup. It is part of Week 3 itself.

## Week 3 Deliverables

By the end of the week, the repository should contain all of the following implemented behavior.

### 1. Real `sentiment-service`

- Kafka consumer for `stream.chat.messages`
- ML client for `POST /ml/sentiment`
- persistence model for sentiment rows
- repository and service layer for writes and history reads
- recent sentiment REST API
- optional producer for `stream.sentiment.events`

### 2. Database Migration Baseline

- Flyway migration creating `sentiment_events`
- indexes supporting streamer history queries
- schema validation enabled so drift fails startup

### 3. GraphQL Sentiment Surface

- GraphQL query for recent sentiment history
- GraphQL subscription for live sentiment updates
- gateway bridge for live Kafka sentiment events

### 4. Frontend Sentiment UI

- sentiment history panel
- live sentiment update surface
- clear loading, empty, and error states
- simple chart or structured analytics presentation

### 5. Testing Coverage For The New Slice

- `sentiment-service` integration tests
- GraphQL query and subscription tests for sentiment
- frontend tests for sentiment rendering
- ML response shape validation where appropriate

### 6. Observability For The New Slice

- metrics for sentiment processing count and ML latency
- logs that make the flow understandable
- at least one trace showing the request path into the sentiment pipeline

## Required Scope Breakdown

The work should be executed in the following order.

---

## Phase 1 - Finalize Contracts And Data Model

This phase should happen before writing most of the implementation.

### Objectives

- make all input, output, event, and persistence shapes explicit
- prevent schema drift during service implementation

### Tasks

1. Freeze the `ChatMessageEvent` contract as the upstream input to `sentiment-service`.
2. Define the canonical `SentimentAnalysisEvent` shape used inside the sentiment pipeline and for optional downstream publication.
3. Define the persistence fields for `sentiment_events`.
4. Define the REST response shape for recent sentiment history.
5. Define the GraphQL `SentimentAnalysisEvent` type for query and subscription use.
6. Ensure docs and code use consistent field naming.

### Minimum Sentiment Data Contract

Persist at least these fields:

- `sentimentEventId`
- `sourceEventId`
- `streamer`
- `user`
- `message`
- `chatTimestamp`
- `processedAt`
- `label`
- `score`
- `modelVersion`

### Output Of This Phase

- one agreed event contract
- one agreed DB row shape
- one agreed history API response shape
- one agreed GraphQL shape

### Risks

- coding first and discovering later that GraphQL, DB, and Kafka representations drift from each other

---

## Phase 2 - Make `chat-service` Ingest-Only

This phase removes the current architectural overlap.

### Objectives

- stop `chat-service` from owning sentiment logic
- keep `chat-service` small, clear, and stable

### Tasks

1. Identify all current sentiment-related logic in `chat-service`.
2. Move or re-implement ML client logic in `sentiment-service`.
3. Move or re-implement sentiment-event creation in `sentiment-service`.
4. Remove sentiment publishing responsibilities from `chat-service`.
5. Keep only:
   - request validation
   - event ID creation
   - Kafka publish of `stream.chat.messages`
   - ingest metrics
6. Ensure Week 2 behavior remains intact after the cleanup.

### Expected End State For `chat-service`

- `POST /api/chat/ingest` remains
- chat events continue to publish to Kafka
- logging and ingest metrics remain
- no sentiment inference happens inside the service
- no sentiment topic publishing happens inside the service

### Risks

- temporarily breaking chat ingest while moving downstream logic
- leaving duplicate implementations in two services and creating confusion

---

## Phase 3 - Build The Core Of `sentiment-service`

This is the main implementation phase.

### Objectives

- turn `sentiment-service` from a bootstrap app into a real pipeline component

### Tasks

#### Dependencies And Configuration

1. Add required dependencies if missing:
   - `spring-kafka`
   - `spring-boot-starter-data-jpa`
   - `flyway-core`
   - PostgreSQL driver
   - validation support if needed
2. Ensure config-server properties support:
   - Kafka consumer configuration
   - datasource configuration
   - JPA validation mode
   - Flyway migration location
   - `ml-engine` base URL
3. Ensure health and Prometheus exposure remain enabled.

#### Kafka Consumer

1. Implement a consumer for `stream.chat.messages`.
2. Deserialize `ChatMessageEvent` cleanly.
3. Log enough context to debug the flow without noisy dumping.
4. Preserve correlation and tracing headers where possible.

#### ML Client

1. Implement a dedicated sentiment ML client in `sentiment-service`.
2. Call `POST /ml/sentiment` with the correct request shape.
3. Validate the response shape before continuing.
4. Add explicit timeout behavior rather than relying on indefinite defaults.
5. Surface failures clearly in logs.

#### Sentiment Record Creation

1. Convert a `ChatMessageEvent` and ML response into a full sentiment domain object.
2. Generate `sentimentEventId` deterministically enough for traceability or at least uniquely.
3. Stamp `processedAt` when sentiment work completes.
4. Keep the persisted representation and event representation aligned.

#### Persistence

1. Create an entity or persistence model for `sentiment_events`.
2. Add repository support for writes.
3. Add repository support for recent-by-streamer reads.
4. Sort history descending by `chatTimestamp`.
5. Support a caller-supplied `limit` with sensible validation.

#### Service Layer

1. Add a service layer rather than putting all logic directly in the Kafka listener.
2. Keep orchestration clear:
   - consume event
   - call ML
   - build sentiment record
   - persist
   - optionally publish live event
3. Keep the orchestration small enough to read easily.

#### Optional Live Event Publication

1. Decide whether `stream.sentiment.events` remains the live-fanout topic.
2. If yes, publish to it only after persistence succeeds.
3. Keep event contents aligned with the persisted record.

### Output Of This Phase

- one chat event flowing through `sentiment-service` causes one sentiment row to be written to Postgres

### Risks

- putting too much logic in the Kafka listener
- mismatched DTOs between Java and Python
- publishing live sentiment before persistence and creating ordering inconsistencies

---

## Phase 4 - Add Flyway Migration And Persistence Baseline

This phase makes database startup reliable and repeatable.

### Objectives

- ensure clean environment startup works without manual schema creation

### Tasks

1. Add `V1__create_sentiment_events.sql`.
2. Create `sentiment_events` with the required fields.
3. Choose types carefully for:
   - IDs
   - timestamps
   - score
   - label
   - model version
4. Add an index on at least:
   - `(streamer, chat_timestamp DESC)`
5. Keep `ddl-auto=validate` so startup fails if schema drifts.
6. Verify Flyway runs on a clean Postgres instance.

### DB Considerations

- use a schema shape that supports query-by-streamer history efficiently
- do not over-normalize the first version
- optimize for read clarity and ingestion simplicity

### Output Of This Phase

- a clean DB boot produces the required table automatically

### Risks

- schema mismatch between migration and JPA model
- missing index making the history query path slow later

---

## Phase 5 - Expose History API From `sentiment-service`

This phase creates a service-owned query surface for historical sentiment.

### Objectives

- provide a clean boundary for recent sentiment queries
- keep historical reads service-owned rather than gateway-owned

### Tasks

1. Implement a REST endpoint similar to:
   - `GET /api/sentiment/recent?streamer=...&limit=...`
2. Validate required query inputs.
3. Return sentiment rows in descending recency order.
4. Decide whether the API returns domain objects directly or a response DTO.
5. Keep the response shape stable and documented.

### Required Behavior

- return only the requested streamer’s data
- cap or validate `limit`
- return an empty list rather than an error when no history exists

### Output Of This Phase

- gateway can call `sentiment-service` for recent sentiment history

### Risks

- leaking persistence implementation details into the external API shape

---

## Phase 6 - Extend `api-gateway` For Sentiment Query And Subscription

This phase connects the new sentiment source to GraphQL.

### Objectives

- expose historical and live sentiment through the gateway

### Tasks

#### GraphQL Schema

1. Add `SentimentAnalysisEvent` to the schema.
2. Add query:
   - `recentSentiment(streamer: String!, limit: Int!): [SentimentAnalysisEvent!]!`
3. Add subscription:
   - `onSentiment(streamer: String!): SentimentAnalysisEvent!`

#### Query Resolver

1. Add a gateway client for `sentiment-service` REST history reads.
2. Implement the query resolver using that client.
3. Handle service errors and empty responses sensibly.

#### Subscription Resolver And Event Bridge

1. Add a gateway Kafka consumer for `stream.sentiment.events` if live sentiment remains event-based.
2. Add a subscription bus for live sentiment events similar to the chat bus.
3. Filter subscription streams by streamer.
4. Ensure the event bridge is stable enough for real-time UI use.

### Output Of This Phase

- frontend can query history and subscribe to live sentiment through GraphQL only

### Risks

- gateway depending on DB directly instead of respecting service boundaries
- inconsistent event type shapes between gateway and `sentiment-service`

---

## Phase 7 - Add Frontend Sentiment UI

This phase makes the new backend capability visible to the user.

### Objectives

- turn sentiment data into an actual analytics surface

### Tasks

1. Add GraphQL query documents for `recentSentiment`.
2. Add GraphQL subscription documents for `onSentiment`.
3. Add a sentiment page or panel to the existing app.
4. Render a recent history list.
5. Render a simple chart or structured visual summary.
6. Make live updates append into the UI.
7. Handle:
   - loading state
   - empty state
   - service error state
   - subscription error state
8. Keep the live chat panel working while adding the new view.

### UI Requirements

- sentiment should not be hidden behind raw developer output
- recent and live sentiment should both be understandable
- the page should still be usable on smaller screens

### Output Of This Phase

- the frontend displays recent sentiment and updates live as new chat is processed

### Risks

- mixing historical query results and subscription results inconsistently
- weak state handling causing duplicate or out-of-order rows in the UI

---

## Phase 8 - Testing Strategy For Week 3

Week 3 is not complete without meaningful automated coverage.

### `ml-engine`

Keep and extend the current Python tests as needed.

Required:

- health endpoint test
- deterministic sentiment response test
- response shape validation test
- invalid payload test if not already present

### `sentiment-service`

Required integration tests:

1. consume chat event
2. call ML service
3. persist sentiment row
4. optionally publish `stream.sentiment.events`
5. recent sentiment history endpoint returns persisted results

Recommended environment:

- Testcontainers Postgres
- Kafka test support or Testcontainers Kafka
- stubbed or local test double for `ml-engine`

### `api-gateway`

Required tests:

1. `health` query still returns `ok`
2. `recentSentiment` query returns history from the service layer
3. `onSentiment` subscription receives live sentiment events

### Frontend

Required tests:

1. health component renders success
2. sentiment panel renders loading state
3. sentiment panel renders history results
4. sentiment panel handles empty state
5. sentiment panel handles GraphQL errors
6. live subscription event updates the UI

### Contract Tests

Add validation where practical so Java event shapes and Python API shapes do not drift silently.

### Exit Condition For Testing

The Week 3 flow should be testable without manual clicking being the only proof.

---

## Phase 9 - Observability Requirements For Week 3

Week 3 must add enough telemetry to make the new slice inspectable.

### Metrics

Add at least:

- sentiment processed count
- sentiment label count by label
- ML request latency for sentiment inference
- persistence success and failure visibility

Possible metric names:

- `streamsense_sentiment_events_total{label=...}`
- `streamsense_ml_sentiment_latency_ms`

### Logs

Ensure logs make it easy to answer:

- was a chat event consumed?
- was the ML call made?
- what label and score were returned?
- was persistence successful?
- was a live sentiment event published?

### Tracing

Verify at least one representative trace exists for:

- ingest request accepted by `chat-service`
- chat event processed downstream
- ML sentiment call
- persistence step if trace context reaches it

### Dashboard Expectations

If a full dashboard is too much for this week, at minimum make the metrics scrapeable and demonstrate them manually.

---

## Detailed Implementation Sequence

Execute Week 3 in this order.

1. Freeze contracts and DB row shape.
2. Add Flyway migration for `sentiment_events`.
3. Add missing `sentiment-service` dependencies and config.
4. Implement `sentiment-service` persistence model and repository.
5. Implement the ML client in `sentiment-service`.
6. Implement the Kafka consumer in `sentiment-service`.
7. Persist sentiment rows from consumed chat events.
8. Add optional `stream.sentiment.events` publication after persistence.
9. Remove sentiment-processing logic from `chat-service`.
10. Add recent sentiment REST API.
11. Extend gateway schema and resolvers.
12. Add gateway live sentiment subscription bridge.
13. Add frontend sentiment query and subscription support.
14. Build the sentiment panel and chart/list UI.
15. Add service integration tests.
16. Add GraphQL tests.
17. Add frontend tests.
18. Verify metrics and tracing.
19. Update docs for local run and verification.

Do not reverse this order casually. The gateway and frontend work should sit on top of a real service implementation, not a stub-only contract.

## Documentation Updates Required During Week 3

Do not defer documentation until after coding.

Update the following kinds of docs as the work lands:

- architecture docs where service ownership changes need to be reflected clearly
- runbook docs with new verification steps for sentiment history and live sentiment
- contracts docs with the final sentiment event and persistence shape
- troubleshooting notes for:
  - `ml-engine` unreachable
  - sentiment history query returns empty
  - subscription receives no live events
  - Flyway migration failures

## Manual Verification Checklist

At the end of Week 3, manually verify the slice in this order.

1. Start Compose with Kafka, Postgres, services, and frontend.
2. Confirm Flyway creates the sentiment schema.
3. Confirm `ml-engine` health endpoint is up.
4. Ingest multiple chat messages for a test streamer.
5. Confirm `sentiment-service` consumes chat events.
6. Confirm sentiment rows appear in Postgres.
7. Query recent sentiment through `sentiment-service` directly.
8. Query `recentSentiment` through GraphQL.
9. Subscribe to `onSentiment(streamer)`.
10. Ingest another message and confirm the UI updates live.
11. Check Prometheus metric presence.
12. Check Zipkin for at least one representative trace.

## Definition Of Done

Week 3 is done when all of the following are true at the same time:

- the service boundary is correct
- the sentiment pipeline is real, not implied
- Postgres persists sentiment results automatically from chat events
- GraphQL exposes both recent and live sentiment
- frontend renders both recent and live sentiment
- tests exist for the new path
- metrics and traces make the path observable
- docs describe how to run and verify the feature

## Things That Must Not Be Deferred Out Of Week 3

- moving sentiment logic out of `chat-service`
- adding the DB migration
- making `sentiment-service` actually persist data
- exposing recent sentiment through the gateway
- adding at least a basic frontend sentiment view
- adding automated tests for the new core path

If these are deferred, Week 3 is not complete even if the repo can still demo pieces of the flow.

## Risks And Mitigations

### Risk: `sentiment-service` grows too much ad hoc orchestration

Mitigation:

- keep listener, ML client, persistence model, and service orchestration separated cleanly

### Risk: JSON contract drift between Java and Python

Mitigation:

- freeze request and response shapes early
- add contract validation tests where possible

### Risk: GraphQL is built against a fake sentiment source

Mitigation:

- do not implement gateway sentiment features until `sentiment-service` history reads and optional live events are real

### Risk: persistence works but live subscriptions do not

Mitigation:

- treat history query and live subscription as separate acceptance criteria and verify both

### Risk: broad exception handling hides failures

Mitigation:

- do not swallow processing failures silently
- log enough context to identify failed source events
- prepare for Week 4 retry and dead-letter work without pretending it is already complete

### Risk: frontend duplicates live events or mixes ordering badly

Mitigation:

- deduplicate by event ID
- define clear merge behavior between recent history and live updates

## Stretch Goals Only If Core Week 3 Work Finishes Early

These are allowed only after the success criteria are met:

- richer sentiment charting
- better error-state visuals
- more contract tests
- stronger tracing through Kafka propagation
- preliminary retry or fallback scaffolding in preparation for Week 4

Do not let these stretch goals delay the core Week 3 deliverables.

## Week 3 Summary

This week should turn the repository from a live chat transport demo into the first production-shaped analytics slice.

The key outcomes are:

- correct service ownership
- real sentiment inference downstream of Kafka
- persisted sentiment history
- GraphQL query and live subscription support
- frontend sentiment analytics view
- tests and observability strong enough to trust the new flow

If those outcomes are delivered, Week 3 is successful.
