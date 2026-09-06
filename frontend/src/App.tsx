import { useEffect, useState } from "react";
import { useQuery, useSubscription } from "@apollo/client/react";
import { Health } from "./components/Health";
import { RecommendationPanel } from "./components/RecommendationPanel";
import { SegmentationPreview } from "./components/SegmentationPreview";
import { SentimentPanel } from "./components/SentimentPanel";
import { SponsorPanel } from "./components/SponsorPanel";
import { StreamMetricsOverview } from "./components/StreamMetricsOverview";
import { TwitchIngestionStatus } from "./components/TwitchIngestionStatus";
import { VideoCaptureStatus } from "./components/VideoCaptureStatus";
import {
  RECENT_SENTIMENT_QUERY,
  RECENT_SPONSOR_SENTIMENT_QUERY,
  RECENT_SPONSOR_TRANSCRIPT_SENTIMENT_QUERY,
  RECENT_SPONSOR_DETECTIONS_QUERY,
  RECENT_TRANSCRIPT_SEGMENTS_QUERY,
  RECENT_TRANSCRIPT_SENTIMENT_QUERY,
} from "./graphql/queries";
import {
  ON_CHAT_MESSAGE_SUBSCRIPTION,
  ON_SENTIMENT_SUBSCRIPTION,
  ON_SPONSOR_DETECTION_SUBSCRIPTION,
  ON_SPONSOR_SENTIMENT_SUBSCRIPTION,
  ON_SPONSOR_TRANSCRIPT_SENTIMENT_SUBSCRIPTION,
  ON_TRANSCRIPT_SEGMENT_SUBSCRIPTION,
  ON_TRANSCRIPT_SENTIMENT_SUBSCRIPTION,
} from "./graphql/subscriptions";
import type {
  OnChatMessageSubscription,
  OnChatMessageSubscriptionVariables,
  OnSentimentSubscription,
  OnSentimentSubscriptionVariables,
  OnSponsorDetectionSubscription,
  OnSponsorDetectionSubscriptionVariables,
  OnSponsorSentimentSubscription,
  OnSponsorSentimentSubscriptionVariables,
  OnSponsorTranscriptSentimentSubscription,
  OnSponsorTranscriptSentimentSubscriptionVariables,
  OnTranscriptSegmentSubscription,
  OnTranscriptSegmentSubscriptionVariables,
  OnTranscriptSentimentSubscription,
  OnTranscriptSentimentSubscriptionVariables,
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
} from "./graphql/generated";

type SponsorDetectionEvent = SponsorDetectionsQuery["sponsorDetections"][number];
type TranscriptSegmentEvent = RecentTranscriptSegmentsQuery["recentTranscriptSegments"][number];
type ChatMessageEvent = OnChatMessageSubscription["onChatMessage"];
type SentimentEvent = RecentSentimentQuery["recentSentiment"][number];
type TranscriptSentimentEvent = RecentTranscriptSentimentQuery["recentTranscriptSentiment"][number];

type PortfolioStreamer = {
  handle: string;
  brand: string;
  owner: string;
  risk: string;
};

type RestTranscriptState = {
  streamer: string;
  segments: TranscriptSegmentEvent[];
  error: string | null;
};

const portfolioStreamers: PortfolioStreamer[] = [
  { handle: "test", brand: "Nike", owner: "Demo channel", risk: "Low" },
  { handle: "speedrun-lab", brand: "Prime", owner: "Speedrun lab", risk: "Watch" },
  { handle: "arena-night", brand: "Razer", owner: "Arena night", risk: "Medium" },
];

function formatTime(ts?: number | null): string {
  if (!ts) return "--:--:--";
  return new Date(ts).toLocaleTimeString();
}

function mergeById<T>(liveItems: T[], historyItems: T[], getId: (item: T) => string, limit: number): T[] {
  const seen = new Set<string>();
  const merged: T[] = [];

  for (const item of [...liveItems, ...historyItems]) {
    const id = getId(item);
    if (seen.has(id)) continue;
    seen.add(id);
    merged.push(item);
  }

  return merged.slice(0, limit);
}

function percent(value: number): string {
  const normalized = value <= 1 ? value * 100 : value;
  return `${Math.max(0, Math.min(100, normalized))}%`;
}

