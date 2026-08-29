# Rate-Limit-Relaxed Compose Benchmark

## Goal

Run the chat ingest benchmark with gateway rate limiting disabled so the measurement reflects downstream processing rather than edge `429` rejection behavior.

## Environment Setup

This shell initially had Docker CLI access but did not have Docker Compose, Java, or Maven installed. Installed the missing local tools required by the repository workflow:

```bash
sudo apt-get install -y docker-compose-v2
sudo apt-get install -y openjdk-21-jdk-headless maven
```

Then packaged, built, and started the Compose stack:

```bash
make up
```

Verified the main health endpoints before the benchmark:

- `http://localhost:8080/actuator/health` returned `UP`
- `http://localhost:8083/actuator/health` returned `UP`
- `http://localhost:8000/ml/health` returned `ok`

## Benchmark Commands

Disabled gateway rate limiting and verified the gateway environment value inside the container:

```bash
STREAMSENSE_GATEWAY_RATE_LIMIT_ENABLED=false docker compose up -d --force-recreate api-gateway
docker compose exec -T api-gateway printenv STREAMSENSE_GATEWAY_RATE_LIMIT_ENABLED
```

The environment value was `false`.

Ran the load benchmark:

```bash
python3 tools/load/chat_ingest_load.py \
  --base-url http://localhost:8080 \
  --rate 2 \
  --duration 30 \
  --streamers 3 \
  --output /tmp/streamsense-relaxed-2026-04-28.json
```

Restored the default gateway rate-limiting setting after the run:

```bash
STREAMSENSE_GATEWAY_RATE_LIMIT_ENABLED=true docker compose up -d --force-recreate api-gateway
```

## Load Tool Results

- requests attempted: `60`
- requests succeeded: `60`
- failed requests: `0`
- achieved request rate: `2.03 req/s`
- HTTP p50: `11.82 ms`
- HTTP p95: `18.62 ms`
- matched sentiment events: `60 / 60`
- unmatched sentiment events: `0`
- sentiment p50: `19.0 ms`
- sentiment p95: `34.35 ms`
- status codes: `60 x 200`

Raw JSON output was written to:

```text
/tmp/streamsense-relaxed-2026-04-28.json
```

## Prometheus Samples

Captured immediately after the load run:

- `sum(kafka_consumergroup_lag)`: `0`
- `streamsense_cache_hits_total{cache="recentSentiment"}`: absent, treated as `0`
- `streamsense_cache_misses_total{cache="recentSentiment"}`: `3`
- `streamsense_sentiment_fallback_total`: absent, treated as `0`
- `streamsense_gateway_rate_limit_rejections_total`: absent while disabled, treated as `0`
- `streamsense_sentiment_persistence_latency_ms_seconds_count`: `60`
- `streamsense_sentiment_persistence_latency_ms_seconds_sum`: `0.172752129 s`
- `streamsense_sentiment_persistence_latency_ms_seconds_max`: `0.079001168 s`
- `streamsense_sentiment_end_to_end_latency_ms_seconds_count`: `60`
- `streamsense_sentiment_end_to_end_latency_ms_seconds_sum`: `3.064 s`
- `streamsense_sentiment_end_to_end_latency_ms_seconds_max`: `1.104 s`

## Result

The rate-limit-relaxed baseline succeeded. With the gateway rate limiter disabled, the benchmark produced no `429` responses and all `60` accepted chat events were matched to persisted sentiment results within the load tool settle window.

## Files Changed

- `docs/performance-report.md`
- `opencodeCommandHistory/2026-04-28-rate-limit-relaxed-benchmark.md`
