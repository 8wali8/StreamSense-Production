# Phase 2.5: Streamer Transcript And Transcript Sentiment

## Phase Goal

Phase 2.5 adds live streamer speech transcription from Twitch audio and processes that transcript through a separate sentiment pipeline.

By the end of this phase, a configured live Twitch channel should produce persisted transcript text and transcript-derived sentiment that is kept separate from chat sentiment.

Target flow:

```text
Twitch HLS audio -> video-capture-service -> ffmpeg audio chunks -> ml-engine local Whisper -> Kafka stream.transcript.segments -> sentiment-service -> Postgres -> Kafka stream.transcript.sentiment.events -> api-gateway -> GraphQL/frontend
```

This phase should not store raw audio long term. Audio chunks are temporary processing artifacts and should be deleted after transcription succeeds or fails.

## User Decisions

Confirmed product decisions:

- Use local Whisper, not an external transcription API.
- Use Whisper model `small.en` for the first implementation.
- Do not store raw audio chunks long term.
- Persist transcript text long term, similar to chat text.
- Keep transcript sentiment separated from chat sentiment.
- Use `4000` characters as the transcript text max length per segment.
- Expose a short transcript text preview in status surfaces.
- Use the existing sentiment model for transcript sentiment in this phase.
- Continue using the Phase 2 Twitch channel/video infrastructure.

## Current Starting Point

Already available after Phase 2:

- `video-capture-service` resolves Twitch HLS using `streamlink`.
- `video-capture-service` uses `ffmpeg` for real frame extraction.
- `video-capture-service` publishes Kafka events.
- `ml-engine` is the ML boundary and already accepts HTTP calls from Java services.
- `sentiment-service` consumes text events, calls `ml-engine`, persists sentiment, and publishes sentiment events.
- `api-gateway` exposes GraphQL history and subscriptions.
- `frontend` renders live chat, chat sentiment, sponsor detections, and status pills.
- MinIO exists for frame artifacts, but transcript audio should not be retained there by default.

Main missing capability:

- No audio chunk extraction.
- No local Whisper dependency or model loading path.
- No transcription endpoint.
- No transcript event model.
- No transcript persistence.
- No transcript-specific sentiment topic, table, GraphQL surface, or frontend panel.

## Target Behavior

Given a live Twitch channel such as `austincs`:

1. `video-capture-service` resolves the Twitch HLS stream.
2. It extracts short audio chunks using `ffmpeg` at a configured cadence.
3. It sends each chunk to `ml-engine` for local Whisper transcription.
4. It deletes the local audio chunk after transcription succeeds or fails.
5. It publishes non-empty transcript segments to Kafka topic `stream.transcript.segments`.
6. `sentiment-service` consumes transcript segments.
7. `sentiment-service` persists transcript text long term.
8. `sentiment-service` calls its existing sentiment path for transcript text.
9. `sentiment-service` persists transcript sentiment in a separate table.
10. `sentiment-service` publishes transcript sentiment to Kafka topic `stream.transcript.sentiment.events`.
11. `api-gateway` exposes transcript and transcript sentiment history/subscriptions separately from chat sentiment.
12. `frontend` shows streamer transcript and streamer transcript sentiment separately from audience chat sentiment.

## Recommended Architecture

### Capture Ownership

Put audio extraction in `video-capture-service`.

Reason:

- It already owns Twitch HLS resolution.
- It already depends on `ffmpeg`.
- It already has lifecycle/status/metrics patterns for Twitch media capture.
- It avoids creating another Twitch stream resolver that competes for HLS access.

### Transcription Ownership

Put Whisper inference in `ml-engine`.

Reason:

- It is already the ML boundary.
- It centralizes model dependencies and model versioning.
- It keeps capture orchestration separate from model execution.

### Persistence Ownership

Put transcript and transcript sentiment persistence in `sentiment-service` for Phase 2.5.

Reason:

- The data is text analytics data.
- `sentiment-service` already owns text sentiment persistence and history APIs.
- It avoids creating a dedicated transcript service before the product value is proven.

