# Real Sentiment Implementation Plan

## Goal

Replace the current deterministic hash-based sentiment stub with a real NLP sentiment model while preserving the existing StreamSense event contracts, Kafka topics, persistence schema, GraphQL schema, and frontend expectations.

## Current State

The sentiment pipeline wiring is already functional:

- `chat-service` publishes `ChatMessageEvent` records to `stream.chat.messages`.
- `video-capture-service` publishes transcript segments to `stream.transcript.segments` when transcript capture is enabled.
- `sentiment-service` consumes chat messages and transcript segments.
- `sentiment-service` calls `ml-engine` at `POST /ml/sentiment`.
- `sentiment-service` persists sentiment records to Postgres.
- `sentiment-service` publishes results to:
  - `stream.sentiment.events`
  - `stream.transcript.sentiment.events`
- `api-gateway` consumes those result topics and exposes GraphQL queries/subscriptions.
- The frontend renders recent/live chat sentiment and transcript sentiment.

The fake part is `ml-engine/src/main/python/app/sentiment.py`:

- `compute_sentiment(message)` hashes the normalized message with SHA-256.
- The hash is mapped deterministically into `[-1.0, 1.0]`.
- Labels are derived from arbitrary score thresholds.
- `/ml/sentiment` returns `modelVersion: "stub-v1"`.

This means sentiment is deterministic but not semantic.

## Target Model

Use Hugging Face Transformers with:

- Model: `cardiffnlp/twitter-roberta-base-sentiment-latest`
- Task: `text-classification` / `sentiment-analysis`
- Runtime: CPU first, because the current Docker setup uses CPU Torch for SAM.

Why this model:

- It is trained for social-media-style English text.
- Twitch chat resembles short social text more than formal reviews.
- It supports Negative / Neutral / Positive output.
- It maps cleanly to the existing StreamSense labels.

## API Contract To Preserve

Do not change the existing `/ml/sentiment` request/response shape.

Request:

```json
{
  "eventId": "evt-123",
  "streamer": "marlon",
  "user": "viewer1",
  "message": "this stream is great",
  "timestamp": 1710000000000
}
```

Response:

```json
{
  "label": "POSITIVE",
  "score": 0.87,
  "modelVersion": "cardiffnlp/twitter-roberta-base-sentiment-latest"
}
```

Keep labels as:

- `NEGATIVE`
- `NEUTRAL`
- `POSITIVE`

Keep score in `[-1.0, 1.0]`.

## Score Mapping

The model returns class probabilities. Map them to the current StreamSense score contract as:

```text
score = positive_probability - negative_probability
```

Examples:

- Strong positive: `positive=0.92`, `negative=0.02` -> `score=0.90`
- Strong negative: `positive=0.03`, `negative=0.88` -> `score=-0.85`
- Neutral: `positive=0.15`, `negative=0.10` -> `score=0.05`

Use the highest-probability class as `label`.

## ML Engine Changes

### 1. Replace Stub With Pluggable Analyzer

Update `ml-engine/src/main/python/app/sentiment.py` to provide a real analyzer behind the existing `compute_sentiment(message)` function.

Suggested structure:

```text
SentimentConfig
SentimentAnalyzer protocol/base
TransformersSentimentAnalyzer
LexicalFallbackSentimentAnalyzer
create_sentiment_analyzer()
compute_sentiment(message)
```

Keep `compute_sentiment(message) -> tuple[str, float]` so `app.main` stays minimally changed.

### 2. Add Environment Config

Add support for:

- `STREAMSENSE_SENTIMENT_BACKEND`
  - default: `transformers`
  - supported values: `transformers`, `lexical`, `stub` if we want an explicit test/dev backend
- `STREAMSENSE_SENTIMENT_MODEL`
  - default: `cardiffnlp/twitter-roberta-base-sentiment-latest`
- `STREAMSENSE_SENTIMENT_DEVICE`
  - default: `cpu`
