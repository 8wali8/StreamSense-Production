# StreamSense Production Gap Plan

## Purpose

This document defines the concrete gap between the current StreamSense repository and an end-to-end product that takes live Twitch stream data, ports it through all microservices, and outputs relevant analytics metrics through the frontend and API.

The current repository is a production-shaped demo platform. It has working service boundaries, Kafka event flow, persistence, GraphQL access, frontend panels, observability, Docker Compose, local Kubernetes manifests, and load/smoke tooling. The remaining gap is not basic microservice wiring. The remaining gap is real Twitch ingestion, real stream/video data handling, metric aggregation, and product-grade analytics outputs.

## Current State

Implemented platform capabilities:

- `chat-service` accepts synthetic chat payloads through `POST /api/chat/ingest` and publishes `stream.chat.messages`.
- `sentiment-service` consumes chat events, calls `ml-engine`, persists sentiment rows, publishes `stream.sentiment.events`, and exposes recent sentiment history.
- `video-service` accepts synthetic/reference frame payloads through `POST /api/video/upload-frame`, calls `ml-engine`, persists sponsor detections, publishes `stream.sponsor.detections`, and exposes recent sponsor history.
- `recommendation-service` reads recent sentiment and sponsor history and returns deterministic, explainable recommendations.
- `api-gateway` routes `/api/**`, exposes GraphQL queries/subscriptions, includes auth hooks and rate limiting, and fans out live events.
- `frontend` renders live chat, sentiment, sponsor detections, and recommendations.
- Postgres stores service-owned history.
- Redis caches hot history reads.
- Kafka transports chat, sentiment, frame, and sponsor events.
- Prometheus, Grafana, and Zipkin provide local observability.
- Docker Compose and local `kind` Kubernetes deployment are documented.

Current demo input model:

- Chat input is manually posted JSON, not Twitch chat ingestion.
- Video input is manually posted `frameRef` JSON, not real Twitch video frame sampling.
- Stream identity is mostly a `streamer` string, not a Twitch channel/user/session model.
- ML behavior is deterministic and contract-focused, not production inference.
- Metrics are mostly service/runtime metrics plus recent event panels, not a complete product metrics API.

## Target Product State

The target product flow should be:

1. A user enters or configures a Twitch channel.
2. StreamSense resolves the Twitch channel identity and active stream session.
3. StreamSense connects to live Twitch chat and ingests messages automatically.
4. StreamSense captures or samples live Twitch video frames at a controlled cadence.
5. Chat events flow through Kafka into sentiment analysis.
6. Frame events flow through Kafka into sponsor/logo detection.
7. Services persist normalized, session-aware analytics events.
8. Aggregation jobs or services compute stream-level metrics over time windows.
9. `recommendation-service` generates campaign recommendations from aggregated metrics, not just recent raw events.
10. `api-gateway` exposes both raw history and product-level metric summaries through GraphQL.
11. `frontend` shows a Twitch stream analytics dashboard with live and historical product metrics.
12. Observability proves ingestion health, processing lag, inference behavior, fallback behavior, and user-facing API performance.

## Gap 1: Twitch Chat Ingestion

Current gap:

- The platform accepts chat only through `POST /api/chat/ingest`.
- There is no Twitch IRC, EventSub, or Helix integration.
- There is no Twitch OAuth/client credential configuration.
- There is no reconnect, dedupe, or Twitch rate-limit handling.

Required work:

1. Add Twitch credential configuration through Config Server and deployment manifests.
2. Add secret handling for Twitch client ID, client secret, bot/user OAuth token, and webhook secret if EventSub is used.
3. Decide ingestion mode:
   - Twitch IRC for live chat messages.
   - EventSub for stream lifecycle events.
   - Helix API for channel metadata and stream state.