function sentimentClass(label?: string): string {
  const normalized = label?.toLowerCase();
  if (normalized === "positive") return "analysis-positive";
  if (normalized === "negative") return "analysis-negative";
  return "analysis-neutral";
}

function formatScore(value?: number | null): string {
  return typeof value === "number" && Number.isFinite(value) ? value.toFixed(2) : "--";
}

function matchedContext(
  event: { matchedTerms?: string[] | null; matchedSponsor?: string | null },
  fallback: string,
): string {
  const terms = Array.isArray(event.matchedTerms) ? event.matchedTerms.filter(Boolean) : [];
  return terms.join(", ") || event.matchedSponsor || fallback || "sponsor context";
}

const replayVodByStreamer: Record<string, string> = {
  "redbull-testing": "2750461300",
};

function twitchPlayerUrl(streamer: string): string {
  const channel = normalizeStreamerHandle(streamer);
  const parent = encodeURIComponent(window.location.hostname || "localhost");
  const replayVodId = replayVodByStreamer[channel];
  if (replayVodId) {
    return `https://player.twitch.tv/?video=${encodeURIComponent(replayVodId)}&parent=${parent}&muted=true&autoplay=true`;
  }
  return `https://player.twitch.tv/?channel=${encodeURIComponent(channel)}&parent=${parent}&muted=true&autoplay=true`;
}

function normalizeStreamerHandle(streamer: string): string {
  return streamer.trim().toLowerCase().replace(/^[@#]+/, "");
}

async function switchRuntimeChannels(path: string, streamer: string): Promise<void> {
  const normalizedStreamer = normalizeStreamerHandle(streamer);
  const response = await fetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ channels: [normalizedStreamer] }),
  });
  if (!response.ok) {
    throw new Error(`${path} returned ${response.status}`);
  }
}

function sponsorProfileFromInput(streamer: string, sponsorInput: string, campaignGoal: string) {
  const normalizedStreamer = normalizeStreamerHandle(streamer);
  const parts = sponsorInput.split(",").map((part) => part.trim()).filter(Boolean);
  const sponsor = parts[0] || sponsorInput.trim();
  const semanticTerms = parts.slice(1);
  return {
    streamer: normalizedStreamer,
    sponsor,
    aliases: [] as string[],
    semanticTerms,
    campaignGoal,
  };
}

async function updateSponsorRelevance(streamer: string, sponsorInput: string, campaignGoal: string): Promise<void> {
  const profile = sponsorProfileFromInput(streamer, sponsorInput, campaignGoal);
  if (!profile.sponsor) return;
  const response = await fetch("/api/sentiment/relevance/sponsors", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(profile),
  });
  if (!response.ok) {
    throw new Error(`/api/sentiment/relevance/sponsors returned ${response.status}`);
  }
}

