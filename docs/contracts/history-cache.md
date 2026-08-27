# History Cache Contract

## Ownership

- `sentiment-service` owns caching for `GET /api/sentiment/recent`.
- `video-service` owns caching for `GET /api/video/detections/recent`.
- `api-gateway` remains a caller for GraphQL history queries and does not own cached history state.

## Source Of Truth

- Postgres is the source of truth for historical analytics data.
- Redis is an ephemeral hot-read cache only.
- Kafka remains for event transport and live subscription fanout, not history queries.

## Cache Pattern

Both services use the same read-through pattern:

1. read Redis by service-owned key
2. on hit, return cached JSON payload
3. on miss, query Postgres
4. cache the response with TTL
5. return the DB result

After a new history row is persisted, the owning service evicts recent-history keys for that streamer so repeated reads stay fresh.

## Key Shapes

- sentiment: `sentiment:recent:{urlEncodedStreamer}:{limit}`
- sponsor: `sponsor:recent:{urlEncodedStreamer}:{limit}`

## Serialization

- Redis values are JSON strings matching the service history response payload shape.
- Java-native binary serialization is intentionally avoided so cache contents stay inspectable.

## TTL

- initial TTL is 60 seconds
- TTL is centrally configured through Config Server per service

## Persistence Notes

- `sentiment_events` keeps index `(streamer, chat_timestamp DESC)`
- `sponsor_detections` keeps index `(streamer, captured_at DESC)`

These indexes match the current historical query shape:

- filter by streamer
- order by newest first
- limit by recent N

## Retention Stance

- Sprint 6 does not add archival or deletion jobs yet.
- Redis is expected to expire data automatically.
- Postgres remains authoritative until a later retention policy is implemented.
