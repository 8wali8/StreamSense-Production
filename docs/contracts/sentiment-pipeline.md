# Sentiment Pipeline Contract

This document defines the data contracts used by the StreamSense sentiment pipeline.

## Pipeline Overview

chat-service → Kafka `stream.chat.messages` → sentiment-service → ml-engine → Postgres → Kafka `stream.sentiment.events` → api-gateway → frontend

Kafka topic flow:

- `chat-service` publishes `stream.chat.messages`
- `sentiment-service` consumes `stream.chat.messages`
- `sentiment-service` performs ML inference and persistence
- `sentiment-service` publishes `stream.sentiment.events` after persistence

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

docs/schemas/sentiment-analysis-event.schema.json

Fields:

| Field | Description |
|------|-------------|
sentimentEventId | Unique ID for the sentiment event
sourceEventId | Original ChatMessageEvent.eventId
streamer | Twitch streamer name
user | Chat user
message | Original chat message
chatTimestamp | Original chat timestamp (epoch millis)
processedAt | Time sentiment result generated
label | Sentiment category (POSITIVE / NEUTRAL / NEGATIVE)
score | Sentiment polarity score [-1.0, 1.0]
modelVersion | ML model version
sponsorRelevant, matchedSponsor, matchedTerms, relevanceScore, relevanceReason, relevanceVersion | Sponsor relevance result for the message
source, channelLogin, streamSessionId, twitchStreamId | Optional capture-session fields copied from the chat message (null for older rows); persisted since V4

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

chatTimestamp  
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

Index:

(streamer, chat_timestamp DESC)

REST history endpoint:

GET /api/sentiment/recent?streamer=...&limit=...

---

# GraphQL API

Query:

recentSentiment(streamer: String!, limit: Int!)

Subscription:

onSentiment(streamer: String!)
