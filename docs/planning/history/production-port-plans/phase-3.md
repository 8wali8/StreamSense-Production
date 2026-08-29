# Phase 3: Product Metrics Aggregation

## Phase Goal

Phase 3 turns the live Twitch event streams from Phases 1, 2, and 2.5 into durable product-level analytics metrics.

By the end of this phase, a configured live Twitch channel should produce metrics that flow through:

```text
Twitch chat/video/audio events -> Kafka raw analytics topics -> analytics aggregation service -> Postgres aggregate tables -> api-gateway -> GraphQL metric summaries/time series -> frontend dashboard panels
```

This phase moves the product away from frontend-derived counts over recent event lists and toward backend-owned, time-bucketed stream analytics that can support recommendations, historical views, alerts, and campaign reporting.

This phase should not replace the ML models, add campaign-aware recommendations, implement real auth, or solve tenant isolation. Those remain later phases. Phase 3 should, however, define the aggregate shapes that Phase 4 recommendations will consume.

## Current Starting Point

Already available after Phase 2.5:

- `chat-service` ingests real Twitch chat and publishes `stream.chat.messages`.
- `sentiment-service` consumes chat messages, persists chat sentiment, and publishes `stream.sentiment.events`.
- `video-capture-service` samples live Twitch frames and publishes `stream.video.frames`.
- `video-service` consumes frame events, persists sponsor detections, and publishes `stream.sponsor.detections`.
- `video-capture-service` extracts Twitch audio chunks and calls `ml-engine` for local Whisper transcription.
- `video-capture-service` publishes transcript segments to `stream.transcript.segments`.
- `sentiment-service` consumes transcript segments, persists transcript sentiment, and publishes `stream.transcript.sentiment.events`.
- `api-gateway` exposes raw recent history and live subscriptions for chat, sentiment, sponsor detections, transcripts, and transcript sentiment.
- `frontend` renders live raw event panels and status components.
- Docker Compose can run the full local Twitch path with `.env.twitch.local`.

Main missing capability:

- There is no backend-owned aggregate metrics model.
- The dashboard still depends heavily on recent raw events.
- There are no durable time-bucketed metrics keyed by stream session.
- There is no single API for stream health, engagement, sentiment, sponsor exposure, and risk summaries.
- Recommendations cannot yet consume normalized metric windows.

## Target Behavior

Given a live Twitch channel such as `jynxzi`, `austincs`, or another configured active channel:

1. StreamSense consumes raw chat, chat sentiment, transcript sentiment, and sponsor detection events.
2. An aggregation service writes idempotent rollups into durable tables.
3. Metrics are keyed by `streamSessionId` when available and by `streamer` plus source fallback when session identity is missing.
4. Metrics are bucketed by time window, starting with one-minute buckets.
5. The API exposes both summary metrics and time-series metrics.
6. The frontend shows product analytics panels instead of only raw event lists.
7. Metrics update as new Twitch events arrive.
8. Empty, late, duplicate, and out-of-order events are handled predictably.
9. Aggregation lag and failures are visible through logs, Prometheus metrics, and health endpoints.
10. Phase 4 can consume aggregate metrics as recommendation inputs.

## Relationship To Earlier Phases

Phase 1 proved live Twitch chat ingestion.

Phase 2 proved live Twitch video frame sampling and sponsor detection from real frame artifacts.

Phase 2.5 proved local streamer speech transcription and separate transcript sentiment.

Phase 3 should not rewrite those pipelines. It should consume their Kafka outputs and REST histories as needed, then create product-level metrics as a downstream read model.

Recommended identity approach for Phase 3:

- Prefer `streamSessionId` for aggregation keys when it exists.
- Keep `streamer` as a required compatibility key in all aggregate reads.
- Store `channelLogin` when available.
- Store `twitchStreamId` when available.
- Support a generated local session ID from the capture pipeline until a first-class stream/session service is implemented.
- Expose GraphQL queries that accept `streamer` now and optionally `streamSessionId` where the underlying records support it.

## Ownership Split

### Work I Can Do In The Repo

I can implement and test the repository changes:

- Add a dedicated `analytics-service` for aggregation.
- Add metric event consumers for chat, chat sentiment, transcript sentiment, and sponsor detections.
- Add aggregate persistence tables and repositories.
- Add idempotency tables or processed-event tracking.
- Add REST endpoints for metric summaries and time series.
- Add `api-gateway` client calls and GraphQL types/queries.
- Add frontend metric panels.
- Add Docker Compose and Kubernetes wiring.
- Add config-server entries for analytics-service.
- Add contract docs and JSON schemas for metric API responses.
- Add tests for aggregation correctness, duplicate events, late events, and empty streams.
- Add Prometheus metrics for aggregator lag, processed counts, duplicate counts, and failures.
- Update runbooks and Twitch E2E verification steps.

### Work You Need To Provide Or Decide

You need to provide product decisions that affect metric semantics:

- Confirm the first dashboard time range defaults, for example last 15 minutes and last 60 minutes.
- Confirm the first bucket size, recommended default is one minute.
- Confirm whether transcript sentiment should contribute to overall brand safety/risk or remain visually separate.
- Confirm sponsor exposure duration assumptions for sampled frames.
- Confirm the first brand safety/risk score formula is acceptable as transparent heuristic logic.
- Confirm whether low-confidence sponsor detections should count toward exposure metrics.
- Confirm whether deleted or moderated chat messages need special handling later.
- Confirm retention expectations for raw aggregates and processed-event idempotency records.

Do not commit secrets. Phase 3 should not require new Twitch secrets beyond what Phases 1, 2, and 2.5 already use.

## Key Product Decisions Before Implementation

### Decision 1: Aggregation Ownership

Recommended choice for Phase 3:

- Add a dedicated `analytics-service`.

Reason:

- Metrics cross multiple domains: chat, sentiment, transcript, sponsor detections, and later recommendations.
- Putting cross-domain aggregation in `sentiment-service` or `video-service` would blur service ownership.
- `recommendation-service` should consume metrics later, not own the raw aggregation pipeline.
- A separate service gives a clean API boundary for metric summaries and time series.
- It can scale independently if Kafka consumer lag or aggregation work grows.

Alternative:

- Add service-owned aggregation inside `sentiment-service` and `video-service`, then compose results in `api-gateway`.

Tradeoff:

- Less initial service scaffolding, but harder to produce cross-domain risk and engagement metrics cleanly.

Phase 3 default unless we explicitly decide otherwise:

- Add `analytics-service` as a Java Spring Boot service, consistent with the other durable state services.

### Decision 2: Runtime Aggregation Model

Recommended choice for Phase 3:

- Use Kafka consumers that update Postgres aggregate tables incrementally.

Reason:

- Metrics update in near real time as events arrive.
- Kafka offsets provide natural streaming progress.
- Postgres is already present and adequate for first production-shaped rollups.
- The frontend can read stable API responses instead of recalculating from raw event windows.

Alternative:

- Scheduled polling jobs that call existing history endpoints and recompute windows.

Tradeoff:

- Simpler to wire initially, but less accurate, higher service coupling, and harder to make idempotent.

Phase 3 default:

- Kafka consumers with idempotent event processing and upserted time buckets.

### Decision 3: Bucket Size

Recommended initial bucket size:

```text
1 minute
```

Reason:

- It is granular enough for live dashboard movement.
- It is coarse enough to avoid excessive write amplification.
- It supports chat rate, sentiment trend, sponsor exposure, and spike detection.

Supported future bucket sizes:

```text
5 minutes
15 minutes
1 hour
```

Phase 3 implementation rule:

- Persist one-minute canonical buckets first.
- Larger windows can be computed at read time by summing one-minute buckets unless performance requires rollup tables later.

### Decision 4: Idempotency And Duplicate Handling

Recommended choice:

- Track processed event IDs per source topic.

Reason:

- Kafka consumers can reprocess events during restarts or rebalance.
- Aggregate increments must not double-count messages, detections, or sentiment events.
- Existing event IDs are stable enough for first-phase idempotency.

Processed event key examples:

```text
stream.chat.messages:eventId
stream.sentiment.events:sentimentEventId
stream.sponsor.detections:detectionEventId
stream.transcript.sentiment.events:sentimentEventId
```

Acceptance for this decision:

- Duplicate Kafka messages do not change aggregate values after the first successful process.
- Processed-event tracking is transactional with aggregate updates.

### Decision 5: Sponsor Exposure Duration

Recommended initial approximation:

- Count each sponsor detection as one exposure.
- Estimate exposure duration from the configured frame sampling interval.
- Cap a single detection's contribution at the sampling interval.
- Group adjacent detections for the same sponsor in nearby buckets later if needed.

Reason:

- Phase 2 samples frames at a controlled cadence, not continuous video.
- Exact on-screen duration requires continuous tracking, which is outside Phase 3.
- The approximation is transparent and useful enough for dashboard and recommendation evidence.

Initial formula:

```text
estimatedExposureMs += configuredFrameSampleIntervalMs for each accepted sponsor detection
```

Recommended default:

```text
10000 ms per detection
```

Rules:

- Ignore detections below `minimumSponsorConfidence` unless configured otherwise.
- Count fallback detections separately.
- Store average confidence and max confidence per sponsor.

### Decision 6: Brand Safety And Risk

Recommended initial scope:

- Implement a transparent heuristic risk score, not a hidden model.

Inputs:

- negative chat sentiment ratio
- negative transcript sentiment ratio
- sentiment spike count
- low-confidence sponsor detection ratio
- fallback sponsor detection ratio
- sharp engagement spikes paired with negative sentiment

Initial score shape:

```text
riskScore: 0.0 to 1.0
riskLevel: LOW, MEDIUM, HIGH
```

Rules:

- Keep the formula documented in code and docs.
- Expose contributing factors through GraphQL.
- Do not pretend this is a trained brand safety model.

## Proposed Architecture

### New Service

Add:

```text
analytics-service/
```

Recommended implementation language:

- Java 21 with Spring Boot.

Reason:

- Existing durable backend services are Java/Spring.
- Kafka, Postgres, Flyway, Actuator, Micrometer, and config-server patterns already exist.
- Aggregation correctness benefits from transactional database writes.

Expected responsibilities:

- Consume raw analytics Kafka topics.
- Normalize event timestamps into one-minute buckets.
- Upsert aggregate counters and sums.
- Track processed source events for idempotency.
- Expose REST APIs for metric summaries and time series.
- Export Prometheus metrics for processing health.

Expected dependencies:

```text
spring-boot-starter-web
spring-boot-starter-actuator
spring-boot-starter-validation
spring-boot-starter-data-jpa
spring-kafka
flyway-core
postgresql
micrometer-registry-prometheus
```

### Data Flow

Runtime flow:

```text
stream.chat.messages
  -> analytics-service ChatMetricConsumer
  -> stream_metric_buckets chat counters

stream.sentiment.events
  -> analytics-service ChatSentimentMetricConsumer
  -> stream_metric_buckets sentiment counters and score sums

stream.transcript.sentiment.events
  -> analytics-service TranscriptSentimentMetricConsumer
  -> stream_metric_buckets transcript sentiment counters and score sums

stream.sponsor.detections
  -> analytics-service SponsorMetricConsumer
  -> sponsor_metric_buckets sponsor counters, exposure estimates, confidence stats

analytics-service REST
  -> api-gateway GraphQL
  -> frontend metric panels
```

### Query Flow

Recommended REST endpoints on `analytics-service`:

