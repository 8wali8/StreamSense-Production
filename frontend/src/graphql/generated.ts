/** Internal type. DO NOT USE DIRECTLY. */
type Exact<T extends { [key: string]: unknown }> = { [K in keyof T]: T[K] };
/** Internal type. DO NOT USE DIRECTLY. */
export type Incremental<T> = T | { [P in keyof T]?: P extends ' $fragmentName' | '__typename' ? T[P] : never };
export type Maybe<T> = T | null;
export type InputMaybe<T> = Maybe<T>;
/** All built-in and custom scalars, mapped to their actual values */
export type Scalars = {
  ID: { input: string; output: string; }
  String: { input: string; output: string; }
  Boolean: { input: boolean; output: boolean; }
  Int: { input: number; output: number; }
  Float: { input: number; output: number; }
};

export type AnalyticsDataQuality = {
  aggregationLagMs?: Maybe<Scalars['Float']['output']>;
  latestEventAt?: Maybe<Scalars['Float']['output']>;
  lowData: Scalars['Boolean']['output'];
};

export type BrandSafetyMetrics = {
  factors: Array<RiskFactor>;
  level: Scalars['String']['output'];
  score?: Maybe<Scalars['Float']['output']>;
};

export type ChatMessageEvent = {
  channelLogin?: Maybe<Scalars['String']['output']>;
  eventId: Scalars['ID']['output'];
  message: Scalars['String']['output'];
  source?: Maybe<Scalars['String']['output']>;
  streamSessionId?: Maybe<Scalars['String']['output']>;
  streamer: Scalars['String']['output'];
  timestamp: Scalars['Float']['output'];
  twitchStreamId?: Maybe<Scalars['String']['output']>;
  user: Scalars['String']['output'];
};

export type ChatMetrics = {
  messagesPerMinute: Scalars['Float']['output'];
  peakMessagesPerMinute: Scalars['Float']['output'];
  totalMessages: Scalars['Float']['output'];
  uniqueChatters: Scalars['Float']['output'];
};

export type ChatMoment = {
  at: Scalars['Float']['output'];
  averageScore: Scalars['Float']['output'];
  count: Scalars['Int']['output'];
  negativeShare: Scalars['Float']['output'];
  offsetMs: Scalars['Float']['output'];
  positiveShare: Scalars['Float']['output'];
  sample?: Maybe<Scalars['String']['output']>;
  user?: Maybe<Scalars['String']['output']>;
};

export type DirectResponse = {
  chatCommand?: Maybe<Scalars['String']['output']>;
  commandUsers: Scalars['Int']['output'];
  commandUses: Scalars['Int']['output'];
  linkPosts: Scalars['Int']['output'];
  trackedLinkHost?: Maybe<Scalars['String']['output']>;
};

export type EngagementMetrics = {
  latestSpikeAt?: Maybe<Scalars['Float']['output']>;
  spikeCount: Scalars['Float']['output'];
};

export type ExposureSegment = {
  detections: Scalars['Int']['output'];
  durationMs: Scalars['Float']['output'];
  endedAt: Scalars['Float']['output'];
  offsetMs: Scalars['Float']['output'];
  peakConfidence: Scalars['Float']['output'];
  sponsor: Scalars['String']['output'];
  startedAt: Scalars['Float']['output'];
  videoTimestampMs?: Maybe<Scalars['Float']['output']>;
};

export type HighlightMoment = {
  at: Scalars['Float']['output'];
  detail: Scalars['String']['output'];
  kind: Scalars['String']['output'];
  offsetMs: Scalars['Float']['output'];
  score: Scalars['Float']['output'];
  title: Scalars['String']['output'];
};

export type Query = {
  brandSafetyMetrics: BrandSafetyMetrics;
  health: Scalars['String']['output'];
  recentSentiment: Array<SentimentAnalysisEvent>;
  recentSponsorSentiment: Array<SentimentAnalysisEvent>;
  recentSponsorTranscriptSentiment: Array<TranscriptSentimentEvent>;
  recentTranscriptSegments: Array<TranscriptSegmentEvent>;
  recentTranscriptSentiment: Array<TranscriptSentimentEvent>;
  recommendations: Array<Recommendation>;
  session?: Maybe<StreamSession>;
  sessionSummary?: Maybe<SessionSummary>;
  sessions: Array<StreamSession>;
  sponsorDetections: Array<SponsorDetectionEvent>;
  sponsorExposureMetrics: Array<SponsorExposureMetric>;
  sponsorMoments?: Maybe<SponsorMoments>;
  streamMetricsSummary: StreamMetricsSummary;
  streamMetricsTimeseries: Array<StreamMetricBucket>;
};