4. Implement a Twitch chat connector in `chat-service` or a dedicated `twitch-ingestion-service`.
5. Normalize Twitch messages into the existing `ChatMessageEvent` shape plus new Twitch/session fields.
6. Publish normalized messages to `stream.chat.messages` keyed by stable Twitch channel or stream session identity.
7. Add retry, reconnect, heartbeat, and duplicate-message protection.
8. Add ingestion metrics:
   - connected channels
   - messages ingested
   - reconnect count
   - Twitch API failures
   - dropped/duplicate messages
9. Add tests with mocked Twitch IRC/EventSub/Helix responses.
10. Update runbooks with real Twitch credential setup and local dry-run mode.

## Gap 2: Twitch Stream And Session Identity

Current gap:

- Events are keyed mostly by `streamer` string.
- There is no first-class stream session concept.
- Metrics cannot reliably distinguish separate broadcasts by the same streamer.

Required work:

1. Add a stream/session model owned by an appropriate service.
2. Persist Twitch identity fields:
   - `twitchUserId`
   - `channelLogin`
   - `displayName`
   - `streamSessionId`
   - `twitchStreamId`
   - `startedAt`
   - `endedAt`
   - `gameId`
   - `gameName`
   - `title`
   - `language`
3. Extend event contracts for chat, sentiment, frame, sponsor, and recommendations to include session identity.
4. Add database migrations for session-aware indexes.
5. Update GraphQL queries to accept either `streamer` for backwards demo use or `streamSessionId` for production use.
6. Update frontend state to track the selected active stream session.
7. Update Kafka keys to preserve ordering by `streamSessionId` or stable channel ID where appropriate.
8. Add migration notes and contract tests for the new fields.

## Gap 3: Real Video Frame Capture

Current gap:

- `video-service` accepts a small synthetic/reference frame payload.
- There is no live Twitch HLS pull, frame sampling, binary frame storage, or timestamp alignment.

Required work:

1. Resolve active Twitch stream playback/HLS source for a configured channel.
2. Add a frame capture worker or service that samples frames at a configurable cadence.
3. Store sampled frames in object storage or a local development equivalent.
4. Publish `FrameData` events with:
   - `frameId`
   - `streamSessionId`
   - `twitchUserId`
   - `frameRef`
   - `frameSequence`
   - `capturedAt`
   - `videoTimestampMs`
5. Ensure `video-service` can consume frame references that point to actual stored frame artifacts.
6. Add backpressure controls so frame sampling does not overwhelm Kafka or ML inference.
7. Add frame retention rules for raw artifacts.
8. Add tests for frame event creation, storage failure handling, and processing fallback.
9. Add metrics:
   - frames captured
   - frames skipped
   - frame capture latency
   - object storage failures
   - video processing lag

## Gap 4: Product Metrics Aggregation

Current gap:

- The frontend computes simple counts from recent event lists.
- GraphQL exposes recent sentiment, sponsor detections, and recommendations, but not complete product metrics.
- There is no durable aggregate model for stream-level metrics.

Required work:

1. Define product metrics for the first production version:
   - total chat messages
   - chat messages per minute
   - unique chatters
   - sentiment distribution
   - sentiment score trend by time bucket
   - negative spike detection
   - sponsor detections by brand
   - sponsor exposure count
   - estimated sponsor exposure duration
   - average sponsor confidence
   - brand safety/risk score
   - engagement spike windows
   - recommendation score history
2. Decide ownership for metric aggregation:
   - add aggregation inside existing services for service-owned metrics, or
   - add a dedicated analytics/metrics service if cross-domain aggregation becomes large.
3. Create aggregate tables keyed by `streamSessionId`, time bucket, and metric type.
4. Add Kafka consumers or scheduled jobs that update aggregates from sentiment and sponsor events.
5. Add REST endpoints for aggregate reads.
6. Add GraphQL types and queries for metric summaries and time series.
7. Update frontend dashboards to consume aggregated metrics instead of deriving everything from recent events.
8. Add tests for aggregation correctness, late events, duplicate events, and empty streams.
9. Add Prometheus metrics for aggregator lag and failures.