Future extraction option:

- Move transcript storage into a dedicated `transcript-service` if transcript search, diarization, retention controls, or multi-speaker analysis becomes large.

## Local Whisper Plan

Recommended library:

```text
faster-whisper
```

Recommended default model:

```text
small.en
```

Recommended compute type:

```text
int8
```

Fallback if local CPU is too slow:

```text
base.en
```

Deferred:

- GPU inference.
- Speaker diarization.
- Word-level timestamps.
- Multi-language model selection beyond config.

Model loading behavior:

- Load the Whisper model once at `ml-engine` startup or lazily on the first transcription request.
- Expose model load state in `/ml/health` or a new transcription status endpoint.
- Return a clear `503` if transcription is requested and the model cannot load.
- Include `modelVersion` in every transcript response.

## Audio Chunking Plan

Recommended defaults:

```text
chunk duration: 15 seconds
chunk format: wav
sample rate: 16000 Hz
channels: mono
codec: pcm_s16le
```

Possible `ffmpeg` command shape:

```bash
ffmpeg -y -loglevel warning -rw_timeout 15000000 -i "${hls_url}" -t 15 -vn -ac 1 -ar 16000 -c:a pcm_s16le /tmp/streamsense-audio.wav
```

Chunk lifecycle:

- Create chunk in container-local temp directory.
- Send file bytes to `ml-engine` as multipart upload.
- Delete temp file in `finally` block.
- Do not upload audio chunks to MinIO by default.
- Count deleted chunks and cleanup failures in metrics.

Backpressure:

- Only one transcription chunk in flight per channel by default.
- Skip new chunk capture if the previous transcription has not completed.
- Track skipped chunks with reason `transcription_in_flight`.
- Use bounded timeouts for audio extraction and transcription calls.

## New ML Endpoint

Add to `ml-engine`:

```text
POST /ml/transcribe
```

Request:

```text
multipart/form-data
file: audio/wav
streamer: string
segmentId: string
startedAt: long
endedAt: long
language: optional string
```

Response:

```json
{
  "text": "transcribed streamer speech",
  "language": "en",
  "confidence": 0.91,
  "modelVersion": "faster-whisper-small.en-int8"
}
```

Important behavior:

- Return empty text for silence or no speech, not fake words.
- Do not publish empty transcript segments.
- Return `503` if the model cannot load or transcription fails.
- Log segment IDs and durations, not raw transcript text by default.

## New Kafka Topics

Add topics:

```text
stream.transcript.segments
stream.transcript.sentiment.events
```

Optional dead-letter topics later:

```text
stream.transcript.segments.dlt
stream.transcript.sentiment.events.dlt
```

## Event Contracts

### `TranscriptSegmentEvent`

Topic:

```text
stream.transcript.segments
```

Shape:

```json
{
  "segmentId": "string",
  "streamer": "austincs",
  "source": "TWITCH_AUDIO",
  "speaker": "streamer",
  "text": "transcribed streamer speech",
  "language": "en",
  "startedAt": 1710000000000,
  "endedAt": 1710000015000,
  "durationMs": 15000,
  "confidence": 0.91,
  "modelVersion": "faster-whisper-small.en-int8",
  "streamSessionId": "austincs-1710000000000",
  "channelLogin": "austincs",
  "captureWorkerId": "video-capture-service-1"
}
```

Rules:

- `text` must be non-empty after trimming.
- `speaker` is initially `streamer` because no diarization is in scope.
- `confidence` can be nullable if the local Whisper implementation cannot produce a reliable segment-level confidence.
- `streamSessionId` should reuse Phase 2 capture session ID when available.

### `TranscriptSentimentEvent`

Topic:

```text
stream.transcript.sentiment.events
```

Shape:

```json
{
  "transcriptSentimentEventId": "string",
  "sourceSegmentId": "string",
  "streamer": "austincs",
  "speaker": "streamer",
  "text": "transcribed streamer speech",
  "segmentStartedAt": 1710000000000,
  "segmentEndedAt": 1710000015000,
  "processedAt": 1710000017000,
  "label": "POSITIVE",
  "score": 0.82,
  "sentimentModelVersion": "stub-v1",
  "transcriptionModelVersion": "faster-whisper-small.en-int8",
  "streamSessionId": "austincs-1710000000000"
}
```