export type QueryBrandSafetyMetricsArgs = {
  from?: InputMaybe<Scalars['Float']['input']>;
  sessionId?: InputMaybe<Scalars['ID']['input']>;
  streamSessionId?: InputMaybe<Scalars['String']['input']>;
  streamer: Scalars['String']['input'];
  to?: InputMaybe<Scalars['Float']['input']>;
  windowMinutes?: InputMaybe<Scalars['Int']['input']>;
};


export type QueryRecentSentimentArgs = {
  limit: Scalars['Int']['input'];
  streamer: Scalars['String']['input'];
};


export type QueryRecentSponsorSentimentArgs = {
  limit: Scalars['Int']['input'];
  sponsor?: InputMaybe<Scalars['String']['input']>;
  streamer: Scalars['String']['input'];
};


export type QueryRecentSponsorTranscriptSentimentArgs = {
  limit: Scalars['Int']['input'];
  sponsor?: InputMaybe<Scalars['String']['input']>;
  streamer: Scalars['String']['input'];
};


export type QueryRecentTranscriptSegmentsArgs = {
  limit: Scalars['Int']['input'];
  streamer: Scalars['String']['input'];
};


export type QueryRecentTranscriptSentimentArgs = {
  limit: Scalars['Int']['input'];
  streamer: Scalars['String']['input'];
};


export type QueryRecommendationsArgs = {
  limit: Scalars['Int']['input'];
  streamer: Scalars['String']['input'];
};


export type QuerySessionArgs = {
  id: Scalars['ID']['input'];
};


export type QuerySessionSummaryArgs = {
  chatCommand?: InputMaybe<Scalars['String']['input']>;
  cpmPer30sEquivalent?: InputMaybe<Scalars['Float']['input']>;
  hostReadRatePer1000?: InputMaybe<Scalars['Float']['input']>;
  sessionId: Scalars['ID']['input'];
  sponsor?: InputMaybe<Scalars['String']['input']>;
  trackedLinkHost?: InputMaybe<Scalars['String']['input']>;
};


export type QuerySessionsArgs = {
  from?: InputMaybe<Scalars['Float']['input']>;
  limit?: InputMaybe<Scalars['Int']['input']>;
  streamer: Scalars['String']['input'];
  to?: InputMaybe<Scalars['Float']['input']>;
};


export type QuerySponsorDetectionsArgs = {
  limit: Scalars['Int']['input'];
  streamer: Scalars['String']['input'];
};


export type QuerySponsorExposureMetricsArgs = {
  from?: InputMaybe<Scalars['Float']['input']>;
  sessionId?: InputMaybe<Scalars['ID']['input']>;
  streamSessionId?: InputMaybe<Scalars['String']['input']>;
  streamer: Scalars['String']['input'];
  to?: InputMaybe<Scalars['Float']['input']>;
  windowMinutes?: InputMaybe<Scalars['Int']['input']>;
};


export type QuerySponsorMomentsArgs = {
  sessionId: Scalars['ID']['input'];
  sponsor?: InputMaybe<Scalars['String']['input']>;
};


export type QueryStreamMetricsSummaryArgs = {
  from?: InputMaybe<Scalars['Float']['input']>;
  sessionId?: InputMaybe<Scalars['ID']['input']>;
  streamSessionId?: InputMaybe<Scalars['String']['input']>;
  streamer: Scalars['String']['input'];
  to?: InputMaybe<Scalars['Float']['input']>;
  windowMinutes?: InputMaybe<Scalars['Int']['input']>;
};


export type QueryStreamMetricsTimeseriesArgs = {
  bucketSeconds: Scalars['Int']['input'];
  from?: InputMaybe<Scalars['Float']['input']>;
  sessionId?: InputMaybe<Scalars['ID']['input']>;
  streamSessionId?: InputMaybe<Scalars['String']['input']>;
  streamer: Scalars['String']['input'];
  to?: InputMaybe<Scalars['Float']['input']>;
  windowMinutes?: InputMaybe<Scalars['Int']['input']>;
};

