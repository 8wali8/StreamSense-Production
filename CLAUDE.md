# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What is StreamSense

Real-time sponsor analytics platform for Twitch streams, built as distributed microservices. It ingests chat, video frames, and transcript audio; runs ML-backed sentiment, sponsor relevance, sponsor detection, segmentation, and transcription through a FastAPI ml-engine; and exposes live results via GraphQL (queries + WebSocket subscriptions) to a React dashboard.

## Commands

The root task runner is `makefile` (lowercase). It uses `SHELL := /bin/bash`, so run it from a bash-capable shell (Git Bash on Windows). `tools/start-stack.ps1` is the PowerShell equivalent for starting the stack on Windows. Run `make help` for the full target list.

### Full Stack (Docker Compose — primary development mode)

```bash
make up              # Package Java jars, build images, start everything
make up-fast         # Start existing images/containers (no packaging/building)
make down            # Stop everything
make build           # Build all Docker images (does NOT package jars first)
make build SERVICE=<name>   # Build a single image (e.g., SERVICE=api-gateway)
make package         # Build all Java jars locally (mvn -DskipTests package)
make test            # Run all tests (Java + Python + frontend lint/build)
make test SERVICE=<name>    # Test a single service
make logs            # Follow all logs
make smoke-e2e       # API-level Compose smoke test (tools/smoke/compose_smoke.py)
make replay-smoke    # Verify VOD replay alias path against a running stack (tools/smoke/replay_smoke.py)
make demo-seed       # Seed demo chat/frame data into a running stack
make clean           # docker compose down (keeps volumes)
make nuke            # docker compose down -v (removes volumes/data)
```

Java Dockerfiles copy `target/*.jar` — after Java changes you must package jars before rebuilding images. `make up` does this for you; `make build` alone does not.

Third-party images are pinned as `name:tag@sha256:<digest>` in `docker-compose.yml`, `k8s/**`, and every `Dockerfile`; never add a floating tag such as `:latest` or `:16`. To bump one, pick the new tag, take the digest from `docker buildx imagetools inspect name:tag`, and update every occurrence (Renovate will do this once enabled).

Twitch verification targets (`make twitch-up`, `twitch-video-up`, `twitch-transcript-up`, `twitch-analytics-up`, and matching `*-status` targets) load credentials from `.env.twitch.local` (not committed).

### Java Services

Not a Maven monorepo — each Java service has its own `pom.xml`. Run Maven from the service directory or with `mvn -f <service>/pom.xml`.

```bash
mvn clean test                     # Run tests
mvn clean package -DskipTests      # Build JAR
mvn clean test -Dtest=MyTest       # Run a single test class
```

### Python services (ml-engine, video-capture-service)