Rules:

- Do not publish transcript sentiment to the existing `stream.sentiment.events` topic unless explicitly adding a union source field later.
- Keep chat sentiment subscriptions unchanged.
- Keep transcript sentiment GraphQL fields separate.

## Persistence Plan

Add tables in `sentiment-service`.

### `transcript_segments`

Columns:

```text
segment_id varchar(64) primary key
streamer varchar(255) not null
source varchar(32) not null
speaker varchar(64) not null
text varchar(4000) not null
language varchar(32)
started_at bigint not null
ended_at bigint not null
duration_ms bigint not null
confidence double precision
model_version varchar(128) not null
stream_session_id varchar(255)
channel_login varchar(255)
capture_worker_id varchar(255)
created_at bigint not null
```

Indexes:

```text
(streamer, started_at desc)
(stream_session_id, started_at desc)
```

### `transcript_sentiment_events`

Columns:

```text
transcript_sentiment_event_id varchar(64) primary key
source_segment_id varchar(64) not null
streamer varchar(255) not null
speaker varchar(64) not null
text varchar(4000) not null
segment_started_at bigint not null
segment_ended_at bigint not null
processed_at bigint not null
label varchar(32) not null
score double precision not null
sentiment_model_version varchar(64) not null
transcription_model_version varchar(128) not null
stream_session_id varchar(255)
```

Indexes:

```text
(streamer, segment_started_at desc)
(stream_session_id, segment_started_at desc)
```

Retention:

- Persist transcript text long term for this phase.
- Raw audio is not persisted.
- Production retention policy is still required before public launch.

## Service Changes

### `video-capture-service`

Add responsibilities:

- Capture audio chunks from the resolved Twitch HLS URL.
- Send chunks to `ml-engine /ml/transcribe`.
- Delete chunk files immediately after transcription attempt.
- Publish `TranscriptSegmentEvent` for non-empty transcripts.
- Track transcription status per channel.

Recommended config:

```bash
STREAMSENSE_TWITCH_TRANSCRIPT_ENABLED=true
TWITCH_TRANSCRIPT_CHUNK_SECONDS=15
TWITCH_TRANSCRIPT_LANGUAGE=en
TWITCH_TRANSCRIPT_MAX_IN_FLIGHT_PER_CHANNEL=1
TWITCH_TRANSCRIPT_AUDIO_FORMAT=wav
TWITCH_TRANSCRIPT_SAMPLE_RATE=16000
STREAMSENSE_TRANSCRIPT_SEGMENTS_TOPIC=stream.transcript.segments
STREAMSENSE_ML_ENGINE_BASE_URL=http://ml-engine:8000
```

Status additions:

- `transcriptionEnabled`
- `lastTranscriptAt`
- `lastTranscriptTextPreview` with a short limited preview
- `transcriptSegmentsPublished`
- `audioChunksCaptured`
- `audioChunksDeleted`
- `transcriptionErrors`

### `ml-engine`

Add responsibilities:

- Install and load `faster-whisper`.
- Add `/ml/transcribe` endpoint.
- Validate audio input.
- Run local Whisper transcription.
- Return empty text for silence.
- Return model version and language.

Recommended config:

```bash
STREAMSENSE_TRANSCRIPTION_ENABLED=true
STREAMSENSE_WHISPER_MODEL=small.en
STREAMSENSE_WHISPER_COMPUTE_TYPE=int8
STREAMSENSE_WHISPER_DEVICE=cpu
STREAMSENSE_WHISPER_LANGUAGE=en
STREAMSENSE_WHISPER_MODEL_CACHE=/models/whisper
```

### `sentiment-service`

Add responsibilities:

- Consume `stream.transcript.segments`.
- Persist transcript segments.
- Analyze transcript text with existing sentiment client.
- Persist transcript sentiment separately.
- Publish `stream.transcript.sentiment.events`.
- Expose transcript history REST endpoints.

