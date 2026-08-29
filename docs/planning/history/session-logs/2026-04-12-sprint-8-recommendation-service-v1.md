# Sprint 8 - Recommendation Service V1

## Goal

Implement a real `recommendation-service` that turns recent sentiment and sponsor history into deterministic, explainable recommendations, then expose that flow through `api-gateway` and the frontend.

## What changed

### recommendation-service

- added typed config binding for downstream services and recommendation experiment variants
- added centralized experiment config in `config-server/config-repo/recommendation-service.yml`
- added downstream REST clients for:
  - `sentiment-service /api/sentiment/recent`
  - `video-service /api/video/detections/recent`
- added deterministic recommendation generation with categories:
  - `CONTENT_MOMENTUM`
  - `SPONSOR_ALIGNMENT`
  - `AUDIENCE_TONE`
  - `CAUTION_SIGNAL`
- added `GET /api/recommendations?streamer={streamer}&limit={limit}`
- added recommendation metrics:
  - `streamsense_recommendations_served_total`
  - `streamsense_experiment_variant_total`
  - `streamsense_recommendation_latency_ms`

### api-gateway

- added `streamsense.services.recommendation-service.base-url`
- added central route for `/api/recommendations/**`
- added GraphQL type `Recommendation`
- added GraphQL query:
  - `recommendations(streamer: String!, limit: Int!): [Recommendation!]!`
- kept gateway logic thin by delegating recommendation generation to `recommendation-service`

### frontend

- added `RECOMMENDATIONS_QUERY`
- added `RecommendationPanel`
- recommendation cards show:
  - title
  - category
  - score
  - reason summary
  - detailed reasons
  - experiment name
  - variant id

### stack and CI

- added recommendation-service healthcheck in `docker-compose.yml`
- made `api-gateway` wait for `recommendation-service`
- updated `docker-smoke` CI to package, build, start, and verify Sprint 8 recommendation behavior

## Tests run

### Backend and frontend tests

```bash
cd recommendation-service
mvn test

cd api-gateway
mvn test

cd frontend
npm test
```

### Packaging and frontend build

```bash
cd recommendation-service
mvn -DskipTests package

cd api-gateway
mvn -DskipTests package

cd frontend
npm run lint
npm run build
```

## Docker verification

### Stack startup

```bash
docker compose up -d --build eureka-server config-server zookeeper kafka kafka-topics-init postgres redis ml-engine chat-service sentiment-service video-service recommendation-service api-gateway frontend
```

### Seed live Sprint 8 data through the gateway

```bash
curl -X POST http://localhost:8080/api/chat/ingest \
  -H "Content-Type: application/json" \
  -d '{"streamer":"sprint8-e2e","user":"u1","message":"this stream is great","timestamp":1710001000000}'

curl -X POST http://localhost:8080/api/chat/ingest \
  -H "Content-Type: application/json" \
  -d '{"streamer":"sprint8-e2e","user":"u2","message":"love this energy","timestamp":1710001001000}'

curl -X POST http://localhost:8080/api/chat/ingest \
  -H "Content-Type: application/json" \
  -d '{"streamer":"sprint8-e2e","user":"u3","message":"pretty solid segment","timestamp":1710001002000}'

curl -X POST http://localhost:8080/api/video/upload-frame \
  -H "Content-Type: application/json" \
  -d '{"streamer":"sprint8-e2e","frameRef":"frames/sprint8-e2e-1.png","frameSequence":1,"capturedAt":1710001003000}'

curl -X POST http://localhost:8080/api/video/upload-frame \
  -H "Content-Type: application/json" \
  -d '{"streamer":"sprint8-e2e","frameRef":"frames/sprint8-e2e-2.png","frameSequence":2,"capturedAt":1710001004000}'
```

### Verified live

- recent sentiment history appeared for `sprint8-e2e`
- recent sponsor detections appeared for `sprint8-e2e`
- direct recommendation REST output returned recommendation objects with explanations
- GraphQL recommendation query returned the same recommendation categories and variant metadata
- frontend served successfully at `http://localhost:3000`
- recommendation metrics were exposed live from `recommendation-service`

Observed live recommendation categories for `sprint8-e2e`:

- `CAUTION_SIGNAL`
- `SPONSOR_ALIGNMENT`
- `AUDIENCE_TONE`
- `CONTENT_MOMENTUM`

Observed live metrics:

- `streamsense_recommendations_served_total{experiment="recommendation-ranking-v1",variant="balanced"} 8.0`
- `streamsense_experiment_variant_total{experiment="recommendation-ranking-v1",variant="balanced"} 2.0`

## Notes

- recommendation output is deterministic for a given signal window and experiment variant; only `generatedAt` changes between requests
- the current ML stub still influences the tone of recommendation output through sentiment and sponsor histories
- Zipkin was not started during the Sprint 8 Docker validation run, so trace export warnings appeared in logs but did not block service health or recommendation behavior

## Result

Sprint 8 now has a working recommendation vertical slice with:

- centralized experiment config
- service-owned signal aggregation
- deterministic recommendation generation
- gateway GraphQL exposure
- frontend recommendation rendering
- test and Docker verification coverage