```text
GET /api/analytics/streams/{streamer}/summary?windowMinutes=15&streamSessionId=optional
GET /api/analytics/streams/{streamer}/timeseries?windowMinutes=60&bucketSeconds=60&streamSessionId=optional
GET /api/analytics/streams/{streamer}/sponsors?windowMinutes=60&streamSessionId=optional
GET /api/analytics/streams/{streamer}/risk?windowMinutes=15&streamSessionId=optional
GET /api/analytics/health
```

Recommended GraphQL fields on `api-gateway`:

```graphql
streamMetricsSummary(streamer: String!, streamSessionId: String, windowMinutes: Int!): StreamMetricsSummary!
streamMetricsTimeseries(streamer: String!, streamSessionId: String, windowMinutes: Int!, bucketSeconds: Int!): [StreamMetricBucket!]!
sponsorExposureMetrics(streamer: String!, streamSessionId: String, windowMinutes: Int!): [SponsorExposureMetric!]!
brandSafetyMetrics(streamer: String!, streamSessionId: String, windowMinutes: Int!): BrandSafetyMetrics!
```

Potential subscription, optional for Phase 3:

```graphql
onStreamMetricsUpdated(streamer: String!, streamSessionId: String): StreamMetricsSummary!
```

Recommendation:

- Defer metrics subscriptions unless frontend polling is not sufficient.
- Start with polling every 10 to 30 seconds to reduce GraphQL subscription complexity.

## Metric Definitions

### Audience Volume Metrics

Inputs:

- `stream.chat.messages`

Metrics:

```text
totalChatMessages
chatMessagesPerMinute
uniqueChatters
activeChattersInWindow
peakChatMessagesPerMinute
```

Rules:

- Increment `totalChatMessages` by one for each unique chat event.
- Count unique chatters with exact per-bucket distinct usernames for Phase 3.
- Overall window unique chatters can be computed from a bucket-level contributor table or approximated by summing bucket unique counts only if clearly labeled.

Recommended Phase 3 exact approach:

- Store `stream_bucket_chatters` rows keyed by bucket and normalized username.
- Count distinct usernames for a window from those rows.

### Chat Sentiment Metrics

Inputs:

- `stream.sentiment.events`

Metrics:

```text
chatSentimentPositiveCount
chatSentimentNeutralCount
chatSentimentNegativeCount
chatSentimentAverageScore
chatSentimentNegativeRatio
chatSentimentTrend
negativeSpikeCount
```

Rules:

- Increment label-specific counters by normalized label.
- Add sentiment score to a running sum.
- Compute average score as `scoreSum / sentimentCount`.
- Detect a negative spike when the current bucket's negative ratio exceeds threshold and message count exceeds a minimum volume.

Recommended defaults:

```text
negativeSpikeRatioThreshold=0.60
negativeSpikeMinimumEvents=10
```

### Transcript Sentiment Metrics

Inputs:

- `stream.transcript.sentiment.events`

Metrics:

```text
transcriptSegmentsAnalyzed
transcriptSentimentPositiveCount
transcriptSentimentNeutralCount
transcriptSentimentNegativeCount
transcriptSentimentAverageScore
transcriptNegativeRatio
```

Rules:

- Keep transcript sentiment separate from chat sentiment in persisted counters.
- The summary can expose combined risk factors but should not merge the raw counters without explicit labels.

### Sponsor Exposure Metrics

Inputs:

- `stream.sponsor.detections`

Metrics:

```text
sponsorDetectionsTotal
sponsorDetectionsByBrand
sponsorExposureCountByBrand
estimatedSponsorExposureMsByBrand
averageSponsorConfidenceByBrand
maxSponsorConfidenceByBrand
fallbackDetectionCountByBrand
lowConfidenceDetectionCountByBrand
```

Rules:

- Normalize sponsor/brand names by trimming whitespace and using a canonical display value.
- Store unknown or empty sponsor values as `UNKNOWN` only if existing event contracts can produce them.
- Count accepted detections separately from low-confidence detections.
- Keep fallback counts separate from normal detections.

Recommended defaults:

```text
minimumSponsorConfidence=0.50
estimatedExposureMsPerDetection=10000
```

### Engagement Spike Metrics

Inputs:

- chat message buckets
- chat sentiment buckets
- transcript sentiment buckets
- sponsor buckets

Metrics:

```text
engagementSpikeCount
latestEngagementSpikeAt
peakChatMessagesPerMinute
spikeWindows
```

Initial spike rule:

```text
currentBucketChatCount >= max(minimumSpikeMessages, trailingAverageChatCount * spikeMultiplier)
```

Recommended defaults:

```text
minimumSpikeMessages=20
spikeMultiplier=2.0
trailingWindowMinutes=5
```

Rules:

- Do not flag spikes when there is too little baseline data unless the absolute count is high enough.
- Expose spike windows with bucket timestamp, chat count, baseline, multiplier, and dominant sentiment if available.

### Brand Safety And Risk Metrics

Inputs:

- chat negative ratio
- transcript negative ratio
- negative spike count
- engagement spike count with negative sentiment
- low-confidence sponsor ratio
- fallback sponsor ratio

Metrics:

```text
riskScore
riskLevel
riskFactors
```

Initial heuristic:

```text
riskScore = clamp(
  chatNegativeRatio * 0.35
  + transcriptNegativeRatio * 0.25
  + normalizedNegativeSpikeScore * 0.20
  + sponsorQualityRisk * 0.10
  + negativeEngagementSpikeRisk * 0.10,
  0.0,
  1.0
)
```

Risk levels:

```text
LOW: 0.00 to 0.33
MEDIUM: 0.34 to 0.66
HIGH: 0.67 to 1.00
```

Rules:

- If there is no data, return `UNKNOWN` or `LOW_DATA`, not `LOW`.
- Include factor values so the UI can explain the score.
- Keep this formula configurable.

## Persistence Plan

