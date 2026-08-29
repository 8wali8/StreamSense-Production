# Week 2 Implementation Plan

## Goal

Deliver the first thin end-to-end real-time product slice:

`chat ingest -> Kafka -> api-gateway GraphQL subscription -> frontend live chat`

Week 2 is where the platform stops being only infrastructure scaffolding and becomes a working real-time application.

The slice is intentionally thin. It is meant to prove the backbone of:

- HTTP ingest
- Kafka transport
- GraphQL gateway bridging
- frontend real-time consumption

It is not yet the full analytics system. That comes later.

## Week 2 Success Criteria

Week 2 is complete only when all of the following are true:

- Kafka runs locally via Docker Compose.
- `stream.chat.messages` exists and is created reliably.
- `chat-service` exposes `POST /api/chat/ingest`.
- `chat-service` validates incoming chat payloads.
- `chat-service` publishes `ChatMessageEvent` to Kafka.
- `api-gateway` exposes:
  - `Query { health: String! }`
  - `Subscription { onChatMessage(streamer: String!): ChatMessageEvent! }`
- `api-gateway` consumes chat events from Kafka and bridges them into GraphQL subscriptions.
- frontend subscribes to live chat messages and renders them in real time.
- the GraphQL subscription protocol is locked and consistent across gateway and frontend.
- baseline tests prove the chat slice works.
- basic metrics and traces exist for the new slice.

## Week 2 Purpose

Week 2 is about proving the repository can move live data through its intended architecture.

The system does not need full persistence, analytics, resilience, or recommendation logic yet. It does need one coherent real-time path that demonstrates the architecture is viable.

The thin slice must remain intentionally thin.

Week 2 should not get polluted with Week 3+ concerns such as:

- sentiment ownership living in `chat-service`
- persistence of analytics
- recommendation logic
- sponsor detection
- Redis caching
- rate limiting or full auth

## Current Starting Point

This plan assumes Week 1 has produced a usable baseline:

- repo structure is coherent
- Eureka and Config Server can run
- baseline Docker Compose exists
- monitoring skeleton exists
- CI baseline exists

Week 2 starts by adding the first real product behavior on top of that platform.

## Week 2 Deliverables

By the end of the week, the repository should contain all of the following:

### 1. Local Kafka Cluster

- Kafka and Zookeeper integrated into Compose
- optional Kafka UI
- reliable topic creation for `stream.chat.messages`

### 2. Chat Ingest Service

- request DTO for chat ingest
- validated `POST /api/chat/ingest`
- `ChatMessageEvent` published to Kafka
- ingest metrics

### 3. API Gateway GraphQL Baseline

- `health` query
- `onChatMessage(streamer)` subscription
- Kafka consumer that forwards chat events into a subscription bus

### 4. Frontend Live Chat View

- Apollo client configured for HTTP and WebSocket
- live chat page with streamer-based subscription filtering
- status and empty-state handling

### 5. Testing And Observability For The Slice

- backend tests for validation and Kafka publish behavior
- gateway GraphQL tests for health and subscription flow
- frontend test for live chat rendering behavior
- basic metrics and trace verification for the path

## Non-Negotiable Design Decision For Week 2

Lock the transport and protocol choices now.

### GraphQL Subscription Protocol

Use one protocol consistently end to end:

- backend and frontend must both use `graphql-ws` / `graphql-transport-ws`

Do not leave this ambiguous, and do not mix protocols across layers.

### Gateway Role

The gateway is the central GraphQL surface.

That means:

- the frontend should subscribe through `api-gateway`
- the gateway should bridge Kafka to GraphQL subscriptions
- do not bypass the gateway with direct service WebSockets or direct Kafka-to-frontend hacks

### Scope Discipline

Week 2 should stop at live chat transport.

The chat slice should not become responsible for sentiment inference, sponsor detection, or persistence this week.

---

## Phase 1 - Add Kafka To The Local Platform

This phase introduces the event backbone.

### Objectives

- make Kafka a real local dependency of the platform
- ensure topics are created predictably

### Tasks

1. Add Kafka and Zookeeper to Docker Compose.
2. Add optional Kafka UI if useful for local visibility.
3. Ensure Kafka exposes appropriate listeners for:
   - container-to-container access
   - host-machine access
4. Add a topic-initialization step or one-shot container.
5. Create topic:
   - `stream.chat.messages`
6. Verify topic creation is stable under repeated startup.
7. Avoid relying on implicit topic auto-creation as the only mechanism.

### Output Of This Phase

- the event backbone for the Week 2 chat slice exists and starts reliably

### Risks

- listener misconfiguration making Kafka work from the host but not from containers, or vice versa
- topic-init races during container startup

---

## Phase 2 - Implement `chat-service` Ingest Path

This phase creates the first application-level event producer.

### Objectives

- accept chat requests over HTTP
- validate them
- publish them to Kafka as chat events

### Tasks

#### API Surface

