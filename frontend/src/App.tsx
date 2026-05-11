import { useState } from "react";
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

type SponsorDetectionEvent = {
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
  source?: string | null;
  channelLogin?: string | null;
  streamSessionId?: string | null;
  twitchStreamId?: string | null;
  videoTimestampMs?: number | null;
};

type TranscriptSegmentEvent = {
  segmentId: string;
  streamer: string;
  text: string;
  startedAt: number;
  endedAt: number;
  language?: string | null;
  confidence?: number | null;
  modelVersion: string;
  source?: string | null;
  channelLogin?: string | null;
  streamSessionId?: string | null;
  videoTimestampMs: number;
  transcriptSequence: number;
  captureWorkerId?: string | null;
};

type ChatMessageEvent = {
  eventId: string;
  streamer: string;
  user: string;
  message: string;
  timestamp: number;
};

type SentimentEvent = {
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
  matchedSponsor?: string | null;
  matchedTerms: string[];
  relevanceScore: number;
  relevanceReason?: string | null;
  relevanceVersion?: string | null;
};

type TranscriptSentimentEvent = {
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
  streamSessionId?: string | null;
  transcriptSequence: number;
  sponsorRelevant: boolean;
  matchedSponsor?: string | null;
  matchedTerms: string[];
  relevanceScore: number;
  relevanceReason?: string | null;
  relevanceVersion?: string | null;
};

type PortfolioStreamer = {
  handle: string;
  brand: string;
  owner: string;
  risk: string;
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

function twitchPlayerUrl(streamer: string): string {
  const channel = streamer.trim().toLowerCase().replace(/^[@#]/, "");
  const parent = encodeURIComponent(window.location.hostname || "localhost");
  return `https://player.twitch.tv/?channel=${encodeURIComponent(channel)}&parent=${parent}&muted=true&autoplay=true`;
}

async function switchRuntimeChannels(path: string, streamer: string): Promise<void> {
  const response = await fetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ channels: [streamer] }),
  });
  if (!response.ok) {
    throw new Error(`${path} returned ${response.status}`);
  }
}

