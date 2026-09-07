# Phase 2: Twitch Video Capture And Real Frame Sponsor Pipeline

## Phase Goal

Phase 2 replaces synthetic frame posts with automatic Twitch live video frame sampling while preserving the existing StreamSense video processing boundary.

By the end of this phase, a configured live Twitch channel should produce sampled frame artifacts that flow through:

```text
Twitch live video/HLS -> video-capture worker -> frame artifact storage -> Kafka stream.video.frames -> video-service -> ml-engine -> Postgres -> Kafka stream.sponsor.detections -> api-gateway -> GraphQL subscriptions/frontend
```

This phase proves that the sponsor pipeline is driven by real Twitch video frames instead of manually posted `frameRef` strings.

Phase 2 should not attempt to fully solve product metric aggregation, campaign-aware recommendations, real auth, tenant isolation, or a production-grade logo detection model. Those remain later phases. Phase 2 should, however, make the sponsor inference call image-aware enough to prove the ML service can resolve and inspect actual captured frame artifacts.

## Current Starting Point

Already available in the repo:

- `video-service` exposes `POST /api/video/upload-frame` for synthetic/reference frame payloads.
- `FrameUploadRequest` contains `streamer`, `frameRef`, `frameSequence`, and `capturedAt`.
- `video-service` publishes `FrameData` to Kafka topic `stream.video.frames`.
- `video-service` also consumes `stream.video.frames` and calls `ml-engine` through `POST /ml/sponsor`.
- `ml-engine` currently returns deterministic sponsor detections based on `streamer`, `frameRef`, and `frameSequence`.
- `video-service` persists `SponsorDetectionEvent` rows in Postgres.
- `video-service` publishes detections to Kafka topic `stream.sponsor.detections`.
- `api-gateway` consumes sponsor detections and exposes `sponsorDetections(streamer, limit)` plus `onSponsorDetection(streamer)`.
- `frontend` renders a sponsor panel with history, live subscription updates, confidence, fallback count, model version, and bounding box fields.
- `docs/contracts/sponsor-pipeline.md` documents the current reference-based sponsor pipeline contract.
- Phase 1 added real Twitch chat ingestion and status visibility while keeping synthetic paths available.

Main missing capability:

- There is no Twitch video/HLS capture worker.
- There is no frame artifact storage path.
- `FrameData.frameRef` is only a logical string, not a reference to a captured frame artifact.
- `ml-engine` does not fetch, decode, or validate real image data.
- There is no video capture status endpoint, capture metrics, or dashboard state.
- There is no controlled frame sampling cadence or backpressure policy.

## Phase 2 Target Behavior

Given a live Twitch channel already configured for Phase 1, such as `austincs` or another active test channel:

1. StreamSense resolves or accepts the active Twitch channel login.
2. A video capture worker resolves a playable Twitch live stream source.
3. The worker samples frames at a configured cadence, for example one frame every 5 to 15 seconds.
4. Each sampled frame is stored as an artifact in local object storage or a local filesystem-backed development equivalent.
5. The worker publishes a normalized `FrameData` event to `stream.video.frames` for each stored artifact.
6. `video-service` consumes the real frame event through the same Kafka path it already owns.
7. `video-service` calls `ml-engine` with the frame reference and metadata.
8. `ml-engine` resolves the artifact, verifies it can open the image, and returns a sponsor detection response.
9. `video-service` persists and publishes `SponsorDetectionEvent` records tied to the original captured frame.
10. `api-gateway` GraphQL sponsor history and subscriptions emit real Twitch-derived sponsor detection events.
11. `frontend` shows that sponsor detections are coming from live Twitch video capture, not the mock upload-frame path.
12. Operators can see capture status, frame rate, skipped frames, storage failures, Kafka publish failures, processing lag, and fallback detections.

## Relationship To Phase 1

Phase 1 proved real Twitch chat ingestion with the existing `streamer` key preserved as the Twitch channel login.

Phase 2 should reuse that same channel login as the first join key so the frontend can keep selecting one active streamer. A full `streamSessionId` model is still required by the production plan, but Phase 2 should introduce session-aware video fields in a compatible way instead of blocking all video work on a complete cross-service session model.

Recommended Phase 2 identity approach:

- Keep `streamer` as the required channel login for backward compatibility.
- Add optional production identity fields to video contracts where needed.
- Prefer a generated `captureSessionId` or `streamSessionId` in the capture worker status and emitted events.
- Use Twitch stream ID from Helix when credentials are available.
- If Helix is not configured, generate a local session ID from `channelLogin + captureStartTime` and mark `twitchStreamId` as absent.

## Ownership Split

### Work I Can Do In The Repo

I can implement and test the repository changes:

- Add a dedicated video capture worker service or a capture module inside `video-service`.
- Add config properties for Twitch video capture, frame cadence, storage, and backpressure.
- Add Docker Compose and Kubernetes wiring for the capture worker and storage.
- Add local object storage or filesystem-backed frame artifact storage.
- Publish real `FrameData` events to Kafka from captured frame artifacts.
- Extend video event contracts and schema docs safely.
- Update `video-service` processing to carry new optional frame/session metadata into sponsor detections.
- Update `ml-engine` to resolve and validate real frame artifacts before returning a detection.
- Add capture status and metrics.
- Add frontend video ingestion status and clearer sponsor panel labeling.
- Add tests with mocked Twitch/HLS/frame sources and local fixture images.
- Update runbooks and smoke checks.

### Work You Need To Provide Or Decide

You need to provide product/runtime decisions that depend on external access or deployment preference:

- Confirm the Twitch channel to use for video verification.
- Confirm whether that channel will be live during manual verification.
- Confirm whether public Twitch stream access is enough, or whether OAuth-authenticated video access is required.
- Decide whether local frame artifacts should use MinIO or a mounted local filesystem for Phase 2.
- Decide acceptable frame sampling cadence for local testing.
- Decide acceptable raw frame retention policy for local/dev.
- Confirm whether screenshots/frame artifacts may contain user-visible personal data and whether redaction is needed before persistence.
- Confirm whether Phase 2 should add a new `video-capture-service` or embed capture inside `video-service`.

Do not commit Twitch secrets or OAuth tokens. If video access requires credentials, pass them through ignored local env files, Docker Compose env, Kubernetes secrets, or a future secret manager.

## Key Product Decisions Before Implementation

### Decision 1: Capture Worker Location

Recommended choice for Phase 2:

- Add a dedicated `video-capture-service` or `video-capture-worker`.

Reason:

- Video capture is operationally different from frame processing.
- HLS resolution and frame extraction require native binaries such as `ffmpeg` and likely `streamlink` or a similar tool.
- Capture can hang, reconnect, consume CPU, or be rate-limited independently from `video-service`.
- Keeping capture separate protects `video-service` API, Kafka consumer, persistence, and sponsor processing from capture process crashes.
- It creates a clean future path for scaling capture workers separately from sponsor inference.

Alternative:

- Embed the capture lifecycle inside `video-service`.

Tradeoff:

- Fewer service directories and manifests, but worse fault isolation and a heavier Java runtime image.

Phase 2 default unless you choose otherwise:

- Add a dedicated worker that publishes `FrameData` to the existing Kafka topic.

### Decision 2: Twitch Video Source Resolver

Recommended choice for Phase 2:

- Use `streamlink` to resolve a Twitch live channel into an HLS stream URL, then use `ffmpeg` to sample frames.

Reason:

- Twitch public playback URLs are not exposed through the same simple official API path as chat.
- `streamlink` is a pragmatic, widely used way to resolve public Twitch live streams for tooling.
- `ffmpeg` is the standard frame extraction tool.
- This approach avoids building a fragile Twitch playback implementation from scratch.

Alternative:

- Use a Twitch Helix/EventSub-driven lifecycle resolver plus a custom HLS pull implementation.

Tradeoff:

- More control long term, but more initial fragility and likely still requires HLS/video tooling.

Phase 2 default:

- `streamlink https://twitch.tv/{channel} best --stream-url` to resolve stream URL.
- `ffmpeg` to extract one JPEG/PNG frame at a configured cadence.
- OAuth can be passed to `streamlink` only if required.

### Decision 3: Frame Artifact Storage

Recommended choice for Phase 2:

- Add MinIO for Docker Compose and Kubernetes-local development, using S3-compatible references.

Reason:

- Production will likely use S3, GCS, Azure Blob, or another object store.
- MinIO exercises object storage semantics locally instead of relying on container-local paths.
- It lets `video-capture-service`, `video-service`, and `ml-engine` resolve the same artifact references without shared volume coupling.

Alternative:

- Store frames on a mounted local filesystem path shared by capture, video, and ML containers.

Tradeoff:

- Faster to implement, but less production-like and easier to break in Docker/Kubernetes.

Phase 2 default:

- Use MinIO locally.
- Define frame refs as `s3://streamsense-frames/{channel}/{captureSessionId}/{frameId}.jpg` or an equivalent internal URI.
- Keep a local filesystem storage adapter only for tests.

### Decision 4: Sampling Cadence And Backpressure

Recommended local default:

- Capture one frame every 10 seconds per channel.
- Allow config range from 5 seconds to 60 seconds.
- Start with one channel.

Reason:

- This proves the end-to-end video path without overwhelming Kafka, object storage, ML inference, or local laptops.
- A 10 second cadence gives visible frontend updates during manual verification.

Backpressure requirements:

- Do not start a new capture if the previous capture is still storing/publishing unless explicitly configured.
- Skip frames rather than building an unbounded backlog.
- Track skipped frames and reasons.
- Enforce max in-flight captures per channel.
- Stop sampling if Kafka publish failures exceed threshold until reconnect/backoff succeeds.

### Decision 5: ML Realism Boundary

Recommended Phase 2 boundary:

- Make `ml-engine` image-aware by resolving and opening the real frame artifact.
- Keep the actual sponsor classification deterministic or fixture-based for Phase 2.
- Defer real logo/object detection model replacement to the ML realism phase.

Reason:

- Phase 2's core gap is real Twitch frame sampling and artifact flow.
- Real logo detection is a separate model operations problem requiring model choice, artifacts, thresholds, and benchmarks.
- The important Phase 2 proof is that detections are produced from real captured frames, and bad/missing frames produce visible fallback behavior.

Acceptance for this decision:

- `ml-engine` must fail or fallback if the frame artifact cannot be fetched or decoded.
- `ml-engine` must include a model version that distinguishes image-aware stub behavior, for example `frame-aware-stub-v1`.
- Tests must use real fixture image files.

## Proposed Architecture

### New Service

Add a service with a name like:

```text
video-capture-service/
```

Recommended implementation language:

- Python 3.11.

Reason:

- The worker primarily orchestrates subprocesses, storage, and Kafka publishing.
- `streamlink`, `ffmpeg`, and S3 clients are straightforward in Python.
- It avoids adding native video tooling to the Java `video-service` image.

Expected dependencies:

```text
confluent-kafka or kafka-python
boto3
prometheus-client
fastapi
uvicorn
pydantic
pytest
streamlink
```

Expected system binaries in Docker image:

```text
ffmpeg
```

