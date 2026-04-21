# Sprint 11 Performance Report

## Status

This is the first Sprint 11 performance working document.

What is complete in this iteration:

- repeatable chat-ingest load tooling exists under `tools/load/`
- performance-oriented Grafana dashboard provisioning exists for Docker and Kubernetes
- schema drift protection now exists for the documented core event contracts
- sentiment and sponsor services now emit persistence and end-to-end latency timers

What is not complete in this iteration:

- a live Docker Compose benchmark was not executed in this session because Docker was unavailable from the local environment
- the measured numbers below therefore cover tooling validation only, not full platform throughput claims

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

The live Compose benchmark planned for this Sprint 11 slice could not run in this session because Docker was unavailable:

- `docker compose down --remove-orphans` failed with `Cannot connect to the Docker daemon`
- gateway, sentiment-service, Prometheus, and Grafana localhost health checks all failed because the stack was not running

That means no honest end-to-end platform throughput or latency numbers should be claimed yet from this session.

## First Live Runs To Execute Once Docker Is Available

### Baseline

```bash
make package
docker compose up -d --build
python tools/load/chat_ingest_load.py \
  --base-url http://localhost:8080 \
  --rate 2 \
  --duration 30 \
  --streamers 3 \
  --output /tmp/streamsense-baseline.json
```

### Degraded path

```bash
ML_ENGINE_FORCE_FAILURE=true docker compose up -d ml-engine
python tools/load/chat_ingest_load.py \
  --base-url http://localhost:8080 \
  --rate 2 \
  --duration 30 \
  --streamers 3 \
  --output /tmp/streamsense-degraded.json
ML_ENGINE_FORCE_FAILURE=false docker compose up -d ml-engine
```

## Next Report Update

When Docker is available, update this document with:

- environment details for the actual run machine
- baseline request latency p50 and p95
- matched sentiment end-to-end p50 and p95
- fallback or error behavior under `ML_ENGINE_FORCE_FAILURE=true`
- screenshots or metric references from the performance dashboard
- any bottleneck observations in Kafka lag, cache ratio, or persistence latency
