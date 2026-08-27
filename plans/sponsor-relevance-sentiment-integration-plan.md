# Sponsor-Relevance Sentiment Integration Plan

## Objective

Add sponsor-relevance filtering so StreamSense can report sentiment specifically about the active sponsor or campaign while preserving the existing general chat and transcript sentiment streams.

This should not silently drop unrelated chat or transcript sentiment. General audience tone is still useful for engagement, risk, and recommendation context. Sponsor-relevant sentiment should be an additional filtered view with explicit relevance metadata.

## Current State

The current sentiment path is production-shaped but sponsor-agnostic:

1. `sentiment-service` consumes `stream.chat.messages` and `stream.transcript.segments`.
2. `SentimentService` calls `ml-engine` for every chat message and every transcript segment.
3. It persists general sentiment into `sentiment_events` and `transcript_sentiment_events`.
4. It publishes general Kafka events to `stream.sentiment.events` and `stream.transcript.sentiment.events`.
5. `api-gateway` exposes only general sentiment through REST-backed GraphQL queries and Kafka-backed subscriptions.
6. The frontend shows general chat sentiment and general transcript sentiment in the Live Stream Console and evidence panels.
7. `analytics-service` aggregates general sentiment by label and score only.
8. `recommendation-service` summarizes general sentiment and sponsor detections separately.

There is currently no sponsor relevance metadata in sentiment DTOs, persistence, GraphQL types, frontend models, analytics buckets, or recommendation sentiment inputs.

## Recommended Direction

Use one shared sentiment analysis pass, then annotate each result with sponsor-relevance metadata. Expose sponsor-relevant sentiment through new filtered endpoints and GraphQL fields while keeping the existing general endpoints unchanged.

Implementation choices selected for the first end-to-end pass:

1. Build the full path from ML relevance through frontend display.
2. Run embedding-based semantic relevance in `ml-engine` through a new endpoint.
3. Keep runtime sponsor configuration in memory for the first version.
4. Let the Live Stream Console update active sponsor terms for the selected streamer at runtime.
5. Use moderately broad matching: direct matches are relevant immediately; broad semantic/category matches need enough embedding similarity to avoid the weakest false positives.

Recommended behavior:

1. Continue producing `recentSentiment`, `onSentiment`, `recentTranscriptSentiment`, and `onTranscriptSentiment` as general audience tone.
2. Add relevance metadata to sentiment records and events.
3. Add new sponsor-filtered history queries and subscriptions for chat and transcript sentiment.
4. Add sponsor-specific frontend panels and metrics without removing the existing general sentiment panels.
5. Update analytics and recommendations to use sponsor-filtered sentiment where the product question is sponsor performance, while retaining general sentiment for overall audience tone.

This avoids a breaking change and avoids recomputing ML sentiment twice.

## Relevance Model

Add broad sponsor matching in `sentiment-service` first. The matcher should combine direct word/alias matching with semantically related terms so sponsor relevance errs on the side of inclusion.

Metadata to add:

1. `sponsorRelevant: Boolean`
2. `matchedSponsor: String`
3. `matchedTerms: [String]`
4. `relevanceScore: Float`
5. `relevanceReason: String`
6. `relevanceVersion: String`

Initial matcher behavior:

1. Normalize text with lowercase, Unicode normalization, punctuation folding, repeated whitespace collapsing, and URL stripping.
2. Match active sponsor names and aliases using token-aware matching, not naive substring matching.
3. Handle common hashtag and mention forms such as `#nike`, `@nike`, `nikepartner`, and `nike ad`.
4. Match configured semantic terms and broad category terms that can imply the sponsor, such as `shoes`, `sneakers`, `kicks`, `running gear`, or `apparel` for Nike.
5. Allow intentionally broad sponsor vocabularies because false positives are preferable to missing sponsor-relevant discussion.
6. Optionally match campaign terms from the UI goal later, but start with configured sponsor names, aliases, and semantic/category terms.
7. Assign relevance score deterministically from match strength, term specificity, and number of matched direct or semantic terms.
8. Record `relevanceVersion`, for example `sponsor-broad-match-v1`.

Avoid at first:

1. Inferring sponsor relevance from nearby sponsor visual detections.
2. Treating all text inside a stream with a sponsor detection as sponsor-relevant.
3. Sending every message to an LLM/classifier for relevance in the first implementation slice unless explicitly approved.

Those can come later after there is a persisted deterministic baseline.

## Sponsor Configuration