export default function App() {
  const [streamerInput, setStreamerInput] = useState("test");
  const [selectedStreamer, setSelectedStreamer] = useState("test");
  const [sponsorBrand, setSponsorBrand] = useState("Nike");
  const [campaignGoal, setCampaignGoal] = useState("Launch-week brand lift");
  const [channelSwitchStatus, setChannelSwitchStatus] = useState("Runtime capture follows the streamer field.");

  async function analyzeStreamer() {
    const nextStreamer = normalizeStreamerHandle(streamerInput);
    if (!nextStreamer) return;

    await loadStreamer(nextStreamer, sponsorBrand);
  }

  async function selectPortfolioStreamer(streamer: PortfolioStreamer) {
    const nextStreamer = normalizeStreamerHandle(streamer.handle);
    setStreamerInput(nextStreamer);
    setSponsorBrand(streamer.brand);
    await loadStreamer(nextStreamer, streamer.brand);
  }

  async function loadStreamer(streamer: string, brand: string) {
    const nextStreamer = normalizeStreamerHandle(streamer);
    const isSameStreamer = nextStreamer === selectedStreamer;
    setSelectedStreamer(nextStreamer);
    setSponsorBrand(brand);
    setChannelSwitchStatus(isSameStreamer ? `Updating sponsor relevance for @${nextStreamer}...` : `Switching Twitch ingest to @${nextStreamer}...`);

    const runtimeUpdates = isSameStreamer
      ? [updateSponsorRelevance(nextStreamer, brand, campaignGoal)]
      : [
          switchRuntimeChannels("/api/chat/twitch/channels", nextStreamer),
          switchRuntimeChannels("/api/video/capture/channels", nextStreamer),
          updateSponsorRelevance(nextStreamer, brand, campaignGoal),
        ];
    const results = await Promise.allSettled(runtimeUpdates);
    const failures = results.filter((result) => result.status === "rejected");
    if (failures.length > 0) {
      setChannelSwitchStatus(`Loaded @${nextStreamer}; ${failures.length} runtime update failed. Check service status pills.`);
      return;
    }

    setChannelSwitchStatus(
      isSameStreamer
        ? `Sponsor relevance updated for @${nextStreamer}; existing replay capture was left running.`
        : `Chat, video frames, transcript capture, and sponsor relevance are pointed at @${nextStreamer}.`,
    );
  }

  const selectedPortfolio = portfolioStreamers.find((streamer) => streamer.handle === selectedStreamer);
  const displayBrand = sponsorBrand.trim() || selectedPortfolio?.brand || "Sponsor";

  return (
    <div className="app-shell">
      <aside className="sidebar" aria-label="Primary navigation">
        <div className="brand-lockup">
          <div className="brand-mark">SS</div>
          <div>
            <div className="brand-name">StreamSense</div>
            <div className="brand-kicker">Live sponsor ops</div>
          </div>
        </div>

        <nav className="nav-stack">
          <a className="nav-item nav-item-active" href="#console">Live console</a>
          <a className="nav-item" href="#metrics">Metrics</a>
          <a className="nav-item" href="#evidence">Evidence</a>
          <a className="nav-item" href="#roster">Roster</a>
        </nav>

        <div className="sidebar-card">
          <span className="field-label">Active review</span>
          <strong>@{selectedStreamer}</strong>
          <span>{displayBrand} / {campaignGoal || "No campaign goal"}</span>
        </div>
      </aside>

      <main className="main-stage">
        <header className="command-panel" id="console">
          <div>
            <div className="eyebrow">Twitch sponsor monitoring</div>
            <h1>Live stream console</h1>
            <p>Watch captured stream frames, chat, transcript, sponsor detections, and risk signals in the same operating view.</p>
          </div>

          <div className="command-status-row">
            <Health />
            <TwitchIngestionStatus />
            <VideoCaptureStatus />
          </div>

          <div className="command-form" aria-label="Stream analysis controls">
            <label>
              <span className="field-label">Streamer</span>
              <input
                className="text-input"
                value={streamerInput}
                onChange={(event) => setStreamerInput(event.target.value)}
                placeholder="e.g. shroud"
              />
            </label>
            <label>
              <span className="field-label">Sponsor</span>
              <input
                className="text-input"
                value={sponsorBrand}
                onChange={(event) => setSponsorBrand(event.target.value)}
                placeholder="Nike, Prime, Razer"
              />
            </label>
            <label>
              <span className="field-label">Goal</span>
              <input
                className="text-input"
                value={campaignGoal}
                onChange={(event) => setCampaignGoal(event.target.value)}
                placeholder="Brand lift, renewal, risk review"
              />
            </label>
            <button className="button-primary" onClick={analyzeStreamer}>Load Console</button>
          </div>

          <div className="runtime-channel-note">{channelSwitchStatus}</div>
        </header>

        <LiveStreamConsole
          streamer={selectedStreamer}
          sponsorBrand={displayBrand}
          campaignGoal={campaignGoal}
        />

        <section className="metrics-section" id="metrics">
          <StreamMetricsOverview streamer={selectedStreamer} />
        </section>

        <section className="evidence-grid" id="evidence">
          <SentimentPanel streamer={selectedStreamer} hideControls />
          <SponsorPanel streamer={selectedStreamer} hideControls />
          <RecommendationPanel streamer={selectedStreamer} hideControls />
        </section>

        <section className="roster-section" id="roster">
          <div className="section-heading">
            <div>
              <div className="eyebrow">Watched channels</div>
              <h2>Quick-switch roster</h2>
            </div>
            <span className="status-pill">{portfolioStreamers.length} saved</span>
          </div>
          <div className="portfolio-grid">
            {portfolioStreamers.map((streamer) => (
              <button
                className={`portfolio-card${streamer.handle === selectedStreamer ? " portfolio-card-active" : ""}`}
                key={streamer.handle}
                onClick={() => selectPortfolioStreamer(streamer)}
              >
                <span>@{streamer.handle}</span>
                <strong>{streamer.brand}</strong>
                <div className="portfolio-meta">
                  <span>{streamer.owner}</span>
                  <span>Risk: {streamer.risk}</span>
                </div>
              </button>
            ))}
          </div>
        </section>
      </main>
    </div>
  );
}

