# Sentiment Pipeline Contract

This document defines the data contracts used by the StreamSense sentiment pipeline.

## Pipeline Overview

chat-service → Kafka → ml-engine → Kafka → sentiment-service → Postgres → api-gateway → frontend

Kafka topic flow:

stream.chat.messages  
→ processed by chat-service + ml-engine  
→ produces stream.sentiment.events

---

# Kafka Topic

Topic name:

stream.sentiment.events

Kafka key:

streamer

Reason:

Maintains ordering of sentiment events per streamer.

---

# Sentiment Event Schema

Defined in:

docs/schemas/sentiment-analysis-event.json

Fields:

| Field | Description |
|------|-------------|
sentimentEventId | Unique ID for the sentiment event
sourceEventId | Original ChatMessageEvent.eventId
streamer | Twitch streamer name
user | Chat user
message | Original chat message
timestamp | Original chat timestamp (epoch millis)
processedAt | Time sentiment result generated
label | Sentiment category (POSITIVE / NEUTRAL / NEGATIVE)
score | Sentiment polarity score [-1.0, 1.0]
modelVersion | ML model version

---

# ML Service Contract

Endpoint:

POST /ml/sentiment

Request schema:

docs/schemas/ml-sentiment-request.json

Response schema:

docs/schemas/ml-sentiment-response.json

---

# Label Enum

Allowed values:

POSITIVE  
NEUTRAL  
NEGATIVE

---

# Score Range

Score values must be within:

[-1.0, 1.0]

Interpretation:

score < 0 → negative sentiment  
score ≈ 0 → neutral sentiment  
score > 0 → positive sentiment

---

# Timestamp Format

All timestamps in the pipeline use:

epoch milliseconds (UTC)

This includes:

timestamp  
processedAt

---

# Database Table

Table name:

sentiment_events

Columns:

sentiment_event_id  
source_event_id  
streamer  
user_name  
message  
chat_timestamp  
processed_at  
label  
score  
model_version  
inserted_at

Index:

(streamer, chat_timestamp DESC)

---

# GraphQL API

Query:

recentSentiment(streamer: String!, limit: Int!)

Subscription:

onSentiment(streamer: String!)
