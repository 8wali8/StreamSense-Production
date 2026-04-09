# Local Runbook
---

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

# Quickstart (Docker Compose)

This repo is Docker-first.

- Spring services talk to `config-server`, `eureka-server`, `kafka`, and the other containers through Docker DNS.
- Java Dockerfiles currently copy `target/*.jar`, so package those JARs before Compose builds the images.

## 1. Build service JARs

Each Java Dockerfile copies `target/*.jar`, so build the Java services first:

```
cd <service>
mvn clean package -DskipTests
```

Services:

- eureka-server
- config-server
- api-gateway
- chat-service
- sentiment-service
- video-service
- recommendation-service

---

## 2. Start the stack

From repo root, start the full stack:

```
docker compose up -d --build
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
