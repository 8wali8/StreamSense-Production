/**
 * Sample GraphQL objects as the gateway returns them. Every object carries `__typename` because
 * Apollo's cache normalises by it; a fixture without one would leave the query result partial.
 *
 * The fixture types are the generated operation result types plus `__typename`, so a renamed
 * field or a changed nullability in the schema fails to compile here instead of letting a test
 * exercise a response the gateway cannot return.
 */

import type {
  OnChatMessageSubscription,
  RecentSentimentQuery,
  RecentTranscriptSegmentsQuery,
  RecentTranscriptSentimentQuery,
  RecommendationsQuery,
  SponsorDetectionsQuery,
  StreamAnalyticsQuery,
} from "../graphql/generated";

export function sentimentEvent(overrides: Partial<SentimentEventFixture> = {}): SentimentEventFixture {
  return {
    __typename: "SentimentAnalysisEvent",
    sentimentEventId: "sent-1",
    sourceEventId: "src-1",
    streamer: "test",
    user: "u1",
    message: "great stream",
    chatTimestamp: 1710000000000,
    processedAt: 1710000000500,
    label: "POSITIVE",
    score: 0.82,
    modelVersion: "stub-v1",
    sponsorRelevant: false,
    matchedSponsor: null,
    matchedTerms: [],
    relevanceScore: 0,
    relevanceReason: null,
    relevanceVersion: null,
    ...overrides,
  };
}

export type SentimentEventFixture = RecentSentimentQuery["recentSentiment"][number] & {
  __typename: "SentimentAnalysisEvent";
};

export function sponsorDetection(overrides: Partial<SponsorDetectionFixture> = {}): SponsorDetectionFixture {
  return {
    __typename: "SponsorDetectionEvent",
    detectionEventId: "det-1",
    sourceFrameId: "frame-1",
    streamer: "test",
    frameRef: "frames/test.png",
    frameSequence: 1,
    capturedAt: 1710000000000,
    processedAt: 1710000000500,
    sponsor: "Nike",
    confidence: 0.91,
    modelVersion: "stub-v1",
    x: 0.12,
    y: 0.18,
    width: 0.31,
    height: 0.24,
    source: "TWITCH",
    channelLogin: "test",
    streamSessionId: "test-1710000000000",
    twitchStreamId: null,
    videoTimestampMs: 0,
    ...overrides,
  };
}

export type SponsorDetectionFixture = SponsorDetectionsQuery["sponsorDetections"][number] & {
  __typename: "SponsorDetectionEvent";
};

export function transcriptSegment(overrides: Partial<TranscriptSegmentFixture> = {}): TranscriptSegmentFixture {
  return {
    __typename: "TranscriptSegmentEvent",
    segmentId: "segment-1",
    streamer: "test",
    text: "welcome back everyone",
    startedAt: 1710000000000,
    endedAt: 1710000003000,
    language: "en",
    confidence: 0.72,
    modelVersion: "faster-whisper-small.en-int8",
    source: "TWITCH",
    channelLogin: "test",
    streamSessionId: "test-1710000000000",
    videoTimestampMs: 1000,
    transcriptSequence: 1,
    captureWorkerId: "video-capture-service-1",
    ...overrides,
  };
}

export type TranscriptSegmentFixture = RecentTranscriptSegmentsQuery["recentTranscriptSegments"][number] & {
  __typename: "TranscriptSegmentEvent";
};

export function transcriptSentiment(overrides: Partial<TranscriptSentimentFixture> = {}): TranscriptSentimentFixture {
  return {
    __typename: "TranscriptSentimentEvent",
    sentimentEventId: "tsent-1",
    segmentId: "segment-1",
    streamer: "test",
    text: "welcome back everyone",
    segmentStartedAt: 1710000000000,
    segmentEndedAt: 1710000003000,
    processedAt: 1710000003500,
    label: "POSITIVE",
    score: 0.61,
    modelVersion: "stub-v1",
    transcriptModelVersion: "faster-whisper-small.en-int8",
    streamSessionId: "test-1710000000000",
    transcriptSequence: 1,
    sponsorRelevant: false,
    matchedSponsor: null,
    matchedTerms: [],
    relevanceScore: 0,
    relevanceReason: null,
    relevanceVersion: null,
    ...overrides,
  };
}

export type TranscriptSentimentFixture = RecentTranscriptSentimentQuery["recentTranscriptSentiment"][number] & {
  __typename: "TranscriptSentimentEvent";
};

export function chatMessage(overrides: Partial<ChatMessageFixture> = {}): ChatMessageFixture {
  return {
    __typename: "ChatMessageEvent",
    eventId: "evt-1",
    streamer: "test",
    user: "u1",
    message: "hello chat",
    timestamp: 1710000000000,
    ...overrides,
  };
}

