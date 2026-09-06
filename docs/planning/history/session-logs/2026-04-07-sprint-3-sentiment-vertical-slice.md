# Sprint 3 Work Log: Sentiment Vertical Slice

## Objective

Implement the first real sentiment analytics vertical slice:

`chat ingest -> Kafka chat event -> sentiment-service -> ml-engine -> Postgres persistence -> GraphQL query/subscription -> frontend sentiment view`

## Scope Completed

### 1. Corrected service ownership

`chat-service` was reduced back to ingest-only behavior.

Removed from `chat-service`:

- Kafka chat consumer doing sentiment work
- ML client
- sentiment event creation
- sentiment Kafka producer
- sentiment-only DTOs and event classes

Updated files:

- `chat-service/src/main/java/com/streamsense/chatservice/config/StreamSenseProperties.java`
- `chat-service/src/main/java/com/streamsense/chatservice/config/ConfigDebugRunner.java`
- `chat-service/src/test/java/com/streamsense/chatservice/ChatServiceApplicationTests.java`
- `config-server/config-repo/chat-service.yml`

Deleted files:

- `chat-service/src/main/java/com/streamsense/chatservice/consumer/ChatMessageLogConsumer.java`
- `chat-service/src/main/java/com/streamsense/chatservice/client/MlEngineClient.java`
- `chat-service/src/main/java/com/streamsense/chatservice/config/RestClientConfig.java`
- `chat-service/src/main/java/com/streamsense/chatservice/config/SentimentKafkaProducerConfig.java`
- `chat-service/src/main/java/com/streamsense/chatservice/kafka/SentimentKafkaProducer.java`
- `chat-service/src/main/java/com/streamsense/chatservice/dto/MlSentimentRequest.java`
- `chat-service/src/main/java/com/streamsense/chatservice/dto/MlSentimentResponse.java`
- `chat-service/src/main/java/com/streamsense/chatservice/events/SentimentAnalysisEvent.java`

### 2. Built the real `sentiment-service`

Added:

- Kafka consumer for `stream.chat.messages`
- ML client calling `POST /ml/sentiment`
- sentiment persistence entity and repository
- service orchestration layer
- Kafka publication to `stream.sentiment.events` after persistence
- REST history endpoint `GET /api/sentiment/recent`
- sentiment metrics and logging

Main files added/updated:

- `sentiment-service/pom.xml`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/config/StreamSenseProperties.java`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/config/RestClientConfig.java`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/config/SentimentKafkaProducerConfig.java`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/config/ConfigDebugRunner.java`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/events/ChatMessageEvent.java`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/events/SentimentAnalysisEvent.java`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/dto/MlSentimentRequest.java`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/dto/MlSentimentResponse.java`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/client/MlEngineClient.java`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/persistence/SentimentRecordEntity.java`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/persistence/SentimentRecordRepository.java`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/metrics/SentimentMetrics.java`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/kafka/SentimentKafkaProducer.java`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/kafka/ChatMessageConsumer.java`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/service/SentimentService.java`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/controller/SentimentHistoryController.java`
- `sentiment-service/src/main/resources/db/migration/V1__create_sentiment_events.sql`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/SentimentServiceApplication.java`
- `config-server/config-repo/sentiment-service.yml`

### 3. Added persistence baseline

Implemented Flyway migration:

- `V1__create_sentiment_events.sql`

Schema includes:

- `sentiment_event_id`
- `source_event_id`
- `streamer`
- `user_name`
- `message`
- `chat_timestamp`
- `processed_at`
- `label`
- `score`
- `model_version`

Index added:

- `(streamer, chat_timestamp DESC)`

### 4. Extended `api-gateway` for sentiment query and live subscription

Added:

- sentiment event type in GraphQL schema
- `recentSentiment(streamer, limit)` query
- `onSentiment(streamer)` subscription
- REST client to `sentiment-service`
- Kafka consumer and subscription bus for `stream.sentiment.events`
- dedicated Kafka container factories for chat and sentiment event types

Main files added/updated:

- `api-gateway/pom.xml`
- `api-gateway/src/main/java/com/streamsense/apigateway/config/GatewayKafkaConfig.java`
- `api-gateway/src/main/java/com/streamsense/apigateway/events/SentimentAnalysisEvent.java`
- `api-gateway/src/main/java/com/streamsense/apigateway/subscriptions/SentimentSubscriptionBus.java`
- `api-gateway/src/main/java/com/streamsense/apigateway/consumer/SentimentKafkaConsumer.java`
- `api-gateway/src/main/java/com/streamsense/apigateway/client/SentimentServiceClient.java`
- `api-gateway/src/main/java/com/streamsense/apigateway/graphql/SentimentGraphqlController.java`
- `api-gateway/src/main/java/com/streamsense/apigateway/consumer/ChatKafkaConsumer.java`
- `api-gateway/src/main/resources/graphql/schema.graphqls`

### 5. Added frontend sentiment analytics UI

Added:

- GraphQL query doc for `recentSentiment`
- GraphQL subscription doc for `onSentiment`
- `SentimentPanel` UI with:
  - recent history
  - live sentiment updates
  - label counts
  - average score
  - loading, empty, and error states
- app layout updated to show live chat and sentiment together

Main files added/updated:

- `frontend/src/graphql/queries.ts`
- `frontend/src/graphql/subscriptions.ts`
- `frontend/src/components/SentimentPanel.tsx`
- `frontend/src/App.tsx`

### 6. Added Sprint 3 automated tests

Added backend tests:

- `sentiment-service/src/test/java/com/streamsense/sentimentservice/SentimentPipelineIntegrationTest.java`
- updated `sentiment-service/src/test/java/com/streamsense/sentimentservice/SentimentServiceApplicationTests.java`
- `api-gateway/src/test/java/com/streamsense/apigateway/graphql/SentimentHistoryQueryTest.java`
- `api-gateway/src/test/java/com/streamsense/apigateway/graphql/SentimentSubscriptionIntegrationTest.java`

Updated gateway tests to include required sentiment-service properties.

Added frontend tests:

- `frontend/src/components/SentimentPanel.test.tsx`

Extended Python tests:

- `ml-engine/src/test/python/test_sentiment.py`
  - added invalid payload validation test

### 7. Runtime and docs/CI updates

Updated Docker wiring:

- `sentiment-service` now depends on Kafka, topic init, Postgres, and `ml-engine`
- added healthchecks for `sentiment-service`, `postgres`, and `ml-engine`
- `api-gateway` now waits for healthy `sentiment-service`

Updated docs:

- `docs/howtorun.md`
  - Sprint 2 terminology normalized
  - Sprint 3 verification commands added
- `docs/contracts/sentiment-pipeline.md`
  - ownership and pipeline shape corrected
  - `chatTimestamp` naming aligned
  - history API documented

Updated CI:

- `.github/workflows/ci.yml`
  - Docker smoke expanded toward Sprint 3:
    - packages `sentiment-service`
    - starts Postgres + `sentiment-service`
    - checks direct history API and GraphQL `recentSentiment`

## Verification Performed

### Java tests

Confirmed passing locally:

- `chat-service`
- `sentiment-service`
- `api-gateway`

### Frontend checks

Passed locally:

```bash
npm run lint
npm run test
npm run build
```

Frontend tests now pass with 9 tests total.

### Python tests

Passed locally:

```bash
PYTHONPATH=src/main/python python3 -m pytest src/test/python
```

Result:

- 5 tests passed

### Compose config validation

Passed locally:

```bash
docker compose config
```

## Final Live Docker Verification

After Docker recovered, the full live Sprint 3 slice was verified successfully.

### Stack status and health

Verified running and healthy:

- `eureka-server`
- `config-server`
- `kafka`
- `ml-engine`
- `chat-service`
- `sentiment-service`
- `api-gateway`
- `frontend`

Health endpoints confirmed:

- `http://localhost:8000/ml/health`
- `http://localhost:8761/actuator/health`
- `http://localhost:8888/actuator/health`
- `http://localhost:8080/actuator/health`
- `http://localhost:8081/actuator/health`
- `http://localhost:8083/actuator/health`

### Topic verification

Confirmed in Kafka:

- `stream.chat.messages`
- `stream.sentiment.events`

### End-to-end persistence proof

Performed live ingest for streamer `sprint3-proof`.

Verified:

- `POST /api/chat/ingest` returned an `eventId`
- `GET /api/sentiment/recent?streamer=sprint3-proof&limit=5` returned a persisted sentiment row
- GraphQL `recentSentiment(streamer, limit)` returned the same sentiment record through `api-gateway`
- Postgres contained the persisted row in `sentiment_events`
- Kafka `stream.sentiment.events` contained the sentiment event
- frontend served successfully on `http://localhost:3000`

### Live subscription proof

Verified GraphQL `onSentiment(streamer)` end to end using a live WebSocket client.

Flow proven:

- subscribe to `onSentiment(streamer: "sprint3-live-proof")`
- send a new chat ingest event for that streamer
- receive a live sentiment event back through the gateway subscription

Received payload included:

- `sentimentEventId`
- `sourceEventId`
- `streamer`
- `user`
- `label`
- `score`
- `modelVersion`

### Observability proof

Started and verified:

- `zipkin`
- `prometheus`
- `grafana`

Verified:

- Zipkin health endpoint returned `UP`
- Prometheus health endpoint returned healthy
- Zipkin service list included:
  - `api-gateway`
  - `chat-service`
  - `sentiment-service`
- Prometheus query for `streamsense_sentiment_events_total` returned data for `sentiment-service`
- `sentiment-service` actuator metrics exposed:
  - `streamsense_sentiment_events_total`
  - `streamsense_ml_sentiment_latency_ms_seconds_*`

## Net Effect

Sprint 3 is now functionally proven live in Docker:

- correct service ownership
- real downstream sentiment inference
- Flyway-backed persistence
- direct history API
- GraphQL history query and live subscription
- frontend sentiment panel
- observability signals exposed and visible
- meaningful automated coverage across backend, gateway, frontend, and Python
