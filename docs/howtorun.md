# Local Runbook
---

Kubernetes runbook:

- `docs/kubernetes-kind.md` covers the local `kind` workflow added in Sprint 9.

# Prerequisites

Install:

- Java 21
- Maven
- Docker + Docker Compose
- Node.js (frontend)
- [uv](https://docs.astral.sh/uv/) (Python services: `uv sync --locked` inside `ml-engine/` or `video-capture-service/`)

Optional debugging tools:

```
npm install -g wscat
```

---

# Local Secrets

Credentials are never committed. Docker Compose reads them from git-ignored files under `secrets/` and mounts each one at `/run/secrets/<NAME>`; Kubernetes builds a `streamsense-secrets-<hash>` Secret from the git-ignored `k8s/secrets/streamsense.env`. Create both sets once:

```bash
make secrets
```

Every missing file gets a fresh random value with mode `0600`; existing files are kept. `make up`, `make up-fast`, and `tools/start-stack.ps1` run this step automatically, so a fresh clone starts without extra setup and without credentials anyone else knows. The committed `*.example` files are placeholders that document the file names. `secrets/README.md` lists every file and which container consumes it. Postgres and MinIO persist the credentials they were first started with, so changing those files later needs `make nuke`.

# Final Quickstart (Docker Compose)

This repo is Docker-first. Spring services talk to `config-server`, `eureka-server`, `kafka`, and the other containers through Docker DNS.

The canonical local demo command is:

```bash
make up
```

`make up` packages the Java service JARs, builds images, and starts the full Compose stack with `docker compose up -d --build`.

If the JARs and images are already current, use the faster path:

```bash
make up-fast
```

Run the final API-level smoke path from a clean Compose state:

```bash
make smoke-e2e
```

Seed demo data into an already-running stack:

```bash
make demo-seed
```

Print and open the main demo surfaces:

```bash
make demo-open
```

Equivalent manual startup, if you do not use `make`:

```bash
make package
docker compose up -d --build
```

The sprint-by-sprint sections below are retained as historical verification detail. For a final demo, prefer the commands above.

## Gateway Toggles

Gateway auth is disabled by default for local Docker work. Enabling it requires an HS256 signing secret of at least 32 bytes; the gateway refuses to start without one. The secret is read from the file `secrets/STREAMSENSE_GATEWAY_AUTH_HMAC_SECRET` (empty by default). Write one, then restart only the gateway with auth enabled:

```bash
printf '%s' 'replace-me-with-at-least-32-bytes-of-secret' > secrets/STREAMSENSE_GATEWAY_AUTH_HMAC_SECRET
STREAMSENSE_GATEWAY_AUTH_ENABLED=true docker compose up -d --force-recreate api-gateway
```

Mint a matching bearer token with `python tools/mint-jwt.py --subject demo-user` (details under "Verify auth toggle" below). Restore the local bypass mode with:

```bash
STREAMSENSE_GATEWAY_AUTH_ENABLED=false docker compose up -d api-gateway
```

Gateway rate limiting is enabled by default. To run a backend benchmark without edge `429` responses:

```bash
STREAMSENSE_GATEWAY_RATE_LIMIT_ENABLED=false docker compose up -d api-gateway
```

Restore the normal demo policy with:

```bash
STREAMSENSE_GATEWAY_RATE_LIMIT_ENABLED=true docker compose up -d api-gateway
```

## Twitch Chat Ingestion Toggle

Twitch chat ingestion is disabled by default. The existing synthetic demo and smoke paths continue to use `POST /api/chat/ingest` unless you explicitly enable Twitch ingestion.

Required local values:

```bash
export STREAMSENSE_TWITCH_CHAT_ENABLED=true
export TWITCH_CHAT_USERNAME=<twitch-bot-or-user-login>
export TWITCH_CHAT_OAUTH_TOKEN=<oauth-token-or-oauth-prefixed-token>
export TWITCH_CHANNELS=<target-channel-login>
```

Optional overrides:

```bash
export TWITCH_CHAT_HOST=irc.chat.twitch.tv
export TWITCH_CHAT_PORT=6697
export TWITCH_CHAT_SSL=true
```

Start or recreate the stack after exporting the values:

```bash
make up
```

If you keep those values in the ignored local file `.env.twitch.local`, use:

```bash
make twitch-up
```

Check the connector status through the gateway:

```bash
curl http://localhost:8080/api/chat/twitch/status
```

Equivalent make target:

```bash
make twitch-status
```

Expected disabled response shape when no Twitch credentials are configured:

```json
{"enabled":false,"state":"DISABLED","channels":[],"lastMessageAt":0,"lastError":null,"reconnectAttempts":0}
```

Expected enabled behavior:

- `state` moves to `CONNECTED` after the IRC connection succeeds
- `channels` contains the configured channel login
- `lastMessageAt` updates after real Twitch chat arrives
- `streamsense_twitch_chat_messages_total` increases in Prometheus

Important:

- Do not commit Twitch OAuth tokens or client secrets.
- Use a bot/test Twitch account for local development when possible.
- Twitch ingestion feeds the same `stream.chat.messages` Kafka topic as synthetic ingest, so the existing sentiment pipeline, GraphQL subscriptions, and frontend live chat panel should continue to work for the configured channel.

## Twitch Video Capture Toggle

Twitch video capture is disabled by default. The existing synthetic sponsor path through `POST /api/video/upload-frame` remains available unless you explicitly enable video capture.

Phase 2 local capture uses a real Twitch playback resolver and real frame extraction:

```text
streamlink -> ffmpeg -> MinIO frame artifact -> stream.video.frames -> video-service -> ml-engine -> stream.sponsor.detections
```

Recommended local values in `.env.twitch.local`:

```bash
STREAMSENSE_TWITCH_VIDEO_ENABLED=true
TWITCH_VIDEO_CHANNELS=austincs
TWITCH_VIDEO_QUALITY=best
TWITCH_VIDEO_SAMPLE_INTERVAL_SECONDS=10
STREAMSENSE_FRAME_STORAGE_BACKEND=s3
STREAMSENSE_FRAME_STORAGE_BUCKET=streamsense-frames
STREAMSENSE_FRAME_STORAGE_ENDPOINT=http://minio:9000
STREAMSENSE_SPONSOR_REQUIRE_FRAME_READ=true
```

The frame storage access key and secret key are not environment variables any more. They come from `secrets/STREAMSENSE_FRAME_STORAGE_ACCESS_KEY` and `secrets/STREAMSENSE_FRAME_STORAGE_SECRET_KEY`, which also seed the MinIO root credentials (see "Local Secrets" above).

Public Twitch streams do not need a video OAuth token. If your target stream requires authenticated playback, add `TWITCH_VIDEO_OAUTH_TOKEN` through `.env.twitch.local` only.

Start or recreate the stack with Twitch chat/video values loaded:

```bash
make twitch-video-up
```

Check video capture status through the gateway:

```bash
make twitch-video-status
```

Expected disabled response shape:

```json
{"enabled":false,"state":"DISABLED","channels":["disabled"],"lastFrameAt":null,"channelStatuses":[{"channel":"disabled","state":"DISABLED"}]}
```

Expected enabled behavior when the Twitch channel is live:

- `state` moves to `CAPTURING`
- `channels` contains the configured channel login
- `lastFrameAt` updates every sampling interval
- MinIO at `http://localhost:9001` contains non-empty frame objects under the `streamsense-frames` bucket
- `stream.video.frames` receives frame events whose `frameRef` starts with `s3://streamsense-frames/`
- `stream.sponsor.detections` receives detections with `modelVersion=frame-aware-stub-v1`
- the frontend video status pill shows capture state and the sponsor panel receives live detections

Useful verification commands:

```bash
docker compose logs video-capture-service
docker compose exec kafka kafka-console-consumer --bootstrap-server kafka:9092 --topic stream.video.frames --from-beginning --timeout-ms 10000
docker compose exec kafka kafka-console-consumer --bootstrap-server kafka:9092 --topic stream.sponsor.detections --from-beginning --timeout-ms 10000
```

Prometheus metrics:

```promql
streamsense_twitch_video_frames_captured_total
streamsense_twitch_video_frames_published_total
streamsense_twitch_video_last_frame_age_seconds
streamsense_frames_ingested_total
streamsense_sponsor_detections_total
```

Important:

- Do not commit video OAuth tokens.
- Do not commit captured frame artifacts.
- Treat captured frames as potentially sensitive content.
- If `STREAMSENSE_SPONSOR_REQUIRE_FRAME_READ=true`, `ml-engine` fails unreadable frame refs so `video-service` emits visible fallback detections instead of pretending the frame was analyzed.

## Twitch Transcript Capture Toggle

Streamer transcript capture is disabled by default and runs only when Twitch video capture is also enabled. It extracts short WAV chunks from the live Twitch HLS stream, sends them to local `faster-whisper` in `ml-engine`, publishes transcript segments to Kafka, then stores transcript text and transcript-only sentiment separately from chat sentiment.

```text
streamlink -> ffmpeg WAV chunk -> ml-engine /ml/transcribe -> stream.transcript.segments -> sentiment-service -> stream.transcript.sentiment.events
```

Recommended local values in `.env.twitch.local`:

```bash
STREAMSENSE_TWITCH_VIDEO_ENABLED=true
STREAMSENSE_TWITCH_TRANSCRIPT_ENABLED=true
TWITCH_VIDEO_CHANNELS=austincs
TWITCH_TRANSCRIPT_SEGMENT_SECONDS=10
TWITCH_TRANSCRIPT_LANGUAGE=en
STREAMSENSE_WHISPER_MODEL=small.en
STREAMSENSE_WHISPER_COMPUTE_TYPE=int8
STREAMSENSE_WHISPER_DEVICE=cpu
STREAMSENSE_TRANSCRIPT_TEXT_MAX_CHARS=4000
```

Start or recreate the stack with transcript capture enabled:

```bash
make twitch-transcript-up
```

Check capture status and the latest transcript preview:

```bash
make twitch-transcript-status
```

Expected enabled behavior when the Twitch channel is live and speaking:

- `channelStatuses[].transcriptSegmentsCaptured` increases
- `channelStatuses[].transcriptSegmentsPublished` increases for non-empty transcript text
- `channelStatuses[].lastTranscriptPreview` shows a short transcript preview
- `stream.transcript.segments` receives transcript segment events
- `stream.transcript.sentiment.events` receives transcript sentiment events
- GraphQL exposes `recentTranscriptSegments`, `recentTranscriptSentiment`, `onTranscriptSegment`, and `onTranscriptSentiment`
- the frontend shows separate Transcript and Voice sentiment panels

Useful verification commands:

```bash
docker compose logs video-capture-service ml-engine sentiment-service
docker compose exec kafka kafka-console-consumer --bootstrap-server kafka:9092 --topic stream.transcript.segments --from-beginning --timeout-ms 10000
docker compose exec kafka kafka-console-consumer --bootstrap-server kafka:9092 --topic stream.transcript.sentiment.events --from-beginning --timeout-ms 10000
```

Important:

- The first `small.en` transcription may download the model into the `whisper-models` Docker volume.
- Raw WAV chunks are temporary files in `video-capture-service` and are deleted after each transcription attempt.
- Persisted transcript text is stored in `sentiment-service`; chat sentiment and transcript sentiment remain separate.
- Do not commit Twitch tokens or captured audio artifacts.

## Twitch Analytics Aggregation Toggle

Product metrics aggregation runs in `analytics-service`. It consumes the real Twitch event topics and writes one-minute aggregate buckets to Postgres.

```text
stream.chat.messages + stream.sentiment.events + stream.transcript.sentiment.events + stream.sponsor.detections -> analytics-service -> /api/analytics -> GraphQL/frontend metric panels
```

Start or recreate the stack with chat, video, transcript, and analytics enabled:

```bash
make twitch-analytics-up
```

Check the current aggregate summary for the first configured Twitch channel:

```bash
make twitch-analytics-status
```

Direct REST checks:

```bash
curl -fsS 'http://localhost:8080/api/analytics/streams/<channel>/summary?windowMinutes=15'
curl -fsS 'http://localhost:8080/api/analytics/streams/<channel>/timeseries?windowMinutes=15&bucketSeconds=60'
curl -fsS 'http://localhost:8080/api/analytics/streams/<channel>/sponsors?windowMinutes=60'
curl -fsS 'http://localhost:8080/api/analytics/streams/<channel>/risk?windowMinutes=15'
```

Expected enabled behavior after events arrive:

- `chat.totalMessages` and `chat.messagesPerMinute` update from real Twitch chat
- `chatSentiment` updates from `stream.sentiment.events`
- `transcriptSentiment` updates from `stream.transcript.sentiment.events`
- `sponsorExposure` updates from `stream.sponsor.detections`
- `risk.level` returns `LOW_DATA` until enough signals exist, then `LOW`, `MEDIUM`, or `HIGH`
- GraphQL exposes `streamMetricsSummary`, `streamMetricsTimeseries`, `sponsorExposureMetrics`, and `brandSafetyMetrics`
- the frontend shows the Live stream metrics panel above the raw event panels

Useful verification commands:

```bash
docker compose logs analytics-service api-gateway
docker compose exec postgres psql -U streamsense -d streamsense -c 'select streamer, bucket_start, chat_message_count, chat_sentiment_count, transcript_sentiment_count from stream_metric_buckets order by bucket_start desc limit 10;'
docker compose exec postgres psql -U streamsense -d streamsense -c 'select streamer, sponsor, detection_count, estimated_exposure_ms from sponsor_metric_buckets order by bucket_start desc limit 10;'
```

## Sprint 2 quickstart

Sprint 2 is complete when the live chat slice works end to end:

- Kafka topic exists
- ingest works
- GraphQL health returns `ok`
- subscription receives chat events
- frontend updates at `http://localhost:3000`

---

## 3. Verify infrastructure

| Service | URL |
|------|------|
| Eureka | http://localhost:8761 |
| Config Server | http://localhost:8888 |
| Kafka UI | http://localhost:8088 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3001 |
| Zipkin | http://localhost:9411 |
| Frontend | http://localhost:3000 |

Health checks:

```
http://localhost:8888/actuator/health  (config-server)
http://localhost:8080/actuator/health  (api-gateway)
http://localhost:8081/actuator/health  (chat-service)
```

Verify Config Server returns in-repo config:

```
http://localhost:8888/chat-service/default
```

Verify the Sprint 2 topic exists:

```
docker compose exec kafka kafka-topics --bootstrap-server kafka:9092 --list
```

Look for:

```
stream.chat.messages
```

---

# Functional Verification

## Test ingest endpoint

```
curl -X POST http://localhost:8081/api/chat/ingest   -H "Content-Type: application/json"   -d '{"streamer":"test","user":"u1","message":"hello","timestamp":1710000000000}'
```

Response:

```
{ "eventId": "..." }
```

---

## Test GraphQL query

```
curl -X POST http://localhost:8080/graphql   -H "Content-Type: application/json"   -d '{"query":"query { health }"}'
```

Expected:

```
{ "data": { "health": "ok" } }
```

---

## Test GraphQL subscription

Connect:

```
npx wscat -c ws://localhost:8080/graphql -s graphql-transport-ws
```

Init:

```
{"type":"connection_init"}
```

Subscribe:

```
{
"id":"1",
"type":"subscribe",
"payload":{
"query":"subscription($streamer:String!){ onChatMessage(streamer:$streamer){ eventId streamer user message timestamp } }",
"variables":{"streamer":"test"}
}
}
```

Send another ingest request → event should appear.

Open the frontend and verify live chat updates appear:

```
http://localhost:3000
```

---

# Observability

## Metrics

Prometheus query:

```
streamsense_chat_ingest_total
```

Send ingest requests and confirm the value increases.

---

## Tracing

Open Zipkin:

```
http://localhost:9411
```

Search for traces from:

```
chat-service
```

Look for span:

```
POST /api/chat/ingest
```

## Sprint 2 verification checklist

- `stream.chat.messages` exists
- `POST /api/chat/ingest` returns an event id
- `query { health }` returns `ok`
- `onChatMessage(streamer)` receives events
- frontend live chat updates at `http://localhost:3000`
- `streamsense_chat_ingest_total` increases after ingest requests

## Sprint 3 quickstart

Sprint 3 is complete when the first sentiment analytics slice works end to end:

- `chat-service` ingests and publishes chat events only
- `sentiment-service` consumes chat events and persists sentiment rows
- `recentSentiment` returns persisted history
- `onSentiment` streams live sentiment updates
- frontend renders recent and live sentiment clearly

### Verify the full sentiment slice

ML health:

```
curl http://localhost:8000/ml/health
```

Direct sentiment history API:

```
curl "http://localhost:8083/api/sentiment/recent?streamer=test&limit=5"
```

GraphQL recent sentiment query:

```
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"query RecentSentiment($streamer:String!, $limit:Int!){ recentSentiment(streamer:$streamer, limit:$limit){ sentimentEventId sourceEventId streamer user message chatTimestamp processedAt label score modelVersion } }","variables":{"streamer":"test","limit":5}}'
```

Kafka topics:

```
docker compose exec kafka kafka-topics --bootstrap-server kafka:9092 --list
```

Look for:

```
stream.chat.messages
stream.sentiment.events
```

Live sentiment subscription:

```
npx wscat -c ws://localhost:8080/graphql -s graphql-transport-ws
```

Then send:

```
{"type":"connection_init"}
```

Then:

```
{
  "id":"2",
  "type":"subscribe",
  "payload":{
    "query":"subscription($streamer:String!){ onSentiment(streamer:$streamer){ sentimentEventId sourceEventId streamer user label score modelVersion } }",
    "variables":{"streamer":"test"}
  }
}
```

Ingest another chat message and verify the live sentiment event appears.

Frontend sentiment panel:

```
http://localhost:3000
```

Look for recent history, label counts, average score, and live updates.

## Sprint 3 verification checklist

- `stream.sentiment.events` exists
- `curl http://localhost:8000/ml/health` returns `ok`
- `POST /api/chat/ingest` still returns an event id
- `GET /api/sentiment/recent` returns persisted data after ingest
- `recentSentiment(streamer, limit)` returns persisted sentiment through GraphQL
- `onSentiment(streamer)` receives live sentiment events
- frontend sentiment panel shows history and live updates
- `streamsense_sentiment_events_total` and `streamsense_ml_sentiment_latency_ms` are visible in Prometheus

## Sprint 4 quickstart

Sprint 4 is complete when the sentiment slice remains operational under ML degradation and the failure path is visible instead of silent.

### Normal-path checks

Use the Sprint 3 checks first:

- `POST /api/chat/ingest`
- `GET /api/sentiment/recent`
- `recentSentiment(streamer, limit)`
- `onSentiment(streamer)`
- frontend sentiment panel at `http://localhost:3000`

### Trigger degraded mode

Recommended demo toggle:

```bash
ML_ENGINE_FORCE_FAILURE=true docker compose up -d --build ml-engine
```

Return to normal mode:

```bash
ML_ENGINE_FORCE_FAILURE=false docker compose up -d --build ml-engine
```

Simpler alternative:

```bash
docker compose stop ml-engine
```

### Verify fallback behavior

Ingest while ML is degraded:

```bash
curl -X POST http://localhost:8081/api/chat/ingest \
  -H "Content-Type: application/json" \
  -d '{"streamer":"fallback-demo","user":"u1","message":"ml failure should fallback","timestamp":1710000010000}'
```

Check recent history:

```bash
curl "http://localhost:8083/api/sentiment/recent?streamer=fallback-demo&limit=5"
```

Look for:

- `label = NEUTRAL`
- `score = 0.0`
- `modelVersion = fallback`

Check GraphQL history:

```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"query RecentSentiment($streamer:String!, $limit:Int!){ recentSentiment(streamer:$streamer, limit:$limit){ sentimentEventId label score modelVersion } }","variables":{"streamer":"fallback-demo","limit":5}}'
```

### Verify dead-letter behavior

Inspect the DLT topic:

```bash
docker compose exec kafka kafka-console-consumer \
  --bootstrap-server kafka:9092 \
  --topic stream.chat.messages.dlt \
  --from-beginning \
  --timeout-ms 5000 \
  --max-messages 20
```

### Sprint 4 observability checks

Prometheus queries:

```text
streamsense_sentiment_fallback_total
streamsense_sentiment_dead_letter_total
streamsense_ml_protected_calls_total
resilience4j_circuitbreaker_state{name="mlSentiment"}
```

Grafana:

- open `http://localhost:3001`
- use the `Sprint 4 Resilience Overview` dashboard

Zipkin:

- open `http://localhost:9411`
- verify a degraded-path trace still includes `sentiment-service`

### Sprint 4 verification checklist

- ingest still succeeds when ML is degraded
- fallback sentiment appears through REST, GraphQL, and the frontend
- `stream.chat.messages.dlt` is available for exhausted failures
- fallback, retry, and dead-letter metrics are visible
- breaker state metrics are visible
- degraded-path behavior is documented and demoable

## Sprint 5 quickstart

Sprint 5 is complete when the first sponsor analytics slice works end to end:

- `video-service` accepts frame ingest requests and publishes `stream.video.frames`
- `video-service` processes sponsor detections and publishes `stream.sponsor.detections`
- `sponsorDetections` returns persisted sponsor history
- `onSponsorDetection` streams live sponsor updates
- frontend renders recent and live sponsor detections clearly
- stopping or forcing failure in `ml-engine` still produces fallback sponsor events

### Verify the sponsor slice

Video ingest:

```bash
curl -X POST http://localhost:8084/api/video/upload-frame \
  -H "Content-Type: application/json" \
  -d '{"streamer":"test","frameRef":"frames/demo-001.png","frameSequence":1,"capturedAt":1710000000000}'
```

Direct sponsor history API:

```bash
curl "http://localhost:8084/api/video/detections/recent?streamer=test&limit=5"
```

GraphQL sponsor history query:

```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"query SponsorDetections($streamer:String!, $limit:Int!){ sponsorDetections(streamer:$streamer, limit:$limit){ detectionEventId sourceFrameId streamer frameRef frameSequence capturedAt processedAt sponsor confidence modelVersion x y width height } }","variables":{"streamer":"test","limit":5}}'
```

GraphQL sponsor subscription payload for `wscat`:

```json
{
  "id": "2",
  "type": "subscribe",
  "payload": {
    "query": "subscription($streamer:String!){ onSponsorDetection(streamer:$streamer){ detectionEventId sourceFrameId streamer sponsor confidence modelVersion } }",
    "variables": {
      "streamer": "test"
    }
  }
}
```

Kafka topics should now include:

```text
stream.video.frames
stream.sponsor.detections
```

To trigger fallback behavior:

```bash
ML_ENGINE_FORCE_FAILURE=true docker compose up -d --build ml-engine
```

Sponsor metrics to check in Prometheus:

```text
streamsense_frames_ingested_total
streamsense_sponsor_detections_total
streamsense_sponsor_fallback_total
```

Grafana:

- open `http://localhost:3001`
- use the `Sprint 5 Sponsor Overview` dashboard

### Sprint 5 verification checklist

- `stream.video.frames` exists
- `stream.sponsor.detections` exists
- `POST /api/video/upload-frame` returns `202 Accepted`
- `GET /api/video/detections/recent` returns persisted sponsor detections
- `sponsorDetections(streamer, limit)` returns sponsor history through GraphQL
- `onSponsorDetection(streamer)` receives live sponsor detection events
- frontend sponsor panel shows history and live updates
- fallback sponsor detections appear when `ml-engine` is forced to fail
- sponsor metrics are visible in Prometheus and Grafana

## Sprint 6 quickstart

Sprint 6 is complete when the service-owned history read paths use Redis without moving history ownership into the gateway:

- Redis runs in Docker Compose
- `sentiment-service` caches `GET /api/sentiment/recent`
- `video-service` caches `GET /api/video/detections/recent`
- GraphQL history queries still come from service APIs
- cache hits and misses are visible in Prometheus and Grafana

### Verify the cache slice

Verify Redis is healthy:

```bash
docker compose exec redis redis-cli ping
```

Seed fresh history data for a new streamer:

```bash
curl -X POST http://localhost:8081/api/chat/ingest \
  -H "Content-Type: application/json" \
  -d '{"streamer":"cache-demo","user":"u1","message":"cache me","timestamp":1710000020000}'

curl -X POST http://localhost:8084/api/video/upload-frame \
  -H "Content-Type: application/json" \
  -d '{"streamer":"cache-demo","frameRef":"frames/cache-demo-001.png","frameSequence":1,"capturedAt":1710000021000}'
```

Query recent sentiment twice:

```bash
curl "http://localhost:8083/api/sentiment/recent?streamer=cache-demo&limit=5"
curl "http://localhost:8083/api/sentiment/recent?streamer=cache-demo&limit=5"
```

Query recent sponsor detections twice:

```bash
curl "http://localhost:8084/api/video/detections/recent?streamer=cache-demo&limit=5"
curl "http://localhost:8084/api/video/detections/recent?streamer=cache-demo&limit=5"
```

GraphQL history should still work through the gateway:

```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"query RecentSentiment($streamer:String!, $limit:Int!){ recentSentiment(streamer:$streamer, limit:$limit){ sentimentEventId label modelVersion } }","variables":{"streamer":"cache-demo","limit":5}}'

curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"query SponsorDetections($streamer:String!, $limit:Int!){ sponsorDetections(streamer:$streamer, limit:$limit){ detectionEventId sponsor modelVersion } }","variables":{"streamer":"cache-demo","limit":5}}'
```

Cache metrics to check in Prometheus:

```text
streamsense_cache_hits_total
streamsense_cache_misses_total
streamsense_history_lookup_latency_ms_count
```

Grafana:

- open `http://localhost:3001`
- use the `Sprint 6 Cache Overview` dashboard

### Sprint 6 verification checklist

- Redis responds with `PONG`
- the first recent history query succeeds on DB fallback
- the second identical recent history query increases cache-hit metrics
- `recentSentiment(streamer, limit)` still returns service-owned history through GraphQL
- `sponsorDetections(streamer, limit)` still returns service-owned history through GraphQL
- cache metrics are visible in Prometheus and Grafana

## Sprint 7 quickstart

Sprint 7 is complete when `api-gateway` behaves like a real edge service while preserving the service-owned history model:

- `/api/**` routes are proxied centrally through Spring Cloud Gateway
- auth hooks exist with a local bypass mode
- ingest-facing routes are rate limited
- GraphQL remains available with modularized schema files
- subscription reconnect behavior stays stable through gateway restarts

### Verify gateway routing

Send ingest traffic through the gateway instead of calling services directly:

```bash
curl -X POST http://localhost:8080/api/chat/ingest \
  -H "Content-Type: application/json" \
  -d '{"streamer":"gateway-demo","user":"u1","message":"hello through the gateway","timestamp":1710000030000}'

curl -X POST http://localhost:8080/api/video/upload-frame \
  -H "Content-Type: application/json" \
  -d '{"streamer":"gateway-demo","frameRef":"frames/gateway-demo-001.png","frameSequence":1,"capturedAt":1710000031000}'
```

Query GraphQL history through the same gateway after the downstream services persist the events:

```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"query RecentSentiment($streamer:String!, $limit:Int!){ recentSentiment(streamer:$streamer, limit:$limit){ sentimentEventId streamer label modelVersion } }","variables":{"streamer":"gateway-demo","limit":5}}'

curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"query SponsorDetections($streamer:String!, $limit:Int!){ sponsorDetections(streamer:$streamer, limit:$limit){ detectionEventId streamer sponsor modelVersion } }","variables":{"streamer":"gateway-demo","limit":5}}'
```

### Verify rate limiting

The default chat-ingest limiter allows 30 requests per minute per client key. The client key is the caller's socket address; `X-Forwarded-For` is only honoured when `STREAMSENSE_GATEWAY_TRUSTED_PROXY_HOPS` is greater than `0`. The Kubernetes manifests set it to `1` because the gateway sits behind ingress-nginx, while Compose leaves it at `0` because port 8080 is published directly and anything sent on that port could forge the header. Repeating the request from one host should eventually return `429`:

```bash
for i in $(seq 1 31); do
  curl -s -o /dev/null -w "%{http_code}\n" \
    -X POST http://localhost:8080/api/chat/ingest \
    -H "Content-Type: application/json" \
    -d '{"streamer":"gateway-limit-demo","user":"u1","message":"limit test","timestamp":1710000032000}'
done
```

Expected behavior:

- the first 30 responses return `200`
- the next response returns `429`
- adding or changing an `X-Forwarded-For` header does not reset the count unless a trusted proxy hop is configured

### Verify auth toggle

Tokens are HS256 JWTs verified against a shared secret. The gateway refuses to start with auth enabled and no secret (or one shorter than 32 bytes), so write one to the secret file first, then restart the gateway with auth enabled:

```bash
printf '%s' 'replace-me-with-at-least-32-bytes-of-secret' > secrets/STREAMSENSE_GATEWAY_AUTH_HMAC_SECRET
STREAMSENSE_GATEWAY_AUTH_ENABLED=true docker compose up -d --force-recreate api-gateway
```

Pass the same value to `tools/mint-jwt.py` (for example `--secret "$(cat secrets/STREAMSENSE_GATEWAY_AUTH_HMAC_SECRET)"`).

Without a bearer token, GraphQL should return `401`:

```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"{ health }"}'
```

Mint a token with the same secret (`tools/mint-jwt.py` needs only the Python standard library) and the request succeeds; a token signed with any other key is rejected with `invalid_jwt_signature`:

```bash
TOKEN=$(python tools/mint-jwt.py --subject demo-user)
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"query":"{ health }"}'
```

Subscriptions authenticate at the graphql-transport-ws `connection_init` step rather than on the handshake: the frontend sends `connectionParams.Authorization` from `localStorage["streamsense.authToken"]`, and the same key drives the `Authorization` header on HTTP queries. Store a minted token under that key in the browser to use the dashboard with auth on.

After verification, restore local bypass mode:

```bash
STREAMSENSE_GATEWAY_AUTH_ENABLED=false docker compose up -d api-gateway
```

### Gateway metrics to check in Prometheus

```text
spring_cloud_gateway_routes_count
streamsense_gateway_rate_limit_rejections_total
streamsense_gateway_auth_rejections_total
```

### Sprint 7 verification checklist

- routed chat ingest succeeds through `http://localhost:8080/api/chat/ingest`
- routed frame ingest succeeds through `http://localhost:8080/api/video/upload-frame`
- `recentSentiment(streamer, limit)` still resolves through GraphQL after routed ingest
- `sponsorDetections(streamer, limit)` still resolves through GraphQL after routed frame ingest
- repeated ingest traffic from the same client key eventually returns `429`
- auth-enabled gateway rejects unauthenticated GraphQL requests with `401`
- auth-enabled gateway accepts valid JWT-shaped bearer tokens
- gateway metrics expose route counts and rate-limit rejections

### Sprint 3 observability checks

Prometheus queries:

```
streamsense_sentiment_events_total
streamsense_ml_sentiment_latency_ms_count
```

Zipkin:

Open `http://localhost:9411` and look for a trace spanning:

- `chat-service`
- `sentiment-service`
- `ml-engine`

---

# Running Services Without Docker (Optional)

Docker is the primary path. If you need to run services directly on the host, export Docker hostnames as localhost equivalents first:

```
export CONFIG_SERVER_URL=http://localhost:8888
export EUREKA_DEFAULT_ZONE=http://localhost:8761/eureka
```

Start in this order:

```
1. eureka-server
2. config-server
3. api-gateway
4. other services
```

Example:

```
cd eureka-server
mvn spring-boot:run
```

---

# Tests

Backend tests are CI-friendly (no Docker required).

```
cd chat-service
mvn test
```

```
cd api-gateway
mvn test
```

Tests cover:

- controller validation
- Kafka produce
- GraphQL health query
- GraphQL subscription flow
- gateway auth validation and local bypass behavior
- gateway route proxying and rate-limit enforcement

## Sprint 8 quickstart

Sprint 8 is complete when the recommendation slice works end to end:

- `recommendation-service` serves deterministic, explainable recommendations
- it reads recent sentiment and sponsor history from service-owned APIs
- recommendation experiment config is loaded from Config Server
- `api-gateway` exposes recommendations through GraphQL
- the frontend renders recommendation reasons and active variant details

### Verify the recommendation flow

Seed the stream through the gateway:

```bash
curl -X POST http://localhost:8080/api/chat/ingest \
  -H "Content-Type: application/json" \
  -d '{"streamer":"verify-s8","user":"u1","message":"this stream is great","timestamp":1710001000000}'

curl -X POST http://localhost:8080/api/chat/ingest \
  -H "Content-Type: application/json" \
  -d '{"streamer":"verify-s8","user":"u2","message":"love this energy","timestamp":1710001001000}'

curl -X POST http://localhost:8080/api/video/upload-frame \
  -H "Content-Type: application/json" \
  -d '{"streamer":"verify-s8","frameRef":"frames/verify-s8-1.png","frameSequence":1,"capturedAt":1710001003000}'
```

Check the recommendation REST API directly:

```bash
curl "http://localhost:8082/api/recommendations?streamer=verify-s8&limit=4"
```

Expected response shape per item:

- `recommendationId`
- `title`
- `category`
- `score`
- `reasonSummary`
- `reasons`
- `experimentName`
- `variantId`

Check the GraphQL recommendation query through the gateway:

```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"query Recommendations($streamer:String!, $limit:Int!){ recommendations(streamer:$streamer, limit:$limit){ recommendationId category score reasonSummary variantId experimentName } }","variables":{"streamer":"verify-s8","limit":4}}'
```

Expected behavior:

- response contains `recommendationId`
- `variantId` matches the active Config Server variant
- `reasonSummary` is populated with human-readable explanation text

Open the frontend and verify the recommendation panel renders the same stream:

```text
http://localhost:3000
```

In the UI:

- enter `verify-s8` in the Recommendations panel
- load recommendations
- verify title, category, score, reason summary, detailed reasons, and variant are all visible

### Recommendation metrics to check

```text
streamsense_recommendations_served_total
streamsense_experiment_variant_total
streamsense_recommendation_latency_ms_count
```

### Sprint 8 verification checklist

- `recommendation-service` health is `UP` at `http://localhost:8082/actuator/health`
- `GET /api/recommendations` returns recommendation objects with reasons and variant metadata
- `recommendations(streamer, limit)` works through GraphQL
- recommendation output is derived from recent sentiment and sponsor history, not hardcoded values
- frontend renders recommendation cards with visible explanations
- recommendation metrics increase after live requests

---

# Final Demo Script

Use this sequence for the production-shaped local demo:

```bash
make smoke-e2e
make up
make demo-seed
make demo-open
```

Expected visible surfaces:

- frontend at `http://localhost:3000` shows live/historical analytics
- GraphQL `query { health }` returns `ok` at `http://localhost:8080/graphql`
- Grafana at `http://localhost:3001` has provisioned dashboards, login `admin/admin`
- Zipkin at `http://localhost:9411` lists StreamSense services after traffic has flowed
- Prometheus at `http://localhost:9090` can query StreamSense metrics

For degraded-path evidence, use `docs/degraded-path-proof.md`.

For load runs, use `tools/load/README.md`.

---

# Useful Commands

List containers:

```
docker compose ps
```

View logs:

```
docker compose logs -f <service>
```

Restart service:

```
docker compose restart <service>
```

Rebuild service:

```
docker compose up -d --build <service>
```

---

# Common Issues

### Service missing in Eureka

Wait ~30 seconds — Eureka clients retry registration automatically.

---

### Config Server works locally but not in Docker

Inside containers use:

```
http://config-server:8888
```

not `localhost`.

The Config Server reads from the repo-mounted `config-server/config-repo` directory inside Docker.

---

### Kafka connection errors

Ensure services use:

```
kafka:9092
```

inside Docker.

---

### Subscription receives no events

Check:

- topic name `stream.chat.messages`
- WebSocket protocol `graphql-transport-ws`
- streamer filter matches subscription variable

---

### Gateway returns `429` during load tests

This is expected when the default edge policy is active. It proves rate limiting is working.

For a backend-focused benchmark, temporarily disable gateway rate limiting:

```bash
STREAMSENSE_GATEWAY_RATE_LIMIT_ENABLED=false docker compose up -d api-gateway
```

Restore it after the run:

```bash
STREAMSENSE_GATEWAY_RATE_LIMIT_ENABLED=true docker compose up -d api-gateway
```

---

### Redis cache metrics do not move

Query the same history endpoint twice for the same streamer and limit. The first request should miss and populate Redis; the second should hit.

Prometheus queries:

```promql
streamsense_cache_hits_total
streamsense_cache_misses_total
```

---

### Zipkin has no useful traces

Generate fresh traffic after the stack is healthy:

```bash
python tools/demo/seed_demo.py --streamer trace-proof
```

Then open `http://localhost:9411` and search for `api-gateway`, `chat-service`, `sentiment-service`, or `video-service`.

---

### Degraded fallback is not visible

Use the dedicated proof runbook: `docs/degraded-path-proof.md`.

The most common issue is seeding one streamer while viewing another streamer in the UI or GraphQL query.