Add tables in `analytics-service`.

### `analytics_processed_events`

Purpose:

- Enforce idempotency across Kafka retries, restarts, and rebalances.

Columns:

```text
source_topic varchar(255) not null
source_event_id varchar(255) not null
streamer varchar(255) not null
stream_session_id varchar(255)
event_timestamp bigint not null
processed_at bigint not null
primary key (source_topic, source_event_id)
```

Indexes:

```text
(streamer, processed_at desc)
(stream_session_id, processed_at desc)
```

### `stream_metric_buckets`

Purpose:

- Store one-minute aggregate stream metrics across chat, sentiment, transcript sentiment, and general spike state.

Columns:

```text
id bigserial primary key
streamer varchar(255) not null
channel_login varchar(255)
stream_session_id varchar(255)
twitch_stream_id varchar(255)
bucket_start bigint not null
bucket_size_seconds integer not null
chat_message_count bigint not null default 0
chat_sentiment_count bigint not null default 0
chat_positive_count bigint not null default 0
chat_neutral_count bigint not null default 0
chat_negative_count bigint not null default 0
chat_score_sum double precision not null default 0
transcript_sentiment_count bigint not null default 0
transcript_positive_count bigint not null default 0
transcript_neutral_count bigint not null default 0
transcript_negative_count bigint not null default 0
transcript_score_sum double precision not null default 0
negative_spike_count bigint not null default 0
engagement_spike_count bigint not null default 0
created_at bigint not null
updated_at bigint not null
```

Unique constraint:

```text
(streamer, coalesce(stream_session_id, ''), bucket_start, bucket_size_seconds)
```

Implementation note:

- Postgres expression indexes or a normalized `session_key` column can be used because standard unique constraints do not treat `null` as equal.

Recommended simpler schema:

```text
session_key varchar(255) not null
unique (streamer, session_key, bucket_start, bucket_size_seconds)
```

Where:

```text
session_key = streamSessionId if present, else streamer
```

### `stream_bucket_chatters`

Purpose:

- Track exact unique chatters by bucket.

Columns:

```text
streamer varchar(255) not null
session_key varchar(255) not null
bucket_start bigint not null
bucket_size_seconds integer not null
username varchar(255) not null
first_seen_at bigint not null
primary key (streamer, session_key, bucket_start, bucket_size_seconds, username)
```

### `sponsor_metric_buckets`

Purpose:

- Store sponsor exposure metrics by brand and bucket.

Columns:

```text
id bigserial primary key
streamer varchar(255) not null
channel_login varchar(255)
stream_session_id varchar(255)
session_key varchar(255) not null
twitch_stream_id varchar(255)
bucket_start bigint not null
bucket_size_seconds integer not null
sponsor varchar(255) not null
detection_count bigint not null default 0
accepted_detection_count bigint not null default 0
low_confidence_detection_count bigint not null default 0
fallback_detection_count bigint not null default 0
estimated_exposure_ms bigint not null default 0
confidence_sum double precision not null default 0
max_confidence double precision
created_at bigint not null
updated_at bigint not null
```

Unique constraint:

```text
unique (streamer, session_key, bucket_start, bucket_size_seconds, sponsor)
```

### Optional Future Tables

Defer until needed:

- `stream_metric_rollups_5m`
- `stream_metric_rollups_1h`
- `analytics_metric_snapshots`
- `analytics_anomaly_events`
- `campaign_metric_buckets`

## Event Contract Inputs

### `stream.chat.messages`

Required fields for Phase 3 aggregation:

```text
eventId
streamer
user
message
timestamp
```

Optional fields to use when present:

```text
source
channelLogin
streamSessionId
twitchStreamId
ingestedAt
```

Metric effect:

- Increment chat message count.
- Insert bucket chatter row.

### `stream.sentiment.events`

Required fields:

```text
sentimentEventId
sourceEventId
streamer
chatTimestamp
label
score
```

Optional fields to use when present:

```text
streamSessionId
channelLogin
source
```

Metric effect:

- Increment chat sentiment count.
- Increment label-specific chat sentiment counter.
- Add score to chat sentiment score sum.
- Recompute or mark negative spike status for the bucket.

### `stream.transcript.sentiment.events`

Required fields:

```text
sentimentEventId
segmentId
streamer
segmentStartedAt
label
score
```

Optional fields to use when present:

```text
streamSessionId
transcriptSequence
transcriptModelVersion
```

Metric effect:

- Increment transcript sentiment count.
- Increment label-specific transcript sentiment counter.
- Add score to transcript sentiment score sum.

### `stream.sponsor.detections`

Required fields:

```text
detectionEventId
sourceFrameId
streamer
capturedAt
sponsor
confidence
```

Optional fields to use when present:

```text
streamSessionId
channelLogin
twitchStreamId
videoTimestampMs
modelVersion
fallback
```

Metric effect:

- Increment sponsor detection counters.
- Add estimated sponsor exposure duration when confidence is accepted.
- Track confidence sum and max confidence.
- Track fallback and low-confidence counts.

## REST API Plan

### Summary Response

Endpoint:

```text
GET /api/analytics/streams/{streamer}/summary?windowMinutes=15&streamSessionId=optional
```

Response shape:

```json
{
  "streamer": "jynxzi",
  "streamSessionId": "jynxzi-1778032373428",
  "windowMinutes": 15,
  "bucketSizeSeconds": 60,
  "windowStart": 1778031500000,
  "windowEnd": 1778032400000,
  "chat": {
    "totalMessages": 128,
    "messagesPerMinute": 8.53,
    "uniqueChatters": 42,
    "peakMessagesPerMinute": 21
  },
  "chatSentiment": {
    "positive": 22,
    "neutral": 61,
    "negative": 45,
    "averageScore": -0.18,
    "negativeRatio": 0.35
  },
  "transcriptSentiment": {
    "positive": 3,
    "neutral": 6,
    "negative": 4,
    "averageScore": -0.09,
    "negativeRatio": 0.31
  },
  "sponsorExposure": {
    "totalDetections": 8,
    "acceptedDetections": 7,
    "estimatedExposureMs": 70000,
    "topSponsors": [
      {
        "sponsor": "Nike",
        "detections": 4,
        "estimatedExposureMs": 40000,
        "averageConfidence": 0.82
      }
    ]
  },
  "engagement": {
    "spikeCount": 2,
    "latestSpikeAt": 1778032320000
  },
  "risk": {
    "level": "MEDIUM",
    "score": 0.48,
    "factors": [
      {
        "name": "chatNegativeRatio",
        "value": 0.35,
        "weight": 0.35
      }
    ]
  },
  "dataQuality": {
    "lowData": false,
    "latestEventAt": 1778032395147,
    "aggregationLagMs": 1800
  }
}
```

### Time Series Response

Endpoint:

```text
GET /api/analytics/streams/{streamer}/timeseries?windowMinutes=60&bucketSeconds=60&streamSessionId=optional
```

Response shape:

```json
[
  {
    "bucketStart": 1778032320000,
    "bucketEnd": 1778032380000,
    "chatMessageCount": 21,
    "uniqueChatters": 9,
    "chatAverageScore": -0.24,
    "chatNegativeRatio": 0.52,
    "transcriptAverageScore": 0.10,
    "sponsorDetectionCount": 2,
    "estimatedSponsorExposureMs": 20000,
    "engagementSpike": true,
    "negativeSpike": true
  }
]
```

### Sponsor Response

Endpoint:

```text
GET /api/analytics/streams/{streamer}/sponsors?windowMinutes=60&streamSessionId=optional
```

Response shape:

```json
[
  {
    "sponsor": "Nike",
    "detectionCount": 4,
    "acceptedDetectionCount": 4,
    "estimatedExposureMs": 40000,
    "averageConfidence": 0.82,
    "maxConfidence": 0.94,
    "fallbackDetectionCount": 0,
    "lowConfidenceDetectionCount": 1
  }
]
```

## GraphQL Plan

Add schema file or extend existing schema with:

```graphql
type StreamMetricsSummary {
  streamer: String!
  streamSessionId: String
  windowMinutes: Int!
  bucketSizeSeconds: Int!
  windowStart: Float!
  windowEnd: Float!
  chat: ChatMetrics!
  chatSentiment: SentimentMetricSummary!
  transcriptSentiment: SentimentMetricSummary!
  sponsorExposure: SponsorExposureSummary!
  engagement: EngagementMetrics!
  risk: BrandSafetyMetrics!
  dataQuality: AnalyticsDataQuality!
}

type ChatMetrics {
  totalMessages: Float!
  messagesPerMinute: Float!
  uniqueChatters: Float!
  peakMessagesPerMinute: Float!
}

type SentimentMetricSummary {
  positive: Float!
  neutral: Float!
  negative: Float!
  averageScore: Float
  negativeRatio: Float
}

type SponsorExposureSummary {
  totalDetections: Float!
  acceptedDetections: Float!
  estimatedExposureMs: Float!
  topSponsors: [SponsorExposureMetric!]!
}

type SponsorExposureMetric {
  sponsor: String!
  detectionCount: Float!
  acceptedDetectionCount: Float!
  estimatedExposureMs: Float!
  averageConfidence: Float
  maxConfidence: Float
  fallbackDetectionCount: Float!
  lowConfidenceDetectionCount: Float!
}

type EngagementMetrics {
  spikeCount: Float!
  latestSpikeAt: Float
}

type BrandSafetyMetrics {
  level: String!
  score: Float
  factors: [RiskFactor!]!
}

type RiskFactor {
  name: String!
  value: Float!
  weight: Float!
}

type AnalyticsDataQuality {
  lowData: Boolean!
  latestEventAt: Float
  aggregationLagMs: Float
}

type StreamMetricBucket {
  bucketStart: Float!
  bucketEnd: Float!
  chatMessageCount: Float!
  uniqueChatters: Float!
  chatAverageScore: Float
  chatNegativeRatio: Float
  transcriptAverageScore: Float
  transcriptNegativeRatio: Float
  sponsorDetectionCount: Float!
  estimatedSponsorExposureMs: Float!
  engagementSpike: Boolean!
  negativeSpike: Boolean!
}
```

Queries:

```graphql
extend type Query {
  streamMetricsSummary(streamer: String!, streamSessionId: String, windowMinutes: Int!): StreamMetricsSummary!
  streamMetricsTimeseries(streamer: String!, streamSessionId: String, windowMinutes: Int!, bucketSeconds: Int!): [StreamMetricBucket!]!
  sponsorExposureMetrics(streamer: String!, streamSessionId: String, windowMinutes: Int!): [SponsorExposureMetric!]!
  brandSafetyMetrics(streamer: String!, streamSessionId: String, windowMinutes: Int!): BrandSafetyMetrics!
}
```

Validation rules:

- `windowMinutes` minimum: 1.
- `windowMinutes` maximum: 1440 for Phase 3.
- `bucketSeconds` allowed values: 60 initially.
- `streamer` remains required until a dedicated stream session lookup API exists.

## Frontend Plan

Add product metric panels to the dashboard while keeping raw event panels available for debugging.

Recommended new components:

```text
StreamMetricsOverview.tsx
AudienceVolumePanel.tsx
SentimentTrendPanel.tsx
SponsorExposurePanel.tsx
EngagementSpikesPanel.tsx
BrandSafetyRiskPanel.tsx
AnalyticsDataQualityBadge.tsx
```

Dashboard behavior:

- Fetch metric summary for the selected `streamer` every 10 to 30 seconds.
- Fetch time series for the selected `streamer` and selected window.
- Show chat rate, unique chatters, sentiment distribution, transcript sentiment, sponsor exposure, engagement spikes, and risk level.
- Keep live raw panels below or beside metric panels for traceability.
- Clearly label low-data states when not enough events exist for a meaningful rate or risk score.
- Show degraded states when analytics-service is unavailable while raw subscriptions still work.

Recommended first layout:

- Top row: stream health, chat rate, unique chatters, risk score.
- Middle row: sentiment trend, transcript sentiment trend, sponsor exposure by brand.
- Bottom row: engagement spike windows and raw event panels.

Frontend test cases:

- renders populated summary
- renders empty or low-data summary
- renders analytics-service error state
- renders sponsor exposure metrics
- renders risk factors
- preserves existing raw chat/sentiment/sponsor/transcript panels

## Configuration Plan

### `analytics-service` Config

Proposed config shape:

```yaml
streamsense:
  analytics:
    bucket-size-seconds: 60
    default-window-minutes: 15
    max-window-minutes: 1440
    estimated-sponsor-exposure-ms-per-detection: 10000
    minimum-sponsor-confidence: 0.50
    negative-spike-ratio-threshold: 0.60
    negative-spike-minimum-events: 10
    engagement-spike-minimum-messages: 20
    engagement-spike-multiplier: 2.0
    engagement-spike-trailing-window-minutes: 5
    low-data-minimum-events: 5
  topics:
    chatMessages: stream.chat.messages
    sentimentEvents: stream.sentiment.events
    sponsorDetections: stream.sponsor.detections
    transcriptSentimentEvents: stream.transcript.sentiment.events
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:kafka:9092}
```

Recommended env vars:

```bash
STREAMSENSE_ANALYTICS_BUCKET_SIZE_SECONDS=60
STREAMSENSE_ANALYTICS_DEFAULT_WINDOW_MINUTES=15
STREAMSENSE_ANALYTICS_MAX_WINDOW_MINUTES=1440
STREAMSENSE_ANALYTICS_SPONSOR_EXPOSURE_MS_PER_DETECTION=10000
STREAMSENSE_ANALYTICS_MINIMUM_SPONSOR_CONFIDENCE=0.50
```

### `api-gateway` Config

Add analytics-service base URL and timeout:

```yaml
streamsense:
  services:
    analytics-service-url: http://analytics-service:8085
  analytics:
    request-timeout-ms: 3000
```

### Docker Compose

Add service:

```text
analytics-service
```

Expected dependencies:

- `postgres`
- `kafka`
- `config-server`
- `eureka-server`

Expose local port:

```text
8085:8085
```

### Kubernetes

Add:

- `k8s/apps/analytics-service.yaml`
- analytics-service config in `k8s/config/config-server-config-repo.yaml`
- Prometheus scrape config if needed
- service dependency docs

If `k8s/` changes, run:

```bash
kubectl kustomize k8s
```

## Service Changes

### `analytics-service`

Add packages similar to:

```text
config
controller
events
kafka
metrics
persistence
service
web
```

Core classes:

```text
AnalyticsServiceApplication
StreamSenseProperties
AnalyticsKafkaConfig
ChatMessageMetricConsumer
ChatSentimentMetricConsumer
TranscriptSentimentMetricConsumer
SponsorDetectionMetricConsumer
MetricAggregationService
MetricQueryService
RiskScoreService
EngagementSpikeService
ProcessedEventRepository
StreamMetricBucketRepository
SponsorMetricBucketRepository
AnalyticsController
AnalyticsMetrics
```

Processing rules:

- Validate event has required ID and streamer.
- Derive event timestamp from source-specific event time.
- Derive bucket start by flooring timestamp to bucket size.
- Derive session key from `streamSessionId` or `streamer`.
- Insert processed event row first in the same transaction, or handle duplicate key as already processed.
- Upsert bucket row and increment counters.
- Do not fail the Kafka consumer permanently for one malformed event.
- Send unrecoverable malformed events to a DLT if existing patterns support it, or log and count them with clear metrics.

### `api-gateway`

Add:

- `AnalyticsServiceClient`
- analytics GraphQL DTOs
- `AnalyticsGraphqlController`
- schema contract tests
- timeout/error handling consistent with existing service clients

Fallback behavior:

- If analytics-service is unavailable, GraphQL should return a clear error for metric queries.
- Do not break raw event GraphQL queries or subscriptions.

### `frontend`

Add:

- analytics GraphQL queries
- metric panel components
- polling or refresh strategy
- low-data and degraded states
- tests for new panels

Keep existing panels:

- chat panel
- chat sentiment panel
- sponsor panel
- transcript panel
- transcript sentiment panel

### `recommendation-service`

No required Phase 3 change.

Optional preparation:

- Add config placeholder for analytics-service URL.
- Do not change recommendation behavior until Phase 4.

## Observability Plan

Add Micrometer metrics from `analytics-service`:

```text
streamsense_analytics_events_processed_total{topic="..."}
streamsense_analytics_events_duplicate_total{topic="..."}
streamsense_analytics_events_failed_total{topic="..."}
streamsense_analytics_lag_ms{topic="..."}
streamsense_analytics_bucket_updates_total{metric="..."}
streamsense_analytics_query_latency_seconds{endpoint="..."}
streamsense_analytics_risk_score{streamer="..."}
```

Add logs for:

- service startup config without secrets
- Kafka listener assignment
- aggregate update failures
- duplicate event skips at debug level
- risk calculation edge cases
- invalid event payloads

Add health indicators for:

- Postgres connectivity
- Kafka listener readiness if practical
- latest processed event age per topic

Prometheus/Grafana:

- Scrape analytics-service actuator endpoint.
- Add dashboard panels for processed events, duplicate events, failures, and lag.
- Add local alerts later for no events processed while ingestion is live.

## Testing Plan