export type Recommendation = {
  category: Scalars['String']['output'];
  experimentName: Scalars['String']['output'];
  generatedAt: Scalars['Float']['output'];
  reasonSummary: Scalars['String']['output'];
  reasons: Array<Scalars['String']['output']>;
  recommendationId: Scalars['ID']['output'];
  score: Scalars['Float']['output'];
  streamer: Scalars['String']['output'];
  title: Scalars['String']['output'];
  variantId: Scalars['String']['output'];
};

export type RiskFactor = {
  name: Scalars['String']['output'];
  value: Scalars['Float']['output'];
  weight: Scalars['Float']['output'];
};

export type RiskSpike = {
  at: Scalars['Float']['output'];
  chatMessageCount: Scalars['Float']['output'];
  chatNegativeRatio?: Maybe<Scalars['Float']['output']>;
  offsetMs: Scalars['Float']['output'];
};

export type SentimentAnalysisEvent = {
  channelLogin?: Maybe<Scalars['String']['output']>;
  chatTimestamp: Scalars['Float']['output'];
  label: Scalars['String']['output'];
  matchedSponsor?: Maybe<Scalars['String']['output']>;
  matchedTerms: Array<Scalars['String']['output']>;
  message: Scalars['String']['output'];
  modelVersion: Scalars['String']['output'];
  processedAt: Scalars['Float']['output'];
  relevanceReason?: Maybe<Scalars['String']['output']>;
  relevanceScore: Scalars['Float']['output'];
  relevanceVersion?: Maybe<Scalars['String']['output']>;
  score: Scalars['Float']['output'];
  sentimentEventId: Scalars['ID']['output'];
  source?: Maybe<Scalars['String']['output']>;
  sourceEventId: Scalars['ID']['output'];
  sponsorRelevant: Scalars['Boolean']['output'];
  streamSessionId?: Maybe<Scalars['String']['output']>;
  streamer: Scalars['String']['output'];
  twitchStreamId?: Maybe<Scalars['String']['output']>;
  user: Scalars['String']['output'];
};

export type SentimentMetricSummary = {
  averageScore?: Maybe<Scalars['Float']['output']>;
  negative: Scalars['Float']['output'];
  negativeRatio?: Maybe<Scalars['Float']['output']>;
  neutral: Scalars['Float']['output'];
  positive: Scalars['Float']['output'];
};

export type SessionSummary = {
  averageViewers?: Maybe<Scalars['Float']['output']>;
  chat: ChatMetrics;
  chatMentions: Scalars['Int']['output'];
  chatSentiment: SentimentMetricSummary;
  engagement: EngagementMetrics;
  mentionNegativeShare?: Maybe<Scalars['Float']['output']>;
  mentionPositiveShare?: Maybe<Scalars['Float']['output']>;
  mentionSentiment?: Maybe<Scalars['Float']['output']>;
  mentions: Scalars['Int']['output'];
  onScreenMs: Scalars['Float']['output'];
  onScreenShare?: Maybe<Scalars['Float']['output']>;
  peakViewers?: Maybe<Scalars['Int']['output']>;
  response: DirectResponse;
  risk: BrandSafetyMetrics;
  session: StreamSession;
  sponsor?: Maybe<Scalars['String']['output']>;
  transcriptSentiment: SentimentMetricSummary;
  value: SessionValue;
  voiceMentions: Scalars['Int']['output'];
};

export type SessionValue = {
  averageProminence?: Maybe<Scalars['Float']['output']>;
  basis: Scalars['String']['output'];
  cpmPer30sEquivalent: Scalars['Float']['output'];
  hostReadRatePer1000: Scalars['Float']['output'];
  hostReadValue?: Maybe<Scalars['Float']['output']>;
  logoValue?: Maybe<Scalars['Float']['output']>;
  mediaValue?: Maybe<Scalars['Float']['output']>;
  weightedLogoViewerMinutes?: Maybe<Scalars['Float']['output']>;
};