Each Python service has a `pyproject.toml` and a committed `uv.lock`; dependencies are installed with [uv](https://docs.astral.sh/uv/), never from a requirements file. Test and lint tooling live in the `dev` dependency group, which the Docker images do not install.

```bash
# From ml-engine/ or video-capture-service/
uv sync --locked                                   # Create .venv from uv.lock (includes the dev group)
uv run pytest                                      # All tests (pytest picks up src/main/python from pyproject)
uv run pytest src/test/python/test_X.py            # Single file
uv run ruff check src/main/python src/test/python  # Lint (CI runs this for both services)
uv add <package>            # Add a runtime dependency (updates pyproject.toml and uv.lock)
uv add --group dev <package> # Add a test/lint dependency
```

Never edit `uv.lock` by hand, and commit it together with the `pyproject.toml` change that produced it. CI installs with `uv sync --locked`, which fails if the lock is stale.

### Frontend

```bash
# From frontend/
npm run dev     # Dev server (port 3000)
npm run build   # tsc -b && vite build
npm run test    # Vitest (vitest run)
npm run lint    # ESLint
```

### CI parity

CI (`.github/workflows/ci.yml`) uses Java 21, Python 3.11, Node 20. Its Java matrix covers all eight Java services, and it also tests video-capture-service. `make test` is not identical to CI: for frontend it runs only `lint` + `build` and skips Vitest, while CI runs Vitest too. If you touch `k8s/` or `config-server/config-repo/`, run `kubectl kustomize .` from the repo root — CI validates that plus the JSON embedded in `k8s/config/grafana-config.yaml`. CI also runs a Docker Compose smoke job that exercises chat ingest → sentiment → GraphQL end to end. CI runs on pull requests and on pushes to `main` only, and a `changes` job path-filters the rest: only the Java services whose directories changed are built, and a change under `.github/workflows/` or `config-server/config-repo/` runs everything. Actions are pinned by commit SHA with the version in a comment; bump the SHA and the comment together.

### Kubernetes (kind cluster)

See `docs/kubernetes-kind.md` for cluster setup. Manifests are under `k8s/`; the entry point is the root `kustomization.yaml` (`kubectl kustomize .`). Every container declares `resources` (requests and a memory limit) and a non-root `securityContext` that drops all capabilities; Spring services probe `/actuator/health/liveness` and `/readiness` (readiness includes the datastore checks, liveness never does); stateful data lives on PersistentVolumeClaims in `k8s/platform/storage.yaml`, never `emptyDir`. New workloads follow the same shape or the namespace's Pod Security admission will warn.

## Architecture

### Service Map

| Service | Port | Lang | Role |
|---|---|---|---|
| eureka-server | 8761 | Java | Service discovery |
| config-server | 8888 | Java | Centralised config |
| api-gateway | 8080 | Java | Entry point, GraphQL, auth, rate limiting |
| chat-service | 8081 | Java | Twitch IRC + manual chat ingest → Kafka producer |
| recommendation-service | 8082 | Java | Recommendation summaries from platform signals |
| sentiment-service | 8083 | Java | Kafka consumer → ml-engine sentiment/relevance → Kafka producer |
| video-service | 8084 | Java | Frame events → ml-engine sponsor detection → Kafka producer |
| analytics-service | 8085 | Java | Aggregates stream metrics from event streams |
| video-capture-service | 8090 | Python | Twitch frame capture → MinIO, transcript audio → ml-engine |
| ml-engine | 8000 | Python | FastAPI inference: sentiment, relevance, sponsor, segmentation, transcription |
| frontend | 3000 | React/TS | Live console (Apollo Client, GraphQL subscriptions) |

Infrastructure: Kafka in single-node KRaft mode, no ZooKeeper (host access `localhost:29092`, internal `kafka:9092`; the broker is also the controller and its data lives on a named volume / PVC), PostgreSQL 16, Redis 7, MinIO (9000/9001, frame storage), Prometheus (9090), Grafana (3001), Zipkin (9411), Kafka UI (8088), kafka-exporter (9308).

### Data Flow

```
Twitch Chat → chat-service → stream.chat.messages (Kafka)
                                   ↓
                       sentiment-service → ml-engine (/ml/sentiment, /ml/relevance)
                                   → stream.sentiment.events + stream.transcript.sentiment.events

Twitch Video → video-capture-service → frames to MinIO + stream.video.frames
                                     → audio to ml-engine (/ml/transcribe) → stream.transcript.segments

stream.video.frames → video-service → ml-engine (/ml/sponsor) → stream.sponsor.detections
stream.transcript.segments → sentiment-service (same sentiment/relevance path as chat)

sentiment/sponsor/chat/transcript-sentiment events → analytics-service → Postgres
                                                   → recommendation-service

Frontend ← GraphQL queries/subscriptions ← api-gateway ← Redis/Postgres/Kafka consumers
```

### Kafka Topics

Created by the `kafka-topics-init` Compose service (auto-create is disabled):

- `stream.chat.messages` (+ `.dlt`) — raw chat
- `stream.sentiment.events` — chat sentiment results (now enriched with sponsor-relevance fields)
- `stream.transcript.segments` (+ `.dlt`) — transcribed audio segments
- `stream.transcript.sentiment.events` — transcript sentiment results
- `stream.video.frames` (+ `.dlt`) — frame metadata (frame bytes live in MinIO)
- `stream.sponsor.detections` — ML sponsor detection results
- `<source>.analytics.dlt` — analytics-service dead letters, one per input it consumes (`stream.chat.messages.analytics.dlt`, `stream.sentiment.events.analytics.dlt`, `stream.transcript.sentiment.events.analytics.dlt`, `stream.sponsor.detections.analytics.dlt`). Distinct from the `<source>.dlt` topics, which belong to each source's primary consumer.

### Configuration

Spring services load config from **config-server** at startup; each service's own `src/main/resources/application.yml` is bootstrap-only. Real runtime config lives in `config-server/config-repo/*.yml` (one YAML per service plus `application.yml` for shared defaults). Override the config-server URL with `CONFIG_SERVER_URL`. The import is required, not optional: a service retries config-server for about a minute (`spring.cloud.config.retry.*`) and then fails to start rather than booting with empty config, so running a Java service outside Compose or kind needs a reachable config-server (tests are unaffected: each service's `src/test/resources/application.yml` shadows the bootstrap file and disables config-server and Eureka).

**Downstream URLs are required and every outbound call is bounded.** The gateway binds `streamsense.services.*` into a validated `DownstreamServicesProperties` (all six base URLs `@NotBlank`, plus `connect-timeout` and `response-timeout` applied to every `WebClient` through a `WebClientCustomizer`); proxied routes use `spring.cloud.gateway.httpclient.*`. recommendation-service validates its two base URLs the same way and applies `connect-timeout-ms` and `read-timeout-ms` to `RestClient` through a `RestClientCustomizer`; sentiment-service and video-service already bound their `RestTemplate` from `streamsense.ml.*`. Never add a `localhost` default to a service URL and never build an HTTP client without a timeout.

