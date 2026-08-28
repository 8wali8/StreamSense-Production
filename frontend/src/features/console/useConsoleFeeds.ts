import { getRecentTranscriptSegments } from "../../api/sentiment";
import type {
  OnChatMessageSubscription,
  OnSentimentSubscription,
  OnSponsorDetectionSubscription,
  OnSponsorSentimentSubscription,
  OnSponsorTranscriptSentimentSubscription,
  OnTranscriptSegmentSubscription,
  OnTranscriptSentimentSubscription,
  RecentSentimentQuery,
  RecentSentimentQueryVariables,
  RecentSponsorSentimentQuery,
  RecentSponsorSentimentQueryVariables,
  RecentSponsorTranscriptSentimentQuery,
  RecentSponsorTranscriptSentimentQueryVariables,
  RecentTranscriptSegmentsQuery,
  RecentTranscriptSegmentsQueryVariables,
  RecentTranscriptSentimentQuery,
  RecentTranscriptSentimentQueryVariables,
  SponsorDetectionsQuery,
  SponsorDetectionsQueryVariables,
} from "../../graphql/generated";
import {
  RECENT_SENTIMENT_QUERY,
  RECENT_SPONSOR_DETECTIONS_QUERY,
  RECENT_SPONSOR_SENTIMENT_QUERY,
  RECENT_SPONSOR_TRANSCRIPT_SENTIMENT_QUERY,
  RECENT_TRANSCRIPT_SEGMENTS_QUERY,
  RECENT_TRANSCRIPT_SENTIMENT_QUERY,
} from "../../graphql/queries";
import {
  ON_CHAT_MESSAGE_SUBSCRIPTION,
  ON_SENTIMENT_SUBSCRIPTION,
  ON_SPONSOR_DETECTION_SUBSCRIPTION,
  ON_SPONSOR_SENTIMENT_SUBSCRIPTION,
  ON_SPONSOR_TRANSCRIPT_SENTIMENT_SUBSCRIPTION,
  ON_TRANSCRIPT_SEGMENT_SUBSCRIPTION,
  ON_TRANSCRIPT_SENTIMENT_SUBSCRIPTION,
} from "../../graphql/subscriptions";
import { useLiveEvents, useLiveFeed } from "../../hooks/useLiveFeed";
import { usePolledResource } from "../../hooks/usePolledResource";
import { mergeById } from "../../lib/format";
import { buildTranscriptFeed, type TranscriptLine } from "./transcript-lines";

export type SponsorDetectionEvent = SponsorDetectionsQuery["sponsorDetections"][number];
export type TranscriptSegmentEvent = RecentTranscriptSegmentsQuery["recentTranscriptSegments"][number];
export type ChatMessageEvent = OnChatMessageSubscription["onChatMessage"];
export type SentimentEvent = RecentSentimentQuery["recentSentiment"][number];
export type TranscriptSentimentEvent = RecentTranscriptSentimentQuery["recentTranscriptSentiment"][number];

const POLL_MS = 10000;

export type ConsoleFeeds = {
  sponsors: SponsorDetectionEvent[];
  transcriptFeed: TranscriptLine<TranscriptSentimentEvent>[];
  transcript: { loading: boolean; error: string | undefined };
  chatSentiments: SentimentEvent[];
  chatSentimentLoading: boolean;
  liveChat: ChatMessageEvent[];
  sponsorSentiments: SentimentEvent[];
  sponsorTranscriptSentiments: TranscriptSentimentEvent[];
  sponsorSentimentLoading: boolean;
  latestEventAt: number | undefined;
};