function sponsorProfileFromInput(streamer: string, sponsorInput: string, campaignGoal: string) {
  const parts = sponsorInput.split(",").map((part) => part.trim()).filter(Boolean);
  const sponsor = parts[0] || sponsorInput.trim();
  const semanticTerms = parts.slice(1);
  return {
    streamer,
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
  const [analysisRuns, setAnalysisRuns] = useState(1);
  const [channelSwitchStatus, setChannelSwitchStatus] = useState("Runtime capture follows the streamer field.");

  async function analyzeStreamer() {
    const nextStreamer = streamerInput.trim();
    if (!nextStreamer) return;

    await loadStreamer(nextStreamer, sponsorBrand);
  }

  async function selectPortfolioStreamer(streamer: PortfolioStreamer) {
    setStreamerInput(streamer.handle);
    setSponsorBrand(streamer.brand);
    await loadStreamer(streamer.handle, streamer.brand);
  }

  async function loadStreamer(streamer: string, brand: string) {
    setSelectedStreamer(streamer);
    setSponsorBrand(brand);
    setAnalysisRuns((current) => current + 1);
    setChannelSwitchStatus(`Switching Twitch ingest to @${streamer}...`);

    const results = await Promise.allSettled([
      switchRuntimeChannels("/api/chat/twitch/channels", streamer),
      switchRuntimeChannels("/api/video/capture/channels", streamer),
      updateSponsorRelevance(streamer, brand, campaignGoal),
    ]);
    const failures = results.filter((result) => result.status === "rejected");
    if (failures.length > 0) {
      setChannelSwitchStatus(`Loaded @${streamer}; ${failures.length} runtime update failed. Check service status pills.`);
      return;
    }

    setChannelSwitchStatus(`Chat, video frames, transcript capture, and sponsor relevance are pointed at @${streamer}.`);
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
          key={`${selectedStreamer}-${analysisRuns}`}
          streamer={selectedStreamer}
          sponsorBrand={displayBrand}
          campaignGoal={campaignGoal}
        />

        <section className="metrics-section" id="metrics">
          <StreamMetricsOverview key={`analytics-${selectedStreamer}-${analysisRuns}`} streamer={selectedStreamer} />
        </section>

        <section className="evidence-grid" id="evidence">
          <SentimentPanel key={`sentiment-${selectedStreamer}-${analysisRuns}`} streamer={selectedStreamer} hideControls />
          <SponsorPanel key={`sponsor-${selectedStreamer}-${analysisRuns}`} streamer={selectedStreamer} hideControls />
          <RecommendationPanel key={`recommendations-${selectedStreamer}-${analysisRuns}`} streamer={selectedStreamer} hideControls />
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
  const activeSponsor = sponsorProfileFromInput(streamer, sponsorBrand, campaignGoal).sponsor;

  const sponsorQuery = useQuery<{ sponsorDetections: SponsorDetectionEvent[] }>(RECENT_SPONSOR_DETECTIONS_QUERY, {
    variables: { streamer, limit: 12 },
    fetchPolicy: "network-only",
  });
  const transcriptQuery = useQuery<{ recentTranscriptSegments: TranscriptSegmentEvent[] }>(RECENT_TRANSCRIPT_SEGMENTS_QUERY, {
    variables: { streamer, limit: 10 },
    fetchPolicy: "network-only",
  });
  const sentimentQuery = useQuery<{ recentSentiment: SentimentEvent[] }>(RECENT_SENTIMENT_QUERY, {
    variables: { streamer, limit: 12 },
    fetchPolicy: "network-only",
  });
  const transcriptSentimentQuery = useQuery<{ recentTranscriptSentiment: TranscriptSentimentEvent[] }>(RECENT_TRANSCRIPT_SENTIMENT_QUERY, {
    variables: { streamer, limit: 10 },
    fetchPolicy: "network-only",
  });
  const sponsorSentimentQuery = useQuery<{ recentSponsorSentiment: SentimentEvent[] }>(RECENT_SPONSOR_SENTIMENT_QUERY, {
    variables: { streamer, sponsor: activeSponsor, limit: 12 },
    fetchPolicy: "network-only",
  });
  const sponsorTranscriptSentimentQuery = useQuery<{ recentSponsorTranscriptSentiment: TranscriptSentimentEvent[] }>(RECENT_SPONSOR_TRANSCRIPT_SENTIMENT_QUERY, {
    variables: { streamer, sponsor: activeSponsor, limit: 10 },
    fetchPolicy: "network-only",
  });

  const sponsorHistory = sponsorQuery.data?.sponsorDetections ?? [];
  const transcriptHistory = transcriptQuery.data?.recentTranscriptSegments ?? [];
  const sentimentHistory = sentimentQuery.data?.recentSentiment ?? [];
  const transcriptSentimentHistory = transcriptSentimentQuery.data?.recentTranscriptSentiment ?? [];
  const sponsorSentimentHistory = sponsorSentimentQuery.data?.recentSponsorSentiment ?? [];
  const sponsorTranscriptSentimentHistory = sponsorTranscriptSentimentQuery.data?.recentSponsorTranscriptSentiment ?? [];

  const sponsors = mergeById(liveSponsors, sponsorHistory, (event) => event.detectionEventId, 20);
  const transcriptSegments = mergeById(liveTranscripts, transcriptHistory, (event) => event.segmentId, 16);
  const chatSentiments = mergeById(liveSentiment, sentimentHistory, (event) => event.sentimentEventId, 16);
  const transcriptSentiments = mergeById(
    liveTranscriptSentiment,
    transcriptSentimentHistory,
    (event) => event.sentimentEventId,
    16,
  );
  const sponsorSentiments = mergeById(liveSponsorSentiment, sponsorSentimentHistory, (event) => event.sentimentEventId, 12);
  const sponsorTranscriptSentiments = mergeById(
    liveSponsorTranscriptSentiment,
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

  useSubscription<{ onSponsorDetection: SponsorDetectionEvent }>(ON_SPONSOR_DETECTION_SUBSCRIPTION, {
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

  useSubscription<{ onTranscriptSegment: TranscriptSegmentEvent }>(ON_TRANSCRIPT_SEGMENT_SUBSCRIPTION, {
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

  useSubscription<{ onChatMessage: ChatMessageEvent }>(ON_CHAT_MESSAGE_SUBSCRIPTION, {
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

  useSubscription<{ onSentiment: SentimentEvent }>(ON_SENTIMENT_SUBSCRIPTION, {
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

  useSubscription<{ onTranscriptSentiment: TranscriptSentimentEvent }>(ON_TRANSCRIPT_SENTIMENT_SUBSCRIPTION, {
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

  useSubscription<{ onSponsorSentiment: SentimentEvent }>(ON_SPONSOR_SENTIMENT_SUBSCRIPTION, {
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

  useSubscription<{ onSponsorTranscriptSentiment: TranscriptSentimentEvent }>(ON_SPONSOR_TRANSCRIPT_SENTIMENT_SUBSCRIPTION, {
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
  const latestChat = liveChat[0];
  const latestEventAt = latestFrame?.capturedAt ?? latestTranscriptSentiment?.processedAt ?? latestChat?.timestamp;
  const transcriptSentimentBySegment = new Map(transcriptSentiments.map((event) => [event.segmentId, event]));

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
              <h2>Transcript</h2>
            </div>
            <span className="status-pill">{transcriptSegments.length} segments</span>
          </div>

          <div className="feed-stack">
            {transcriptQuery.loading && transcriptSegments.length === 0 && <div className="empty-state">Loading transcript...</div>}
            {!transcriptQuery.loading && transcriptSegments.length === 0 && <div className="empty-state">No transcript segments yet.</div>}
            {transcriptSegments.map((segment) => {
              const analysis = transcriptSentimentBySegment.get(segment.segmentId);
              return (
                <article className="transcript-line" key={segment.segmentId}>
                  <div className="line-meta">
                    <span>{formatTime(segment.startedAt)}</span>
                    <span>seq {segment.transcriptSequence}</span>
                    <span className={`analysis-chip ${sentimentClass(analysis?.label)}`}>{analysis?.label ?? "pending"}</span>
                  </div>
                  <p>{segment.text}</p>
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
                  <span className={`analysis-chip ${sentimentClass(event.label)}`}>{event.label} {event.score.toFixed(2)}</span>
                  <span className="analysis-chip analysis-live">{event.relevanceScore.toFixed(2)} match</span>
                </div>
                <p>{event.message}</p>
                <div className="line-meta">Matched {event.matchedTerms.join(", ") || event.matchedSponsor || "sponsor context"}</div>
              </article>
            ))}

            {sponsorTranscriptSentiments.map((event) => (
              <article className="transcript-line" key={event.sentimentEventId}>
                <div className="line-meta">
                  <span>{formatTime(event.segmentEndedAt)}</span>
                  <span>transcript</span>
                  <span className={`analysis-chip ${sentimentClass(event.label)}`}>{event.label} {event.score.toFixed(2)}</span>
                  <span className="analysis-chip analysis-live">{event.relevanceScore.toFixed(2)} match</span>
                </div>
                <p>{event.text}</p>
                <div className="line-meta">Matched {event.matchedTerms.join(", ") || event.matchedSponsor || "sponsor context"}</div>
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
            <span className="status-pill">{liveChat.length + chatSentiments.length} signals</span>
          </div>

          <div className="feed-stack compact-feed">
            {liveChat.slice(0, 5).map((event) => (
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
                  <span className={`analysis-chip ${sentimentClass(event.label)}`}>{event.label} {event.score.toFixed(2)}</span>
                </div>
                <p>{event.message}</p>
              </article>
            ))}

            {!sentimentQuery.loading && liveChat.length === 0 && chatSentiments.length === 0 && (
              <div className="empty-state">No chat signals yet.</div>
            )}
          </div>
        </section>
      </aside>
    </section>
  );
}