function LiveStreamConsole({ streamer, sponsorBrand, campaignGoal }: { streamer: string; sponsorBrand: string; campaignGoal: string }) {
  const [liveSponsors, setLiveSponsors] = useState<SponsorDetectionEvent[]>([]);
  const [liveTranscripts, setLiveTranscripts] = useState<TranscriptSegmentEvent[]>([]);
  const [liveChat, setLiveChat] = useState<ChatMessageEvent[]>([]);
  const [liveSentiment, setLiveSentiment] = useState<SentimentEvent[]>([]);
  const [liveTranscriptSentiment, setLiveTranscriptSentiment] = useState<TranscriptSentimentEvent[]>([]);
  const [liveSponsorSentiment, setLiveSponsorSentiment] = useState<SentimentEvent[]>([]);
  const [liveSponsorTranscriptSentiment, setLiveSponsorTranscriptSentiment] = useState<TranscriptSentimentEvent[]>([]);
  const [restTranscriptState, setRestTranscriptState] = useState<RestTranscriptState>({ streamer: "", segments: [], error: null });
  const activeSponsor = sponsorProfileFromInput(streamer, sponsorBrand, campaignGoal).sponsor;

  useEffect(() => {
    let cancelled = false;
    const loadTranscriptFallback = () => {
      fetch(`/api/sentiment/transcript/recent?streamer=${encodeURIComponent(streamer)}&limit=10`)
        .then((response) => {
          if (!response.ok) {
            throw new Error(`/api/sentiment/transcript/recent returned ${response.status}`);
          }
          return response.json() as Promise<TranscriptSegmentEvent[]>;
        })
        .then((segments) => {
          if (!cancelled) {
            setRestTranscriptState({ streamer, segments: Array.isArray(segments) ? segments : [], error: null });
          }
        })
        .catch((error: Error) => {
          if (!cancelled) {
            setRestTranscriptState({ streamer, segments: [], error: error.message });
          }
        });
    };

    loadTranscriptFallback();
    const intervalId = window.setInterval(loadTranscriptFallback, 10000);

    return () => {
      cancelled = true;
      window.clearInterval(intervalId);
    };
  }, [streamer]);

  const sponsorQuery = useQuery<SponsorDetectionsQuery, SponsorDetectionsQueryVariables>(RECENT_SPONSOR_DETECTIONS_QUERY, {
    variables: { streamer, limit: 12 },
    fetchPolicy: "network-only",
  });
  const transcriptQuery = useQuery<RecentTranscriptSegmentsQuery, RecentTranscriptSegmentsQueryVariables>(RECENT_TRANSCRIPT_SEGMENTS_QUERY, {
    variables: { streamer, limit: 10 },
    fetchPolicy: "network-only",
    pollInterval: 10000,
  });
  const sentimentQuery = useQuery<RecentSentimentQuery, RecentSentimentQueryVariables>(RECENT_SENTIMENT_QUERY, {
    variables: { streamer, limit: 12 },
    fetchPolicy: "network-only",
  });
  const transcriptSentimentQuery = useQuery<RecentTranscriptSentimentQuery, RecentTranscriptSentimentQueryVariables>(RECENT_TRANSCRIPT_SENTIMENT_QUERY, {
    variables: { streamer, limit: 10 },
    fetchPolicy: "network-only",
    pollInterval: 10000,
  });
  const sponsorSentimentQuery = useQuery<RecentSponsorSentimentQuery, RecentSponsorSentimentQueryVariables>(RECENT_SPONSOR_SENTIMENT_QUERY, {
    variables: { streamer, sponsor: activeSponsor, limit: 12 },
    fetchPolicy: "network-only",
  });
  const sponsorTranscriptSentimentQuery = useQuery<RecentSponsorTranscriptSentimentQuery, RecentSponsorTranscriptSentimentQueryVariables>(RECENT_SPONSOR_TRANSCRIPT_SENTIMENT_QUERY, {
    variables: { streamer, sponsor: activeSponsor, limit: 10 },
    fetchPolicy: "network-only",
  });

  const sponsorHistory = sponsorQuery.data?.sponsorDetections ?? [];
  const restTranscriptHistory = restTranscriptState.streamer === streamer ? restTranscriptState.segments : [];
  const restTranscriptError = restTranscriptState.streamer === streamer ? restTranscriptState.error : null;
  const transcriptHistory = mergeById(
    transcriptQuery.data?.recentTranscriptSegments ?? [],
    restTranscriptHistory,
    (event) => event.segmentId,
    16,
  );
  const sentimentHistory = sentimentQuery.data?.recentSentiment ?? [];
  const transcriptSentimentHistory = transcriptSentimentQuery.data?.recentTranscriptSentiment ?? [];
  const sponsorSentimentHistory = sponsorSentimentQuery.data?.recentSponsorSentiment ?? [];
  const sponsorTranscriptSentimentHistory = sponsorTranscriptSentimentQuery.data?.recentSponsorTranscriptSentiment ?? [];

  const liveSponsorsForStreamer = liveSponsors.filter((event) => event.streamer === streamer);
  const liveTranscriptsForStreamer = liveTranscripts.filter((event) => event.streamer === streamer);
  const liveChatForStreamer = liveChat.filter((event) => event.streamer === streamer);
  const liveSentimentForStreamer = liveSentiment.filter((event) => event.streamer === streamer);
  const liveTranscriptSentimentForStreamer = liveTranscriptSentiment.filter((event) => event.streamer === streamer);
  const liveSponsorSentimentForStreamer = liveSponsorSentiment.filter((event) => event.streamer === streamer);
  const liveSponsorTranscriptSentimentForStreamer = liveSponsorTranscriptSentiment.filter((event) => event.streamer === streamer);

  const sponsors = mergeById(liveSponsorsForStreamer, sponsorHistory, (event) => event.detectionEventId, 20);
  const transcriptSegments = mergeById(liveTranscriptsForStreamer, transcriptHistory, (event) => event.segmentId, 16);
  const chatSentiments = mergeById(liveSentimentForStreamer, sentimentHistory, (event) => event.sentimentEventId, 16);
  const transcriptSentiments = mergeById(
    liveTranscriptSentimentForStreamer,
    transcriptSentimentHistory,
    (event) => event.sentimentEventId,
    16,
  );
  const sponsorSentiments = mergeById(liveSponsorSentimentForStreamer, sponsorSentimentHistory, (event) => event.sentimentEventId, 12);
  const sponsorTranscriptSentiments = mergeById(
    liveSponsorTranscriptSentimentForStreamer,
    sponsorTranscriptSentimentHistory,
    (event) => event.sentimentEventId,
    10,
  );

  const sponsorHistoryIds = new Set(sponsorHistory.map((event) => event.detectionEventId));
  const transcriptHistoryIds = new Set(transcriptHistory.map((event) => event.segmentId));
  const sentimentHistoryIds = new Set(sentimentHistory.map((event) => event.sentimentEventId));
  const transcriptSentimentHistoryIds = new Set(transcriptSentimentHistory.map((event) => event.sentimentEventId));
  const sponsorSentimentHistoryIds = new Set(sponsorSentimentHistory.map((event) => event.sentimentEventId));
  const sponsorTranscriptSentimentHistoryIds = new Set(sponsorTranscriptSentimentHistory.map((event) => event.sentimentEventId));

  useSubscription<OnSponsorDetectionSubscription, OnSponsorDetectionSubscriptionVariables>(ON_SPONSOR_DETECTION_SUBSCRIPTION, {
    variables: { streamer },
    onData: ({ data }) => {
      const event = data.data?.onSponsorDetection;
      if (!event) return;
      setLiveSponsors((current) => {
        if (sponsorHistoryIds.has(event.detectionEventId) || current.some((item) => item.detectionEventId === event.detectionEventId)) {
          return current;
        }
        return [event, ...current].slice(0, 20);
      });
    },
  });

  useSubscription<OnTranscriptSegmentSubscription, OnTranscriptSegmentSubscriptionVariables>(ON_TRANSCRIPT_SEGMENT_SUBSCRIPTION, {
    variables: { streamer },
    onData: ({ data }) => {
      const event = data.data?.onTranscriptSegment;
      if (!event) return;
      setLiveTranscripts((current) => {
        if (transcriptHistoryIds.has(event.segmentId) || current.some((item) => item.segmentId === event.segmentId)) {
          return current;
        }
        return [event, ...current].slice(0, 16);
      });
    },
  });

  useSubscription<OnChatMessageSubscription, OnChatMessageSubscriptionVariables>(ON_CHAT_MESSAGE_SUBSCRIPTION, {
    variables: { streamer },
    onData: ({ data }) => {
      const event = data.data?.onChatMessage;
      if (!event) return;
      setLiveChat((current) => {
        if (current.some((item) => item.eventId === event.eventId)) return current;
        return [event, ...current].slice(0, 14);
      });
    },
  });

  useSubscription<OnSentimentSubscription, OnSentimentSubscriptionVariables>(ON_SENTIMENT_SUBSCRIPTION, {
    variables: { streamer },
    onData: ({ data }) => {
      const event = data.data?.onSentiment;
      if (!event) return;
      setLiveSentiment((current) => {
        if (sentimentHistoryIds.has(event.sentimentEventId) || current.some((item) => item.sentimentEventId === event.sentimentEventId)) {
          return current;
        }
        return [event, ...current].slice(0, 16);
      });
    },
  });

  useSubscription<OnTranscriptSentimentSubscription, OnTranscriptSentimentSubscriptionVariables>(ON_TRANSCRIPT_SENTIMENT_SUBSCRIPTION, {
    variables: { streamer },
    onData: ({ data }) => {
      const event = data.data?.onTranscriptSentiment;
      if (!event) return;
      setLiveTranscriptSentiment((current) => {
        if (
          transcriptSentimentHistoryIds.has(event.sentimentEventId) ||
          current.some((item) => item.sentimentEventId === event.sentimentEventId)
        ) {
          return current;
        }
        return [event, ...current].slice(0, 16);
      });
    },
  });

  useSubscription<OnSponsorSentimentSubscription, OnSponsorSentimentSubscriptionVariables>(ON_SPONSOR_SENTIMENT_SUBSCRIPTION, {
    variables: { streamer, sponsor: activeSponsor },
    onData: ({ data }) => {
      const event = data.data?.onSponsorSentiment;
      if (!event) return;
      setLiveSponsorSentiment((current) => {
        if (sponsorSentimentHistoryIds.has(event.sentimentEventId) || current.some((item) => item.sentimentEventId === event.sentimentEventId)) {
          return current;
        }
        return [event, ...current].slice(0, 12);
      });
    },
  });

  useSubscription<OnSponsorTranscriptSentimentSubscription, OnSponsorTranscriptSentimentSubscriptionVariables>(ON_SPONSOR_TRANSCRIPT_SENTIMENT_SUBSCRIPTION, {
    variables: { streamer, sponsor: activeSponsor },
    onData: ({ data }) => {
      const event = data.data?.onSponsorTranscriptSentiment;
      if (!event) return;
      setLiveSponsorTranscriptSentiment((current) => {
        if (
          sponsorTranscriptSentimentHistoryIds.has(event.sentimentEventId) ||
          current.some((item) => item.sentimentEventId === event.sentimentEventId)
        ) {
          return current;
        }
        return [event, ...current].slice(0, 10);
      });
    },
  });

  const latestFrame = sponsors.find((event) => event.frameRef);
  const playerUrl = twitchPlayerUrl(streamer);
  const frameOverlays = latestFrame
    ? sponsors.filter((event) => event.frameRef === latestFrame.frameRef).slice(0, 6)
    : [];
  const topSponsor = sponsors.find((event) => event.sponsor !== "UNKNOWN") ?? latestFrame;
  const latestTranscriptSentiment = transcriptSentiments[0];
  const latestChat = liveChatForStreamer[0];
  const latestEventAt = latestFrame?.capturedAt ?? latestTranscriptSentiment?.processedAt ?? latestChat?.timestamp;
  const transcriptSentimentBySegment = new Map(transcriptSentiments.map((event) => [event.segmentId, event]));
  const sponsorTranscriptSentimentBySegment = new Map(sponsorTranscriptSentiments.map((event) => [event.segmentId, event]));
  const transcriptSegmentIds = new Set(transcriptSegments.map((segment) => segment.segmentId));
  const transcriptFeed = [
    ...transcriptSegments.map((segment) => {
      const analysis = transcriptSentimentBySegment.get(segment.segmentId);
      const sponsorAnalysis = sponsorTranscriptSentimentBySegment.get(segment.segmentId) ?? (analysis?.sponsorRelevant ? analysis : undefined);
      return {
        id: segment.segmentId,
        text: analysis?.text || segment.text,
        at: segment.startedAt,
        sequence: segment.transcriptSequence,
        source: segment.source,
        analysis,
        sponsorAnalysis,
      };
    }),
    ...transcriptSentiments.filter((event) => !transcriptSegmentIds.has(event.segmentId)).map((event) => {
      const sponsorAnalysis = sponsorTranscriptSentimentBySegment.get(event.segmentId) ?? (event.sponsorRelevant ? event : undefined);
      return {
        id: event.segmentId,
        text: event.text,
        at: event.segmentStartedAt,
        sequence: event.transcriptSequence,
        source: undefined,
        analysis: event,
        sponsorAnalysis,
      };
    }),
  ].sort((left, right) => right.at - left.at).slice(0, 16);
  const sponsorTranscriptCount = transcriptFeed.filter((line) => line.sponsorAnalysis).length;

  return (
    <section className="stream-console" aria-label="Live stream analysis console">
      <div className="video-console-card">
        <div className="video-topbar">
          <div>
            <span className="live-dot">LIVE</span>
            <strong>@{streamer}</strong>
            <span>{sponsorBrand}</span>
          </div>
          <div className="video-clock">Last signal {formatTime(latestEventAt)}</div>
        </div>

        <div className="stream-frame-shell">
          <iframe
            className="stream-video-iframe"
            src={playerUrl}
            title={`Live Twitch stream for ${streamer}`}
            allow="autoplay; fullscreen; picture-in-picture"
            allowFullScreen
          />

          <div className="video-overlay video-overlay-top-left">
            <span>Campaign</span>
            <strong>{campaignGoal || "Live review"}</strong>
          </div>

          <div className="video-overlay video-overlay-bottom-left">
            <span>Sponsor read</span>
            <strong>{topSponsor ? `${topSponsor.sponsor} ${(topSponsor.confidence * 100).toFixed(0)}%` : "No detection yet"}</strong>
          </div>

          <div className="video-overlay video-overlay-bottom-right">
            <span>Frame</span>
            <strong>{latestFrame ? `#${latestFrame.frameSequence}` : "--"}</strong>
          </div>

          {frameOverlays.map((event) => (
            <div
              className="detection-box"
              key={event.detectionEventId}
              style={{
                left: percent(event.x),
                top: percent(event.y),
                width: percent(event.width),
                height: percent(event.height),
              }}
            >
              <span>{event.sponsor}</span>
            </div>
          ))}
        </div>

        <SegmentationPreview frame={latestFrame} />
      </div>

      <aside className="stream-sidecar" aria-label="Transcript and chat analysis">
        <section className="sidecar-panel transcript-feed">
          <div className="sidecar-heading">
            <div>
              <span className="eyebrow">Streamer audio</span>
              <h2>All transcript</h2>
            </div>
            <span className="status-pill">{transcriptFeed.length} lines · {sponsorTranscriptCount} sponsor</span>
          </div>

          <div className="feed-stack">
            {(transcriptQuery.error || transcriptSentimentQuery.error) && transcriptFeed.length === 0 && (
              <div className="error-state">
                Failed to load transcript: {transcriptQuery.error?.message || transcriptSentimentQuery.error?.message}{restTranscriptError ? `; ${restTranscriptError}` : ""}
              </div>
            )}
            {(transcriptQuery.loading || transcriptSentimentQuery.loading) && transcriptFeed.length === 0 && <div className="empty-state">Loading transcript...</div>}
            {!transcriptQuery.loading && !transcriptSentimentQuery.loading && !transcriptQuery.error && !transcriptSentimentQuery.error && transcriptFeed.length === 0 && (
              <div className="empty-state">No transcript yet.</div>
            )}
            {transcriptFeed.map((line) => {
              const analysis = line.analysis;
              const sponsorAnalysis = line.sponsorAnalysis;
              return (
                <article className={`transcript-line${sponsorAnalysis ? " transcript-line-sponsor" : ""}`} key={line.id}>
                  <div className="line-meta">
                    <span>{formatTime(line.at)}</span>
                    <span>seq {line.sequence}</span>
                    {line.source && <span>{line.source}</span>}
                    <span className={`analysis-chip ${sentimentClass(analysis?.label)}`}>{analysis?.label ?? "pending"}</span>
                    {sponsorAnalysis && <span className="analysis-chip analysis-live">sponsor {formatScore(sponsorAnalysis.relevanceScore)}</span>}
                  </div>
                  <p>{line.text}</p>
                  {sponsorAnalysis && (
                    <div className="line-meta">Matched {matchedContext(sponsorAnalysis, activeSponsor)}</div>
                  )}
                </article>
              );
            })}
          </div>
        </section>

        <section className="sidecar-panel chat-feed">
          <div className="sidecar-heading">
            <div>
              <span className="eyebrow">Sponsor-specific tone</span>
              <h2>{activeSponsor || sponsorBrand} sentiment</h2>
            </div>
            <span className="status-pill">{sponsorSentiments.length + sponsorTranscriptSentiments.length} relevant</span>
          </div>

          <div className="feed-stack compact-feed">
            {sponsorSentiments.map((event) => (
              <article className="chat-line" key={event.sentimentEventId}>
                <div className="line-meta">
                  <span>{formatTime(event.chatTimestamp)}</span>
                  <span>{event.user}</span>
                  <span className={`analysis-chip ${sentimentClass(event.label)}`}>{event.label} {formatScore(event.score)}</span>
                  <span className="analysis-chip analysis-live">{formatScore(event.relevanceScore)} match</span>
                </div>
                <p>{event.message}</p>
                <div className="line-meta">Matched {matchedContext(event, activeSponsor)}</div>
              </article>
            ))}

            {sponsorTranscriptSentiments.map((event) => (
              <article className="transcript-line" key={event.sentimentEventId}>
                <div className="line-meta">
                  <span>{formatTime(event.segmentEndedAt)}</span>
                  <span>transcript</span>
                  <span className={`analysis-chip ${sentimentClass(event.label)}`}>{event.label} {formatScore(event.score)}</span>
                  <span className="analysis-chip analysis-live">{formatScore(event.relevanceScore)} match</span>
                </div>
                <p>{event.text}</p>
                <div className="line-meta">Matched {matchedContext(event, activeSponsor)}</div>
              </article>
            ))}

            {!sponsorSentimentQuery.loading && !sponsorTranscriptSentimentQuery.loading && sponsorSentiments.length === 0 && sponsorTranscriptSentiments.length === 0 && (
              <div className="empty-state">No sponsor-related sentiment yet.</div>
            )}
          </div>
        </section>

        <section className="sidecar-panel chat-feed">
          <div className="sidecar-heading">
            <div>
              <span className="eyebrow">Audience layer</span>
              <h2>Chat + sentiment</h2>
            </div>
            <span className="status-pill">{liveChatForStreamer.length + chatSentiments.length} signals</span>
          </div>

          <div className="feed-stack compact-feed">
            {liveChatForStreamer.slice(0, 5).map((event) => (
              <article className="chat-line" key={event.eventId}>
                <div className="line-meta">
                  <span>{formatTime(event.timestamp)}</span>
                  <span>{event.user}</span>
                  <span className="analysis-chip analysis-live">raw</span>
                </div>
                <p>{event.message}</p>
              </article>
            ))}

            {chatSentiments.map((event) => (
              <article className="chat-line" key={event.sentimentEventId}>
                <div className="line-meta">
                  <span>{formatTime(event.chatTimestamp)}</span>
                  <span>{event.user}</span>
                  <span className={`analysis-chip ${sentimentClass(event.label)}`}>{event.label} {formatScore(event.score)}</span>
                </div>
                <p>{event.message}</p>
              </article>
            ))}

            {!sentimentQuery.loading && liveChatForStreamer.length === 0 && chatSentiments.length === 0 && (
              <div className="empty-state">No chat signals yet.</div>
            )}
          </div>
        </section>
      </aside>
    </section>
  );
}
