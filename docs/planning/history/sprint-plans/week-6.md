# Sprint 6 Implementation Plan

## Goal

Harden the data-access layer for the platform and add Redis-backed hot-read caching for the two historical analytics paths that now exist:

`recentSentiment` and `sponsorDetections`

Sprint 6 should make historical reads faster, more production-shaped, and still clearly service-owned.

The target runtime shape for this sprint is:

`api-gateway GraphQL history query -> service-owned REST history API -> Redis cache or Postgres -> response`

for both:

- `sentiment-service` history
- `video-service` sponsor history

Sprint 6 is not about adding new product surfaces. It is about tightening the read path, making persistence more trustworthy, and establishing a cache pattern the rest of the roadmap can build on.

## Sprint 6 Success Criteria

Sprint 6 is complete only when all of the following are true:

- Redis runs locally in Docker Compose and is healthy.
- `sentiment-service` uses Redis as a read-through cache for `GET /api/sentiment/recent`.
- `video-service` uses Redis as a read-through cache for `GET /api/video/detections/recent`.
- GraphQL historical queries still fetch through service-owned APIs rather than Kafka-backed state.
- cache key naming, TTL, and serialization format are explicit and documented.
- cache hit and miss behavior is visible in metrics.
- persistence/indexing for the sentiment and sponsor history tables are reviewed and tightened where needed.
- automated tests cover cache hit, cache miss, and fallback-to-Postgres behavior.
- the Sprint 6 history paths are verified live in Docker.

## Current Starting Point

This plan assumes the current repo state after Sprint 5 is:

- `chat-service` owns ingest and publishing of chat events only.
- `sentiment-service` consumes chat events, persists sentiment history in Postgres, and exposes `GET /api/sentiment/recent`.
- `video-service` consumes frame events, persists sponsor history in Postgres, and exposes `GET /api/video/detections/recent`.
- `api-gateway` exposes:
  - `recentSentiment(streamer, limit)`
  - `sponsorDetections(streamer, limit)`
- those GraphQL history queries already call service-owned REST APIs rather than reading from Kafka directly.
- Postgres already exists in Compose and is the source of truth for history reads.
- Redis is not yet present in Compose and no active cache strategy exists.
- cache metrics, cache tests, and retention/cleanup notes are still missing.

The largest Sprint 6 gap is not feature completeness. It is that both history paths currently go straight to Postgres on every read and do not yet have a production-shaped hot-read strategy.

## Important Architecture Note

Sprint 6 must preserve query ownership discipline established in the roadmap:

- historical queries stay service-owned
- Kafka remains for event transport and live subscription fanout
- `api-gateway` should keep calling service APIs for history
- Redis should accelerate those service-owned read paths, not replace them with gateway-side event state

That means:

- do not move history storage into Kafka-backed in-memory state
- do not make `api-gateway` the owner of cached history data
- do not bypass `sentiment-service` or `video-service` for GraphQL history reads

The cache should live behind the existing service APIs so ownership stays clear and later gateway work does not become entangled with domain data storage.

## Scope Decisions For Sprint 6

To keep this sprint tight and production-shaped, use the following defaults unless implementation reality forces a small adjustment:

### Cache Pattern

Use read-through caching inside each owning service:

1. compute a cache key from query inputs
2. check Redis first
3. on hit, return cached JSON payload
4. on miss, read from Postgres
5. store the response in Redis with TTL
6. return the DB result

### Cache Ownership

- `sentiment-service` owns the sentiment history cache
- `video-service` owns the sponsor history cache
- `api-gateway` remains a caller, not a cache owner

### Initial Invalidation Strategy

Use a simple and explicit first version:

- TTL-based expiry is required
- when new sentiment history is written for a streamer, invalidate that streamer's recent-history cache entries if practical
- when new sponsor history is written for a streamer, invalidate that streamer's recent-history cache entries if practical