1. Create request DTO for ingest payload.
2. Validate required fields:
   - `streamer`
   - `user`
   - `message`
   - `timestamp`
3. Return a response containing the generated event identifier.

#### Chat Event Contract

1. Define `ChatMessageEvent` clearly.
2. Include at least:
   - `eventId`
   - `streamer`
   - `user`
   - `message`
   - `timestamp`
3. Ensure event IDs are generated by the service.
4. Keep the event shape simple and stable.

#### Kafka Publish Path

1. Add Kafka producer support.
2. Publish to `stream.chat.messages` using a meaningful key, such as streamer.
3. Forward correlation and tracing headers where available.
4. Add ingest and producer latency metrics.

#### Local Consumer For Validation

1. Add a lightweight Kafka consumer in `chat-service` that logs chat events.
2. Keep this consumer purely diagnostic for the thin slice.
3. Do not turn it into the sentiment pipeline this week.

### Output Of This Phase

- a valid HTTP ingest request results in a Kafka chat event

### Risks

- allowing `chat-service` to start taking on downstream analytics responsibilities too early

---

## Phase 3 - Build The Gateway GraphQL Surface

This phase turns Kafka events into frontend-consumable real-time GraphQL.

### Objectives

- make `api-gateway` the live query and subscription entry point for the thin slice

### Tasks

#### GraphQL Schema

1. Add `Query { health: String! }`.
2. Add `Subscription { onChatMessage(streamer: String!): ChatMessageEvent! }`.
3. Add `ChatMessageEvent` type to the schema.
4. Keep schema naming aligned with the Java event type.

#### Health Query

1. Implement a resolver that returns `ok`.
2. Ensure this is a successful query, not a GraphQL error.
3. Keep it as the baseline sanity-check query used by the frontend and tests.

#### Subscription Bridge

1. Add Kafka consumer support in the gateway for `stream.chat.messages`.
2. Use a dedicated consumer group for GraphQL subscription fanout.
3. Add a subscription bus or sink inside the gateway.
4. Publish Kafka-consumed events into that bus.
5. Filter subscription emissions by streamer.

#### Gateway Role Discipline

1. Keep the subscription source inside the gateway.
2. Do not move subscription logic into `chat-service`.
3. Do not introduce direct frontend access to Kafka or service-local WebSockets.

### Output Of This Phase

- the gateway can turn Kafka chat events into GraphQL subscription messages

### Risks

- protocol mismatch between GraphQL server and Apollo client
- building a one-off subscription mechanism that cannot be reused later for sentiment and sponsor events

---

## Phase 4 - Build The Frontend Live Chat View

This phase makes the thin slice visible to the user.

### Objectives

- let a user connect to a streamer-specific live chat stream from the browser

### Tasks

1. Configure Apollo Client with:
   - HTTP link for queries
   - WebSocket link for subscriptions
2. Ensure the WebSocket client uses the same GraphQL subscription protocol as the gateway.
3. Build a `Live Chat` page or component.
4. Allow the user to choose a streamer.
5. Subscribe to `onChatMessage(streamer)`.
6. Render incoming chat messages in a clear list.
7. Show:
   - disconnected state
   - listening state
   - empty state
   - error state
8. Prevent obvious duplicate rendering issues.
9. Keep the UI simple but usable.

### UI Requirements

- the user should be able to connect and disconnect intentionally
- the UI should clearly show which streamer is active
- the page should remain readable on a laptop-sized screen without further styling work

### Output Of This Phase

- live chat messages appear in the browser in response to ingest requests

### Risks

- frontend subscription setup appearing to work but silently using the wrong protocol

---

## Phase 5 - Add Week 2 Testing Coverage

Week 2 is not complete without automated evidence for the thin slice.

### Objectives

- verify the slice with tests at each major boundary

### Required Backend Tests

1. `chat-service` controller validation test.
2. `chat-service` integration test proving a valid ingest produces a Kafka record.
3. `api-gateway` GraphQL health query test asserting `ok` without errors.
4. `api-gateway` subscription integration test proving a Kafka event reaches a GraphQL subscription client.

### Required Frontend Tests

1. health component renders success state
2. live chat view renders initial disconnected or empty state
3. live chat view renders incoming subscription event correctly

### Test Scope Boundary

Week 2 tests should prove the thin slice only.

Do not inflate Week 2 into persistence or ML integration tests that belong to later weeks.

### Exit Condition For Testing

The repo should be able to prove the chat slice works without manual browser inspection being the only evidence.

---

## Phase 6 - Add Basic Week 2 Observability

This phase ensures the first product slice is inspectable.

### Objectives

- expose enough telemetry to verify the chat path works and to prepare for later weeks

### Metrics

Add at least:

- accepted chat ingest count
- Kafka produce latency for chat publish

Possible metric names:

- `streamsense_chat_ingest_total`
- `streamsense_kafka_produce_latency_ms`

### Tracing

Verify there is at least partial trace visibility for:

- `POST /api/chat/ingest`
- Kafka publish step if trace propagation is already wired enough to show it

### Logs

Ensure logs make it easy to answer:

- was the ingest request accepted?
- was the event published to Kafka?
- did the gateway consume it?
- did the frontend receive it?

### Output Of This Phase

- the Week 2 slice can be observed instead of only guessed at

### Risks

- metrics existing in code but never being demonstrated through Prometheus or manual verification

---

## Phase 7 - Document The Week 2 Slice

This phase ensures the thin slice is reproducible by someone else.

### Objectives

- create a run path that a new contributor can follow without reverse-engineering the stack

### Tasks

1. Update the local runbook with the Week 2 quickstart.
2. Document how to:
   - start Kafka and the services
   - ingest a chat message
   - query GraphQL `health`
   - subscribe to `onChatMessage`
   - verify the frontend live chat page
3. Ensure docs reflect the actual exposed ports.
4. Fix stale port or endpoint references immediately when found.

### Output Of This Phase

- the Week 2 slice is demoable using repo docs

### Risks

- docs drifting and sending users to the wrong ports or services

---

## Detailed Implementation Sequence

Execute Week 2 in this order.

1. Add Kafka and stable topic creation to Compose.
2. Define the `ChatMessageEvent` contract.
3. Implement `POST /api/chat/ingest` validation and response.
4. Implement Kafka publishing in `chat-service`.
5. Add diagnostic chat consumer logging in `chat-service`.
6. Add GraphQL schema for `health` and `onChatMessage`.
7. Implement `health` resolver.
8. Add gateway Kafka consumer and subscription bus.
9. Add frontend Apollo HTTP and WebSocket setup.
10. Build the live chat UI.
11. Add backend tests.
12. Add frontend tests.
13. Verify metrics and tracing.
14. Update docs.

Do not start Week 3 sentiment features until this thin slice is clearly stable.

## Manual Verification Checklist

At the end of Week 2, manually verify all of the following:

1. Start the full local stack including Kafka.
2. Confirm `stream.chat.messages` exists.
3. Call `POST /api/chat/ingest` with a valid payload.
4. Confirm a Kafka chat record is produced.
5. Query GraphQL `health` and confirm it returns `ok`.
6. Open a GraphQL subscription client and subscribe to `onChatMessage(streamer)`.
7. Ingest more messages and confirm subscription events arrive.
8. Open the frontend and confirm live chat updates appear.
9. Confirm ingest metrics appear in Prometheus or actuator output.
10. Confirm at least one representative trace can be found in Zipkin if tracing is already wired sufficiently.

## Definition Of Done

Week 2 is done when all of the following are true at the same time:

- chat ingest works
- Kafka transport works
- gateway GraphQL `health` works
- live subscription delivery works
- frontend renders live chat
- tests prove the slice
- the slice can be observed through logs and at least basic metrics
- the docs explain how to run and verify it

## Things That Must Not Be Deferred Out Of Week 2

- stable Kafka topic creation
- validated chat ingest
- working GraphQL `health` query
- working GraphQL live chat subscription
- frontend live chat page
- backend tests for the slice
- frontend test coverage for the live chat surface
- docs that correctly describe how to run the slice

If these are deferred, Week 2 is not actually complete even if the system looks partly functional in manual testing.

## Things That Should Not Be Pulled Into Week 2

- sentiment inference in `chat-service`
- `sentiment-service` persistence implementation
- `recentSentiment` GraphQL query
- live sentiment subscription
- video sponsor work
- recommendation work
- Redis cache work
- gateway auth and rate limiting

These belong to later weeks and should not muddy the Week 2 thin slice.

## Risks And Mitigations

### Risk: GraphQL subscription protocol mismatch

Mitigation:

- lock the protocol choice early and verify both frontend and backend use the same one

### Risk: Kafka topic creation race

Mitigation:

- use an explicit topic initialization step with retries rather than relying on luck

### Risk: gateway subscription bridge is implemented as a one-off hack

Mitigation:

- build a reusable subscription bus pattern that later event types can follow

### Risk: `chat-service` takes on sentiment responsibilities too early

Mitigation:

- keep the Week 2 consumer diagnostic-only and hold the sentiment pipeline for Week 3

### Risk: docs tell users to hit the wrong ports

Mitigation:

- verify all documented ports against Compose before considering the week complete

## Stretch Goals Only If Core Week 2 Work Finishes Early

- improved live chat UI styling
- stronger trace propagation through Kafka headers
- small refinements to metrics naming and dashboard placeholders

Do not allow these to replace the core Week 2 deliverables.

## Week 2 Summary

Week 2 should deliver the first true product slice in the repo.

The key outcomes are:

- Kafka integrated into the local platform
- validated chat ingest endpoint
- chat events published to Kafka
- gateway GraphQL health and live chat subscription working
- frontend live chat page working
- tests and basic observability for the slice

If those outcomes are delivered, the repository is ready for Week 3 sentiment pipeline work.
