# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What is StreamSense

Real-time Twitch chat analytics platform built as distributed microservices. Chat messages flow through Kafka into sentiment analysis, video frames feed sponsor detection via ML inference, and an API gateway aggregates everything for a React dashboard via GraphQL (including WebSocket subscriptions).

## Commands

### Full Stack (Docker Compose — primary development mode)

```bash
make up              # Start entire stack
make down            # Stop everything
make build           # Build all Docker images
make build SERVICE=<name>   # Build a single image (e.g., SERVICE=api-gateway)
make test            # Run all tests (Java + Python + frontend)
make test SERVICE=<name>    # Test a single service
make logs            # Follow all logs
make clean           # Remove containers/volumes
make nuke            # Full teardown including images
```

### Java Services (run from any service directory)

```bash
mvn clean test                     # Run tests
mvn clean package -DskipTests      # Build JAR
mvn clean test -Dtest=MyTest       # Run a single test class
```

### Python — ml-engine

```bash
# From ml-engine/
PYTHONPATH=src/main/python pytest src/test/python          # All tests
PYTHONPATH=src/main/python pytest src/test/python/test_X.py  # Single file
```

### Frontend

```bash
# From frontend/
npm run dev     # Dev server (port 3000)
npm run build   # Production build
npm run test    # Vitest
npm run lint    # ESLint
```

### Kubernetes (kind cluster)

See `docs/kubernetes-kind.md` for cluster setup. Manifests are under `k8s/`.

## Architecture

### Service Map

| Service | Port | Lang | Role |
|---|---|---|---|
| eureka-server | 8761 | Java | Service discovery |
| config-server | 8888 | Java | Centralised config |
| api-gateway | 8080 | Java | Entry point, GraphQL, auth, rate limiting |
| chat-service | 8081 | Java | Chat ingestion → Kafka producer |
| sentiment-service | 8083 | Java | Kafka consumer → ml-engine → Kafka producer |
| video-service | 8084 | Java | Frame ingest → ml-engine → Kafka producer |
| recommendation-service | 8082 | Java | Aggregates signals |
| ml-engine | 8000 | Python | FastAPI inference (sentiment + sponsor detection) |
| frontend | 3000 | React/TS | Dashboard (Apollo Client, GraphQL subscriptions) |

Infrastructure: Kafka/Zookeeper, PostgreSQL 16, Redis 7, Prometheus, Grafana (port 3001), Zipkin (port 9411), Kafka UI (port 8088).

### Data Flow

```
Twitch Chat → chat-service → stream.chat.messages (Kafka)
                                   ↓
                         sentiment-service → ml-engine → stream.sentiment.events

Video Frames → video-service → ml-engine → stream.sponsor.detections

stream.sentiment.events  ┐
stream.sponsor.detections ┤ → recommendation-service → Postgres/Redis
stream.chat.messages      ┘

Frontend ← GraphQL queries/subscriptions ← api-gateway ← Redis/Postgres/Kafka consumers
```

### Kafka Topics

- `stream.chat.messages` — raw chat
- `stream.sentiment.events` — ML sentiment results
- `stream.video.frames` — video frame data
- `stream.sponsor.detections` — ML sponsor detection results

### Configuration

Services load config from **config-server** at startup. Source files live in `config-server/config-repo/` (one YAML per service plus `application.yml` for shared defaults). Each service's own `application.yml` is minimal — it just points to the config server URL.

Override config-server URL with the `CONFIG_SERVER_URL` env var.

### API Gateway internals

The gateway (`api-gateway/src/main/java/com/streamsense/apigateway/`) handles:
- **GraphQL** — queries and mutations via Spring GraphQL
- **WebSocket subscriptions** — `graphql-transport-ws` protocol, real-time push to frontend
- **Auth** — JWT validation filter
- **Rate limiting** — Redis-backed token bucket
- **Routing** — Spring Cloud Gateway routes to downstream services

GraphQL schema is in `docs/schemas/` and `docs/contracts/`.

### Frontend internals

React 19 + Vite + Apollo Client 4. GraphQL operations live in `frontend/src/graphql/`. Apollo config and WebSocket link setup is in `frontend/src/apollo/`. Subscriptions use `graphql-ws`.

### Java service conventions

- Package root: `com.streamsense.<servicename>`
- All services register with Eureka and pull from config-server
- Health endpoint: `GET /actuator/health`
- Tracing: Micrometer + Zipkin (auto-configured via Spring Cloud Sleuth/Micrometer Tracing)
- Kafka: Spring Kafka; consumers use `@KafkaListener`, producers use `KafkaTemplate`

## Key Docs

- `docs/howtorun.md` — local Docker Compose runbook (ports, startup order, troubleshooting)
- `docs/architecture.md` — ASCII architecture diagram
- `docs/kubernetes-kind.md` — Kubernetes/kind deployment guide
- `docs/contracts/` — GraphQL API contracts
- `docs/schemas/` — DB and GraphQL schemas
- `plan.md` — full 12-week production roadmap