export type SponsorDetectionEvent = {
  capturedAt: Scalars['Float']['output'];
  channelLogin?: Maybe<Scalars['String']['output']>;
  confidence: Scalars['Float']['output'];
  detectionEventId: Scalars['ID']['output'];
  frameRef: Scalars['String']['output'];
  frameSequence: Scalars['Float']['output'];
  height: Scalars['Float']['output'];
  modelVersion: Scalars['String']['output'];
  processedAt: Scalars['Float']['output'];
  source?: Maybe<Scalars['String']['output']>;
  sourceFrameId: Scalars['ID']['output'];
  sponsor: Scalars['String']['output'];
  streamSessionId?: Maybe<Scalars['String']['output']>;
  streamer: Scalars['String']['output'];
  twitchStreamId?: Maybe<Scalars['String']['output']>;
  videoTimestampMs?: Maybe<Scalars['Float']['output']>;
  width: Scalars['Float']['output'];
  x: Scalars['Float']['output'];
  y: Scalars['Float']['output'];
};

export type SponsorExposureMetric = {
  acceptedDetectionCount: Scalars['Float']['output'];
  averageConfidence?: Maybe<Scalars['Float']['output']>;
  detectionCount: Scalars['Float']['output'];
  estimatedExposureMs: Scalars['Float']['output'];
  fallbackDetectionCount: Scalars['Float']['output'];
  lowConfidenceDetectionCount: Scalars['Float']['output'];
  maxConfidence?: Maybe<Scalars['Float']['output']>;
  sponsor: Scalars['String']['output'];
};

export type SponsorExposureSummary = {
  acceptedDetections: Scalars['Float']['output'];
  estimatedExposureMs: Scalars['Float']['output'];
  topSponsors: Array<SponsorExposureMetric>;
  totalDetections: Scalars['Float']['output'];
};

export type SponsorMoments = {
  best?: Maybe<HighlightMoment>;
  chatMoments: Array<ChatMoment>;
  riskSpikes: Array<RiskSpike>;
  segments: Array<ExposureSegment>;
  session: StreamSession;
  sponsor?: Maybe<Scalars['String']['output']>;
  voiceMentions: Array<VoiceMention>;
  weakest?: Maybe<HighlightMoment>;
};

export type StreamMetricBucket = {
  bucketEnd: Scalars['Float']['output'];
  bucketStart: Scalars['Float']['output'];
  chatAverageScore?: Maybe<Scalars['Float']['output']>;
  chatMessageCount: Scalars['Float']['output'];
  chatNegativeRatio?: Maybe<Scalars['Float']['output']>;
  engagementSpike: Scalars['Boolean']['output'];
  estimatedSponsorExposureMs: Scalars['Float']['output'];
  negativeSpike: Scalars['Boolean']['output'];
  sponsorDetectionCount: Scalars['Float']['output'];
  transcriptAverageScore?: Maybe<Scalars['Float']['output']>;
  transcriptNegativeRatio?: Maybe<Scalars['Float']['output']>;
  uniqueChatters: Scalars['Float']['output'];
};

export type StreamMetricsSummary = {
  bucketSizeSeconds: Scalars['Int']['output'];
  chat: ChatMetrics;
  chatSentiment: SentimentMetricSummary;
  dataQuality: AnalyticsDataQuality;
  engagement: EngagementMetrics;
  risk: BrandSafetyMetrics;
  sponsorExposure: SponsorExposureSummary;
  streamSessionId?: Maybe<Scalars['String']['output']>;
  streamer: Scalars['String']['output'];
  transcriptSentiment: SentimentMetricSummary;
  windowEnd: Scalars['Float']['output'];
  windowMinutes: Scalars['Int']['output'];
  windowStart: Scalars['Float']['output'];
};

export type StreamSession = {
  averageViewers?: Maybe<Scalars['Float']['output']>;
  category?: Maybe<Scalars['String']['output']>;
  channelLogin?: Maybe<Scalars['String']['output']>;
  durationMs: Scalars['Float']['output'];
  endedAt?: Maybe<Scalars['Float']['output']>;
  id: Scalars['ID']['output'];
  live: Scalars['Boolean']['output'];
  peakViewers?: Maybe<Scalars['Int']['output']>;
  source: Scalars['String']['output'];
  startedAt: Scalars['Float']['output'];
  streamSessionId?: Maybe<Scalars['String']['output']>;
  streamer: Scalars['String']['output'];
  title?: Maybe<Scalars['String']['output']>;
  twitchStreamId?: Maybe<Scalars['String']['output']>;
  viewerSamples: Scalars['Int']['output'];
};