export type ChatMessageFixture = OnChatMessageSubscription["onChatMessage"] & { __typename: "ChatMessageEvent" };

export function recommendation(overrides: Partial<RecommendationFixture> = {}): RecommendationFixture {
  return {
    __typename: "Recommendation",
    recommendationId: "test:sponsor_alignment",
    streamer: "test",
    title: "Highlight Nike moments while they are landing",
    category: "SPONSOR_ALIGNMENT",
    score: 0.83,
    reasonSummary: "Nike is the most visible sponsor in the recent window.",
    reasons: ["Nike appeared in 67% of recent sponsor detections.", "Average confidence for Nike was 0.88."],
    experimentName: "recommendation-ranking-v1",
    variantId: "balanced",
    generatedAt: 1712890800000,
    ...overrides,
  };
}

export type RecommendationFixture = RecommendationsQuery["recommendations"][number] & { __typename: "Recommendation" };

/** The StreamAnalytics query result: summary plus one timeseries bucket. */
/**
 * The generated shape with an optional `__typename` on every object, which Apollo's cache needs and
 * the generated types do not declare.
 */
type WithTypenames<T> =
  T extends Array<infer U>
    ? Array<WithTypenames<U>>
    : T extends object
      ? { [K in keyof T]: WithTypenames<T[K]> } & { __typename?: string }
      : T;

export function streamAnalytics(): WithTypenames<StreamAnalyticsQuery> {
  return {
    streamMetricsSummary: {
      __typename: "StreamMetricsSummary",
      streamer: "test",
      streamSessionId: null,
      windowMinutes: 15,
      bucketSizeSeconds: 60,
      windowStart: 1710000000000,
      windowEnd: 1710000900000,
      chat: {
        __typename: "ChatMetrics",
        totalMessages: 42,
        messagesPerMinute: 2.8,
        uniqueChatters: 11,
        peakMessagesPerMinute: 9,
      },
      chatSentiment: {
        __typename: "SentimentMetricSummary",
        positive: 12,
        neutral: 20,
        negative: 10,
        averageScore: 0.12,
        negativeRatio: 0.238,
      },
      transcriptSentiment: {
        __typename: "SentimentMetricSummary",
        positive: 2,
        neutral: 4,
        negative: 1,
        averageScore: 0.3,
        negativeRatio: 0.143,
      },
      sponsorExposure: {
        __typename: "SponsorExposureSummary",
        totalDetections: 3,
        acceptedDetections: 3,
        estimatedExposureMs: 30000,
        topSponsors: [
          {
            __typename: "SponsorExposureMetric",
            sponsor: "Nike",
            detectionCount: 3,
            acceptedDetectionCount: 3,
            estimatedExposureMs: 30000,
            averageConfidence: 0.81,
            maxConfidence: 0.9,
            fallbackDetectionCount: 0,
            lowConfidenceDetectionCount: 0,
          },
        ],
      },
      engagement: { __typename: "EngagementMetrics", spikeCount: 1, latestSpikeAt: 1710000600000 },
      risk: {
        __typename: "BrandSafetyMetrics",
        level: "LOW",
        score: 0.2,
        factors: [{ __typename: "RiskFactor", name: "chatNegativeRatio", value: 0.238, weight: 0.35 }],
      },
      dataQuality: {
        __typename: "AnalyticsDataQuality",
        lowData: false,
        latestEventAt: 1710000600000,
        aggregationLagMs: 1500,
      },
    },
    streamMetricsTimeseries: [
      {
        __typename: "StreamMetricBucket",
        bucketStart: 1710000000000,
        bucketEnd: 1710000060000,
        chatMessageCount: 7,
        uniqueChatters: 4,
        chatAverageScore: -0.2,
        chatNegativeRatio: 0.3,
        transcriptAverageScore: 0.1,
        transcriptNegativeRatio: 0.1,
        sponsorDetectionCount: 1,
        estimatedSponsorExposureMs: 10000,
        engagementSpike: true,
        negativeSpike: false,
      },
    ],
  };
}

/** REST status bodies. */
export const twitchStatusConnected = {
  enabled: true,
  state: "CONNECTED",
  channels: ["testchannel"],
  lastMessageAt: 1710000000000,
  lastError: null,
  reconnectAttempts: 0,
};

export const videoStatusCapturing = {
  enabled: true,
  state: "CAPTURING",
  channels: ["testchannel"],
  lastFrameAt: 1710000000000,
  lastTranscriptAt: null,
  channelStatuses: [{ channel: "testchannel", state: "CAPTURING", lastError: null, lastTranscriptPreview: null }],
};
