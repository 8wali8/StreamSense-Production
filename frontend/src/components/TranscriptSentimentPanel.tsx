import { useState } from "react";
import { useQuery, useSubscription } from "@apollo/client/react";
import { RECENT_TRANSCRIPT_SENTIMENT_QUERY } from "../graphql/queries";
import { ON_TRANSCRIPT_SENTIMENT_SUBSCRIPTION } from "../graphql/subscriptions";
import type { OnTranscriptSentimentSubscription, RecentTranscriptSentimentQuery } from "../graphql/generated";

type TranscriptSentimentEvent = RecentTranscriptSentimentQuery["recentTranscriptSentiment"][number];

type TranscriptSentimentPanelProps = {
  streamer?: string;
  hideControls?: boolean;
};

function formatTime(ts: number): string {
  return new Date(ts).toLocaleTimeString();
}

export function TranscriptSentimentPanel({ streamer, hideControls = false }: TranscriptSentimentPanelProps) {
  const [streamerInput, setStreamerInput] = useState("test");
  const [localStreamer, setLocalStreamer] = useState("test");
  const [liveEvents, setLiveEvents] = useState<TranscriptSentimentEvent[]>([]);
  const activeStreamer = streamer ?? localStreamer;

  const { data, loading, error } = useQuery<RecentTranscriptSentimentQuery>(RECENT_TRANSCRIPT_SENTIMENT_QUERY, {
    variables: { streamer: activeStreamer, limit: 20 },
    skip: !activeStreamer,
    fetchPolicy: "network-only",
  });

  const historyEvents = data?.recentTranscriptSentiment ?? [];
  const historyIds = new Set(historyEvents.map((event) => event.sentimentEventId));
  const events = [...liveEvents.filter((event) => !historyIds.has(event.sentimentEventId)), ...historyEvents].slice(0, 50);

  const { error: subscriptionError } = useSubscription<OnTranscriptSentimentSubscription>(ON_TRANSCRIPT_SENTIMENT_SUBSCRIPTION, {
    variables: { streamer: activeStreamer },
    skip: !activeStreamer,
    onData: ({ data: subscriptionData }) => {
      const event = subscriptionData.data?.onTranscriptSentiment;
      if (!event) {
        return;
      }

      setLiveEvents((prev) => {
        if (
          historyIds.has(event.sentimentEventId) ||
          prev.some((existing) => existing.sentimentEventId === event.sentimentEventId)
        ) {
          return prev;
        }
        return [event, ...prev].slice(0, 50);
      });
    },
  });

  function onLoad() {
    const nextStreamer = streamerInput.trim();
    if (!nextStreamer) {
      return;
    }
    setLiveEvents([]);
    setLocalStreamer(nextStreamer);
  }

  const averageScore = events.length === 0 ? 0 : events.reduce((sum, event) => sum + event.score, 0) / events.length;

  return (
    <section className="dashboard-panel">
      <div className="panel-title-row panel-heading">
        <div>
          <div className="eyebrow">Streamer transcript sentiment</div>
          <h2>Voice sentiment</h2>
          <p>Sentiment from streamer speech only, not audience chat, for @{activeStreamer}.</p>
        </div>
        <span className="status-pill">avg {averageScore.toFixed(2)}</span>
      </div>

      {!hideControls && (
        <div className="panel-actions">
          <label>
            <span className="field-label">Streamer</span>
            <input className="text-input" value={streamerInput} onChange={(event) => setStreamerInput(event.target.value)} />
          </label>
          <button className="button-primary" onClick={onLoad}>Load voice sentiment</button>
        </div>
      )}

      <div className="status-line">
        Status: {subscriptionError ? `subscription error (${subscriptionError.message})` : `live voice sentiment (streamer=${activeStreamer})`}
      </div>

      {loading && events.length === 0 && <div>Loading transcript sentiment...</div>}
      {error && <div className="error-state" role="alert">Failed to load transcript sentiment: {error.message}</div>}
      {!loading && !error && events.length === 0 && <div className="empty-state">No transcript sentiment yet.</div>}

      <div className="event-list">
        {events.map((event) => (
          <article className="event-card" key={event.sentimentEventId}>
            <div className="event-card-header">
              <div className="event-meta">
                [{formatTime(event.segmentEndedAt)}] segment={event.segmentId}
              </div>
              <span className={`sentiment-label ${sentimentClass(event.label)}`}>{event.label}</span>
            </div>
            <p>{event.text}</p>
            <div className="event-tags">
              <span className="tag">score={event.score.toFixed(2)}</span>
              <span className="tag">sentimentModel={event.modelVersion}</span>
              <span className="tag">transcriptModel={event.transcriptModelVersion}</span>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}

function sentimentClass(label: string): string {
  if (label === "POSITIVE") return "label-positive";
  if (label === "NEGATIVE") return "label-negative";
  return "label-neutral";
}