## Gap 5: ML Realism And Model Operations

Current gap:

- `ml-engine` exposes stable sentiment and sponsor endpoints, but current behavior is deterministic and demo-oriented.
- Sponsor detection uses logical frame references rather than actual image inference.

Required work:

1. Replace deterministic sentiment logic with a real sentiment model or external inference provider.
2. Replace deterministic sponsor detection with real logo/object detection over stored frames.
3. Define model artifacts, load path, and versioning policy.
4. Add confidence calibration and clear thresholds for low-confidence results.
5. Support batching where useful for frame inference.
6. Add model latency, error, timeout, and fallback metrics.
7. Add model-specific tests with fixed fixtures.
8. Add performance benchmarks for inference latency and throughput.
9. Add model rollout controls through config.
10. Document known model limitations and acceptable fallback behavior.

## Gap 6: Recommendation Productization

Current gap:

- Recommendations are deterministic and explainable, based on recent sentiment and sponsor history.
- They do not yet account for full campaign context or aggregated stream metrics.

Required work:

1. Add campaign/sponsor context fields:
   - sponsor brand
   - campaign objective
   - target categories
   - competitor brands
   - risk tolerance
   - minimum exposure targets
2. Feed aggregate metrics into `recommendation-service`.
3. Generate recommendations from metric windows, not only recent raw event lists.
4. Add recommendation history persistence if recommendations need auditing.
5. Add scoring explanations tied to concrete metrics.
6. Add tests for positive, negative, low-data, and conflicting-sponsor scenarios.
7. Add GraphQL fields for recommendation evidence.
8. Update frontend cards to show the metrics behind each recommendation.

## Gap 7: Product Frontend

Current gap:

- The frontend is a strong demo dashboard, but it still references a mock sponsor workflow and demo ingestion.
- It does not start or manage real Twitch ingestion.
- It does not show full stream/session analytics.

Required work:

1. Add a Twitch channel setup flow.
2. Show ingestion connection status for chat and video.
3. Show active stream session metadata.
4. Add product metric panels:
   - audience volume
   - sentiment trend
   - sponsor exposure
   - engagement spikes
   - brand safety/risk
   - recommendation evidence
5. Add loading, empty, degraded, and disconnected states for real ingestion.
6. Add browser-level tests for the main dashboard states.
7. Remove or clearly label mock/demo-only workflow text once real ingestion exists.

## Gap 8: Production Security And Operations

Current gap:

- Gateway auth has local bypass and JWT-shaped hooks.
- There is no full user/account/campaign model.
- Secrets and cloud deployment are not production-ready.

Required work:

1. Implement real authentication and authorization.
2. Add user, organization, and campaign ownership models.
3. Protect Twitch credentials and user tokens with a real secret manager in production.
4. Add tenant isolation to APIs, database queries, Kafka keys, and frontend state.
5. Add retention and deletion policies for chat, frames, analytics, and model outputs.
6. Add backups for Postgres and object storage.
7. Add alerting for ingestion failure, Kafka lag, ML failure, API errors, and dashboard outage.
8. Add cloud deployment choices for Kafka, Postgres, Redis, object storage, and Kubernetes.
9. Add CI/CD deployment workflow after the runtime target is selected.

## Gap 9: End-To-End Proof And Documentation

Current gap:

- The documented final demo uses `make demo-seed`, which injects synthetic data.
- There is no runbook proving a real Twitch channel can feed the full platform.

Required work:

1. Add a real Twitch E2E runbook.
2. Add a command or script to start ingestion for a Twitch channel.
3. Prove live chat appears in Kafka and GraphQL subscriptions.
4. Prove sampled frames produce sponsor detection events.
5. Prove aggregate metrics update during a real stream.
6. Prove recommendations are generated from the real stream metrics.
7. Capture Prometheus, Grafana, and Zipkin evidence for the full path.
8. Add a smoke test mode that can run against a mocked Twitch source for CI.
9. Keep synthetic `demo-seed` as a local fallback, but stop presenting it as the product E2E path.

