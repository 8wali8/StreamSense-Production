# Performance Report

## Status

This report includes the first live Compose benchmark captured in `session-logs/2026-04-23-sprint-11-live-compose-benchmark-and-metrics.md` and the first rate-limit-relaxed benchmark captured in `session-logs/2026-04-28-rate-limit-relaxed-benchmark.md`.

The measured numbers are local-demo measurements, not cloud production claims. Gateway rate limiting was active during the recorded run, so `429` responses are part of the current-system result.

## Implemented Measurement Assets

Load tooling:

- `tools/load/chat_ingest_load.py`
- `tools/load/README.md`

Demo and smoke tooling:

- `tools/demo/seed_demo.py`
- `tools/demo/open_demo.py`
- `tools/smoke/compose_smoke.py`

Dashboarding:

- Docker Grafana dashboard: `monitoring/grafana/provisioning/dashboards/performance-overview.json`
- Kubernetes Grafana ConfigMap embed: `k8s/config/grafana-config.yaml`

Service metrics:

- `streamsense_sentiment_persistence_latency_ms`
- `streamsense_sentiment_end_to_end_latency_ms`
- `streamsense_sponsor_persistence_latency_ms`
- `streamsense_sponsor_end_to_end_latency_ms`

Contract protection:

- JSON schema tests for chat, sentiment, and video events
- GraphQL schema contract coverage in `api-gateway`

## Live Environment

The first live benchmark was run from an Ubuntu WSL copy of the repo with Docker Desktop integration.

Tools used:

- Java 21
- Maven 3.8.7
- Node 20.20.2
- Python
- Docker CLI / Docker Desktop 4.69.0 on WSL2
- `kubectl`
- `make`

Startup and verification commands:

```bash
make package
docker compose up -d --build
kubectl kustomize k8s
cd frontend && npm run test
make test
```

Health checks passed for:

- `http://localhost:8080/actuator/health`
- `http://localhost:8083/actuator/health`
- `http://localhost:8084/actuator/health`
- `http://localhost:8000/ml/health`
- `http://localhost:9090/-/healthy`
- `http://localhost:3001/api/health`

## Baseline Run

Command:

```bash
python3 tools/load/chat_ingest_load.py \
  --base-url http://localhost:8080 \
  --rate 2 \
  --duration 30 \
  --streamers 3 \
  --output /tmp/streamsense-baseline.json
```

Results:

| Metric | Value |
|------|------|
| Requests attempted | `60` |
| Requests succeeded | `48` |
| HTTP p50 | `13.31 ms` |
| HTTP p95 | `16.96 ms` |
| Matched sentiment events | `48 / 48` |
| Sentiment p50 | `20.0 ms` |
| Sentiment p95 | `176.5 ms` |
| Status codes | `48 x 200`, `12 x 429` |

Interpretation:

- successful requests were processed and matched back to sentiment history
- `429` responses came from the current gateway rate-limit policy
- these numbers measure the demo stack with edge protection enabled

## Degraded-Path Run

Command:

```bash
ML_ENGINE_FORCE_FAILURE=true docker compose up -d ml-engine
python3 tools/load/chat_ingest_load.py \
  --base-url http://localhost:8080 \
  --rate 2 \
  --duration 30 \
  --streamers 3 \
  --output /tmp/streamsense-degraded.json
ML_ENGINE_FORCE_FAILURE=false docker compose up -d ml-engine
```

Results:

| Metric | Value |
|------|------|
| Requests attempted | `60` |
| Requests succeeded | `14` |
| HTTP p50 | `3.77 ms` |
| HTTP p95 | `13.18 ms` |
| Matched sentiment events | `4` |
| Unmatched events | `10` |
| Sentiment p50 | `9108.0 ms` |
| Sentiment p95 | `10049.5 ms` |
| Status codes | `14 x 200`, `46 x 429` |

Interpretation:

- gateway rate limiting dominated the degraded run
- matched degraded sentiment latency reflects fallback/retry behavior under forced ML failure
- unmatched events likely needed a longer settle window or were still processing when the load tool queried recent history

## Rate-Limit-Relaxed Benchmark Mode

Use this mode when the measurement goal is downstream processing behavior rather than current edge rejection behavior.

```bash
STREAMSENSE_GATEWAY_RATE_LIMIT_ENABLED=false docker compose up -d api-gateway
python tools/load/chat_ingest_load.py \
  --base-url http://localhost:8080 \
  --rate 2 \
  --duration 30 \
  --streamers 3 \
  --output /tmp/streamsense-relaxed.json
STREAMSENSE_GATEWAY_RATE_LIMIT_ENABLED=true docker compose up -d api-gateway
```

Label any results from this mode separately. They are not directly comparable to the default edge-protected benchmark.

## Rate-Limit-Relaxed Baseline Run

Command:

```bash
STREAMSENSE_GATEWAY_RATE_LIMIT_ENABLED=false docker compose up -d --force-recreate api-gateway
python3 tools/load/chat_ingest_load.py \
  --base-url http://localhost:8080 \
  --rate 2 \
  --duration 30 \
  --streamers 3 \
  --output /tmp/streamsense-relaxed-2026-04-28.json
STREAMSENSE_GATEWAY_RATE_LIMIT_ENABLED=true docker compose up -d --force-recreate api-gateway
```

Results:

| Metric | Value |
|------|------|
| Requests attempted | `60` |
| Requests succeeded | `60` |
| Achieved request rate | `2.03 req/s` |
| HTTP p50 | `11.82 ms` |
| HTTP p95 | `18.62 ms` |
| Matched sentiment events | `60 / 60` |
| Sentiment p50 | `19.0 ms` |
| Sentiment p95 | `34.35 ms` |
| Status codes | `60 x 200` |

Supporting Prometheus observations captured immediately after the run:

| Metric | Value |
|------|------|
| `sum(kafka_consumergroup_lag)` | `0` |
| `streamsense_cache_hits_total{cache="recentSentiment"}` | absent, treated as `0` |
| `streamsense_cache_misses_total{cache="recentSentiment"}` | `3` |
| `streamsense_sentiment_fallback_total` | absent, treated as `0` |
| `streamsense_gateway_rate_limit_rejections_total` | absent while disabled, treated as `0` |
| `streamsense_sentiment_persistence_latency_ms_seconds_count` | `60` |
| `streamsense_sentiment_persistence_latency_ms_seconds_sum` | `0.172752129 s` |
| `streamsense_sentiment_persistence_latency_ms_seconds_max` | `0.079001168 s` |
| `streamsense_sentiment_end_to_end_latency_ms_seconds_count` | `60` |
| `streamsense_sentiment_end_to_end_latency_ms_seconds_sum` | `3.064 s` |
| `streamsense_sentiment_end_to_end_latency_ms_seconds_max` | `1.104 s` |

Interpretation:

- disabling the gateway rate limiter removed edge rejections for this run
- the downstream chat -> sentiment path processed and persisted every accepted event within the load tool settle window
- Kafka consumer lag was `0` at the post-run Prometheus sample
- cache misses were expected for the three streamer history reads performed by the load tool; no cache hits were observed during this one-shot run

## Remaining Performance Work

- run and record one rate-limit-relaxed degraded-path benchmark with a longer settle window
- attach Grafana screenshots or dashboard observations for Kafka lag, cache hit ratio, fallback rate, and persistence latency if needed for final demo evidence
- keep all README or demo claims limited to measured values from this report