If precise invalidation becomes too noisy for Sprint 6, document a TTL-only strategy clearly rather than leaving behavior ambiguous.

### Serialization Strategy

Keep the cache representation simple and inspectable:

- cache the actual REST history response payload shape
- prefer JSON serialization over Java-native serialization
- keep service-owned event/history DTO field names aligned with the cached payload shape

### Retention Strategy

Sprint 6 does not need a full archival solution, but it must define a first-pass data retention approach:

- Postgres remains authoritative
- Redis remains ephemeral
- table growth, index usage, and cleanup expectations should be documented, even if retention is still manual or time-based later

## Sprint 6 Deliverables

### 1. Redis Platform Integration

- add Redis to Docker Compose
- add healthchecks and startup ordering where useful
- add service config for Redis host, port, TTL, and cache naming
- document the local Redis role in the runbook

### 2. Sentiment History Cache

- add Redis support to `sentiment-service`
- cache responses for recent sentiment queries by streamer and limit
- expose consistent behavior for cache hits and misses
- keep Postgres as the fallback source of truth

### 3. Sponsor History Cache

- add the same pattern to `video-service`
- cache responses for recent sponsor detection queries by streamer and limit
- keep history reads service-owned and Postgres-backed on cache miss

### 4. Persistence Hardening

- review existing Flyway migrations and indexes for both history tables
- confirm indexes support current query patterns
- tighten any obvious schema or indexing gaps
- document the first retention and cleanup expectations

### 5. Query Discipline Confirmation

- keep `recentSentiment` and `sponsorDetections` GraphQL resolvers calling service APIs
- avoid introducing gateway-local history materialization
- make the service/cache boundaries explicit in docs and tests

### 6. Tests, Observability, And Verification

- add Redis-backed integration coverage
- add cache metrics such as:
  - `streamsense_cache_hits_total{cache=...}`
  - `streamsense_cache_misses_total{cache=...}`
- add Grafana panels or dashboard updates for cache behavior
- verify both cache paths live in Docker

## Required Scope Breakdown

## Phase 1 - Freeze Cache And Read-Path Rules

1. Confirm the two Sprint 6 cache targets:
   - `GET /api/sentiment/recent`
   - `GET /api/video/detections/recent`
2. Freeze the rule that GraphQL history reads stay service-owned.
3. Define the initial cache key format for both services.
4. Define TTL policy and whether invalidation is TTL-only or TTL-plus-streamer-evict.
5. Define the JSON serialization format for cached payloads.
6. Document the retention stance for Postgres vs Redis.

### Suggested cache-key shape

Use simple, explicit keys that are easy to inspect in Redis:

- `sentiment:recent:{streamer}:{limit}`
- `sponsor:recent:{streamer}:{limit}`

### Suggested TTL stance

Start with a short hot-read TTL that keeps freshness understandable during demos and local verification.

Recommended starting point:

- 30 to 120 seconds

Exact value should be centrally configurable from Config Server rather than hardcoded in service code.

## Phase 2 - Add Redis To The Local Platform

1. Add a Redis container to `docker-compose.yml`.
2. Add a Redis healthcheck if the image and startup path support a stable one.
3. Make `sentiment-service` and `video-service` depend on healthy Redis if the cache client should be available at boot.
4. Add shared or per-service Redis settings in Config Server.
5. Keep the configuration Docker-first by default.

### Expected end state

- local Docker Compose includes Redis as a first-class platform dependency
- service config can target Redis predictably in Docker

## Phase 3 - Implement Sentiment History Read-Through Cache

1. Add the Redis client dependency and minimal wiring in `sentiment-service`.
2. Keep the cache orchestration in the service layer rather than the controller.
3. On `recent` query:
   - try Redis first
   - on hit, return cached recent sentiment list
   - on miss, query Postgres, cache the result, return it