Add sponsor relevance config under `streamsense.sentiment.relevance` in `config-server/config-repo/sentiment-service.yml` and mirror it in `k8s/config/config-server-config-repo.yaml`.

Proposed shape:

```yaml
streamsense:
  sentiment:
    relevance:
      enabled: true
      version: sponsor-broad-match-v1
      minScore: 0.60
      sponsors:
        - name: Nike
          aliases: [nike, swoosh, just do it]
          semanticTerms: [shoes, sneakers, kicks, running shoes, apparel, sportswear, trainers]
        - name: Prime
          aliases: [prime, prime hydration]
          semanticTerms: [hydration, drink, energy drink, bottle]
        - name: Razer
          aliases: [razer]
          semanticTerms: [keyboard, mouse, headset, gaming gear, peripherals]
```

UI-provided sponsor names should be supported later through runtime campaign state, but the current backend has no persistent campaign/session configuration service. Start with static config so service behavior is repeatable and testable.

## Sentiment Service Changes

Primary files:

1. `sentiment-service/src/main/java/com/streamsense/sentimentservice/service/SentimentService.java`
2. `sentiment-service/src/main/java/com/streamsense/sentimentservice/config/StreamSenseProperties.java`
3. `sentiment-service/src/main/java/com/streamsense/sentimentservice/events/SentimentAnalysisEvent.java`
4. `sentiment-service/src/main/java/com/streamsense/sentimentservice/events/TranscriptSentimentEvent.java`
5. `sentiment-service/src/main/java/com/streamsense/sentimentservice/persistence/SentimentRecordEntity.java`
6. `sentiment-service/src/main/java/com/streamsense/sentimentservice/persistence/TranscriptSentimentRecordEntity.java`
7. `sentiment-service/src/main/java/com/streamsense/sentimentservice/persistence/SentimentRecordRepository.java`
8. `sentiment-service/src/main/java/com/streamsense/sentimentservice/persistence/TranscriptSentimentRecordRepository.java`
9. `sentiment-service/src/main/java/com/streamsense/sentimentservice/controller/SentimentHistoryController.java`

Implementation steps:

1. Add a `SponsorRelevanceMatcher` service and small result object.
2. Run the matcher after ML sentiment returns, before persistence and publishing.
3. Add relevance metadata to both chat and transcript sentiment events.
4. Add Flyway migration `V3__add_sponsor_relevance_to_sentiment.sql` for both sentiment tables.
5. Add repository methods for sponsor-relevant recent history.
6. Add REST endpoints:
   - `GET /api/sentiment/sponsor/recent?streamer=&sponsor=&limit=`
   - `GET /api/sentiment/transcript/sponsor/recent?streamer=&sponsor=&limit=`
7. Keep existing REST endpoints returning all sentiment records.
8. Evict or key the recent sentiment cache so sponsor-filtered history does not share the same cache entry as general history.

Persistence columns:

1. `sponsor_relevant BOOLEAN NOT NULL DEFAULT false`
2. `matched_sponsor VARCHAR(255)`
3. `matched_terms VARCHAR(1000)` or JSON/text if this service already accepts plain text columns more easily.
4. `relevance_score DOUBLE PRECISION NOT NULL DEFAULT 0`
5. `relevance_reason VARCHAR(255)`
6. `relevance_version VARCHAR(64)`

Indexes:

1. `sentiment_events(streamer, sponsor_relevant, matched_sponsor, chat_timestamp DESC)`
2. `transcript_sentiment_events(streamer, sponsor_relevant, matched_sponsor, segment_ended_at DESC)`

## Kafka Contract

Recommended first pass: extend existing sentiment events with optional relevance fields rather than adding new Kafka topics.

Reasoning:

1. Existing consumers ignore unknown JSON fields if their DTOs do not bind them.
2. API gateway can expose filtered subscriptions by filtering the enriched event stream.
3. Analytics can choose whether to aggregate general or sponsor-relevant sentiment without duplicating event production.
4. It keeps the implementation smaller than a parallel topic fanout.

Do not stop publishing non-relevant sentiment to `stream.sentiment.events` or `stream.transcript.sentiment.events` in the first pass.

Add separate topics only if downstream systems need isolated sponsor-relevant streams for retention, ACL, or consumer scaling reasons.

## API Gateway Changes

Primary files:

