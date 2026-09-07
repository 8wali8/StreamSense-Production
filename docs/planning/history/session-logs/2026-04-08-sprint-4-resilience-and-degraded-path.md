# Sprint 4 Work Log: Resilience And Degraded Path Verification

## Objective

Add production-style resilience and explicit degraded behavior to the Sprint 3 sentiment pipeline without breaking ingest.

Target runtime path:

`chat ingest -> Kafka chat event -> sentiment-service -> protected ml-engine call -> fallback or success -> persistence -> GraphQL -> frontend`

## Scope Completed So Far

### 1. Added resilience to `sentiment-service`

Main changes:

- added Resilience4j dependencies to `sentiment-service`
- protected the ML dependency call with:
  - circuit breaker
  - retry
  - bulkhead
- kept timeout behavior explicit through config-backed `RestTemplate` timeouts

Main files affected:

- `sentiment-service/pom.xml`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/client/MlEngineClient.java`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/config/RestClientConfig.java`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/config/StreamSenseProperties.java`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/client/MlDependencyException.java`

### 2. Implemented explicit fallback behavior

Fallback contract implemented:

- `label = NEUTRAL`
- `score = 0.0`
- `modelVersion = fallback`

Behavior implemented:

- fallback sentiment is still persisted
- fallback sentiment is still published downstream
- fallback is clearly logged

Main files affected:

- `sentiment-service/src/main/java/com/streamsense/sentimentservice/client/MlEngineClient.java`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/service/SentimentService.java`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/metrics/SentimentMetrics.java`

### 3. Added retry and dead-letter handling

Implemented:

- bounded retry with backoff for Kafka listener failures
- dead-letter publishing after retry exhaustion
- DLT topic for terminal chat-processing failures:
  - `stream.chat.messages.dlt`

Main files affected:

- `sentiment-service/src/main/java/com/streamsense/sentimentservice/config/KafkaProcessingConfig.java`
- `config-server/config-repo/sentiment-service.yml`
- `docker-compose.yml`

### 4. Added central resilience config and future `video-service` scaffolding

Implemented:

- real `resilience4j.*` config in Config Server
- compatibility-style `hystrix.*` config section for sentiment resilience
- reserved future `video-service` resilience config shape for later sponsor ML work

Main files affected:

- `config-server/config-repo/sentiment-service.yml`
- `config-server/config-repo/video-service.yml`
- `docs/compatibility/resilience4j-hystrix-mapping.md`

### 5. Added chaos toggle to `ml-engine`

Implemented:

- `ML_ENGINE_FORCE_FAILURE=true`

This allows repeated degraded-path demos without manually editing code or permanently stopping the container.

Main files affected:

- `ml-engine/src/main/python/app/main.py`
- `ml-engine/src/test/python/test_sentiment.py`
- `docker-compose.yml`

### 6. Added Sprint 4 tests

Implemented coverage for:

- fallback contract creation
- fallback persistence/publication when ML fails
- transient retry path
- dead-letter behavior for terminal failure
- GraphQL fallback history path
- frontend fallback rendering
- `ml-engine` forced-failure mode

Main files affected:

- `sentiment-service/src/test/java/com/streamsense/sentimentservice/client/MlEngineClientTest.java`
- `sentiment-service/src/test/java/com/streamsense/sentimentservice/SentimentPipelineIntegrationTest.java`
- `api-gateway/src/test/java/com/streamsense/apigateway/graphql/SentimentHistoryQueryTest.java`
- `frontend/src/components/SentimentPanel.test.tsx`
- `ml-engine/src/test/python/test_sentiment.py`

### 7. Added Sprint 4 observability and runbook updates

Implemented:

- new resilience counters and protected-call metrics
- Grafana dashboard for fallback, DLT, protected calls, and breaker state
- Sprint 4 runbook steps for healthy and degraded verification

Main files affected:

- `sentiment-service/src/main/java/com/streamsense/sentimentservice/metrics/SentimentMetrics.java`
- `monitoring/grafana/provisioning/dashboards/sprint4-resilience-overview.json`
- `docs/howtorun.md`

## Verification Already Performed

### Local automated checks

Passed:

- `sentiment-service` Maven tests
- `api-gateway` Maven tests
- frontend lint/tests
- `ml-engine` pytest
- `docker compose config`

### Live Docker healthy-path verification

Verified:

- healthy ingest still works
- sentiment still persists normally
- healthy `recentSentiment` still works through REST and GraphQL
- healthy responses still use normal `modelVersion` values such as `stub-v1`

### Live Docker degraded-path verification

Verified:

- recreated `ml-engine` with `ML_ENGINE_FORCE_FAILURE=true`
- ingest still succeeded during ML failure
- fallback sentiment appeared through:
  - `GET /api/sentiment/recent`
  - GraphQL `recentSentiment`
  - GraphQL live `onSentiment`
- fallback values observed live:
  - `label = NEUTRAL`
  - `score = 0.0`
  - `modelVersion = fallback`
- frontend remained available during degraded mode

### Live Docker DLT verification

Verified:

- stopped Postgres temporarily
- ingested a chat event
- observed retries in `sentiment-service` logs
- observed dead-letter logging
- confirmed the original chat event landed in:
  - `stream.chat.messages.dlt`

### Live Docker observability verification

Verified:

- Prometheus query returned:
  - `streamsense_sentiment_fallback_total`
  - `streamsense_sentiment_dead_letter_total`
  - `streamsense_ml_protected_calls_total`
  - `resilience4j_circuitbreaker_state{name="mlSentiment"}`
- Grafana dashboard was provisioned successfully:
  - `Sprint 4 Resilience Overview`
- Zipkin service list included:
  - `api-gateway`
  - `chat-service`
  - `sentiment-service`

## Important Notes

- after rebuilding `sentiment-service`, `api-gateway` needed a restart to clear a stale container IP connection for GraphQL history requests
- the fallback path is fully proven live in `sentiment-service`, REST, GraphQL, and live subscription flow
- the DLT path is proven live using a temporary Postgres outage
- recovery back to normal ML processing is also proven live

## Remaining Gaps / Blockers

No functional blocker remains for the core Sprint 4 resilience behavior.

Remaining strict-closure gaps are observational rather than implementation blockers:

- degraded-path Zipkin proof is still weaker than the other signals
  - Zipkin is up and services are visible
  - but a strong representative degraded-path trace was not captured cleanly in the session notes
- breaker recovery was functionally proven, but the clean metric sample observed after recovery still showed `half_open` during the short observation window rather than an explicit captured `closed = 1`
- frontend fallback behavior is covered by tests and the frontend stayed up live, but the log does not yet include a browser-level screenshot-style confirmation of the fallback card contents during forced-failure mode

## Net Effect

Sprint 4 is mostly complete and functionally working:

- ML failure no longer breaks ingest
- fallback sentiment is explicit and visible
- terminal failures are retried and dead-lettered instead of silently lost
- resilience is centrally configurable
- resilience metrics and dashboards are in place
- healthy-path, degraded-path, and recovery behavior have all been exercised live in Docker