If we want to avoid a Python worker, the alternative is a Java worker with a process manager around `streamlink` and `ffmpeg`, plus AWS SDK or MinIO client. That is viable but less ergonomic.

### New Local Storage

Add MinIO to Docker Compose:

```text
minio:9000
minio-console:9001
bucket: streamsense-frames
```

Add equivalent local Kubernetes manifests if Phase 2 touches `k8s/`:

- MinIO Deployment/Service or documented external object store placeholder.
- Secret or ConfigMap entries for endpoint and bucket.
- Capture worker env vars for object storage.
- `ml-engine` env vars for object storage reads.

### Data Flow

Expected runtime flow:

```text
video-capture-service
  -> resolves Twitch HLS for configured channel
  -> samples frame with ffmpeg
  -> writes frame artifact to object storage
  -> publishes FrameData to stream.video.frames

video-service
  -> consumes FrameData
  -> calls ml-engine /ml/sponsor
  -> persists SponsorDetectionEvent
  -> publishes stream.sponsor.detections

api-gateway
  -> consumes sponsor detections for subscriptions
  -> queries video-service for recent sponsor detections

frontend
  -> displays video capture state and live sponsor detections
```

### Status Flow

Add a status endpoint on the capture worker:

```text
GET /api/video/capture/status
```

Expose it through `api-gateway` under `/api/**`, or call it directly through compose during early verification.

Possible response:

```json
{
  "enabled": true,
  "state": "CAPTURING",
  "channels": ["austincs"],
  "captureSessionId": "austincs-1778000000000",
  "lastFrameAt": 1778000123456,
  "lastFrameRef": "s3://streamsense-frames/austincs/austincs-1778000000000/frame-000001.jpg",
  "lastError": null,
  "framesCaptured": 12,
  "framesSkipped": 1,
  "reconnectAttempts": 0
}
```

Status states:

```text
DISABLED
STARTING
RESOLVING_STREAM
CAPTURING
IDLE_OFFLINE
RECONNECTING
DEGRADED_STORAGE
DEGRADED_KAFKA
FAILED
STOPPED
```

## Event Contract Plan

### Current `FrameData`

Current required fields:

```json
{
  "frameId": "string",
  "streamer": "string",
  "frameRef": "string",
  "frameSequence": 1,
  "capturedAt": 1710000000000
}
```

### Recommended Phase 2 `FrameData`

Keep existing fields required and add optional production fields:

```json
{
  "frameId": "string",
  "streamer": "string",
  "frameRef": "s3://streamsense-frames/austincs/session/frame-000001.jpg",
  "frameSequence": 1,
  "capturedAt": 1710000000000,
  "source": "TWITCH",
  "channelLogin": "austincs",
  "streamSessionId": "austincs-1710000000000",
  "twitchStreamId": "optional twitch stream id",
  "videoTimestampMs": 10000,
  "artifactContentType": "image/jpeg",
  "artifactSizeBytes": 145231,
  "captureWorkerId": "video-capture-service-1"
}
```

Compatibility rules:

- Synthetic `POST /api/video/upload-frame` continues to publish the old fields.
- Existing consumers must continue to process old events.
- New fields should be nullable/optional in Java and GraphQL until a later contract version makes session identity mandatory.
- Kafka keys should remain `streamer` initially unless `streamSessionId` is available; if present, prefer `streamSessionId` for producer key only after validating subscription/history behavior.
- Schema contract tests must be updated intentionally if new fields are added.

### Recommended Phase 2 `SponsorDetectionEvent`

Keep existing fields required and carry through optional frame/session fields:

```json
{
  "detectionEventId": "string",
  "sourceFrameId": "string",
  "streamer": "string",
  "frameRef": "s3://streamsense-frames/austincs/session/frame-000001.jpg",
  "frameSequence": 1,
  "capturedAt": 1710000000000,
  "processedAt": 1710000000500,
  "sponsor": "Nike",
  "confidence": 0.91,
  "modelVersion": "frame-aware-stub-v1",
  "x": 0.12,
  "y": 0.18,
  "width": 0.31,
  "height": 0.24,
  "source": "TWITCH",
  "channelLogin": "austincs",
  "streamSessionId": "austincs-1710000000000",
  "twitchStreamId": "optional twitch stream id",
  "videoTimestampMs": 10000
}
```

Compatibility rules:

- Frontend can initially ignore optional fields except for display labels.
- GraphQL can expose optional fields without breaking existing queries.
- Persistence migration should add nullable columns for optional fields.
- Existing rows remain readable.

## Configuration Plan

### Capture Worker Config

Proposed config shape:

```yaml
streamsense:
  twitch:
    video:
      enabled: false
      channels: []
      quality: best
      sample-interval-seconds: 10
      stream-resolve-timeout-seconds: 20
      frame-capture-timeout-seconds: 15
      reconnect-delay-seconds: 5
      max-reconnect-delay-seconds: 60
      max-consecutive-failures: 5
      max-in-flight-per-channel: 1
      output-format: jpg
      jpeg-quality: 85
  frames:
    storage:
      backend: s3
      bucket: streamsense-frames
      endpoint: http://minio:9000
      region: us-east-1
      access-key: ${STREAMSENSE_FRAME_STORAGE_ACCESS_KEY:streamsense}
      secret-key: ${STREAMSENSE_FRAME_STORAGE_SECRET_KEY:streamsense}
      path-prefix: twitch
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:kafka:9092}
  topics:
    videoFrames: stream.video.frames
```

Recommended env vars:

```bash
STREAMSENSE_TWITCH_VIDEO_ENABLED=true
TWITCH_VIDEO_CHANNELS=austincs
TWITCH_VIDEO_QUALITY=best
TWITCH_VIDEO_SAMPLE_INTERVAL_SECONDS=10
STREAMSENSE_FRAME_STORAGE_BUCKET=streamsense-frames
STREAMSENSE_FRAME_STORAGE_ENDPOINT=http://minio:9000
STREAMSENSE_FRAME_STORAGE_ACCESS_KEY=streamsense
STREAMSENSE_FRAME_STORAGE_SECRET_KEY=streamsense
```

If `streamlink` requires Twitch OAuth:

```bash
TWITCH_VIDEO_OAUTH_TOKEN=oauth:...
```

### `video-service` Config

Add or update:

```yaml
streamsense:
  payload:
    maxFrameRefLength: 1024
  frames:
    storage:
      backend: s3
      bucket: streamsense-frames
      endpoint: http://minio:9000
```

Reason:

- Object-store frame references may exceed the current 512 character limit depending on bucket/prefix/session naming.
- `video-service` may not need to read frames directly in Phase 2 if `ml-engine` owns artifact reads, but it should validate and preserve refs safely.

### `ml-engine` Config

Add:

```bash
STREAMSENSE_FRAME_STORAGE_BACKEND=s3
STREAMSENSE_FRAME_STORAGE_BUCKET=streamsense-frames
STREAMSENSE_FRAME_STORAGE_ENDPOINT=http://minio:9000
STREAMSENSE_FRAME_STORAGE_ACCESS_KEY=streamsense
STREAMSENSE_FRAME_STORAGE_SECRET_KEY=streamsense
STREAMSENSE_SPONSOR_REQUIRE_FRAME_READ=true
```

Reason:

- `ml-engine` must be able to fetch and decode captured frame artifacts.
- `STREAMSENSE_SPONSOR_REQUIRE_FRAME_READ=true` prevents silently returning deterministic results for missing artifacts during real video verification.

## Implementation Plan From My End

### Step 1: Add A Phase 2 Capture Worker Skeleton

Files likely involved:

```text
video-capture-service/
docker-compose.yml
makefile
config-server/config-repo/ possibly if the worker uses config-server later
k8s/ if Kubernetes wiring is included in the same PR
```

Worker responsibilities:

- Load config from env vars.
- Validate required fields only when video capture is enabled.
- Expose `/health` and `/api/video/capture/status`.
- Run one capture loop per configured channel.
- Track channel state independently.
- Publish Prometheus metrics.
- Shut down subprocesses cleanly.

Acceptance criteria:

- Worker starts disabled by default.
- Worker status reports `DISABLED` without Twitch or storage credentials.
- Worker fails fast with clear errors if enabled without channel/storage/Kafka config.
- Unit tests cover config validation and disabled startup.

### Step 2: Add Frame Storage Adapter

Files likely involved:

```text
video-capture-service/src/.../storage.py
video-capture-service/tests/.../test_storage.py
docker-compose.yml
```

Add adapters:

- S3-compatible adapter for MinIO.
- Filesystem adapter for tests and fallback local development.

Storage behavior:

- Store each frame with deterministic path structure.
- Return a stable `frameRef` URI.
- Record content type, size, checksum, and store latency.
- Avoid overwriting existing frames.
- Surface storage failures to status and metrics.

Recommended object key:

```text
twitch/{channelLogin}/{captureSessionId}/{frameSequence}-{frameId}.jpg
```

Acceptance criteria:

- Test fixture image can be stored and read back.
- MinIO bucket is created during local startup or documented bootstrap.
- Storage credentials are never logged.

### Step 3: Add Twitch HLS Resolver

Files likely involved:

```text
video-capture-service/src/.../twitch_source.py
video-capture-service/tests/.../test_twitch_source.py
```

Responsibilities:

- Use `streamlink` to resolve the configured Twitch channel.
- Detect offline channel versus resolver failure.
- Support quality config such as `best`, `720p`, or `480p`.
- Redact OAuth/token values from logs and status.
- Apply timeout to resolver subprocess.

Possible command:

```bash
streamlink --stream-url https://www.twitch.tv/${channel} best
```

If OAuth is needed:

```bash
streamlink --twitch-api-header "Authorization=OAuth ${token}" --stream-url https://www.twitch.tv/${channel} best
```

Acceptance criteria:

- Offline channel maps to `IDLE_OFFLINE` and does not crash the worker.
- Invalid channel or resolver failure maps to `RECONNECTING` or `FAILED` with a safe error.
- Unit tests mock subprocess output and failure cases.

### Step 4: Add Frame Sampler

Files likely involved:

```text
video-capture-service/src/.../frame_sampler.py
video-capture-service/tests/.../test_frame_sampler.py
```

Responsibilities:

- Use `ffmpeg` to sample a single frame from the resolved HLS URL.
- Write the captured frame to a temporary local path.
- Validate output file exists and is non-empty.
- Optionally validate image dimensions using Pillow or ffprobe.
- Clean temporary files after storage succeeds or fails.
- Enforce capture timeout.

Possible command:

```bash
ffmpeg -y -i "${hls_url}" -frames:v 1 -q:v 3 /tmp/streamsense-frame.jpg
```

Better command after testing live Twitch latency:

```bash
ffmpeg -y -loglevel warning -rw_timeout 15000000 -i "${hls_url}" -frames:v 1 -q:v 3 /tmp/streamsense-frame.jpg
```

Acceptance criteria:

- Unit tests do not require real Twitch; they use a fixture input or mock subprocess.
- Manual test with a live Twitch channel produces a JPEG frame artifact.
- Capture timeout does not leave orphaned subprocesses.