1. `api-gateway/src/main/resources/graphql/sentiment.graphqls`
2. `api-gateway/src/main/resources/graphql/query.graphqls`
3. `api-gateway/src/main/resources/graphql/subscription.graphqls`
4. `api-gateway/src/main/java/com/streamsense/apigateway/events/SentimentAnalysisEvent.java`
5. `api-gateway/src/main/java/com/streamsense/apigateway/events/TranscriptSentimentEvent.java`
6. `api-gateway/src/main/java/com/streamsense/apigateway/client/SentimentServiceClient.java`
7. `api-gateway/src/main/java/com/streamsense/apigateway/graphql/SentimentGraphqlController.java`
8. `api-gateway/src/main/java/com/streamsense/apigateway/graphql/TranscriptGraphqlController.java`

GraphQL additions:

```graphql
type SentimentAnalysisEvent {
  sponsorRelevant: Boolean!
  matchedSponsor: String
  matchedTerms: [String!]!
  relevanceScore: Float!
  relevanceReason: String
  relevanceVersion: String
}

type TranscriptSentimentEvent {
  sponsorRelevant: Boolean!
  matchedSponsor: String
  matchedTerms: [String!]!
  relevanceScore: Float!
  relevanceReason: String
  relevanceVersion: String
}

type Query {
  recentSponsorSentiment(streamer: String!, sponsor: String, limit: Int!): [SentimentAnalysisEvent!]!
  recentSponsorTranscriptSentiment(streamer: String!, sponsor: String, limit: Int!): [TranscriptSentimentEvent!]!
}

type Subscription {
  onSponsorSentiment(streamer: String!, sponsor: String): SentimentAnalysisEvent!
  onSponsorTranscriptSentiment(streamer: String!, sponsor: String): TranscriptSentimentEvent!
}
```

If GraphQL list fields are awkward for `matchedTerms`, use `matchedTermsCsv` internally and split in the GraphQL DTO layer.

## Frontend Changes

Primary files:

1. `frontend/src/App.tsx`
2. `frontend/src/graphql/queries.ts`
3. `frontend/src/graphql/subscriptions.ts`
4. `frontend/src/components/SentimentPanel.tsx`
5. `frontend/src/components/TranscriptSentimentPanel.tsx`
6. `frontend/src/components/SponsorPanel.tsx`

Implementation steps:

1. Add sponsor-relevant GraphQL queries and subscriptions.
2. Extend frontend sentiment event types with relevance fields.
3. In `LiveStreamConsole`, keep general transcript and chat feeds but add a sponsor sentiment strip/card keyed by the current Sponsor input.
4. Show why a text item matched using `matchedTerms` and `relevanceScore`.
5. Add a clear empty state such as `No sponsor-related sentiment yet` so users know the filter is working.
6. Preserve existing evidence `SentimentPanel` as general sentiment unless explicitly renamed.
7. Optionally add a toggle later: `General tone` / `Sponsor-specific tone`.

Do not make the Sponsor input only a frontend filter unless backend config/runtime campaign state supports the same sponsor. Otherwise the UI may imply a backend match that did not happen.

## Analytics Changes

Current `analytics-service` aggregates general chat sentiment and transcript sentiment into the same metric buckets. Add sponsor-specific metrics separately rather than replacing general metrics.

Recommended first pass:

1. Extend analytics sentiment DTOs with relevance fields.
2. Ignore non-relevant sentiment for sponsor-specific counters.
3. Add sponsor sentiment counters to metric buckets only if schema churn is acceptable.
4. Otherwise, defer analytics schema changes and let GraphQL/frontend sponsor sentiment history prove the product value first.

Possible sponsor metrics:

1. Sponsor-relevant sentiment count.
2. Sponsor-relevant positive/neutral/negative counts.
3. Sponsor-relevant average score.
4. Sponsor-relevant negative ratio.
5. Matched sponsor breakdown.
6. Sponsor mention rate compared with visual sponsor detections.

Recommended staged approach:

1. Phase 1: No analytics schema change; expose sponsor sentiment history only.
2. Phase 2: Add sponsor sentiment aggregation after the UI and API prove the filtered signal is useful.

## Recommendation Changes

Primary files:

1. `recommendation-service/src/main/java/com/streamsense/recommendationservice/client/SentimentSignal.java`
2. `recommendation-service/src/main/java/com/streamsense/recommendationservice/client/SentimentHistoryClient.java`
3. `recommendation-service/src/main/java/com/streamsense/recommendationservice/service/RecommendationGenerator.java`

Implementation steps:

