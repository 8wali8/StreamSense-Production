# Sprint 11 Performance Report

## Status

This is the first Sprint 11 performance working document.

What is complete in this iteration:

- repeatable chat-ingest load tooling exists under `tools/load/`
- performance-oriented Grafana dashboard provisioning exists for Docker and Kubernetes
- schema drift protection now exists for the documented core event contracts
- sentiment and sponsor services now emit persistence and end-to-end latency timers

What is not complete in this iteration:

- the live benchmark exposed gateway rate limiting at the current test rate, so the numbers below reflect current behavior rather than a no-limit saturation test

## Implemented Measurement Assets

### Load tooling

- `tools/load/chat_ingest_load.py`
- `tools/load/README.md`

The load tool:

- sends paced `POST /api/chat/ingest` requests through the gateway path
- records request latency and status codes
- captures returned `eventId` values
- queries `GET /api/sentiment/recent` after a settle period
- computes matched end-to-end sentiment latency from `processedAt - ingest timestamp`

### Dashboarding

- Docker Grafana dashboard: `monitoring/grafana/provisioning/dashboards/performance-overview.json`
- Kubernetes Grafana ConfigMap embed: `k8s/config/grafana-config.yaml`

Dashboard panels added:

- chat ingest rate
- total consumer lag
- cache hit ratio
- fallback rate
- sentiment and sponsor end-to-end latency p95
- sentiment and sponsor persistence latency p95
- sentiment and sponsor ML inference latency p95
- Kafka produce and consume rates

### New service metrics

- `streamsense_sentiment_persistence_latency_ms`
- `streamsense_sentiment_end_to_end_latency_ms`
- `streamsense_sponsor_persistence_latency_ms`
- `streamsense_sponsor_end_to_end_latency_ms`

## Verification Completed

### Contract protection

Executed:

- `cd chat-service && mvn -Dtest=ChatMessageSchemaContractTest test`
- `cd sentiment-service && mvn clean -Dtest=SentimentAnalysisSchemaContractTest test`
- `cd video-service && mvn -Dtest=VideoEventSchemaContractTest test`

The new contract tests verify that documented JSON schemas match the actual serialized event shapes used by the services.

### Affected service suites

Executed:

- `cd chat-service && mvn test`
- `cd sentiment-service && mvn test`
- `cd video-service && mvn test`

These runs verified that the added latency timers did not break the existing service suites.

### Load tool validation

Because the real stack was unavailable, the load tool was validated end-to-end against a local mock HTTP server that simulated:

- `POST /api/chat/ingest`
- `GET /api/sentiment/recent`

Executed scenario:

- rate: `2 req/s`
- duration: `5s`
- streamers: `2`
- settle period: `1s`

Observed tooling-validation result:

| Metric | Value |
|------|------|
| Requests attempted | `10` |
| Requests succeeded | `10` |
| HTTP request mean latency | `4.15 ms` |
| HTTP request p95 latency | `14.71 ms` |
| Matched sentiment events | `10 / 10` |
| Matched sentiment end-to-end p95 | `250.0 ms` |

These numbers validate the tool mechanics only. They are not platform performance claims.

### Dashboard and manifest validation

Executed:

- `kubectl kustomize k8s > /tmp/streamsense-k8s-rendered.yaml`
- JSON validation for `k8s/config/grafana-config.yaml`
- JSON validation for `monitoring/grafana/provisioning/dashboards/performance-overview.json`

Results:

- Kubernetes manifests rendered successfully
- embedded Grafana dashboard JSON parsed successfully
- Docker Grafana dashboard JSON parsed successfully

## Live Measurement Blocker

The original Docker-availability blocker was resolved by running the stack from Ubuntu WSL against Docker Desktop.

One environment-specific issue remained during packaging:

- Maven could not write build outputs on the Windows-mounted checkout, so the repo was copied to `/home/ujjawal/StreamSense-Production` for the live run.

## Live Run Results

### Baseline

- requests attempted: `60`
- requests succeeded: `48`
- HTTP request p50: `13.31 ms`
- HTTP request p95: `16.96 ms`
- matched sentiment events: `48 / 48`
- matched sentiment p50: `20.0 ms`
- matched sentiment p95: `176.5 ms`
- status codes: `48 x 200`, `12 x 429`

### Degraded path

- requests attempted: `60`
- requests succeeded: `14`
- HTTP request p50: `3.77 ms`
- HTTP request p95: `13.18 ms`
- matched sentiment events: `4`
- unmatched events: `10`
- matched sentiment p50: `9108.0 ms`
- matched sentiment p95: `10049.5 ms`
- status codes: `14 x 200`, `46 x 429`

### Environment Notes

- Docker Desktop 4.69.0 on WSL2
- Java 21 in Ubuntu WSL
- Maven 3.8.7 in Ubuntu WSL
- Node 20.20.2 via `nvm`
- `kubectl kustomize k8s` rendered successfully
- `cd frontend && npm run test` passed
- `make test` passed from the Ubuntu copy of the repo

## Next Report Update

Future updates should focus on:

- gateway rate-limiting tuning
- dashboard screenshots or metric references under load
- Kafka lag, cache ratio, and persistence-latency observations
