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

## Week 2 quickstart

Week 2 is complete when the live chat slice works end to end:

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

Verify the Week 2 topic exists:

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

## Week 2 verification checklist

- `stream.chat.messages` exists
- `POST /api/chat/ingest` returns an event id
- `query { health }` returns `ok`
- `onChatMessage(streamer)` receives events
- frontend live chat updates at `http://localhost:3000`
- `streamsense_chat_ingest_total` increases after ingest requests

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