export type Subscription = {
  onChatMessage: ChatMessageEvent;
  onSentiment: SentimentAnalysisEvent;
  onSponsorDetection: SponsorDetectionEvent;
  onSponsorSentiment: SentimentAnalysisEvent;
  onSponsorTranscriptSentiment: TranscriptSentimentEvent;
  onTranscriptSegment: TranscriptSegmentEvent;
  onTranscriptSentiment: TranscriptSentimentEvent;
};


export type SubscriptionOnChatMessageArgs = {
  streamer: Scalars['String']['input'];
};


export type SubscriptionOnSentimentArgs = {
  streamer: Scalars['String']['input'];
};


export type SubscriptionOnSponsorDetectionArgs = {
  streamer: Scalars['String']['input'];
};


export type SubscriptionOnSponsorSentimentArgs = {
  sponsor?: InputMaybe<Scalars['String']['input']>;
  streamer: Scalars['String']['input'];
};


export type SubscriptionOnSponsorTranscriptSentimentArgs = {
  sponsor?: InputMaybe<Scalars['String']['input']>;
  streamer: Scalars['String']['input'];
};


export type SubscriptionOnTranscriptSegmentArgs = {
  streamer: Scalars['String']['input'];
};


export type SubscriptionOnTranscriptSentimentArgs = {
  streamer: Scalars['String']['input'];
};

export type TranscriptSegmentEvent = {
  captureWorkerId?: Maybe<Scalars['String']['output']>;
  channelLogin?: Maybe<Scalars['String']['output']>;
  confidence?: Maybe<Scalars['Float']['output']>;
  endedAt: Scalars['Float']['output'];
  language?: Maybe<Scalars['String']['output']>;
  modelVersion: Scalars['String']['output'];
  segmentId: Scalars['ID']['output'];
  source: Scalars['String']['output'];
  startedAt: Scalars['Float']['output'];
  streamSessionId: Scalars['String']['output'];
  streamer: Scalars['String']['output'];
  text: Scalars['String']['output'];
  transcriptSequence: Scalars['Float']['output'];
  twitchStreamId?: Maybe<Scalars['String']['output']>;
  videoTimestampMs: Scalars['Float']['output'];
};

export type TranscriptSentimentEvent = {
  label: Scalars['String']['output'];
  matchedSponsor?: Maybe<Scalars['String']['output']>;
  matchedTerms: Array<Scalars['String']['output']>;
  modelVersion: Scalars['String']['output'];
  processedAt: Scalars['Float']['output'];
  relevanceReason?: Maybe<Scalars['String']['output']>;
  relevanceScore: Scalars['Float']['output'];
  relevanceVersion?: Maybe<Scalars['String']['output']>;
  score: Scalars['Float']['output'];
  segmentEndedAt: Scalars['Float']['output'];
  segmentId: Scalars['ID']['output'];
  segmentStartedAt: Scalars['Float']['output'];
  sentimentEventId: Scalars['ID']['output'];
  sponsorRelevant: Scalars['Boolean']['output'];
  streamSessionId: Scalars['String']['output'];
  streamer: Scalars['String']['output'];
  text: Scalars['String']['output'];
  transcriptModelVersion: Scalars['String']['output'];
  transcriptSequence: Scalars['Float']['output'];
};

export type VoiceMention = {
  at: Scalars['Float']['output'];
  label: Scalars['String']['output'];
  offsetMs: Scalars['Float']['output'];
  score: Scalars['Float']['output'];
  sentimentEventId: Scalars['ID']['output'];
  text: Scalars['String']['output'];
};

export type HealthQueryVariables = Exact<{ [key: string]: never; }>;


export type HealthQuery = { health: string };

export type RecentSentimentQueryVariables = Exact<{
  streamer: string;
  limit: number;
}>;


export type RecentSentimentQuery = { recentSentiment: Array<{ sentimentEventId: string, sourceEventId: string, streamer: string, user: string, message: string, chatTimestamp: number, processedAt: number, label: string, score: number, modelVersion: string, sponsorRelevant: boolean, matchedSponsor: string | null, matchedTerms: Array<string>, relevanceScore: number, relevanceReason: string | null, relevanceVersion: string | null }> };

