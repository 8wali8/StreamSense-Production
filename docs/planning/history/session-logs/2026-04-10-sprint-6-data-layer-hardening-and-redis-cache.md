# Sprint 6 Work Log: Data Layer Hardening And Redis Cache

## Objective

Implement the Week 6 roadmap slice from `plan.md`:

`api-gateway GraphQL history query -> service-owned REST history API -> Redis cache or Postgres -> response`

for both:

- `sentiment-service` recent history
- `video-service` sponsor history

The Sprint 6 target was to add Redis-backed hot-read caching without moving history ownership out of the services that already own those query paths.

## Scope Completed

### 1. Added Redis to the local platform

Implemented:

- added Redis to `docker-compose.yml`
- added Redis healthcheck
- made `sentiment-service` and `video-service` wait for healthy Redis
- updated CI Docker smoke to include Redis reachability

Main files affected:

- `docker-compose.yml`
- `.github/workflows/ci.yml`

### 2. Added Redis-backed sentiment history caching

Implemented:

- added Spring Data Redis dependency to `sentiment-service`
- introduced a service-owned cache interface:
  - `RecentSentimentCache`
- added Redis implementation:
  - `RedisRecentSentimentCache`
- wired `SentimentService#getRecentSentiment` to:
  - read cache first
  - fall back to Postgres on miss
  - cache DB results with TTL
- evict recent sentiment cache entries for a streamer after new sentiment persistence

Main files affected:

- `sentiment-service/pom.xml`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/cache/RecentSentimentCache.java`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/cache/RedisRecentSentimentCache.java`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/service/SentimentService.java`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/config/StreamSenseProperties.java`
- `config-server/config-repo/sentiment-service.yml`

### 3. Added Redis-backed sponsor history caching

Implemented:

- added Spring Data Redis dependency to `video-service`
- introduced a service-owned cache interface:
  - `RecentSponsorDetectionsCache`
- added Redis implementation:
  - `RedisRecentSponsorDetectionsCache`
- wired `VideoProcessingService#getRecentDetections` to:
  - read cache first
  - fall back to Postgres on miss
  - cache DB results with TTL
- evict recent sponsor cache entries for a streamer after new detection persistence

Main files affected:

- `video-service/pom.xml`
- `video-service/src/main/java/com/streamsense/videoservice/cache/RecentSponsorDetectionsCache.java`
- `video-service/src/main/java/com/streamsense/videoservice/cache/RedisRecentSponsorDetectionsCache.java`
- `video-service/src/main/java/com/streamsense/videoservice/service/VideoProcessingService.java`
- `video-service/src/main/java/com/streamsense/videoservice/config/StreamSenseProperties.java`
- `config-server/config-repo/video-service.yml`

### 4. Added cache metrics and lookup timing

Implemented:

- added shared cache counters in both services:
  - `streamsense_cache_hits_total{cache=...}`
  - `streamsense_cache_misses_total{cache=...}`
- added history lookup timing histogram in both services:
  - `streamsense_history_lookup_latency_ms_seconds{cache=...,source=cache|db}`

Main files affected:

- `sentiment-service/src/main/java/com/streamsense/sentimentservice/metrics/SentimentMetrics.java`
- `video-service/src/main/java/com/streamsense/videoservice/metrics/VideoMetrics.java`

### 5. Kept GraphQL history service-owned

Confirmed:

- no gateway-local history cache or materialized history state was added
- GraphQL history still resolves through service APIs

This preserved the roadmap rule that:

- Kafka is for event transport and live fanout
- historical queries stay service-owned

### 6. Added docs and dashboard coverage

Implemented:

- added a history-cache contract note covering:
  - ownership
  - key shapes
  - JSON serialization
  - TTL
  - Postgres as source of truth
  - current index coverage
- added Sprint 6 runbook steps for Redis and cache verification
- added Grafana dashboard:
  - `Sprint 6 Cache Overview`

Main files affected:

- `docs/contracts/history-cache.md`
- `docs/howtorun.md`
- `monitoring/grafana/provisioning/dashboards/sprint6-cache-overview.json`

### 7. Reviewed persistence/indexing posture

Reviewed existing history indexes:

- `sentiment_events (streamer, chat_timestamp DESC)`
- `sponsor_detections (streamer, captured_at DESC)`

Result:

- current indexes already match the active Sprint 6 query pattern:
  - filter by streamer
  - newest first
  - recent limit
- no new Flyway migration was needed for this sprint

## Verification Performed

### Local automated checks

Passed:

- `mvn test` in `sentiment-service`
- `mvn test` in `video-service`
- `docker compose config`

Added coverage for:

- sentiment recent-history cache miss path
- sentiment recent-history cache hit path
- sponsor recent-history cache miss path
- sponsor recent-history cache hit path

### Local packaging checks

Passed:

- `mvn -DskipTests package` in `sentiment-service`
- `mvn -DskipTests package` in `video-service`

### Live Docker verification

Verified live in Docker:

- Redis responded with `PONG`
- seeded a fresh streamer through:
  - `POST /api/chat/ingest`
  - `POST /api/video/upload-frame`
- direct service history queries returned persisted data for that streamer:
  - `GET /api/sentiment/recent`
  - `GET /api/video/detections/recent`
- GraphQL history still returned service-owned results through:
  - `recentSentiment(streamer, limit)`
  - `sponsorDetections(streamer, limit)`
- service actuator metrics showed cache activity:
  - `streamsense_cache_hits_total{cache="recentSentiment"}`
  - `streamsense_cache_misses_total{cache="recentSentiment"}`
  - `streamsense_cache_hits_total{cache="recentSponsorDetections"}`
  - `streamsense_cache_misses_total{cache="recentSponsorDetections"}`
  - `streamsense_history_lookup_latency_ms_seconds_count{cache="recentSentiment",source="cache"}`
  - `streamsense_history_lookup_latency_ms_seconds_count{cache="recentSponsorDetections",source="cache"}`

Observed values after the live run:

- sentiment cache hits: `3.0`
- sentiment cache misses: `1.0`
- sponsor cache hits: `3.0`
- sponsor cache misses: `1.0`

## Important Runtime Notes

- during the live verification session, local Docker image metadata for several service images became corrupted and had to be rebuilt cleanly
- Kafka also needed a clean restart because Zookeeper still held a stale broker registration after the interrupted restart
- once Redis, Kafka, and the dependent services were restarted cleanly, the Sprint 6 cache path verified successfully

## Net Effect

Sprint 6 is functionally complete:

- Redis is now part of the local platform
- `sentiment-service` and `video-service` both use Redis for hot recent-history reads
- Postgres remains the source of truth
- GraphQL historical queries remain service-owned
- cache behavior is visible through tests, metrics, and Grafana
