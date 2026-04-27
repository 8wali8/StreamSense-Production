# Degraded-Path Proof Runbook

Use this runbook to capture final evidence that StreamSense degrades explicitly when `ml-engine` fails instead of dropping work silently.

## Goal

Prove this path:

`chat ingest -> Kafka -> sentiment-service -> failed ML call -> Resilience4j fallback -> Postgres -> Kafka sentiment event -> GraphQL -> frontend/metrics/traces`

Expected fallback contract:

- `label = NEUTRAL`
- `score = 0.0`
- `modelVersion = fallback`

## 1. Start A Clean Stack

```bash
make up
python tools/demo/seed_demo.py --streamer degraded-proof-normal
```

Verify the normal path first:

```bash
curl -fsS http://localhost:8080/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"query($streamer:String!,$limit:Int!){ recentSentiment(streamer:$streamer, limit:$limit){ label score modelVersion }}","variables":{"streamer":"degraded-proof-normal","limit":5}}'
```

## 2. Force ML Failure

```bash
ML_ENGINE_FORCE_FAILURE=true docker compose up -d ml-engine
```

Wait for the health check to settle:

```bash
curl -fsS http://localhost:8000/ml/health
```

Seed degraded-path data:

```bash
python tools/demo/seed_demo.py --streamer degraded-proof-fallback --settle-seconds 10
```

## 3. Verify GraphQL Fallback Data

```bash
curl -fsS http://localhost:8080/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"query($streamer:String!,$limit:Int!){ recentSentiment(streamer:$streamer, limit:$limit){ label score modelVersion }}","variables":{"streamer":"degraded-proof-fallback","limit":5}}'
```

Acceptance evidence:

- at least one row appears
- `label` is `NEUTRAL`
- `score` is `0.0`
- `modelVersion` is `fallback`

## 4. Verify Prometheus Metrics

Open Prometheus at `http://localhost:9090` and query:

```promql
streamsense_sentiment_fallback_total
```

```promql
streamsense_ml_protected_calls_total
```

```promql
resilience4j_circuitbreaker_state{name="mlSentiment"}
```

Acceptance evidence:

- fallback counter increased
- protected-call counter increased
- circuit-breaker state shows open or half-open during failure

## 5. Capture Zipkin Evidence

Open Zipkin at `http://localhost:9411`.

Search for traces involving:

- `api-gateway`
- `chat-service`
- `sentiment-service`

Acceptance evidence:

- trace shows the ingest request and downstream processing services during the forced-failure window
- if the exact fallback span is not visible, record the trace ID and pair it with the Prometheus fallback metric sample from the same time window

## 6. Verify Frontend Fallback Rendering

Open `http://localhost:3000` and use streamer `degraded-proof-fallback` if the UI exposes a streamer selector. If the UI uses a fixed/default streamer, seed that streamer instead.

Acceptance evidence:

- sentiment panel remains usable
- fallback sentiment is visible as neutral/fallback data or the panel continues updating without an error state
- record the streamer, time, and observed UI state in the final demo notes

## 7. Verify Recovery

Restore normal ML behavior:

```bash
ML_ENGINE_FORCE_FAILURE=false docker compose up -d ml-engine
```

Seed recovery data:

```bash
python tools/demo/seed_demo.py --streamer degraded-proof-recovery --settle-seconds 10
```

Check that new sentiment records return a normal model version such as `stub-v1`:

```bash
curl -fsS http://localhost:8080/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"query($streamer:String!,$limit:Int!){ recentSentiment(streamer:$streamer, limit:$limit){ label score modelVersion }}","variables":{"streamer":"degraded-proof-recovery","limit":5}}'
```

Capture circuit-breaker recovery evidence:

```promql
resilience4j_circuitbreaker_state{name="mlSentiment",state="closed"}
```

Acceptance evidence:

- recovery data uses a normal model version
- circuit breaker returns to closed state

## Final Evidence To Record

- GraphQL fallback response payload
- Prometheus fallback counter sample
- Prometheus circuit-breaker state sample during failure and after recovery
- Zipkin trace ID or screenshot from the forced-failure window
- frontend fallback observation note
