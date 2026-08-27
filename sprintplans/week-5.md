# Sprint 5 Implementation Plan

## Goal

Deliver the first complete video-to-sponsor vertical slice:

`frame ingest -> Kafka frame event -> video-service processing -> ml-engine sponsor inference or fallback -> persistence -> sponsor Kafka event -> GraphQL -> frontend`

Sprint 5 should bring the sponsor path up to roughly the same functional level the sentiment path reached in Sprint 3 and the resilience level established in Sprint 4.

## Sprint 5 Success Criteria

- `video-service` accepts frame ingest requests at `POST /api/video/upload-frame`.
- frame ingest publishes `FrameData` records to `stream.video.frames`.
- `video-service` consumes frame events, calls `POST /ml/sponsor`, and emits `SponsorDetectionEvent` records to `stream.sponsor.detections`.
- sponsor detections are persisted and queryable by streamer through a service-owned REST endpoint.
- `api-gateway` exposes:
  - `sponsorDetections(streamer, limit)`
  - `onSponsorDetection(streamer)`
- frontend renders recent sponsor detections and live sponsor updates.
- stopping or degrading `ml-engine` does not stop sponsor events from being emitted; fallback detections are still persisted and published.
- automated tests cover controller validation, sponsor ML behavior, Kafka/event flow, GraphQL query/subscription behavior, and frontend rendering.

## Important Architecture Note

The Week 5 roadmap wording leaves open whether sponsor processing should happen synchronously inside the HTTP ingest handler or asynchronously from Kafka.

For this repo, Sprint 5 should implement the real event path, not just a synchronous shortcut:

- ingest endpoint validates the request and publishes `FrameData` to Kafka
- `video-service` consumes `stream.video.frames`
- sponsor inference, fallback handling, persistence, and sponsor-event publication happen from that processing path
- gateway history continues to come from a service-owned REST endpoint rather than Kafka

This keeps the sponsor slice aligned with the platform's event-driven architecture while still keeping the implementation small enough for one sprint.

## Contract Decisions For Sprint 5

### `FrameData`

Use a safe reference-based payload rather than raw image bytes for Sprint 5.

Fields:

- `frameId`
- `streamer`
- `frameRef`
- `frameSequence`
- `capturedAt`

Notes:

- `frameRef` is a small logical reference such as a CDN path, URL fragment, or synthetic demo token.
- Sprint 5 intentionally avoids real binary upload handling so the event flow can be stabilized first.

### `SponsorDetectionEvent`

Fields:

- `detectionEventId`
- `sourceFrameId`
- `streamer`
- `frameRef`
- `frameSequence`
- `capturedAt`
- `processedAt`
- `sponsor`
- `confidence`
- `modelVersion`
- `x`
- `y`
- `width`
- `height`

### Fallback Sponsor Contract

When sponsor inference degrades, emit a real detection event with:

- `sponsor = UNKNOWN`
- `confidence = 0.0`
- `modelVersion = fallback`
- `x = 0.0`
- `y = 0.0`
- `width = 0.0`
- `height = 0.0`

Fallback detections should still be persisted and published so the UI and metrics reflect degraded behavior instead of silent loss.

## Sprint 5 Deliverables

### 1. Contract And Schema Coverage

- add `docs/schemas/frame-data.schema.json`
- add `docs/schemas/sponsor-detection-event.schema.json`
- add a sponsor pipeline contract note describing event ownership and fallback behavior

### 2. `ml-engine` Sponsor Stub

- add `POST /ml/sponsor`
- keep sponsor inference deterministic from request content
- return sponsor name, confidence, bounding box, and model version
- honor the existing `ML_ENGINE_FORCE_FAILURE` toggle for sponsor inference too

### 3. `video-service` Processing Slice

- add request/response DTOs for frame ingest
- validate payload size and required fields
- publish `FrameData` to `stream.video.frames`
- consume frame events from Kafka
- wrap sponsor ML calls with Resilience4j using the reserved `mlSponsor` config
- persist sponsor detections in Postgres
- publish sponsor detection events to `stream.sponsor.detections`
- expose `GET /api/video/detections/recent`

### 4. Gateway Sponsor GraphQL Surface

- extend GraphQL schema with sponsor query and subscription types
- consume sponsor events from Kafka for subscription fanout
- query `video-service` for history
- reuse the replay-based subscription bus behavior already needed for stable live updates

### 5. Frontend Sponsor Dashboard

- add a dedicated sponsor panel
- show recent detections in a table/list
- show a simple confidence trend view
- surface loading, empty, and error states while continuing to show live updates

### 6. Observability And Verification

- add sponsor metrics in `video-service`:
  - `streamsense_frames_ingested_total`
  - `streamsense_sponsor_detections_total{sponsor=...}`
  - `streamsense_sponsor_fallback_total`
  - sponsor inference latency
- add sponsor topics to Compose topic initialization
- add a Grafana dashboard for sponsor flow basics
- verify the healthy and degraded sponsor path end to end in Docker

## Required Scope Breakdown

## Phase 1 - Freeze Contracts And Plan Surface

1. Define the final Sprint 5 `FrameData` and `SponsorDetectionEvent` shapes.
2. Confirm fallback sponsor behavior.
3. Create the Sprint 5 plan and contract docs before broad implementation.

## Phase 2 - Extend `ml-engine`

1. Add sponsor request and response models.
2. Add deterministic sponsor inference logic.
3. Add tests for valid shape, determinism, and forced failure.

## Phase 3 - Implement `video-service`

1. Add dependencies for Kafka, validation, JPA, Flyway, Postgres, AOP, and Resilience4j.
2. Add config properties, REST client config, and Kafka producer/consumer wiring.
3. Implement frame ingest endpoint and Kafka publication.
4. Implement sponsor processing service with fallback behavior.
5. Persist sponsor detections and expose recent history.
6. Add metrics and tests.

## Phase 4 - Extend `api-gateway`

1. Add sponsor event model and service client.
2. Add sponsor Kafka consumer and subscription bus.
3. Extend GraphQL schema and controller coverage.
4. Add query and subscription tests.

## Phase 5 - Extend Frontend

1. Add sponsor queries and subscriptions.
2. Add sponsor dashboard UI.
3. Add frontend tests for history, live updates, and empty/error states.

## Phase 6 - End-To-End Verification And Documentation

1. Update Compose topics and any config needed for local Docker.
2. Run targeted service and frontend tests.
3. Verify the sponsor path live in Docker:
   - healthy inference path
   - forced-failure fallback path
   - gateway history and subscription behavior
   - frontend real-time rendering
4. Record the completed work in `opencodeCommandHistory/`.

## Definition Of Done

Sprint 5 is complete when:

- the video sponsor path works end to end in Docker
- the gateway exposes both sponsor history and live sponsor subscriptions
- the frontend shows sponsor data without reload-only behavior
- fallback sponsor detections still appear when `ml-engine` is intentionally failed
- the sprint plan, schema docs, and command history all match the actual implementation

## Risks To Watch

- payload size creeping upward if frame references turn into raw binary data
- adding more abstraction than the sponsor slice actually needs
- drifting away from service-owned history queries
- shipping a sponsor subscription path that has the same fanout-loss bug the gateway just had
