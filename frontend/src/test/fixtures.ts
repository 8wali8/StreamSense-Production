/**
 * Sample GraphQL objects as the gateway returns them. Every object carries `__typename` because
 * Apollo's cache normalises by it; a fixture without one would leave the query result partial.
 *
 * The fixture types are the generated operation result types plus `__typename`, so a renamed
 * field or a changed nullability in the schema fails to compile here instead of letting a test
 * exercise a response the gateway cannot return.
 */

import type {
  DealSummaryQuery,
  DealsQuery,
  OnChatMessageSubscription,
  RecentSentimentQuery,
  RecentTranscriptSegmentsQuery,
  RecentTranscriptSentimentQuery,
  SessionSummaryQuery,
  SponsorMomentsQuery,
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

/** The one-page report for the replay session, modelled on the approved prototype. */
export function sessionSummary(overrides: Partial<SessionSummaryFixture> = {}): SessionSummaryFixture {
  const base: SessionSummaryFixture = {
    __typename: "SessionSummary",
    session: {
      __typename: "StreamSession",
      id: "7",
      streamer: "redbull-testing",
      source: "CAPTURE",
      twitchStreamId: "2750461300",
      streamSessionId: "redbull-testing-2750461300",
      title: "F1 Replay Night: Monza highlights",
      category: "Formula 1",
      startedAt: 1788816420000,
      endedAt: 1788824460000,
      live: false,
      durationMs: 8040000,
      peakViewers: 1842,
      averageViewers: 1310,
      viewerSamples: 134,
      vodId: null,
    },
    dealId: null,
    sponsor: "Red Bull",
    onScreenMs: 2292000,
    onScreenShare: 0.285,
    mentions: 47,
    chatMentions: 31,
    voiceMentions: 16,
    mentionSentiment: 0.62,
    mentionPositiveShare: 0.81,
    mentionNegativeShare: 0.06,
    averageViewers: 1310,
    peakViewers: 1842,
    risk: {
      __typename: "BrandSafetyMetrics",
      level: "LOW",
      score: 0.14,
      factors: [{ __typename: "RiskFactor", name: "chatNegativeRatio", value: 0.09, weight: 0.35 }],
    },
    chat: {
      __typename: "ChatMetrics",
      totalMessages: 2984,
      messagesPerMinute: 22.3,
      uniqueChatters: 611,
      peakMessagesPerMinute: 120,
    },
    chatSentiment: {
      __typename: "SentimentMetricSummary",
      positive: 1200,
      neutral: 1500,
      negative: 284,
      averageScore: 0.44,
      negativeRatio: 0.09,
    },
    transcriptSentiment: {
      __typename: "SentimentMetricSummary",
      positive: 40,
      neutral: 50,
      negative: 7,
      averageScore: 0.38,
      negativeRatio: 0.07,
    },
    engagement: { __typename: "EngagementMetrics", spikeCount: 3, latestSpikeAt: 1788821220000 },
    value: {
      __typename: "SessionValue",
      logoValue: 874,
      hostReadValue: 314,
      mediaValue: 1190,
      weightedLogoViewerMinutes: 36400,
      averageProminence: 0.71,
      cpmPer30sEquivalent: 12,
      hostReadRatePer1000: 15,
      basis: "logo: weighted viewer-minutes x 2 per 1,000 at CPM 12.0; host reads at 15.0",
    },
    response: {
      __typename: "DirectResponse",
      chatCommand: "!redbull",
      commandUses: 137,
      commandUsers: 91,
      trackedLinkHost: "redbull.com",
      linkPosts: 84,
    },
  };
  return { ...base, ...overrides };
}

export type SessionSummaryFixture = WithTypenames<NonNullable<SessionSummaryQuery["sessionSummary"]>> & {
  __typename: "SessionSummary";
};

/** The timeline behind the same report: two on-screen runs, two voice lines, one chat minute, one spike. */
export function sponsorMoments(): WithTypenames<NonNullable<SponsorMomentsQuery["sponsorMoments"]>> & {
  __typename: "SponsorMoments";
} {
  return {
    __typename: "SponsorMoments",
    sponsor: "Red Bull",
    session: { __typename: "StreamSession", id: "7", startedAt: 1788816420000, durationMs: 8040000 },
    segments: [
      {
        __typename: "ExposureSegment",
        sponsor: "Red Bull",
        startedAt: 1788816660000,
        endedAt: 1788817020000,
        offsetMs: 240000,
        durationMs: 360000,
        videoTimestampMs: 2040000,
        detections: 36,
        peakConfidence: 0.91,
      },
      {
        __typename: "ExposureSegment",
        sponsor: "Red Bull",
        startedAt: 1788820320000,
        endedAt: 1788821220000,
        offsetMs: 3900000,
        durationMs: 900000,
        videoTimestampMs: null,
        detections: 90,
        peakConfidence: 0.95,
      },
    ],
    voiceMentions: [
      {
        __typename: "VoiceMention",
        sentimentEventId: "v1",
        at: 1788817500000,
        offsetMs: 1080000,
        label: "POSITIVE",
        score: 0.84,
        text: "That Red Bull livery looks planted through every corner tonight.",
      },
      {
        __typename: "VoiceMention",
        sentimentEventId: "v2",
        at: 1788822840000,
        offsetMs: 6420000,
        label: "NEGATIVE",
        score: -0.71,
        text: "Honestly the drink tastes like battery acid but the car is unreal.",
      },
    ],
    chatMoments: [
      {
        __typename: "ChatMoment",
        at: 1788821220000,
        offsetMs: 4800000,
        count: 38,
        positiveShare: 0.84,
        negativeShare: 0.03,
        averageScore: 0.7,
        sample: "Red Bull fans are eating well with this performance.",
        user: "PodiumPush",
      },
    ],
    riskSpikes: [
      { __typename: "RiskSpike", at: 1788820560000, offsetMs: 4140000, chatNegativeRatio: 0.6, chatMessageCount: 21 },
    ],
    best: {
      __typename: "HighlightMoment",
      kind: "CHAT_BURST",
      at: 1788821220000,
      offsetMs: 4800000,
      title: "Podium celebration, logo on screen 15 minutes",
      detail: "38 brand mentions in a minute, 84% positive",
      score: 31.9,
    },
    weakest: {
      __typename: "HighlightMoment",
      kind: "VOICE",
      at: 1788822840000,
      offsetMs: 6420000,
      title: "Voice mention read as negative, -0.71",
      detail: "Honestly the drink tastes like battery acid but the car is unreal.",
      score: -0.71,
    },
  };
}

export type DealFixture = WithTypenames<DealsQuery["deals"][number]> & { __typename: "Deal" };

/** The Red Bull deal on the replay channel: four promised streams, a fee, a command, and a tracked link. */
export function deal(overrides: Partial<DealFixture> = {}): DealFixture {
  return {
    __typename: "Deal",
    id: "3",
    streamer: "redbull-testing",
    sponsor: "Red Bull",
    startsAt: 1788400000000,
    endsAt: 1790400000000,
    promisedStreams: 4,
    fee: 2500,
    currency: "USD",
    cpmPer30sEquivalent: 12,
    hostReadRatePer1000: 15,
    trackedLink: "https://www.redbull.com/f1",
    trackedLinkHost: "redbull.com",
    chatCommand: "!redbull",
    channelPointReward: null,
    active: true,
    shareToken: null,
    createdAt: 1788400000000,
    ...overrides,
  };
}

export type DealSummaryFixture = WithTypenames<NonNullable<DealSummaryQuery["dealSummary"]>> & {
  __typename: "DealSummary";
};

/** Two finished streams inside the deal, totals over them. */
export function dealSummary(overrides: Partial<DealSummaryFixture> = {}): DealSummaryFixture {
  return {
    __typename: "DealSummary",
    deal: deal(),
    totals: {
      __typename: "DealTotals",
      streams: 2,
      liveStreams: 0,
      streamedMs: 15000000,
      onScreenMs: 3500000,
      onScreenShare: 0.233,
      mentions: 71,
      chatMentions: 48,
      voiceMentions: 23,
      mentionSentiment: 0.58,
      averageViewers: 1250,
      logoValue: 2600,
      hostReadValue: 900,
      mediaValue: 3500,
      commandUses: 210,
      linkPosts: 120,
    },
    sessions: [
      {
        __typename: "SessionSummary",
        session: {
          __typename: "StreamSession",
          id: "9",
          title: "Singapore GP watch-along",
          startedAt: 1789400000000,
          endedAt: 1789406960000,
          live: false,
          durationMs: 6960000,
          averageViewers: 1190,
          peakViewers: 1600,
        },
        onScreenMs: 1208000,
        onScreenShare: 0.174,
        mentions: 24,
        mentionSentiment: 0.51,
        averageViewers: 1190,
        risk: { __typename: "BrandSafetyMetrics", level: "LOW" },
        value: { __typename: "SessionValue", mediaValue: 2310 },
      },
      {
        __typename: "SessionSummary",
        session: {
          __typename: "StreamSession",
          id: "7",
          title: "F1 Replay Night: Monza highlights",
          startedAt: 1788816420000,
          endedAt: 1788824460000,
          live: false,
          durationMs: 8040000,
          averageViewers: 1310,
          peakViewers: 1842,
        },
        onScreenMs: 2292000,
        onScreenShare: 0.285,
        mentions: 47,
        mentionSentiment: 0.62,
        averageViewers: 1310,
        risk: { __typename: "BrandSafetyMetrics", level: "LOW" },
        value: { __typename: "SessionValue", mediaValue: 1190 },
      },
    ],
    ...overrides,
  };
}
