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

Optional debugging tools:

```
npm install -g wscat
```

---

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

Gateway auth is disabled by default for local Docker work. To restart only the gateway with auth enabled:

```bash
STREAMSENSE_GATEWAY_AUTH_ENABLED=true docker compose up -d api-gateway
```

Restore the local bypass mode with:

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

The default chat-ingest limiter allows 30 requests per minute per client key. Reusing the same `X-Forwarded-For` value should eventually return `429`:

```bash
for i in $(seq 1 31); do
  curl -s -o /dev/null -w "%{http_code}\n" \
    -X POST http://localhost:8080/api/chat/ingest \
    -H "Content-Type: application/json" \
    -H "X-Forwarded-For: 198.51.100.77" \
    -d '{"streamer":"gateway-limit-demo","user":"u1","message":"limit test","timestamp":1710000032000}'
done
```

Expected behavior:

- the first 30 responses return `200`
- the next response returns `429`

### Verify auth toggle

Restart the gateway with auth enabled:

```bash
STREAMSENSE_GATEWAY_AUTH_ENABLED=true docker compose up -d api-gateway
```

Without a bearer token, GraphQL should return `401`:

```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"{ health }"}'
```

To test the JWT hook locally, send a JWT-shaped bearer token with `iss=streamsense-local`, `aud=streamsense-clients`, and a future `exp` claim.

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
