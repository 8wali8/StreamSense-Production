/**
 * Sample GraphQL objects as the gateway returns them. Every object carries `__typename` because
 * Apollo's cache normalises by it; a fixture without one would leave the query result partial.
 */

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

export type SentimentEventFixture = {
  __typename: "SentimentAnalysisEvent";
  sentimentEventId: string;
  sourceEventId: string;
  streamer: string;
  user: string;
  message: string;
  chatTimestamp: number;
  processedAt: number;
  label: string;
  score: number;
  modelVersion: string;
  sponsorRelevant: boolean;
  matchedSponsor: string | null;
  matchedTerms: string[];
  relevanceScore: number;
  relevanceReason: string | null;
  relevanceVersion: string | null;
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

export type SponsorDetectionFixture = {
  __typename: "SponsorDetectionEvent";
  detectionEventId: string;
  sourceFrameId: string;
  streamer: string;
  frameRef: string;
  frameSequence: number;
  capturedAt: number;
  processedAt: number;
  sponsor: string;
  confidence: number;
  modelVersion: string;
  x: number;
  y: number;
  width: number;
  height: number;
  source: string | null;
  channelLogin: string | null;
  streamSessionId: string | null;
  twitchStreamId: string | null;
  videoTimestampMs: number | null;
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

export type TranscriptSegmentFixture = {
  __typename: "TranscriptSegmentEvent";
  segmentId: string;
  streamer: string;
  text: string;
  startedAt: number;
  endedAt: number;
  language: string | null;
  confidence: number | null;
  modelVersion: string;
  source: string;
  channelLogin: string | null;
  streamSessionId: string;
  videoTimestampMs: number;
  transcriptSequence: number;
  captureWorkerId: string | null;
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

export type TranscriptSentimentFixture = {
  __typename: "TranscriptSentimentEvent";
  sentimentEventId: string;
  segmentId: string;
  streamer: string;
  text: string;
  segmentStartedAt: number;
  segmentEndedAt: number;
  processedAt: number;
  label: string;
  score: number;
  modelVersion: string;
  transcriptModelVersion: string;
  streamSessionId: string;
  transcriptSequence: number;
  sponsorRelevant: boolean;
  matchedSponsor: string | null;
  matchedTerms: string[];
  relevanceScore: number;
  relevanceReason: string | null;
  relevanceVersion: string | null;
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

export type ChatMessageFixture = {
  __typename: "ChatMessageEvent";
  eventId: string;
  streamer: string;
  user: string;
  message: string;
  timestamp: number;
};

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

export type RecommendationFixture = {
  __typename: "Recommendation";
  recommendationId: string;
  streamer: string;
  title: string;
  category: string;
  score: number;
  reasonSummary: string;
  reasons: string[];
  experimentName: string;
  variantId: string;
  generatedAt: number;
};

/** The StreamAnalytics query result: summary plus one timeseries bucket. */
export function streamAnalytics() {
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
