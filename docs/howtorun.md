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

## 1. Build service JARs

Each Dockerfile copies `target/*.jar`, so build services first:

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

From repo root:

```
docker compose up -d --build
```

---

## 3. Verify infrastructure

| Service | URL |
|------|------|
| Eureka | http://localhost:8761 |
| Config Server | http://localhost:8888 |
| Kafka UI | http://localhost:8082 |
| Prometheus | http://localhost:9090 |
| Zipkin | http://localhost:9411 |

Health checks:

```
http://localhost:8080/actuator/health  (api-gateway)
http://localhost:8081/actuator/health  (chat-service)
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

---

# Running Services Without Docker

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