### Step 5: Publish `FrameData` From Captured Artifacts

Files likely involved:

```text
video-capture-service/src/.../kafka_publisher.py
video-service/src/main/java/com/streamsense/videoservice/events/FrameData.java
docs/schemas/frame-data.schema.json
docs/contracts/sponsor-pipeline.md
```

Responsibilities:

- Generate `frameId` as UUID or deterministic session sequence ID.
- Maintain monotonic `frameSequence` per channel/session.
- Set `streamer` to the Twitch channel login.
- Set `frameRef` to the stored artifact URI.
- Set `capturedAt` to the actual frame capture time.
- Add optional session/source metadata if implemented in this phase.
- Publish to `stream.video.frames` with key `streamer` or `streamSessionId`.
- Include correlation/trace headers if the worker supports tracing.

Acceptance criteria:

- `stream.video.frames` receives events without calling `POST /api/video/upload-frame`.
- Existing `video-service` consumer processes worker-produced events.
- Synthetic upload-frame path still works.
- Schema docs and contract tests match the emitted shape.

### Step 6: Make `ml-engine` Frame-Artifact Aware

Files likely involved:

```text
ml-engine/src/main/python/app/models.py
ml-engine/src/main/python/app/main.py
ml-engine/src/main/python/app/sponsor.py
ml-engine/src/main/python/app/frame_store.py
ml-engine/requirements.txt
ml-engine/src/test/python/
```

Responsibilities:

- Parse `frameRef` values that point to S3/MinIO or filesystem fixtures.
- Fetch the frame bytes.
- Decode or validate the image file.
- Include frame dimensions/checksum in internal inference logic or logs.
- Return fallback if the frame cannot be fetched or decoded and fallback mode is enabled.
- Return error if `STREAMSENSE_SPONSOR_REQUIRE_FRAME_READ=true` and the frame is missing/invalid.
- Keep response shape compatible with `MlSponsorResponse`.

Recommended Phase 2 behavior:

- Use a deterministic image-aware stub based on frame checksum and dimensions.
- Return `modelVersion="frame-aware-stub-v1"`.
- Defer real logo/object detection to the ML realism phase.

Acceptance criteria:

- Existing sponsor tests still pass with synthetic `frameRef` when frame read is not required.
- New tests verify real fixture image read.
- New tests verify missing image fallback/error behavior.
- Manual Twitch capture produces sponsor detections where `modelVersion` proves the frame-aware path was used.

### Step 7: Carry Optional Session/Frame Metadata Through `video-service`

Files likely involved:

```text
video-service/src/main/java/com/streamsense/videoservice/events/FrameData.java
video-service/src/main/java/com/streamsense/videoservice/events/SponsorDetectionEvent.java
video-service/src/main/java/com/streamsense/videoservice/dto/MlSponsorRequest.java
video-service/src/main/java/com/streamsense/videoservice/persistence/SponsorDetectionEntity.java
video-service/src/main/resources/db/migration/
api-gateway/src/main/java/com/streamsense/apigateway/events/SponsorDetectionEvent.java
api-gateway/src/main/resources/graphql/sponsor.graphqls
frontend/src/components/SponsorPanel.tsx
```

Recommended new nullable fields:

```text
source
channelLogin
streamSessionId
twitchStreamId
videoTimestampMs
artifactContentType
artifactSizeBytes
captureWorkerId
```

Minimum Phase 2 carry-through:

- `source`
- `channelLogin`
- `streamSessionId`
- `videoTimestampMs`

Persistence migration:

- Add nullable columns to `sponsor_detections`.
- Add index on `(streamer, captured_at desc)` if not already sufficient.
- Add future-friendly index on `(stream_session_id, captured_at desc)` where session ID is non-null.

Acceptance criteria:

- Old sponsor rows still read correctly.
- New Twitch frame detections preserve frame/session metadata through GraphQL.
- Frontend can display live source/session labels without breaking old rows.

### Step 8: Add Capture Metrics And Logs

Capture worker Prometheus metrics:

```text
streamsense_twitch_video_capture_enabled
streamsense_twitch_video_capture_state
streamsense_twitch_video_frames_captured_total
streamsense_twitch_video_frames_stored_total
streamsense_twitch_video_frames_published_total
streamsense_twitch_video_frames_skipped_total
streamsense_twitch_video_capture_errors_total
streamsense_twitch_video_storage_errors_total
streamsense_twitch_video_kafka_publish_errors_total
streamsense_twitch_video_reconnects_total
streamsense_twitch_video_last_frame_age_seconds
streamsense_twitch_video_capture_latency_ms
streamsense_twitch_video_storage_latency_ms
streamsense_twitch_video_publish_latency_ms
```

Video-service metric additions:

```text
streamsense_video_frames_from_twitch_total
streamsense_video_frame_processing_lag_ms
streamsense_sponsor_frame_artifact_failures_total
```

Log requirements:

- Log capture lifecycle transitions.
- Log sampled frame ID, channel, sequence, and size.
- Log resolver failures without secrets.
- Log storage failures with bucket/key but without credentials.
- Log Kafka publish failures with topic and channel.

Acceptance criteria:

- Prometheus can query capture and video processing health.
- Logs identify where the path is broken: resolver, ffmpeg, storage, Kafka, video-service, ML, or GraphQL.

### Step 9: Add Runtime Control And Status Surfaces

Minimum backend endpoints:

```text
GET /api/video/capture/status
```

Possible optional endpoints:

```text
POST /api/video/capture/start
POST /api/video/capture/stop
POST /api/video/capture/restart
```

Recommended Phase 2 minimum:

- Status endpoint only.
- Capture starts from config when enabled.
- Runtime control can wait until frontend channel setup exists.