4. After new sentiment persistence, evict or allow expiry for affected keys using the chosen strategy.
5. Emit hit/miss metrics with a cache name label.
6. Log enough to distinguish cache hit, miss, and DB fallback without noisy dumps.

### Expected end state for `sentiment-service`

- recent sentiment reads are faster on repeated calls
- cache behavior is operationally visible
- service ownership does not move to the gateway

## Phase 4 - Implement Sponsor History Read-Through Cache

1. Add the same Redis pattern to `video-service`.
2. Reuse the simplest reasonable cache abstraction or helper pattern already proven in `sentiment-service`.
3. On sponsor history query:
   - try Redis first
   - on hit, return cached recent sponsor list
   - on miss, query Postgres, cache the result, return it
4. After new sponsor detection persistence, evict or allow expiry for affected keys using the chosen strategy.
5. Emit matching hit/miss metrics for sponsor history.

### Expected end state for `video-service`

- sponsor history reads follow the same cache discipline as sentiment history
- the two analytics services use a consistent read-path pattern

## Phase 5 - Persistence And Migration Hardening

1. Review current history table indexes:
   - `sentiment_events`
   - `sponsor_detections`
2. Confirm the current query pattern is covered efficiently:
   - filter by streamer
   - order by time descending
   - limit by recent N
3. Add or refine indexes only where a real gap exists.
4. Review Flyway migration naming and ownership clarity.
5. Document simple retention and cleanup expectations so data growth is not ignored.

### Expected end state

- no obvious indexing gaps remain for the current read paths
- persistence remains clear and defensible before recommendation work builds on top of it

## Phase 6 - Tests And Contract Protection

1. Add integration tests in `sentiment-service` covering:
   - cache miss to Postgres path
   - cache hit path
   - cache response shape stability
2. Add integration tests in `video-service` covering the same behavior.
3. Keep or extend gateway tests to prove history queries still call service APIs and return the same shapes.
4. Add contract assertions for cached JSON shape where reasonable.
5. Prefer realistic integration tests with Redis and Postgres over narrow unit-only coverage.

### Minimum automated coverage target

- one credible cache-miss test per service
- one credible cache-hit test per service
- one GraphQL history query test still proving service-owned history behavior remains intact

## Phase 7 - Observability, Runbook, And Live Verification

1. Add cache metrics to the owning services.
2. Add Grafana panels or a dashboard update for:
   - cache hits
   - cache misses
   - history latency comparison
3. Update `docs/howtorun.md` with Sprint 6 verification steps.
4. Verify live in Docker:
   - first history query misses cache and succeeds
   - second identical query hits cache
   - cache metrics change accordingly
   - both sentiment and sponsor history paths still return correct data

### Suggested live verification flow

1. Start the Docker stack with Redis.
2. Seed or create fresh sentiment and sponsor history data.
3. Query recent sentiment twice for the same streamer and limit.
4. Query recent sponsor detections twice for the same streamer and limit.
5. Confirm cache hit/miss metrics increase as expected.
6. Confirm GraphQL history still matches the underlying service response.

## Definition Of Done

Sprint 6 is complete when:

- Redis is part of the local platform and starts reliably
- `sentiment-service` and `video-service` both use Redis for hot historical reads
- Postgres remains the source of truth and is used on cache miss
- GraphQL historical queries remain service-owned and do not regress into Kafka-backed history state
- cache behavior is visible in tests, metrics, and Grafana
- persistence/indexing for the current history queries is reviewed and tightened where needed
- the Sprint 6 read paths are proven live in Docker and documented

## Risks To Watch

- moving caching into `api-gateway` and weakening service ownership
- using opaque Java serialization in Redis instead of a clear payload shape
- implementing cache invalidation that is more complex than the sprint needs
- leaving TTL strategy undocumented and creating confusing stale-read behavior
- adding Redis in a way that makes service boot fragile instead of resilient
- over-optimizing retention before the recommendation and Kubernetes work is ready
