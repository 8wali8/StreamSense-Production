# StreamSense Repository Improvement Plan

## Goal

Stabilize the current repository around one complete, reliable vertical slice:

`chat ingest -> Kafka -> sentiment processing -> persistence -> GraphQL -> frontend`

Do that before expanding `video-service` and `recommendation-service`.

## Recommended Architecture Decision

Use this as the target architecture for the next round of work:

- `chat-service` owns chat ingestion only
- `sentiment-service` owns consuming chat events, calling `ml-engine`, persisting sentiment, and optionally publishing sentiment events
- `api-gateway` owns GraphQL query/subscription access
- `frontend` consumes the GraphQL API
- `ml-engine` remains an internal inference service

This is the cleanest path because it aligns service ownership with the repo docs and removes the current overlap between `chat-service` and `sentiment-service`.

## Current State Summary

What works now:
- `chat-service` accepts `POST /api/chat/ingest`
- `chat-service` publishes `stream.chat.messages`
- `api-gateway` consumes chat messages and exposes `onChatMessage(streamer)`
- `frontend` subscribes to live chat messages
- `ml-engine` exposes a working deterministic sentiment stub
- basic tests exist for chat ingest, chat Kafka publish, gateway subscription, and ML endpoints

What is incomplete or broken:
- `sentiment-service` is mostly empty
- `chat-service` currently contains sentiment-processing logic that belongs in `sentiment-service`
- GraphQL schema exposes `health`, but no resolver exists
- frontend health widget likely errors immediately
- there is no end-to-end sentiment persistence/query flow
- there are no Flyway migrations for the configured Postgres-backed sentiment service
- Docker/dev workflow is brittle and docs/config are partially out of sync

## Prioritization Method

Order below is based on:
- production value
- architectural importance
- risk reduction
- ease and speed of implementation

Priority labels:
- `P0`: do next
- `P1`: do immediately after P0
- `P2`: do after the core flow is stable
- `P3`: defer until the platform is coherent

Ease labels:
- `Easy`
- `Medium`
- `Hard`

## Priority Order

| Rank | Priority | Ease | Task |
| --- | --- | --- | --- |
| 1 | P0 | Easy | Fix GraphQL `health` mismatch |
| 2 | P0 | Medium | Lock service ownership for sentiment flow |
| 3 | P0 | Medium | Build the minimum real `sentiment-service` |
| 4 | P0 | Medium | Prevent silent message loss in sentiment processing |
| 5 | P0 | Easy | Add missing DB migrations and persistence baseline |
| 6 | P1 | Medium | Implement sentiment GraphQL API in `api-gateway` |
| 7 | P1 | Medium | Add frontend sentiment UI |
| 8 | P1 | Medium | Fix Docker startup/readiness and config drift |
| 9 | P1 | Medium | Expand automated tests around the real flow |
| 10 | P2 | Easy | Clean docs, ports, Makefile, and repo metadata |
| 11 | P2 | Medium | Harden `ml-engine` for non-demo use |
| 12 | P3 | Hard | Resume `video-service` and `recommendation-service` work |

---

## 1. Fix GraphQL `health` Mismatch

Priority: `P0`  
Ease: `Easy`

Problem:
- GraphQL schema defines `health`
- frontend queries `health`
- no resolver exists
- current gateway test expects failure instead of success

Why this comes first:
- very small change
- immediately removes a broken frontend behavior
- brings docs, schema, and app behavior back into sync

Exact next steps:
1. Add a `@QueryMapping` resolver in `api-gateway` that returns `"ok"` for `health`.
2. Update the existing GraphQL health test to assert success instead of GraphQL error.
3. Verify frontend `Health` component renders a healthy state instead of an error.

Touches:
- `api-gateway/src/main/resources/graphql/schema.graphqls`
- `api-gateway/src/main/java/com/streamsense/apigateway/graphql/*`
- `api-gateway/src/test/java/com/streamsense/apigateway/graphql/GraphqlHealthQueryTest.java`
- `frontend/src/components/Health.tsx`

Done when:
- `query { health }` returns `"ok"`
- frontend health widget shows `Health: ok`