Kubernetes reads the same files: the root `kustomization.yaml` generates the config-server ConfigMap from `config-server/config-repo/*.yml`, so there is no copy to keep in sync. Apply with `kubectl apply -k .` from the repository root.

**Secrets are never literal in committed files.** Compose mounts git-ignored `secrets/<NAME>` files at `/run/secrets/<NAME>` (`make secrets` creates any missing file with a random value; `make up` and `start-stack.ps1` do this automatically; the `*.example` files are placeholders). Spring services import `optional:configtree:/run/secrets/`, so a placeholder like `${POSTGRES_PASSWORD}` in config-repo resolves from the file or from an env var of the same name. Python services accept `<NAME>_FILE`. Kubernetes builds the hash-suffixed `streamsense-secrets` Secret from git-ignored `k8s/secrets/streamsense.env` via `secretGenerator` (so a changed value rolls the pods), and manifests use `secretKeyRef`. New credentials follow the same three paths.

The Python services (ml-engine, video-capture-service) do not use config-server or Eureka; they are configured via environment variables (see their entries in `docker-compose.yml` for the full catalog — ML model backends/caches, frame storage, transcript settings).

Useful env toggles: `STREAMSENSE_GATEWAY_AUTH_ENABLED` (requires the `STREAMSENSE_GATEWAY_AUTH_HMAC_SECRET` secret file or key, ≥32 bytes), `STREAMSENSE_GATEWAY_RATE_LIMIT_ENABLED`, `STREAMSENSE_GATEWAY_TRUSTED_PROXY_HOPS`, `ML_ENGINE_FORCE_FAILURE`, `STREAMSENSE_TWITCH_CHAT_ENABLED`, `STREAMSENSE_TWITCH_VIDEO_ENABLED`, `STREAMSENSE_TWITCH_TRANSCRIPT_ENABLED`.

### Twitch VOD replay

Both chat-service and video-capture-service support replaying a recorded Twitch VOD instead of a live stream, driven by named replay aliases (default alias: `redbull-testing`, wired in `config-server/config-repo/chat-service.yml` and the video-capture-service environment in `docker-compose.yml`). chat-service replays from a chat fixture (`classpath:replay/...`) or the Twitch GraphQL API; video-capture-service replays frames from the VOD URL. See `plans/vod-replay-testing-plan.md` for the replay startup workflow.

### API Gateway internals

The gateway (`api-gateway/src/main/java/com/streamsense/apigateway/`) handles:
- **GraphQL** — queries via Spring GraphQL (no mutations — writes go through REST routes)
- **WebSocket subscriptions** — `graphql-transport-ws` protocol, real-time push to frontend
- **Auth** — JWT validation filter
- **Rate limiting** — in-memory fixed-window limiter (per gateway instance, not Redis-backed). Clients are keyed by socket address; `X-Forwarded-For` is only consulted when `streamsense.gateway.trusted-proxy-hops` > 0 (k8s sets 1 behind ingress-nginx, Compose 0)
- **Routing** — Spring Cloud Gateway routes to downstream services

GraphQL schema is in `docs/schemas/` and `docs/contracts/`.

### Frontend internals

React 19 + Vite + Apollo Client 4. GraphQL operations live in `frontend/src/graphql/`. Apollo config and WebSocket link setup is in `frontend/src/apollo/client.ts`. Subscriptions use `graphql-ws`.

In Docker, nginx serves the frontend at `http://localhost:3000` and proxies `/graphql`, `/api`, and `/ml` to the backend. Local `npm run dev` has **no** Vite proxy for these routes — for end-to-end browser checks, prefer the Docker frontend.

### Java service conventions

- Package root: `com.streamsense.<servicename>`
- All Java services register with Eureka and pull from config-server
- Health endpoint: `GET /actuator/health` (Python services: ml-engine `GET /ml/health`, video-capture-service `GET /health`)
- Tracing: Micrometer + Zipkin
- Kafka: Spring Kafka; consumers use `@KafkaListener`, producers use `KafkaTemplate`

### Test behavior

Java tests are self-contained: test configs disable config-server and Eureka; integration tests use Embedded Kafka, H2, and MockWebServer instead of the Docker stack. `sentiment-service`, `video-service`, and `analytics-service` use Flyway migrations under `src/main/resources/db/migration/`; video-service and analytics-service use custom Flyway history tables and baseline settings from config (all three share the single `streamsense` Postgres database, so history table names must stay distinct).

## Key Docs

- `docs/howtorun.md` — local Docker Compose runbook (ports, startup order, troubleshooting)
- `docs/architecture.md` — architecture diagram (README.md has a mermaid version)
- `docs/kubernetes-kind.md` — Kubernetes/kind deployment guide
- `docs/contracts/` — GraphQL API contracts
- `docs/schemas/` — DB and GraphQL schemas
- `plans/vod-replay-testing-plan.md` — Twitch VOD replay workflow
- `plan.md` — full 12-week production roadmap