export type RecentSponsorSentimentQueryVariables = Exact<{
  streamer: string;
  sponsor?: string | null | undefined;
  limit: number;
}>;


export type RecentSponsorSentimentQuery = { recentSponsorSentiment: Array<{ sentimentEventId: string, sourceEventId: string, streamer: string, user: string, message: string, chatTimestamp: number, processedAt: number, label: string, score: number, modelVersion: string, sponsorRelevant: boolean, matchedSponsor: string | null, matchedTerms: Array<string>, relevanceScore: number, relevanceReason: string | null, relevanceVersion: string | null }> };

export type RecentTranscriptSegmentsQueryVariables = Exact<{
  streamer: string;
  limit: number;
}>;


export type RecentTranscriptSegmentsQuery = { recentTranscriptSegments: Array<{ segmentId: string, streamer: string, text: string, startedAt: number, endedAt: number, language: string | null, confidence: number | null, modelVersion: string, source: string, channelLogin: string | null, streamSessionId: string, videoTimestampMs: number, transcriptSequence: number, captureWorkerId: string | null }> };

export type RecentTranscriptSentimentQueryVariables = Exact<{
  streamer: string;
  limit: number;
}>;


export type RecentTranscriptSentimentQuery = { recentTranscriptSentiment: Array<{ sentimentEventId: string, segmentId: string, streamer: string, text: string, segmentStartedAt: number, segmentEndedAt: number, processedAt: number, label: string, score: number, modelVersion: string, transcriptModelVersion: string, streamSessionId: string, transcriptSequence: number, sponsorRelevant: boolean, matchedSponsor: string | null, matchedTerms: Array<string>, relevanceScore: number, relevanceReason: string | null, relevanceVersion: string | null }> };

export type RecentSponsorTranscriptSentimentQueryVariables = Exact<{
  streamer: string;
  sponsor?: string | null | undefined;
  limit: number;
}>;


export type RecentSponsorTranscriptSentimentQuery = { recentSponsorTranscriptSentiment: Array<{ sentimentEventId: string, segmentId: string, streamer: string, text: string, segmentStartedAt: number, segmentEndedAt: number, processedAt: number, label: string, score: number, modelVersion: string, transcriptModelVersion: string, streamSessionId: string, transcriptSequence: number, sponsorRelevant: boolean, matchedSponsor: string | null, matchedTerms: Array<string>, relevanceScore: number, relevanceReason: string | null, relevanceVersion: string | null }> };

export type SponsorDetectionsQueryVariables = Exact<{
  streamer: string;
  limit: number;
}>;


export type SponsorDetectionsQuery = { sponsorDetections: Array<{ detectionEventId: string, sourceFrameId: string, streamer: string, frameRef: string, frameSequence: number, capturedAt: number, processedAt: number, sponsor: string, confidence: number, modelVersion: string, x: number, y: number, width: number, height: number, source: string | null, channelLogin: string | null, streamSessionId: string | null, twitchStreamId: string | null, videoTimestampMs: number | null }> };

export type StreamAnalyticsQueryVariables = Exact<{
  streamer: string;
  windowMinutes: number;
  bucketSeconds: number;
}>;


export type StreamAnalyticsQuery = { streamMetricsSummary: { streamer: string, streamSessionId: string | null, windowMinutes: number, bucketSizeSeconds: number, windowStart: number, windowEnd: number, chat: { totalMessages: number, messagesPerMinute: number, uniqueChatters: number, peakMessagesPerMinute: number }, chatSentiment: { positive: number, neutral: number, negative: number, averageScore: number | null, negativeRatio: number | null }, transcriptSentiment: { positive: number, neutral: number, negative: number, averageScore: number | null, negativeRatio: number | null }, sponsorExposure: { totalDetections: number, acceptedDetections: number, estimatedExposureMs: number, topSponsors: Array<{ sponsor: string, detectionCount: number, acceptedDetectionCount: number, estimatedExposureMs: number, averageConfidence: number | null, maxConfidence: number | null, fallbackDetectionCount: number, lowConfidenceDetectionCount: number }> }, engagement: { spikeCount: number, latestSpikeAt: number | null }, risk: { level: string, score: number | null, factors: Array<{ name: string, value: number, weight: number }> }, dataQuality: { lowData: boolean, latestEventAt: number | null, aggregationLagMs: number | null } }, streamMetricsTimeseries: Array<{ bucketStart: number, bucketEnd: number, chatMessageCount: number, uniqueChatters: number, chatAverageScore: number | null, chatNegativeRatio: number | null, transcriptAverageScore: number | null, transcriptNegativeRatio: number | null, sponsorDetectionCount: number, estimatedSponsorExposureMs: number, engagementSpike: boolean, negativeSpike: boolean }> };