## Suggested Implementation Sequence

### Phase 1: Twitch Identity And Chat

Goal: Replace manual chat posts with real Twitch chat ingestion.

Deliverables:

- Twitch config and secret placeholders.
- Twitch channel/session model.
- Twitch chat connector.
- Normalized chat events with session identity.
- Updated Kafka contract tests.
- Frontend ingestion status for chat.
- Runbook for real chat ingestion.

Acceptance criteria:

- Given a Twitch channel, chat messages arrive in `stream.chat.messages` without manual `curl` calls.
- `onChatMessage` streams real Twitch messages through GraphQL.
- Sentiment events are produced from real Twitch chat.

### Phase 2: Video Capture And Sponsor Detection

Goal: Replace synthetic frame posts with real Twitch frame sampling.

Deliverables:

- Twitch video/HLS sampling worker.
- Frame artifact storage path.
- Session-aware `FrameData` events.
- Real frame references consumed by `video-service`.
- Sponsor detection over real frame inputs.
- Video ingestion metrics and dashboards.

Acceptance criteria:

- Given a live Twitch channel, frames are sampled automatically.
- `stream.video.frames` receives real frame events.
- `stream.sponsor.detections` receives detections tied to the active stream session.

### Phase 3: Metrics Aggregation

Goal: Create product-level metrics instead of only raw recent event views.

Deliverables:

- Metric definitions and schemas.
- Aggregate persistence tables.
- Aggregation consumers/jobs.
- GraphQL metric summary and time-series queries.
- Frontend metric panels.
- Aggregation tests.

Acceptance criteria:

- Dashboard shows chat rate, sentiment trend, sponsor exposure, and engagement/risk metrics for a real stream session.
- Metrics update as new Twitch chat and frame events arrive.

### Phase 4: Recommendation Upgrade

Goal: Generate recommendations from campaign context and aggregate metrics.

Deliverables:

- Campaign context model.
- Recommendation inputs from aggregate metrics.
- Recommendation evidence fields.
- Frontend recommendation explanations tied to metrics.
- Tests for key campaign scenarios.

Acceptance criteria:

- Recommendations explain which stream metrics caused each action.
- Recommendations change when sentiment, exposure, or risk metrics change.

### Phase 5: Production Hardening

Goal: Make the product operable outside the local demo environment.

Deliverables:

- Real auth and tenant isolation.
- Secret management.
- Cloud deployment plan.
- Retention and backup policies.
- Alerting and SLO dashboards.
- E2E runbook with real Twitch evidence.

Acceptance criteria:

- A fresh environment can be configured with Twitch credentials and run the full product path.
- Operators can observe ingestion health, Kafka lag, ML failures, API failures, and frontend-impacting issues.

## Definition Of Done For Product E2E

The repository should be considered end-to-end product-ready when all of the following are true:

- A Twitch channel can be connected without manual event seeding.
- Chat messages are ingested from Twitch and processed through `chat-service`, Kafka, `sentiment-service`, `ml-engine`, Postgres, `api-gateway`, and frontend subscriptions.
- Video frames are sampled from Twitch and processed through `video-service`, Kafka, `ml-engine`, Postgres, `api-gateway`, and frontend subscriptions.
- Stream/session identity is consistent across chat, sentiment, frame, sponsor, recommendation, and metric records.
- Product metrics are available through GraphQL and visible in the frontend.
- Recommendations are based on aggregate stream metrics and campaign context.
- Degraded ML and ingestion failures are visible through fallback events, metrics, traces, and UI state.
- Docker/local runbooks and production deployment docs both describe the real Twitch path.
- CI has a mocked Twitch E2E test, and manual docs include a real Twitch proof path.