Frontend additions:

- Add a video capture status pill near the Twitch chat status pill.
- Distinguish `video disabled`, `offline`, `capturing`, `degraded`, and `failed` states.
- Show last frame age and capture session/channel.
- Update sponsor panel copy from mock/demo wording to live capture wording when enabled.

Acceptance criteria:

- User can tell whether sponsor detections are fed by live video capture.
- Existing sponsor panel still works with synthetic demo data.
- Frontend tests cover disabled, capturing, and failed states.

### Step 10: Update Compose, Kubernetes, Make, And Docs

Files likely involved:

```text
docker-compose.yml
makefile
k8s/
config-server/config-repo/
k8s/config/config-server-config-repo.yaml
docs/howtorun.md
docs/contracts/sponsor-pipeline.md
production-plan.md if implementation changes roadmap wording
```

Compose additions:

- `minio`
- `video-capture-service`
- env wiring for capture worker
- object storage env wiring for `ml-engine`
- health checks where practical

Make target additions:

```text
make twitch-video-up
make twitch-video-status
make twitch-video-smoke
```

Docs additions:

- Required local env vars.
- How to start stack with Twitch chat and video enabled.
- How to open MinIO console.
- How to verify a frame artifact exists.
- How to verify Kafka `stream.video.frames` receives real events.
- How to verify GraphQL `onSponsorDetection` receives real Twitch-derived detections.
- Troubleshooting offline channels, streamlink failures, ffmpeg failures, object storage failures, and no sponsor detections.

Acceptance criteria:

- A developer with Docker, Twitch access, and a live channel can follow docs without reading code.
- `kubectl kustomize k8s` passes if k8s manifests are touched.

## Required Work From Your End

### 1. Live Twitch Channel

You need to provide or choose a Twitch channel that is live during manual verification.

Needed value:

```bash
TWITCH_VIDEO_CHANNELS=austincs
```

Recommended:

- Use the same channel as Phase 1 when it is live and active.
- Use your own test stream if you need deterministic visual content.
- Use a public active channel for passive proof if sponsor/logo accuracy is not the focus.

### 2. Capture Cadence

You need to confirm how aggressive capture should be for local testing.

Recommended default:

```bash
TWITCH_VIDEO_SAMPLE_INTERVAL_SECONDS=10
```

Practical options:

- `5`: faster UI proof, more CPU/storage/ML load.
- `10`: recommended balance.
- `30`: lighter load, slower proof.

### 3. Storage Backend

You need to choose whether Phase 2 local development uses MinIO or filesystem storage.

Recommended default:

- MinIO.

Reason:

- It is closer to production object storage and lets services share artifacts cleanly.

### 4. Twitch Video Access

You need to confirm whether public access is enough.

Likely Phase 2 default:

- Public stream access via `streamlink`.

If your target channel requires OAuth or subscriber-only access, provide an appropriate token through ignored local env only.

### 5. Raw Frame Retention

You need to confirm whether local captured frames can be retained in MinIO during development.

Recommended Phase 2 default:

- Retain frames locally for manual inspection.
- Document that production retention/deletion policy is required before public launch.
- Add a cleanup command or lifecycle setting after capture is stable.

## Local Environment Variables

Proposed `.env.twitch.local` additions:

```bash
STREAMSENSE_TWITCH_VIDEO_ENABLED=true
TWITCH_VIDEO_CHANNELS=austincs
TWITCH_VIDEO_QUALITY=best
TWITCH_VIDEO_SAMPLE_INTERVAL_SECONDS=10
STREAMSENSE_FRAME_STORAGE_BACKEND=s3
STREAMSENSE_FRAME_STORAGE_BUCKET=streamsense-frames
STREAMSENSE_FRAME_STORAGE_ENDPOINT=http://minio:9000
STREAMSENSE_FRAME_STORAGE_ACCESS_KEY=streamsense
STREAMSENSE_FRAME_STORAGE_SECRET_KEY=streamsense
STREAMSENSE_SPONSOR_REQUIRE_FRAME_READ=true
```

Optional if needed:

```bash
TWITCH_VIDEO_OAUTH_TOKEN=oauth:your_token_here
```

Security notes:

- Do not commit real OAuth tokens.
- Do not log storage secrets.
- Do not include captured frames in git.
- Treat captured frames as potentially sensitive content.

## Expected Code-Level Changes

Likely touched areas:

```text
video-capture-service/
video-service/src/main/java/com/streamsense/videoservice/events/FrameData.java
video-service/src/main/java/com/streamsense/videoservice/events/SponsorDetectionEvent.java
video-service/src/main/java/com/streamsense/videoservice/service/VideoProcessingService.java
video-service/src/main/java/com/streamsense/videoservice/persistence/SponsorDetectionEntity.java
video-service/src/main/resources/db/migration/
video-service/src/test/java/
ml-engine/src/main/python/app/
ml-engine/src/test/python/
api-gateway/src/main/java/com/streamsense/apigateway/events/SponsorDetectionEvent.java
api-gateway/src/main/resources/graphql/sponsor.graphqls
frontend/src/components/
frontend/src/graphql/
docker-compose.yml
k8s/
config-server/config-repo/video-service.yml
k8s/config/config-server-config-repo.yaml
docs/howtorun.md
docs/contracts/sponsor-pipeline.md
docs/schemas/frame-data.schema.json
docs/schemas/sponsor-detection-event.schema.json
makefile
```

Expected new package/service shape:

```text
video-capture-service/
  Dockerfile
  requirements.txt
  src/main/python/app/main.py
  src/main/python/app/config.py
  src/main/python/app/status.py
  src/main/python/app/twitch_source.py
  src/main/python/app/frame_sampler.py
  src/main/python/app/storage.py
  src/main/python/app/kafka_publisher.py
  src/main/python/app/capture_loop.py
  src/test/python/
```

Expected reuse of existing code:

- Existing `stream.video.frames` topic remains the frame ingress topic.
- Existing `video-service` Kafka consumer remains the sponsor processing boundary.
- Existing `POST /api/video/upload-frame` remains available for tests, local demos, and fallback.
- Existing `stream.sponsor.detections` topic remains the sponsor detection topic.
- Existing GraphQL sponsor query/subscription names remain available.

## Testing Plan

### Automated Tests I Should Add

Capture worker unit tests:

- Config validation passes when disabled with no Twitch/storage config.
- Config validation fails clearly when enabled without channel.
- Config validation fails clearly when enabled without storage config.
- Twitch resolver handles stream URL output.
- Twitch resolver handles offline channel output.
- Twitch resolver handles timeout.
- Frame sampler handles successful `ffmpeg` output.
- Frame sampler handles empty output.
- Frame sampler kills timed-out process.
- Storage adapter writes fixture image and returns expected `frameRef`.
- Kafka publisher serializes expected `FrameData` shape.
- Capture loop skips frame when previous capture is in flight.
- Capture loop updates status after storage failure.
- Capture loop updates status after Kafka failure.

Video-service tests:

- `FrameData` with old shape still processes.
- `FrameData` with Twitch metadata processes.
- Sponsor detection carries optional session/source metadata.
- Persistence migration stores optional metadata.
- Recent detections return old and new rows.
- Schema contract tests match updated docs.
- Synthetic `POST /api/video/upload-frame` still publishes valid frame events.

ML tests:

- `/ml/sponsor` works in synthetic mode without requiring frame read.
- `/ml/sponsor` reads a real fixture image from filesystem storage.
- `/ml/sponsor` reads a real fixture image from mocked S3/MinIO storage if practical.
- Missing frame returns fallback or error depending on `STREAMSENSE_SPONSOR_REQUIRE_FRAME_READ`.
- Invalid image returns fallback or error depending on config.
- Response remains compatible with existing Java DTO.

Gateway/frontend tests:

- GraphQL sponsor type exposes optional fields if added.
- `onSponsorDetection(streamer)` still filters by streamer.
- Frontend sponsor panel renders Twitch/session labels when present.
- Frontend video capture status pill renders disabled, capturing, and failed states.

### Automated Commands To Run

Java:

```bash
cd video-service && mvn -B -ntp clean test
cd api-gateway && mvn -B -ntp test
```

ML:

```bash
cd ml-engine && pip install -r requirements.txt ruff && ruff check src/main/python src/test/python && PYTHONPATH=src/main/python pytest src/test/python
```

Frontend:

```bash
cd frontend && npm run lint && npm run test -- --run && npm run build
```

Kubernetes if touched:

```bash
kubectl kustomize k8s
```

Contract/whitespace:

```bash
git diff --check
```

### Manual Verification Tests

Test 1: Disabled regression

```bash
make smoke-e2e
```

Expected:

- Existing synthetic chat and synthetic frame path still pass.
- Video capture worker is disabled or absent from the smoke path.

Test 2: Start stack with Twitch chat and video enabled

```bash
make twitch-video-up
```

Expected:

- Docker Compose starts MinIO, video-capture-service, video-service, ml-engine, Kafka, gateway, and frontend.
- Twitch chat remains connected if Phase 1 env is present.
- Video capture status moves from `STARTING` to `CAPTURING` or `IDLE_OFFLINE`.

Test 3: Confirm capture status

```bash
curl -fsS http://localhost:8080/api/video/capture/status
```

Expected when channel is live:

```json
{
  "enabled": true,
  "state": "CAPTURING"
}
```

Test 4: Confirm frame artifact exists

Options:

```bash
docker compose logs video-capture-service
```

or inspect MinIO console:

```text
http://localhost:9001
```

Expected:

- A JPEG/PNG frame exists under the expected bucket/key.
- The object size is non-zero.

Test 5: Confirm Kafka frame events

```bash
docker compose exec kafka kafka-console-consumer \
  --bootstrap-server kafka:9092 \
  --topic stream.video.frames \
  --from-beginning \
  --timeout-ms 10000
```

Expected:

- Events include `frameRef` pointing to stored Twitch frame artifacts.
- Events arrive without calling `POST /api/video/upload-frame`.

Test 6: Confirm sponsor detections

```bash
docker compose exec kafka kafka-console-consumer \
  --bootstrap-server kafka:9092 \
  --topic stream.sponsor.detections \
  --from-beginning \
  --timeout-ms 10000
```

Expected:

- Detections appear for captured frames.
- `modelVersion` is `frame-aware-stub-v1` or the chosen Phase 2 model version.
- Fallback events are visible if frame read fails.

Test 7: Confirm GraphQL sponsor history

```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"query($streamer:String!,$limit:Int!){ sponsorDetections(streamer:$streamer, limit:$limit){ streamer frameRef frameSequence sponsor confidence modelVersion capturedAt processedAt }}","variables":{"streamer":"austincs","limit":10}}'
```

Expected:

- Recent sponsor detections include real captured frame refs.

Test 8: Confirm GraphQL subscription

```bash
npx wscat -c ws://localhost:8080/graphql -s graphql-transport-ws
```

Then:

```json
{"type":"connection_init"}
```

Then:

```json
{
  "id":"1",
  "type":"subscribe",
  "payload":{
    "query":"subscription($streamer:String!){ onSponsorDetection(streamer:$streamer){ detectionEventId streamer frameRef frameSequence sponsor confidence modelVersion capturedAt processedAt } }",
    "variables":{"streamer":"austincs"}
  }
}
```

Expected:

- New sponsor detections stream in as frames are sampled.

Test 9: Confirm frontend

```text
http://localhost:3000
```

Expected:

- Twitch chat status still shows Phase 1 live chat state.
- Video capture status shows `capturing` or a clear offline/degraded state.
- Sponsor panel updates as frames are sampled and processed.
- Copy no longer presents sponsor flow as only a mock workflow when live video is enabled.

Test 10: Confirm metrics

Prometheus queries:

```promql
streamsense_twitch_video_frames_captured_total
streamsense_twitch_video_frames_published_total
streamsense_twitch_video_last_frame_age_seconds
streamsense_frames_ingested_total
streamsense_sponsor_detections_total
streamsense_sponsor_inference_latency_ms_count
```

Expected:

- Capture counters increase.
- Existing video-service frame and sponsor counters increase.
- Last-frame age resets after each successful capture.

## Failure Modes To Handle

Expected failures:

- Twitch channel is offline.
- Twitch channel does not exist.
- Twitch blocks or changes playback resolution behavior.
- `streamlink` cannot resolve HLS.
- `ffmpeg` hangs or exits non-zero.
- Frame output file is empty.
- Object storage is unavailable.
- Object storage credentials are wrong.
- Kafka is unavailable.
- Kafka publish succeeds but video-service is lagging.
- `ml-engine` cannot fetch the frame artifact.
- `ml-engine` cannot decode the frame.
- Sponsor inference is slow or fails.
- Captured frame contains no recognizable sponsor.
- Local disk fills with temporary files.

Required behavior:

- Offline channels should not crash the worker.
- Capture subprocesses must have timeouts.
- Temporary files must be cleaned.
- Storage/Kafka failures should update status and metrics.
- Backoff should be bounded.
- Frame drops should be counted, not hidden.
- Missing or invalid frame artifacts should produce visible fallback/error behavior.
- Existing synthetic frame ingest should remain available even if video capture is down.
- No Twitch tokens or storage secrets should appear in logs.

## Phase 2 Acceptance Criteria

Phase 2 is complete when all of these are true:

- Video capture can be disabled and the existing smoke/demo path still works.
- Video capture can be enabled with environment variables.
- A live Twitch channel produces sampled frame artifacts automatically.
- Frame artifacts are stored in local object storage or the selected local equivalent.
- `stream.video.frames` receives real captured frame events without manual `curl` calls.
- `video-service` consumes captured frame events through the existing Kafka path.
- `ml-engine` verifies actual frame artifacts before returning Phase 2 sponsor detections.
- `stream.sponsor.detections` receives detections tied to captured Twitch frames.
- `sponsorDetections(streamer, limit)` returns Twitch video-derived detections.
- `onSponsorDetection(streamer)` streams Twitch video-derived detections.
- Frontend shows video capture status and sponsor detections for the selected streamer.
- Prometheus exposes capture, storage, publish, lag, and sponsor processing metrics.
- Tests cover capture config, resolver behavior, sampler behavior, storage behavior, Kafka publishing shape, ML frame reads, and existing synthetic regression behavior.
- Documentation explains how to configure, run, verify, and troubleshoot Twitch video capture without committing secrets.

## Out Of Scope For Phase 2

- Full Twitch stream/session domain model across every service.
- EventSub lifecycle automation unless needed for basic stream-online detection.
- Multi-channel capture hardening beyond simple comma-separated config.
- Production-grade logo/object detection model.
- GPU inference.
- Product metric aggregation tables and dashboards.
- Campaign context and recommendation algorithm upgrades.
- User authentication and tenant isolation.
- Production retention/deletion enforcement beyond docs and local cleanup guidance.
- Cloud production deployment of object storage and capture workers.
- Browser-based channel setup/start/stop control.

## Open Questions

1. Should Phase 2 create a dedicated `video-capture-service`, or do you want capture embedded inside `video-service` despite the operational coupling?
2. Should local frame storage use MinIO, or should we start with a shared local filesystem volume?
3. What frame cadence should we use for local proof: 5, 10, or 30 seconds?
4. Which Twitch channel should be used for the first live video verification, and when will it be live?
5. Is public Twitch playback access enough, or do you expect OAuth-authenticated playback to be required?
6. Can local captured frames be retained for inspection during development?
7. Do you want Phase 2 to add optional `streamSessionId` fields now, or keep only `streamer` until the dedicated session phase?
8. Should `ml-engine` hard-fail when a frame artifact cannot be read, or emit fallback sponsor events for dashboard continuity?

## Recommended Defaults

Unless you choose otherwise, use these defaults:

- Add a dedicated Python `video-capture-service`.
- Use `streamlink` for Twitch stream URL resolution.
- Use `ffmpeg` for frame extraction.
- Use MinIO as local S3-compatible frame storage.
- Sample one frame every 10 seconds.
- Start with one configured Twitch channel.
- Keep `streamer` as the required channel login key.
- Add nullable `source`, `channelLogin`, `streamSessionId`, and `videoTimestampMs` fields if the implementation remains contained.
- Keep `POST /api/video/upload-frame` for tests and fallback.
- Make `ml-engine` read and validate actual frame artifacts, but keep sponsor classification deterministic for Phase 2.
- Use fallback sponsor detections for transient ML/frame-read failures unless `STREAMSENSE_SPONSOR_REQUIRE_FRAME_READ=true` is enabled for strict verification.
- Do not store secrets in committed repo files.