Do not reuse the existing `sentiment_events` table for transcript sentiment unless adding a required `sourceType` migration. Separate tables are clearer and preserve chat sentiment behavior.

### `api-gateway`

Add:

```graphql
type TranscriptSegment {
  segmentId: ID!
  streamer: String!
  source: String!
  speaker: String!
  text: String!
  language: String
  startedAt: Float!
  endedAt: Float!
  durationMs: Float!
  confidence: Float
  modelVersion: String!
  streamSessionId: String
}

type TranscriptSentimentEvent {
  transcriptSentimentEventId: ID!
  sourceSegmentId: ID!
  streamer: String!
  speaker: String!
  text: String!
  segmentStartedAt: Float!
  segmentEndedAt: Float!
  processedAt: Float!
  label: String!
  score: Float!
  sentimentModelVersion: String!
  transcriptionModelVersion: String!
  streamSessionId: String
}
```

Queries:

```graphql
recentTranscript(streamer: String!, limit: Int!): [TranscriptSegment!]!
recentTranscriptSentiment(streamer: String!, limit: Int!): [TranscriptSentimentEvent!]!
```

Subscriptions:

```graphql
onTranscriptSegment(streamer: String!): TranscriptSegment!
onTranscriptSentiment(streamer: String!): TranscriptSentimentEvent!
```

### `frontend`

Add panels:

- `TranscriptPanel`
- `TranscriptSentimentPanel`

Frontend behavior:

- Show streamer speech transcript separately from chat.
- Show streamer transcript sentiment separately from audience chat sentiment.
- Show empty/silence state clearly.
- Show degraded transcription status if local Whisper is unavailable or lagging.

## Metrics

### Capture Metrics

```text
streamsense_twitch_audio_chunks_captured_total
streamsense_twitch_audio_chunks_deleted_total
streamsense_twitch_audio_chunks_skipped_total{reason}
streamsense_twitch_audio_capture_errors_total{stage}
streamsense_twitch_transcript_segments_published_total
streamsense_twitch_transcript_last_segment_age_seconds
streamsense_twitch_transcription_request_latency_ms
```

### ML Metrics

```text
streamsense_transcription_requests_total{status}
streamsense_transcription_latency_ms
streamsense_transcription_audio_duration_ms
streamsense_transcription_empty_segments_total
streamsense_whisper_model_loaded
streamsense_whisper_model_load_latency_ms
```

### Sentiment Metrics

```text
streamsense_transcript_segments_consumed_total
streamsense_transcript_sentiment_processed_total{label}
streamsense_transcript_sentiment_persistence_total{status}
streamsense_transcript_sentiment_end_to_end_latency_ms
```

## Implementation Steps

### Step 1: Add Transcript Event Contracts

Files likely involved:

```text
docs/schemas/transcript-segment-event.schema.json
docs/schemas/transcript-sentiment-event.schema.json
docs/contracts/transcript-pipeline.md
```

Acceptance criteria:

- Schemas document transcript and transcript sentiment events.
- Required fields match Java/Python event classes.
- Contracts state that raw audio is temporary only.

### Step 2: Add Local Whisper To `ml-engine`

Files likely involved:

```text
ml-engine/requirements.txt
ml-engine/src/main/python/app/models.py
ml-engine/src/main/python/app/main.py
ml-engine/src/main/python/app/transcription.py
ml-engine/src/test/python/
```

Acceptance criteria:

- `/ml/transcribe` accepts real audio file uploads.
- Local Whisper produces real transcript text for a fixture audio sample.
- Silence returns empty text.
- Model version is included.
- Forced ML failure behavior remains supported.

### Step 3: Add Audio Chunk Capture To `video-capture-service`

Files likely involved:

```text
video-capture-service/src/main/python/app/audio_sampler.py
video-capture-service/src/main/python/app/transcription_client.py
video-capture-service/src/main/python/app/transcript_publisher.py
video-capture-service/src/main/python/app/capture_loop.py
```

