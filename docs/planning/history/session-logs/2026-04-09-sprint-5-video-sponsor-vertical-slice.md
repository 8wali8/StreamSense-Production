# Sprint 5 Work Log: Video Sponsor Vertical Slice

## Objective

Implement the Week 5 sponsor detection slice from `plan.md`:

`frame ingest -> Kafka frame event -> video-service processing -> ml-engine sponsor inference or fallback -> persistence -> sponsor Kafka event -> GraphQL -> frontend`

The Sprint 5 target was to make `video-service` a real owner of the video-to-sponsor flow and expose both live and historical sponsor data through the gateway and UI.

## Scope Completed

### 1. Created the Sprint 5 plan and froze the contract

Implemented:

- added `sprintplans/week-5.md`
- defined the Sprint 5 `FrameData` and `SponsorDetectionEvent` contracts
- chose a safe reference-based frame payload for Sprint 5 rather than raw binary image upload
- made the fallback sponsor contract explicit:
  - `sponsor = UNKNOWN`
  - `confidence = 0.0`
  - `modelVersion = fallback`
  - zeroed bounding box fields

Main files affected:

- `sprintplans/week-5.md`
- `docs/contracts/sponsor-pipeline.md`
- `docs/schemas/frame-data.schema.json`
- `docs/schemas/sponsor-detection-event.schema.json`

### 2. Extended `ml-engine` with sponsor detection

Implemented:

- added `POST /ml/sponsor`
- added deterministic sponsor inference based on `streamer`, `frameRef`, and `frameSequence`
- returned sponsor name, confidence, and normalized bounding box fields
- reused `ML_ENGINE_FORCE_FAILURE` for sponsor failure simulation too

Main files affected:

- `ml-engine/src/main/python/app/main.py`
- `ml-engine/src/main/python/app/models.py`
- `ml-engine/src/main/python/app/sponsor.py`
- `ml-engine/src/test/python/test_sponsor.py`

### 3. Implemented the `video-service` sponsor pipeline

Implemented:

- added Kafka, JPA, Flyway, Postgres, validation, and Resilience4j dependencies
- added `POST /api/video/upload-frame`
- published `FrameData` records to `stream.video.frames`
- consumed `stream.video.frames` in `video-service`
- wrapped sponsor ML calls with the reserved `mlSponsor` resilience config
- persisted sponsor detections in Postgres
- published sponsor detections to `stream.sponsor.detections`
- exposed `GET /api/video/detections/recent`
- added sponsor metrics:
  - `streamsense_frames_ingested_total`
  - `streamsense_sponsor_detections_total{sponsor=...}`
  - `streamsense_sponsor_fallback_total`
  - `streamsense_sponsor_inference_latency_ms`

Main files affected:

- `video-service/pom.xml`
- `video-service/src/main/java/com/streamsense/videoservice/config/StreamSenseProperties.java`
- `video-service/src/main/java/com/streamsense/videoservice/config/RestClientConfig.java`
- `video-service/src/main/java/com/streamsense/videoservice/client/MlEngineClient.java`
- `video-service/src/main/java/com/streamsense/videoservice/controller/VideoController.java`
- `video-service/src/main/java/com/streamsense/videoservice/service/VideoProcessingService.java`
- `video-service/src/main/java/com/streamsense/videoservice/kafka/VideoFrameProducer.java`
- `video-service/src/main/java/com/streamsense/videoservice/kafka/VideoFrameConsumer.java`
- `video-service/src/main/java/com/streamsense/videoservice/kafka/SponsorDetectionProducer.java`
- `video-service/src/main/java/com/streamsense/videoservice/persistence/SponsorDetectionEntity.java`
- `video-service/src/main/java/com/streamsense/videoservice/persistence/SponsorDetectionRepository.java`
- `video-service/src/main/resources/db/migration/V2__create_sponsor_detections.sql`
- `video-service/src/test/java/com/streamsense/videoservice/VideoPipelineIntegrationTest.java`

### 4. Extended the gateway for sponsor history and live subscriptions

Implemented:

- added sponsor history query:
  - `sponsorDetections(streamer, limit)`
- added sponsor live subscription:
  - `onSponsorDetection(streamer)`
- added `VideoServiceClient` for sponsor history
- consumed `stream.sponsor.detections` for GraphQL subscription fanout
- reused replay-based sinks for stable live delivery

Main files affected:

- `api-gateway/src/main/resources/graphql/schema.graphqls`
- `api-gateway/src/main/java/com/streamsense/apigateway/client/VideoServiceClient.java`
- `api-gateway/src/main/java/com/streamsense/apigateway/events/SponsorDetectionEvent.java`
- `api-gateway/src/main/java/com/streamsense/apigateway/graphql/SponsorGraphqlController.java`
- `api-gateway/src/main/java/com/streamsense/apigateway/consumer/SponsorDetectionKafkaConsumer.java`
- `api-gateway/src/main/java/com/streamsense/apigateway/subscriptions/SponsorSubscriptionBus.java`
- `api-gateway/src/main/java/com/streamsense/apigateway/config/GatewayKafkaConfig.java`
- `api-gateway/src/test/java/com/streamsense/apigateway/graphql/SponsorDetectionsQueryTest.java`
- `api-gateway/src/test/java/com/streamsense/apigateway/graphql/SponsorSubscriptionIntegrationTest.java`

### 5. Added the frontend sponsor dashboard

Implemented:

- added a dedicated sponsor panel rather than mixing sponsor state into sentiment UI
- added sponsor history query and live subscription usage
- rendered:
  - recent detections list
  - average confidence and fallback count metrics
  - simple confidence trend bars
- kept loading, empty, and error states explicit
- verified the frontend proxy path for live sponsor subscriptions

Main files affected:

- `frontend/src/components/SponsorPanel.tsx`
- `frontend/src/components/SponsorPanel.test.tsx`
- `frontend/src/graphql/queries.ts`
- `frontend/src/graphql/subscriptions.ts`
- `frontend/src/App.tsx`

### 6. Updated runtime config, Compose, and observability

Implemented:

- added sponsor topics to Compose topic initialization:
  - `stream.video.frames`
  - `stream.sponsor.detections`
- added `video-service` config for Kafka, Postgres, Flyway, history, payload limits, and ML base URL
- added gateway config for sponsor topic and `video-service` base URL
- added a Grafana dashboard for sponsor metrics
- updated the runbook with Sprint 5 verification steps

Main files affected:

- `config-server/config-repo/video-service.yml`
- `config-server/config-repo/api-gateway.yml`
- `docker-compose.yml`
- `monitoring/grafana/provisioning/dashboards/sprint5-sponsor-overview.json`
- `docs/howtorun.md`

## Verification Performed

### Local automated checks

Passed:

- `PYTHONPATH=src/main/python python3 -m pytest src/test/python` in `ml-engine`
- `mvn test` in `video-service`
- `mvn test` in `api-gateway`
- `npm test` in `frontend`

### Docker healthy-path verification

Verified live in Docker:

- `video-service` health at `http://localhost:8084/actuator/health`
- `api-gateway` health at `http://localhost:8080/actuator/health`
- frontend served at `http://localhost:3000`
- uploaded a frame for streamer `sprint5-healthy`
- `POST /api/video/upload-frame` returned `202 Accepted`
- GraphQL sponsor subscription through the frontend proxy `ws://localhost:3000/graphql` emitted:
  - `sponsor = Logitech`
  - `confidence = 0.918`
  - `modelVersion = stub-v1`
- REST history at `GET /api/video/detections/recent?streamer=sprint5-healthy&limit=5` returned persisted sponsor detections
- GraphQL history at `sponsorDetections(streamer, limit)` returned the same persisted data
- Prometheus returned:
  - `streamsense_frames_ingested_total`
  - `streamsense_sponsor_detections_total{sponsor="Logitech"}`

### Docker degraded-path verification

Verified live in Docker:

- stopped `ml-engine`
- uploaded a frame for streamer `sprint5-fallback`
- GraphQL sponsor subscription through the frontend proxy still emitted a live event with:
  - `sponsor = UNKNOWN`
  - `confidence = 0.0`
  - `modelVersion = fallback`
- REST history returned persisted fallback sponsor detection data
- GraphQL history returned persisted fallback sponsor detection data
- Prometheus returned:
  - `streamsense_sponsor_fallback_total{reason="MlDependencyException"}`
- brought `ml-engine` back and verified `http://localhost:8000/ml/health`

## Important Runtime Notes

- `video-service` shares the same Postgres schema with `sentiment-service`, so a service-specific Flyway history table was needed to avoid migration-version collisions during Compose startup
- the runtime config for `video-service` now uses:
  - `spring.flyway.table = video_service_flyway_history`
  - `spring.flyway.baseline-on-migrate = true`
  - `spring.flyway.baseline-version = 0`
- during live verification, `video-service` had to be restarted after `config-server` was healthy so it would not fall back to incomplete local defaults
- the frontend-proxied GraphQL websocket path was used for live sponsor verification, which proved both gateway and frontend proxy routing for Sprint 5

## Net Effect

Sprint 5 is functionally complete:

- `video-service` now owns a working video sponsor pipeline
- sponsor detections persist and stream live
- GraphQL exposes both sponsor history and live sponsor updates
- the frontend renders sponsor analytics separately from sentiment analytics
- degraded sponsor behavior is explicit and visible instead of silent