export type OnChatMessageSubscriptionVariables = Exact<{
  streamer: string;
}>;


export type OnChatMessageSubscription = { onChatMessage: { eventId: string, streamer: string, user: string, message: string, timestamp: number } };

export type OnSentimentSubscriptionVariables = Exact<{
  streamer: string;
}>;


export type OnSentimentSubscription = { onSentiment: { sentimentEventId: string, sourceEventId: string, streamer: string, user: string, message: string, chatTimestamp: number, processedAt: number, label: string, score: number, modelVersion: string, sponsorRelevant: boolean, matchedSponsor: string | null, matchedTerms: Array<string>, relevanceScore: number, relevanceReason: string | null, relevanceVersion: string | null } };

export type OnSponsorSentimentSubscriptionVariables = Exact<{
  streamer: string;
  sponsor?: string | null | undefined;
}>;


export type OnSponsorSentimentSubscription = { onSponsorSentiment: { sentimentEventId: string, sourceEventId: string, streamer: string, user: string, message: string, chatTimestamp: number, processedAt: number, label: string, score: number, modelVersion: string, sponsorRelevant: boolean, matchedSponsor: string | null, matchedTerms: Array<string>, relevanceScore: number, relevanceReason: string | null, relevanceVersion: string | null } };

export type OnTranscriptSegmentSubscriptionVariables = Exact<{
  streamer: string;
}>;


export type OnTranscriptSegmentSubscription = { onTranscriptSegment: { segmentId: string, streamer: string, text: string, startedAt: number, endedAt: number, language: string | null, confidence: number | null, modelVersion: string, source: string, channelLogin: string | null, streamSessionId: string, videoTimestampMs: number, transcriptSequence: number, captureWorkerId: string | null } };

export type OnTranscriptSentimentSubscriptionVariables = Exact<{
  streamer: string;
}>;


export type OnTranscriptSentimentSubscription = { onTranscriptSentiment: { sentimentEventId: string, segmentId: string, streamer: string, text: string, segmentStartedAt: number, segmentEndedAt: number, processedAt: number, label: string, score: number, modelVersion: string, transcriptModelVersion: string, streamSessionId: string, transcriptSequence: number, sponsorRelevant: boolean, matchedSponsor: string | null, matchedTerms: Array<string>, relevanceScore: number, relevanceReason: string | null, relevanceVersion: string | null } };

export type OnSponsorTranscriptSentimentSubscriptionVariables = Exact<{
  streamer: string;
  sponsor?: string | null | undefined;
}>;


export type OnSponsorTranscriptSentimentSubscription = { onSponsorTranscriptSentiment: { sentimentEventId: string, segmentId: string, streamer: string, text: string, segmentStartedAt: number, segmentEndedAt: number, processedAt: number, label: string, score: number, modelVersion: string, transcriptModelVersion: string, streamSessionId: string, transcriptSequence: number, sponsorRelevant: boolean, matchedSponsor: string | null, matchedTerms: Array<string>, relevanceScore: number, relevanceReason: string | null, relevanceVersion: string | null } };

export type OnSponsorDetectionSubscriptionVariables = Exact<{
  streamer: string;
}>;


export type OnSponsorDetectionSubscription = { onSponsorDetection: { detectionEventId: string, sourceFrameId: string, streamer: string, frameRef: string, frameSequence: number, capturedAt: number, processedAt: number, sponsor: string, confidence: number, modelVersion: string, x: number, y: number, width: number, height: number, source: string | null, channelLogin: string | null, streamSessionId: string | null, twitchStreamId: string | null, videoTimestampMs: number | null } };