Acceptance criteria:

- Uses real `ffmpeg` audio extraction from Twitch HLS.
- Sends chunks to `ml-engine`.
- Deletes chunks after each attempt.
- Publishes only non-empty transcript segments.
- Does not store raw audio in MinIO or Postgres.

### Step 4: Add Transcript Persistence And Sentiment Processing

Files likely involved:

```text
sentiment-service/src/main/java/.../events/TranscriptSegmentEvent.java
sentiment-service/src/main/java/.../events/TranscriptSentimentEvent.java
sentiment-service/src/main/java/.../kafka/TranscriptSegmentConsumer.java
sentiment-service/src/main/java/.../persistence/TranscriptSegmentEntity.java
sentiment-service/src/main/java/.../persistence/TranscriptSentimentEntity.java
sentiment-service/src/main/resources/db/migration/
```

Acceptance criteria:

- Transcript segments are persisted long term.
- Transcript sentiment is persisted separately from chat sentiment.
- Existing chat sentiment pipeline is unchanged.
- Transcript sentiment events publish to `stream.transcript.sentiment.events`.

### Step 5: Add Gateway GraphQL Surfaces

Files likely involved:

```text
api-gateway/src/main/java/com/streamsense/apigateway/events/
api-gateway/src/main/java/com/streamsense/apigateway/consumer/
api-gateway/src/main/java/com/streamsense/apigateway/graphql/
api-gateway/src/main/resources/graphql/
```

Acceptance criteria:

- `recentTranscript` returns persisted transcript text.
- `recentTranscriptSentiment` returns transcript-only sentiment.
- `onTranscriptSegment` streams live transcript segments.
- `onTranscriptSentiment` streams live transcript sentiment.
- Existing `recentSentiment` and `onSentiment` remain chat-focused.

### Step 6: Add Frontend Panels

Files likely involved:

```text
frontend/src/components/TranscriptPanel.tsx
frontend/src/components/TranscriptSentimentPanel.tsx
frontend/src/graphql/queries.ts
frontend/src/graphql/subscriptions.ts
frontend/src/App.tsx
```

Acceptance criteria:

- Transcript panel shows streamer speech text.
- Transcript sentiment panel shows sentiment derived only from streamer speech.
- Empty/silence state is clear.
- Chat sentiment panel remains audience/chat-specific.

### Step 7: Update Runtime Config And Docs

Files likely involved:

```text
docker-compose.yml
makefile
config-server/config-repo/sentiment-service.yml
config-server/config-repo/api-gateway.yml
k8s/
docs/howtorun.md
docs/contracts/transcript-pipeline.md
```

Acceptance criteria:

- Topics are created locally.
- Env vars are documented.
- Docker Compose runs with local Whisper enabled.
- Kubernetes manifests render.
- Runbook explains how to verify transcript without storing raw audio.

## Test Plan

### Unit Tests

- `ml-engine` transcribes a fixture audio file.
- `ml-engine` returns empty text for silence.
- `ml-engine` handles invalid audio.
- Audio sampler invokes `ffmpeg` with expected arguments.
- Audio sampler deletes temp files after success and failure.
- Transcript publisher serializes expected event shape.
- `sentiment-service` persists transcript segments.
- `sentiment-service` persists transcript sentiment separately from chat sentiment.
- GraphQL controllers expose transcript query/subscription fields.
- Frontend transcript panels render loading, empty, live, and error states.

### Integration Tests

- Fake audio chunk produces transcript segment event.
- Transcript segment event produces transcript sentiment event.
- Existing chat sentiment tests still pass.
- Existing Phase 2 video capture tests still pass.

### Manual Live Tests

Start stack:

```bash
make twitch-video-up
```

Check capture/transcript status:

```bash
curl http://localhost:8080/api/video/capture/status
```

Check transcript Kafka topic:

```bash
docker compose exec kafka kafka-console-consumer \
  --bootstrap-server kafka:9092 \
  --topic stream.transcript.segments \
  --from-beginning \
  --timeout-ms 20000
```