/** Every live and historical signal the console shows for one streamer and sponsor. */
export function useConsoleFeeds(streamer: string, activeSponsor: string): ConsoleFeeds {
  const forStreamer = (event: { streamer: string }) => event.streamer === streamer;

  const sponsors = useLiveFeed<SponsorDetectionsQuery, OnSponsorDetectionSubscription, SponsorDetectionsQueryVariables, SponsorDetectionEvent>({
    query: RECENT_SPONSOR_DETECTIONS_QUERY,
    variables: { streamer, limit: 12 },
    selectHistory: (data) => data.sponsorDetections,
    subscription: ON_SPONSOR_DETECTION_SUBSCRIPTION,
    subscriptionVariables: { streamer },
    selectEvent: (data) => data.onSponsorDetection,
    getId: (event) => event.detectionEventId,
    limit: 20,
    accept: forStreamer,
    resetKey: streamer,
  });

  const transcripts = useLiveFeed<RecentTranscriptSegmentsQuery, OnTranscriptSegmentSubscription, RecentTranscriptSegmentsQueryVariables, TranscriptSegmentEvent>({
    query: RECENT_TRANSCRIPT_SEGMENTS_QUERY,
    variables: { streamer, limit: 10 },
    pollInterval: POLL_MS,
    selectHistory: (data) => data.recentTranscriptSegments,
    subscription: ON_TRANSCRIPT_SEGMENT_SUBSCRIPTION,
    subscriptionVariables: { streamer },
    selectEvent: (data) => data.onTranscriptSegment,
    getId: (event) => event.segmentId,
    limit: 16,
    accept: forStreamer,
    resetKey: streamer,
  });
  // REST fallback for transcript history, so the feed survives a GraphQL hiccup.
  const restTranscript = usePolledResource(() => getRecentTranscriptSegments(streamer, 10), POLL_MS, streamer);
  const transcriptSegments = mergeById(transcripts.items, restTranscript.data ?? [], (event) => event.segmentId, 16);

  const chatSentiments = useLiveFeed<RecentSentimentQuery, OnSentimentSubscription, RecentSentimentQueryVariables, SentimentEvent>({
    query: RECENT_SENTIMENT_QUERY,
    variables: { streamer, limit: 12 },
    selectHistory: (data) => data.recentSentiment,
    subscription: ON_SENTIMENT_SUBSCRIPTION,
    subscriptionVariables: { streamer },
    selectEvent: (data) => data.onSentiment,
    getId: (event) => event.sentimentEventId,
    limit: 16,
    accept: forStreamer,
    resetKey: streamer,
  });

  const transcriptSentiments = useLiveFeed<RecentTranscriptSentimentQuery, OnTranscriptSentimentSubscription, RecentTranscriptSentimentQueryVariables, TranscriptSentimentEvent>({
    query: RECENT_TRANSCRIPT_SENTIMENT_QUERY,
    variables: { streamer, limit: 10 },
    pollInterval: POLL_MS,
    selectHistory: (data) => data.recentTranscriptSentiment,
    subscription: ON_TRANSCRIPT_SENTIMENT_SUBSCRIPTION,
    subscriptionVariables: { streamer },
    selectEvent: (data) => data.onTranscriptSentiment,
    getId: (event) => event.sentimentEventId,
    limit: 16,
    accept: forStreamer,
    resetKey: streamer,
  });

  const sponsorSentiments = useLiveFeed<RecentSponsorSentimentQuery, OnSponsorSentimentSubscription, RecentSponsorSentimentQueryVariables, SentimentEvent>({
    query: RECENT_SPONSOR_SENTIMENT_QUERY,
    variables: { streamer, sponsor: activeSponsor, limit: 12 },
    selectHistory: (data) => data.recentSponsorSentiment,
    subscription: ON_SPONSOR_SENTIMENT_SUBSCRIPTION,
    subscriptionVariables: { streamer, sponsor: activeSponsor },
    selectEvent: (data) => data.onSponsorSentiment,
    getId: (event) => event.sentimentEventId,
    limit: 12,
    accept: forStreamer,
    resetKey: streamer,
  });

  const sponsorTranscriptSentiments = useLiveFeed<
    RecentSponsorTranscriptSentimentQuery,
    OnSponsorTranscriptSentimentSubscription,
    RecentSponsorTranscriptSentimentQueryVariables,
    TranscriptSentimentEvent
  >({
    query: RECENT_SPONSOR_TRANSCRIPT_SENTIMENT_QUERY,
    variables: { streamer, sponsor: activeSponsor, limit: 10 },
    selectHistory: (data) => data.recentSponsorTranscriptSentiment,
    subscription: ON_SPONSOR_TRANSCRIPT_SENTIMENT_SUBSCRIPTION,
    subscriptionVariables: { streamer, sponsor: activeSponsor },
    selectEvent: (data) => data.onSponsorTranscriptSentiment,
    getId: (event) => event.sentimentEventId,
    limit: 10,
    accept: forStreamer,
    resetKey: streamer,
  });

  const chat = useLiveEvents<OnChatMessageSubscription, ChatMessageEvent>({
    subscription: ON_CHAT_MESSAGE_SUBSCRIPTION,
    variables: { streamer },
    selectEvent: (data) => data.onChatMessage,
    getId: (event) => event.eventId,
    limit: 14,
    accept: forStreamer,
    resetKey: streamer,
  });

  const transcriptFeed = buildTranscriptFeed(transcriptSegments, transcriptSentiments.items, sponsorTranscriptSentiments.items, 16);
  const latestFrame = sponsors.items.find((event) => event.frameRef);
  const latestEventAt = latestFrame?.capturedAt ?? transcriptSentiments.items[0]?.processedAt ?? chat.live[0]?.timestamp;

  const transcriptError = transcripts.error?.message || transcriptSentiments.error?.message;
  return {
    sponsors: sponsors.items,
    transcriptFeed,
    transcript: {
      loading: transcripts.loading || transcriptSentiments.loading,
      error: transcriptError ? `${transcriptError}${restTranscript.error ? `; ${restTranscript.error}` : ""}` : undefined,
    },
    chatSentiments: chatSentiments.items,
    chatSentimentLoading: chatSentiments.loading,
    liveChat: chat.live,
    sponsorSentiments: sponsorSentiments.items,
    sponsorTranscriptSentiments: sponsorTranscriptSentiments.items,
    sponsorSentimentLoading: sponsorSentiments.loading || sponsorTranscriptSentiments.loading,
    latestEventAt,
  };
}