- `STREAMSENSE_SENTIMENT_CACHE_DIR`
  - default: `/models/sentiment`
- `STREAMSENSE_SENTIMENT_MAX_CHARS`
  - suggested default: `1000`
- `STREAMSENSE_SENTIMENT_PRELOAD`
  - suggested default: `false` initially, optionally `true` after startup behavior is stable

### 3. Add Dependencies

Update `ml-engine/requirements.txt`:

```text
transformers
huggingface_hub
safetensors
```

Torch is already present for SAM:

```text
torch==2.2.2+cpu
torchvision==0.17.2+cpu
```

Avoid adding another Torch stack.

### 4. Text Preprocessing

Normalize input text before inference:

- Trim whitespace.
- Replace URLs with `http`.
- Replace mentions with `@user`.
- Preserve emoji and punctuation.
- Truncate long text before tokenization or use tokenizer truncation.

Do not over-clean Twitch chat. Slang, caps, punctuation, and emoji carry useful sentiment.

### 5. Error Handling

If model loading or inference fails:

- Log the failure.
- Return a controlled fallback sentiment.
- Mark fallback output with `modelVersion: "fallback"` only if the existing response model is extended to carry model version from the analyzer.

Current `/ml/sentiment` hardcodes `modelVersion` in `main.py`; move model version ownership into the analyzer result so fallback/model identity is accurate.

Recommended internal return type:

```python
SentimentResult(label: str, score: float, model_version: str)
```

Then `/ml/sentiment` returns `result.model_version`.

## Docker Compose Changes

Update `docker-compose.yml` for `ml-engine`:

```yaml
environment:
  STREAMSENSE_SENTIMENT_BACKEND: ${STREAMSENSE_SENTIMENT_BACKEND:-transformers}
  STREAMSENSE_SENTIMENT_MODEL: ${STREAMSENSE_SENTIMENT_MODEL:-cardiffnlp/twitter-roberta-base-sentiment-latest}
  STREAMSENSE_SENTIMENT_DEVICE: ${STREAMSENSE_SENTIMENT_DEVICE:-cpu}
  STREAMSENSE_SENTIMENT_CACHE_DIR: /models/sentiment
  STREAMSENSE_SENTIMENT_MAX_CHARS: ${STREAMSENSE_SENTIMENT_MAX_CHARS:-1000}
volumes:
  - sentiment-models:/models/sentiment
```

Add volume:

```yaml
volumes:
  sentiment-models:
```

## Config Server Changes

Review `config-server/config-repo/sentiment-service.yml`:

- Current ML connect timeout: `2000ms`
- Current ML read timeout: `3000ms`

For real model inference, consider increasing read timeout:

```yaml
streamsense:
  ml:
    readTimeoutMs: 10000
```

If this change is made, mirror it in `k8s/config/config-server-config-repo.yaml` because Kubernetes duplicates config-server repo contents there.

## Java Service Changes

Keep Java changes minimal.

Likely no required contract changes because `MlSentimentResponse` already has:

- `label`
- `score`
- `modelVersion`

Potential small changes:

- Ensure `MlEngineClient.validateResponse` still accepts model names like `cardiffnlp/twitter-roberta-base-sentiment-latest`.
- Keep fallback behavior in `sentiment-service` for ML dependency failures.
- Do not change Kafka topics or event schemas.

## Transcript Sentiment

Short-term:

- Use the same sentiment endpoint/model for transcript text.
- Keep current `sentiment-service` transcript flow unchanged.

Medium-term improvements:

- Add transcript-specific cleanup for ASR artifacts and filler repetition.
- Chunk long transcript segments if they exceed tokenizer limits.
- Aggregate chunk scores with weighted averaging.
- Keep final output contract unchanged.

## Testing Plan

### ML Unit Tests

Update `ml-engine/src/test/python/test_sentiment.py`:

- Stop expecting `modelVersion == "stub-v1"`.
- Add semantic tests with an injectable/mock analyzer:
  - positive text returns `POSITIVE` and score `> 0`
  - negative text returns `NEGATIVE` and score `< 0`
  - neutral/factual text returns near-neutral score
- Keep validation/error tests.
- Keep deterministic behavior for the same model/input if practical.

Avoid forcing every unit test to download/load the real Hugging Face model. Use dependency injection/mocking for fast tests.

### ML Integration Test

Add an optional integration test that runs the real model when dependencies/model are available.

Possible gate:

```text
STREAMSENSE_RUN_REAL_SENTIMENT_TESTS=true
```

### Java Tests

Update sentiment-service tests that currently assert `stub-v1` when appropriate:

- `SentimentPipelineIntegrationTest` mocks ML responses, so tests can keep mocked model versions or switch to the new expected real model string.
- `SentimentAnalysisSchemaContractTest` uses `stub-v1` only as sample data; update to a real-looking model version for clarity.
- `MlEngineClientTest` fallback behavior should remain unchanged.

### Commands

ML checks:

```powershell
cd ml-engine
$env:PYTHONPATH='src/main/python'
python -m pytest src/test/python
python -m ruff check src/main/python src/test/python
```

Sentiment service checks:

```powershell
cd sentiment-service
mvn -B -ntp clean test
```

Compose validation:

```powershell
docker compose config
```

## Runtime Verification

Manual ML endpoint test:

```powershell
Invoke-RestMethod -Uri "http://localhost:8000/ml/sentiment" `
  -Method Post `
  -ContentType "application/json" `
  -Body '{"eventId":"test-pos","streamer":"test","user":"viewer","message":"I love this stream, this is amazing","timestamp":1710000000000}'
```

Expected:

- `label` should be `POSITIVE`.
- `score` should be positive.
- `modelVersion` should be the real model name.

Negative test:

```powershell
Invoke-RestMethod -Uri "http://localhost:8000/ml/sentiment" `
  -Method Post `
  -ContentType "application/json" `
  -Body '{"eventId":"test-neg","streamer":"test","user":"viewer","message":"this is awful and terrible","timestamp":1710000000000}'
```

Expected:

- `label` should be `NEGATIVE`.
- `score` should be negative.

End-to-end live verification:

1. Start/restart `ml-engine` with real sentiment backend.
2. Start/restart `sentiment-service`.
3. Ensure chat and transcript ingestion are enabled.
4. Watch GraphQL recent sentiment:

```powershell
$body = @{
  query = 'query RecentSentiment($streamer:String!, $limit:Int!){ recentSentiment(streamer:$streamer, limit:$limit){ message label score modelVersion } recentTranscriptSentiment(streamer:$streamer, limit:$limit){ text label score modelVersion } }'
  variables = @{ streamer = 'marlon'; limit = 5 }
} | ConvertTo-Json -Depth 5
Invoke-RestMethod -Uri "http://localhost:3000/graphql" -Method Post -ContentType "application/json" -Body $body
```

Expected:

- Recent chat sentiment uses the real model version.
- Recent transcript sentiment uses the real model version.
- Labels should correlate with text meaning better than the current hash stub.

## Rollout Notes

- First inference will be slower because the model must download/load.
- Persist model files in `/models/sentiment` so restarts do not redownload.
- CPU inference may become the bottleneck for high-volume chat.
- If throughput is too slow, later options include:
  - batching in `ml-engine`
  - sampling chat messages
  - using a smaller model such as DistilBERT SST-2 for speed
  - moving sentiment to a dedicated worker/service
  - GPU acceleration

## Success Criteria

- `/ml/sentiment` no longer returns `stub-v1` during normal operation.
- Positive/negative sample messages produce semantically sensible labels.
- Chat sentiment and transcript sentiment continue flowing through existing Kafka/Postgres/GraphQL/frontend paths.
- Java service fallback behavior still works when `ml-engine` fails.
- Full tests for touched components pass.