Check transcript sentiment Kafka topic:

```bash
docker compose exec kafka kafka-console-consumer \
  --bootstrap-server kafka:9092 \
  --topic stream.transcript.sentiment.events \
  --from-beginning \
  --timeout-ms 20000
```

Check GraphQL transcript history:

```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"query($streamer:String!,$limit:Int!){ recentTranscript(streamer:$streamer, limit:$limit){ streamer text startedAt endedAt modelVersion }}","variables":{"streamer":"austincs","limit":5}}'
```

Check GraphQL transcript sentiment history:

```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"query($streamer:String!,$limit:Int!){ recentTranscriptSentiment(streamer:$streamer, limit:$limit){ streamer text label score transcriptionModelVersion sentimentModelVersion }}","variables":{"streamer":"austincs","limit":5}}'
```

Expected:

- Transcript segments appear when the streamer speaks.
- Silence produces no fake transcript.
- Transcript sentiment appears separately from chat sentiment.
- Raw audio files are not retained after processing.

## Failure Modes

Expected failures:

- Twitch stream has no speech during chunk window.
- `ffmpeg` audio extraction fails.
- Whisper model download/load fails.
- Local CPU transcription is too slow.
- Transcription request times out.
- Transcript text is empty or too long.
- Kafka publish fails.
- Sentiment processing fails.

Required behavior:

- Silence should not create fake transcript text.
- Transcript segment text should be capped at `4000` characters.
- Audio chunks must be deleted after success or failure.
- Failures must be visible in status, logs, and metrics.
- Existing chat and video pipelines should continue if transcript processing is degraded.
- No raw audio should be persisted unless explicitly reconfigured later.

## Acceptance Criteria

Phase 2.5 is complete when all of these are true:

- Local Whisper runs inside `ml-engine`.
- Real Twitch audio chunks are extracted with `ffmpeg`.
- Raw audio chunks are deleted after transcription attempts.
- Non-empty transcript text is published to `stream.transcript.segments`.
- Transcript text is persisted long term.
- Transcript sentiment is processed and persisted separately from chat sentiment.
- `stream.transcript.sentiment.events` receives transcript-only sentiment events.
- GraphQL exposes transcript and transcript sentiment history/subscriptions.
- Frontend shows transcript and transcript sentiment separately from chat sentiment.
- Existing Phase 1 chat and Phase 2 video/sponsor paths still pass.
- Docs explain local Whisper setup, verification, and raw audio deletion behavior.

## Out Of Scope

- External transcription APIs.
- Long-term raw audio storage.
- Speaker diarization.
- Multi-speaker attribution.
- Word-level timestamps.
- Closed caption integration.
- Subtitle export.
- Search over transcript history.
- Transcript summarization.
- Metric aggregation over transcript sentiment; that belongs in Phase 3.
- Recommendation changes using transcript sentiment; that belongs in Phase 4.

## Open Questions

Resolved decisions:

- Default Whisper model is `small.en`.
- Transcript text max length is `4000` characters per segment.
- Transcript status should expose a short text preview.
- Transcript sentiment should use the existing sentiment model unchanged for now.

Remaining implementation questions:

1. If `small.en` is too slow on local CPU during live verification, should we temporarily fall back to `base.en` or keep `small.en` and increase chunk interval?
2. What exact preview length should the status endpoint expose: 80, 120, or 160 characters?

## Recommended Defaults

Unless changed during implementation, use these defaults:

- `video-capture-service` owns audio chunk extraction.
- `ml-engine` owns local Whisper transcription.
- `sentiment-service` owns transcript persistence and transcript sentiment.
- Whisper model: `small.en`.
- Compute type: `int8`.
- Device: `cpu`.
- Audio chunk duration: `15` seconds.
- Audio format: `wav`, mono, 16 kHz.
- Transcript text max length: `4000` characters.
- Transcript status preview: enabled.
- Transcript sentiment model: existing sentiment model.
- Do not store raw audio.
- Persist transcript text long term.
- Keep transcript sentiment separate from chat sentiment.