---

## 2. Lock Service Ownership for Sentiment Flow

Priority: `P0`  
Ease: `Medium`

Problem:
- `chat-service` currently ingests chat and also performs sentiment processing
- `sentiment-service` exists in docs/config but does not own the flow
- ownership is unclear and future work will be messy until this is fixed

Recommendation:
- make `chat-service` ingest-only
- move sentiment consumer/ML/publish responsibilities into `sentiment-service`

Why this comes now:
- this is the most important architecture correction in the repo
- it prevents duplicated logic and unclear boundaries later

Exact next steps:
1. Decide that `sentiment-service` is the only service responsible for sentiment processing.
2. Keep `chat-service` responsible only for validating requests and publishing `stream.chat.messages`.
3. Move or re-implement the current `ChatMessageLogConsumer`, ML client, and sentiment-event creation inside `sentiment-service`.
4. Remove the sentiment-processing consumer from `chat-service` after `sentiment-service` passes tests.

Touches:
- `chat-service/src/main/java/com/streamsense/chatservice/consumer/ChatMessageLogConsumer.java`
- `chat-service/src/main/java/com/streamsense/chatservice/client/MlEngineClient.java`
- `chat-service/src/main/java/com/streamsense/chatservice/kafka/SentimentKafkaProducer.java`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/**`

Done when:
- `chat-service` publishes chat events only
- `sentiment-service` consumes chat events and owns downstream sentiment work

---

## 3. Build the Minimum Real `sentiment-service`

Priority: `P0`  
Ease: `Medium`

Problem:
- the service is configured for Kafka, Postgres, JPA, and Flyway
- the code currently contains only the application bootstrap class

Why this comes now:
- the repo cannot reach the planned end-to-end sentiment flow without it

Exact next steps:
1. Add missing dependencies to `sentiment-service`:
   - `spring-kafka`
   - `spring-boot-starter-data-jpa`
   - `flyway-core`
   - PostgreSQL driver
2. Add a `ChatMessageEvent` consumer for `stream.chat.messages`.
3. Add an ML client that calls `POST /ml/sentiment`.
4. Add a persistence model for `sentiment_events`.
5. Add a repository and service layer for writing sentiment rows.
6. Optionally publish `stream.sentiment.events` after persistence if live downstream fanout is needed.

Minimum data to persist:
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

Touches:
- `sentiment-service/pom.xml`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/**`
- `sentiment-service/src/main/resources/**`
- `config-server/config-repo/sentiment-service.yml`

Done when:
- sending a chat event causes one sentiment row to appear in Postgres

---

## 4. Prevent Silent Message Loss

Priority: `P0`  
Ease: `Medium`

Problem:
- current sentiment processing catches all exceptions and logs them
- failures can be treated as handled
- this risks silently losing sentiment work

Why this comes now:
- correctness is more important than new features
- silent loss is the highest-risk runtime bug in the current event pipeline

Exact next steps:
1. Remove broad swallow-and-log-only behavior from sentiment processing.
2. Configure Kafka listener error handling with retry behavior.
3. Add a dead-letter strategy for messages that fail repeatedly.
4. Add request timeouts and explicit failure handling for `ml-engine` calls.
5. Make failure states observable through logs and metrics.

Touches:
- current implementation reference:
  - `chat-service/src/main/java/com/streamsense/chatservice/consumer/ChatMessageLogConsumer.java`
- future target:
  - `sentiment-service/src/main/java/com/streamsense/sentimentservice/**`

Done when:
- ML failures and publish failures are retried or dead-lettered
- failures are visible and not silently dropped

---

## 5. Add DB Migrations and Persistence Baseline

Priority: `P0`  
Ease: `Easy`

Problem:
- `sentiment-service` config enables Flyway
- JPA is expected to validate schema
- no migration files currently exist

Why this comes now:
- fresh database startup will remain unreliable without this

Exact next steps:
1. Add `V1__create_sentiment_events.sql`.
2. Create the `sentiment_events` table with the contract-defined fields.
3. Add the recommended index on `(streamer, chat_timestamp DESC)`.
4. Keep `ddl-auto=validate` so startup fails if schema drifts.

Touches:
- `sentiment-service/src/main/resources/db/migration/*.sql`
- `config-server/config-repo/sentiment-service.yml`
- `docs/contracts/sentiment-pipeline.md`

Done when:
- a clean Postgres instance starts successfully with `sentiment-service`
- Flyway applies the schema on boot

---

## 6. Implement Sentiment GraphQL API in `api-gateway`

Priority: `P1`  
Ease: `Medium`

Problem:
- docs define sentiment query/subscription capabilities
- gateway currently exposes only chat subscriptions

Add these GraphQL capabilities:
- `recentSentiment(streamer: String!, limit: Int!)`
- `onSentiment(streamer: String!)`

Why this comes after the service work:
- gateway should sit on top of a real sentiment source, not a stubbed contract only

Exact next steps:
1. Extend GraphQL schema with `SentimentAnalysisEvent` and the sentiment query/subscription fields.
2. Add a client in `api-gateway` to fetch recent sentiment from `sentiment-service`.
3. Add a Kafka consumer or internal bus for live sentiment subscription updates if `stream.sentiment.events` is retained.
4. Add GraphQL tests for:
   - health query
   - recent sentiment query
   - live sentiment subscription

Touches:
- `api-gateway/src/main/resources/graphql/schema.graphqls`
- `api-gateway/src/main/java/com/streamsense/apigateway/**`
- `config-server/config-repo/api-gateway.yml`
- `api-gateway/src/test/java/com/streamsense/apigateway/graphql/**`

Done when:
- frontend can query historical sentiment and subscribe to live sentiment updates

---

## 7. Add Frontend Sentiment UI

Priority: `P1`  
Ease: `Medium`

Problem:
- frontend currently shows only health and live chat
- repo docs describe a broader analytics dashboard

Exact next steps:
1. Add a sentiment history panel using `recentSentiment`.
2. Add a live sentiment stream panel using `onSentiment`.
3. Show loading, empty, and error states clearly.
4. Keep the live chat panel, but make sentiment the primary analytics surface.
5. Add env-based API config or Vite proxy support for local dev.

Touches:
- `frontend/src/App.tsx`
- `frontend/src/pages/**`
- `frontend/src/graphql/**`
- `frontend/src/apollo/client.ts`
- `frontend/vite.config.ts`

Done when:
- the frontend can display recent sentiment for a streamer
- the frontend updates live as new sentiment arrives

---

## 8. Fix Docker Startup, Readiness, and Config Drift

Priority: `P1`  
Ease: `Medium`

Problem:
- startup depends on prebuilt jars
- Compose readiness is partial
- local and Docker configs disagree
- some docs are inaccurate

Exact next steps:
1. Add healthchecks for:
   - `config-server`
   - `postgres`
   - `kafka`
   - `ml-engine`
2. Update `depends_on` to reflect actual readiness, not just container startup.
3. Replace Dockerfiles that require prebuilt jars with multi-stage builds, or standardize one root build command.
4. Remove machine-specific config assumptions from local config-server setup.
5. Make hostnames and ports env-driven where possible.
6. Fix the Kafka UI port mismatch in docs.

Touches:
- `docker-compose.yml`
- `config-server/src/main/resources/application.yml`
- `config-server/config-repo/*.yml`
- all Java service `Dockerfile`s
- `docs/howtorun.md`

Done when:
- `docker compose up --build` is predictable
- services do not race each other on startup
- docs match the actual runtime

---

## 9. Expand Automated Tests Around the Real Flow

Priority: `P1`  
Ease: `Medium`

Problem:
- current tests cover only part of the system
- the highest-risk path has little or no coverage

Exact next steps:
1. Add `sentiment-service` integration tests for:
   - consume chat event
   - call ML service
   - persist sentiment
   - optionally publish sentiment event
2. Add error-path tests for ML timeout/failure.
3. Add contract validation tests for event DTOs against the schema docs.
4. Add positive GraphQL tests for `health`, `recentSentiment`, and `onSentiment`.
5. Add frontend component tests for health and sentiment rendering.

Touches:
- `sentiment-service/src/test/java/**`
- `api-gateway/src/test/java/**`
- `chat-service/src/test/java/**`
- `frontend/src/**/*.test.*`
- `docs/schemas/*.json`

Done when:
- the full core flow is testable without manual clicking

---

## 10. Clean Docs, Repo Metadata, and Developer Workflow

Priority: `P2`  
Ease: `Easy`

Problem:
- docs and code drift makes the repo harder to trust
- some metadata is stale
- root workflow could be simpler

Exact next steps:
1. Update README and docs so they describe the actual implemented state.
2. Fix stale service metadata such as `video-service/pom.xml` naming.
3. Add a root `Makefile` or equivalent standard commands for:
   - build
   - test
   - up
   - down
   - logs
4. Remove or rewrite sections that claim non-Docker startup if not actually supported yet.
5. Keep one canonical architecture document and point other docs to it.

Touches:
- `README.md`
- `docs/*.md`
- `video-service/pom.xml`
- root `makefile`

Done when:
- a new contributor can understand and run the repo without reverse-engineering it

---

## 11. Harden `ml-engine`

Priority: `P2`  
Ease: `Medium`

Problem:
- it works as a stub but is not production-shaped yet

Exact next steps:
1. Move `modelVersion` to config or environment.
2. Pin Python dependencies.
3. Add tests for invalid payloads and edge cases.
4. Add clearer request/response validation behavior.
5. Decide whether sponsor detection is truly in scope now or should be removed from docs until implemented.

Touches:
- `ml-engine/src/main/python/app/main.py`
- `ml-engine/src/main/python/app/models.py`
- `ml-engine/requirements.txt`
- `ml-engine/src/test/python/**`
- `docs/architecture.md`
- `README.md`

Done when:
- ML responses are predictable, validated, and configurable

---

## 12. Defer `video-service` and `recommendation-service` Until the Core Is Stable

Priority: `P3`  
Ease: `Hard`

Problem:
- both services are still skeletal
- starting them before the sentiment path is coherent will spread effort too thin

Exact next steps:
1. Leave them as scaffolds for now.
2. Fix POM metadata and basic startup consistency only.
3. Return to them after the sentiment vertical slice is working end to end.

Touches:
- `video-service/**`
- `recommendation-service/**`

Done when:
- the core platform is stable enough to support a second feature track

---

## Recommended Execution Sequence

### Phase 1: Fast Corrections
1. Fix GraphQL `health`
2. Add Flyway migration
3. align docs with ports and current startup reality

### Phase 2: Core Ownership and Persistence
1. move sentiment ownership into `sentiment-service`
2. implement the minimum `sentiment-service`
3. add retry/error handling

### Phase 3: Product Surface
1. add gateway sentiment GraphQL API
2. add frontend sentiment views

### Phase 4: Reliability and DX
1. improve Docker builds/readiness
2. add missing tests
3. clean docs and repo workflow

### Phase 5: Broader Platform Work
1. harden `ml-engine`
2. resume `video-service`
3. resume `recommendation-service`

---

## Success Criteria

The repository is in a much better state when all of the following are true:

- `chat-service` only ingests and publishes chat events
- `sentiment-service` consumes chat events and persists sentiment
- Postgres schema is created automatically through Flyway
- `api-gateway` exposes working `health`, `recentSentiment`, and `onSentiment`
- frontend shows health, live chat, recent sentiment, and live sentiment
- startup through Docker is repeatable
- failures are retried or dead-lettered instead of silently lost
- tests exist for the full core path
- docs match the actual architecture

## Suggested First Sprint

If only one short sprint is available, do these in order:

1. Fix GraphQL `health`
2. Add Flyway migration for `sentiment_events`
3. implement minimum `sentiment-service`
4. move sentiment processing out of `chat-service`
5. add retry/error handling
6. expose `recentSentiment` in `api-gateway`
7. add a basic frontend sentiment panel

That gives the repo one coherent, defensible end-to-end feature.