### Unit Tests

Add tests for:

- bucket timestamp calculation
- session key derivation
- chat message aggregation
- unique chatter insertion
- sentiment label normalization
- transcript sentiment aggregation
- sponsor exposure duration calculation
- low-confidence sponsor handling
- fallback sponsor handling
- negative spike detection
- engagement spike detection
- risk score calculation
- low-data risk behavior

### Integration Tests

Add Spring integration tests for:

- duplicate processed events are not double counted
- chat event updates stream bucket and chatter table transactionally
- sentiment event updates score sums and label counters
- sponsor event updates sponsor bucket counters
- late event updates its original bucket
- malformed event is counted and does not crash the listener
- REST summary response matches persisted buckets
- REST time series response fills missing buckets with zeros if that behavior is chosen

### Contract Tests

Add tests for:

- GraphQL schema contains analytics types and queries
- GraphQL metric queries call analytics client and map responses correctly
- JSON schema examples for metric responses stay valid

### Frontend Tests

Add tests for:

- summary cards render values and units
- time-series panels render empty state
- risk panel renders level and factors
- sponsor exposure panel renders brands and confidence
- degraded analytics state does not hide raw event panels

### Verification Commands

Expected commands after implementation:

```bash
cd analytics-service && mvn -B -ntp clean test
cd api-gateway && mvn -B -ntp test
cd frontend && npm run lint && npm run test && npm run build
docker compose config
kubectl kustomize k8s
git diff --check
```

If Java service parent/build wiring changes, also run the relevant make target documented by `make help`.

## Implementation Sequence

### Step 1: Define Contracts And Metric Semantics

Files likely involved:

- `docs/contracts/analytics-metrics.md`
- `docs/schemas/stream-metrics-summary.schema.json`
- `docs/schemas/stream-metric-bucket.schema.json`
- `docs/schemas/sponsor-exposure-metric.schema.json`
- `productionportplans/phase-3.md`

Deliverables:

- Final list of Phase 3 metrics.
- Metric formulas and defaults.
- REST response examples.
- GraphQL type plan.

Acceptance criteria:

- Metrics are understandable without reading service code.
- Low-data and unknown states are explicitly defined.

### Step 2: Scaffold `analytics-service`

Files likely involved:

- `analytics-service/pom.xml`
- `analytics-service/src/main/java/...`
- `analytics-service/src/main/resources/application.yml`
- `analytics-service/src/test/java/...`
- `docker-compose.yml`
- `config-server/config-repo/analytics-service.yml`

Deliverables:

- Spring Boot service starts with Actuator health.
- Config Server bootstrap works locally.
- Docker Compose can run the service.
- Basic health endpoint passes.

Acceptance criteria:

- `cd analytics-service && mvn -B -ntp test` passes.
- `docker compose up -d analytics-service` starts healthy.

### Step 3: Add Persistence And Repositories

Files likely involved:

- `analytics-service/src/main/resources/db/migration/V1__create_analytics_tables.sql`
- JPA entities and repositories

Deliverables:

- `analytics_processed_events`
- `stream_metric_buckets`
- `stream_bucket_chatters`
- `sponsor_metric_buckets`

Acceptance criteria:

- Flyway migrations run in tests and Docker Compose.
- Repository tests verify unique constraints and basic upserts.

### Step 4: Implement Aggregation Service Logic

Files likely involved:

- `MetricAggregationService`
- `RiskScoreService`
- `EngagementSpikeService`
- `AnalyticsMetrics`

Deliverables:

- Event-specific aggregation methods.
- Transactional idempotency.
- Bucket upsert logic.
- Risk and spike calculations.

Acceptance criteria:

- Unit and integration tests prove aggregation correctness.
- Duplicate events are ignored after first processing.
- Late events update the correct historical bucket.

### Step 5: Add Kafka Consumers

Files likely involved:

- Kafka config
- event DTOs
- listener classes

Deliverables:

- Consumers for `stream.chat.messages`.
- Consumers for `stream.sentiment.events`.
- Consumers for `stream.transcript.sentiment.events`.
- Consumers for `stream.sponsor.detections`.
- DLT or error handling consistent with existing services.

Acceptance criteria:

- Service can consume live Twitch events and update buckets.
- Malformed messages are counted and do not halt processing.
- Consumer group IDs are explicit.

### Step 6: Add Analytics REST APIs

Files likely involved:

- `AnalyticsController`
- response DTOs
- query service

Deliverables:

- summary endpoint
- time series endpoint
- sponsor exposure endpoint
- risk endpoint if not embedded only in summary

Acceptance criteria:

- Empty streams return valid low-data responses.
- Window validation prevents expensive unbounded queries.
- Responses are sorted by bucket time.

### Step 7: Add Gateway GraphQL Support

Files likely involved:

- `api-gateway/src/main/java/.../client/AnalyticsServiceClient.java`
- `api-gateway/src/main/java/.../graphql/AnalyticsGraphqlController.java`
- `api-gateway/src/main/resources/graphql/*.graphqls`
- gateway tests

Deliverables:

- GraphQL metric summary query.
- GraphQL metric time-series query.
- GraphQL sponsor exposure query.
- GraphQL brand safety query.

Acceptance criteria:

- Gateway schema tests pass.
- Query tests verify response mapping and service errors.

### Step 8: Add Frontend Metric Panels

Files likely involved:

- `frontend/src/graphql/queries.ts`
- `frontend/src/components/*Metric*.tsx`
- `frontend/src/App.tsx`
- frontend tests

Deliverables:

- Audience volume panel.
- Sentiment trend panel.
- Sponsor exposure panel.
- Engagement spikes panel.
- Brand safety/risk panel.
- Data quality/degraded state badge.

Acceptance criteria:

- Dashboard shows metrics for selected Twitch channel.
- Panels handle empty, loading, degraded, and populated states.
- Existing raw event panels still work.

