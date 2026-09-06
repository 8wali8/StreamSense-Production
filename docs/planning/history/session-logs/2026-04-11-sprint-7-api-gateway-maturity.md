# Sprint 7 - API Gateway Maturity

## Goal

Make `api-gateway` behave like a production-shaped edge service with centralized routing, auth hooks, rate limiting, modular GraphQL schema files, and more reliable subscription behavior.

## What changed

- added centralized Spring Cloud Gateway `/api/**` routes for chat, sentiment, and video traffic in `config-server/config-repo/api-gateway.yml`
- added gateway auth properties and a JWT-shaped auth filter with local bypass mode
- added gateway rate limiting for ingest-facing routes with observable rejection metrics
- added a routed-request header filter so route IDs are visible on proxied traffic
- split the GraphQL schema into domain files under `api-gateway/src/main/resources/graphql/`
- hardened frontend websocket client settings with keepalive, retry backoff, and optional bearer-token connection params
- added gateway unit and integration tests for JWT validation, auth enforcement, route proxying, schema contract stability, rate limiting, and replay-based subscription behavior
- added `STREAMSENSE_GATEWAY_AUTH_ENABLED` to `docker-compose.yml` so auth mode is testable in local Docker runs

## Tests run

### Gateway and frontend

```bash
cd api-gateway
mvn test

cd frontend
npm test
npm run build

cd api-gateway
mvn -DskipTests package
```

### Docker verification

```bash
docker compose up -d --build eureka-server config-server zookeeper kafka kafka-topics-init postgres redis ml-engine chat-service sentiment-service video-service api-gateway frontend
```

Initial runtime issue found:

- Kafka failed to start because `/brokers/ids/1` still existed in ZooKeeper from an older broker session

Recovery used:

```bash
docker compose rm -sf kafka zookeeper kafka-topics-init
docker compose up -d zookeeper kafka kafka-topics-init
docker compose up -d chat-service sentiment-service video-service api-gateway frontend
```

## End-to-end verification completed

### Routed ingest through gateway

- `POST http://localhost:8080/api/chat/ingest` returned an event id through the gateway route
- `POST http://localhost:8080/api/video/upload-frame` returned `accepted` through the gateway route

### GraphQL history after routed ingest

- `recentSentiment(streamer, limit)` returned persisted sentiment history for `sprint7-e2e`
- `sponsorDetections(streamer, limit)` returned persisted sponsor history for `sprint7-e2e`

### Rate limiting

- 31 routed chat-ingest requests from the same `X-Forwarded-For` value produced `30` successes and `1` `429`
- live Prometheus metrics exposed:
  - `spring_cloud_gateway_routes_count 3.0`
  - `streamsense_gateway_rate_limit_rejections_total{limit="chat-ingest",path="/api/chat/ingest"} 1.0`

### Auth toggle

Auth enabled verification:

```bash
STREAMSENSE_GATEWAY_AUTH_ENABLED=true docker compose up -d api-gateway
```

Observed behavior:

- unauthenticated `POST /graphql` returned `401`
- a valid JWT-shaped bearer token returned `200` and GraphQL `health: ok`

Restored local bypass mode:

```bash
STREAMSENSE_GATEWAY_AUTH_ENABLED=false docker compose up -d api-gateway
```

## Result

Sprint 7 implementation is working locally with:

- centralized gateway routing
- auth hook plus Docker-friendly bypass mode
- live rate-limit enforcement on ingest traffic
- modular GraphQL schema loading
- preserved service-owned history queries
- stable subscription and frontend reconnect defaults