1. Extend `SentimentSignal` with relevance fields.
2. Add `recentSponsorSentiment(streamer, sponsor, limit)` client method once the sentiment-service endpoint exists.
3. Keep general sentiment in `CONTENT_MOMENTUM` and `AUDIENCE_TONE` recommendations.
4. Use sponsor-relevant sentiment in `SPONSOR_ALIGNMENT` and sponsor caution reasons.
5. Add recommendation reasons that explicitly distinguish general audience mood from sponsor-specific sponsor mentions.

Example behavior:

1. General sentiment positive but sponsor sentiment negative: recommend sponsor recovery or creative adjustment.
2. General sentiment neutral but sponsor sentiment positive: recommend leaning into sponsor read moments.
3. Sponsor detections high but sponsor sentiment sparse: recommend clearer verbal sponsor callout.

## Rollout Phases

### Phase 1: Sentiment Relevance Metadata

1. Add static relevance config.
2. Add matcher and unit tests.
3. Add DB migration and entity/DTO fields.
4. Enrich existing chat/transcript sentiment events.
5. Keep existing APIs and UI behavior unchanged.

### Phase 2: Sponsor-Filtered API And GraphQL

1. Add sponsor-filtered REST history endpoints.
2. Add sponsor-filtered GraphQL queries and subscriptions.
3. Add API gateway DTO fields.
4. Add gateway filtering tests.

### Phase 3: Frontend Sponsor Sentiment View

1. Add sponsor sentiment queries/subscriptions.
2. Add a sponsor sentiment card in Live Stream Console.
3. Display matched terms and relevance score.
4. Preserve general sentiment panels.

### Phase 4: Recommendations

1. Fetch sponsor-filtered sentiment.
2. Use sponsor-specific sentiment in sponsor alignment recommendations.
3. Update recommendation tests.

### Phase 5: Analytics Metrics

1. Add schema for sponsor sentiment aggregation if needed.
2. Aggregate sponsor-relevant sentiment separately from general sentiment.
3. Add sponsor sentiment fields to `StreamMetricsSummary` and frontend metrics.

## Test Plan

Sentiment service:

1. Unit tests for exact sponsor match, aliases, hashtags, mentions, punctuation, case, negative examples, and multiple sponsors.
2. Service tests proving general sentiment still persists and publishes for non-relevant text.
3. Service tests proving relevant text persists relevance metadata.
4. Repository tests for recent sponsor-relevant history.
5. Migration validation through `mvn -B -ntp clean test` in `sentiment-service`.

API gateway:

1. GraphQL schema tests for new fields and queries.
2. Subscription filtering tests for sponsor relevance and optional sponsor argument.
3. REST client tests for new sentiment-service endpoints.

Frontend:

1. Type and query updates compile.
2. Component tests for sponsor sentiment empty, positive, negative, and matched-term states.
3. `npm run lint`, `npm run test`, and `npm run build` in `frontend`.

Recommendation service:

1. Generator tests for general sentiment vs sponsor sentiment divergence.
2. Client tests for sponsor sentiment endpoint.

Analytics service if Phase 5 is implemented:

1. Aggregation tests for sponsor-relevant sentiment counters.
2. Regression tests proving general sentiment counters are unchanged.

Kubernetes/config:

1. Mirror config changes in `k8s/config/config-server-config-repo.yaml`.
2. Run `kubectl kustomize k8s` after touching `k8s/`.

## Risks And Tradeoffs

1. Static sponsor aliases are simple and testable but require config changes for new sponsors.
2. Frontend Sponsor input cannot fully control backend relevance until campaign state is persisted or passed to sentiment-service.
3. Keyword matching will miss indirect sponsor discussion such as `the shoes are fire` unless `shoes` is configured as an alias, which may create false positives.
4. Extending existing Kafka events is smaller but means consumers receive more fields; separate topics are cleaner for isolation but more expensive to wire.
5. Analytics schema changes should wait until the filtered signal is visible and validated in the UI.

## Open Decision Before Implementation

Use this default unless you want a different contract: preserve general sentiment unchanged, enrich existing sentiment events with sponsor relevance metadata, and add new sponsor-filtered REST/GraphQL views.

Alternative choices:

1. Add separate sponsor sentiment Kafka topics immediately.
2. Stop publishing/storing non-relevant sentiment for sponsor workflows.
3. Let the UI Sponsor input define backend relevance dynamically before adding persistent campaign state.

The recommended first implementation slice is Phase 1 only: relevance config, deterministic matcher, DB migration, DTO/entity enrichment, and tests, with no frontend or API contract changes yet.