### Step 9: Add Deployment And Observability Wiring

Files likely involved:

- `docker-compose.yml`
- `k8s/apps/analytics-service.yaml`
- `k8s/config/config-server-config-repo.yaml`
- `k8s/config/prometheus-config.yaml`
- `monitoring/prometheus/prometheus.yml`
- `docs/howtorun.md`
- `makefile`

Deliverables:

- Compose service.
- Kubernetes manifest.
- Prometheus scrape config.
- Optional make commands.
- Runbook verification steps.

Possible make commands:

```text
make analytics-status
make twitch-analytics-up
make twitch-analytics-status
```

Acceptance criteria:

- Local stack starts with analytics-service.
- Prometheus can scrape analytics metrics.
- Runbook proves metrics update during live Twitch ingestion.

### Step 10: Live E2E Verification

Use an active Twitch channel with chat/video/transcript enabled.

Verification steps:

1. Start stack with Twitch env loaded.
2. Confirm chat, video, and transcript status endpoints are live.
3. Confirm raw Kafka events are arriving.
4. Confirm analytics-service consumes events.
5. Confirm aggregate tables update.
6. Confirm REST summary returns non-zero metrics.
7. Confirm GraphQL summary and time series return data.
8. Confirm frontend panels show live metric changes.
9. Confirm duplicate replay does not double-count a known event.
10. Confirm service metrics show low lag and no processing failures.

Acceptance criteria:

- Dashboard shows chat rate, sentiment trend, sponsor exposure, engagement spikes, and risk metrics for a real stream session.
- Metrics update as new Twitch events arrive.
- REST and GraphQL reads use aggregate tables, not frontend-derived raw lists.

## Rollout Plan

### Phase 3A: Backend Metrics Core

Deliver:

- analytics-service scaffold
- persistence tables
- Kafka consumers
- summary and time-series REST endpoints
- unit and integration tests

Exit criteria:

- REST metrics update from synthetic or test Kafka events.

### Phase 3B: Gateway And Frontend

Deliver:

- GraphQL analytics queries
- frontend metric panels
- empty/degraded/loading states
- frontend tests

Exit criteria:

- Dashboard renders aggregate metrics through GraphQL.

### Phase 3C: Live Twitch Proof And Observability

Deliver:

- Compose/Kubernetes wiring
- Prometheus metrics
- runbook updates
- live Twitch verification evidence

Exit criteria:

- Live Twitch stream updates metrics end to end.

## Backward Compatibility

Rules:

- Do not remove synthetic chat or frame ingest paths.
- Do not remove raw recent GraphQL queries.
- Do not remove raw event frontend panels.
- Do not require `streamSessionId` for all historical/demo events yet.
- Do not change existing Kafka topic names.
- New analytics queries should be additive.

Compatibility behavior:

- If `streamSessionId` is missing, aggregate under `sessionKey = streamer`.
- If transcript sentiment is unavailable, transcript sentiment metrics return zero counts and low-data state.
- If sponsor detections are unavailable, sponsor metrics return empty sponsor lists.
- If chat is unavailable, rate metrics return zero and low-data state.

## Failure Handling

Kafka processing failures:

- Count failures by topic.
- Log source topic, partition, offset, and event ID when available.
- Avoid logging full chat or transcript text by default.
- Use DLTs if consistent with existing service patterns.

Database failures:

- Let Kafka retry after transaction failure.
- Do not mark event as processed unless aggregate writes commit.
- Expose health degradation.

Malformed events:

- Count and skip if required fields are missing.
- Store enough diagnostic context to debug schemas.
- Do not crash the service loop.

Late events:

- Update the historical bucket matching the source event timestamp.
- Queries over that window should reflect the updated bucket.

Out-of-order events:

- Treat them like late events.
- Spike calculations can be recomputed for the affected bucket and neighboring buckets if necessary.

Low data:

- Return `lowData=true` when event counts are below configured thresholds.
- Risk should return `LOW_DATA` or equivalent instead of a misleading low risk score.

## Open Questions

Questions to confirm before or during implementation:

- Should transcript sentiment contribute to brand safety in the first UI version or remain separate?
- Should sponsor exposure estimate use the configured frame interval or actual gap between detections?
- Should `uniqueChatters` be exact for the whole window or approximate from buckets?
- Should analytics-service expose subscriptions in Phase 3, or is polling sufficient?
- Should metrics be retained indefinitely in local/dev, or should Phase 3 add retention cleanup?
- Should risk score be called `brandSafetyRisk`, `streamRisk`, or another product-facing term?

Recommended defaults if no decision is made:

- Include transcript sentiment in risk but show it as a separate factor.
- Use configured frame interval for exposure estimate.
- Use exact unique chatter tracking through `stream_bucket_chatters`.
- Use polling first, no metrics subscriptions.
- Keep aggregates indefinitely in local/dev.
- Use `brandSafetyMetrics` in GraphQL and `risk` in summary payloads.

## Definition Of Done

Phase 3 is done when all of the following are true:

- `analytics-service` runs locally and in Kubernetes manifests.
- Aggregate tables are created through Flyway.
- Kafka consumers process chat, chat sentiment, transcript sentiment, and sponsor detection events.
- Duplicate events do not double-count metrics.
- Late events update the correct bucket.
- REST endpoints return summary, time series, sponsor exposure, and risk metrics.
- GraphQL exposes metric summary and time-series queries.
- Frontend shows metric panels for audience volume, sentiment trend, sponsor exposure, engagement spikes, and brand safety/risk.
- Empty and low-data states are clear.
- Existing raw event panels and synthetic paths still work.
- Tests pass for analytics-service, api-gateway, and frontend.
- `docker compose config` passes.
- `kubectl kustomize k8s` passes if Kubernetes files are touched.
- A live Twitch stream produces visible metric updates end to end.
