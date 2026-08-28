import { useState } from "react";
import type {
  OnSentimentSubscription,
  RecentSentimentQuery,
  RecentSentimentQueryVariables,
} from "../graphql/generated";
import { RECENT_SENTIMENT_QUERY } from "../graphql/queries";
import { ON_SENTIMENT_SUBSCRIPTION } from "../graphql/subscriptions";
import { useLiveFeed } from "../hooks/useLiveFeed";
import { formatTime, sentimentColor, sentimentLabelClass } from "../lib/format";
import { MetricCard } from "./MetricCard";

type SentimentAnalysisEvent = RecentSentimentQuery["recentSentiment"][number];

type SentimentPanelProps = {
  streamer?: string;
  hideControls?: boolean;
};

export function SentimentPanel({ streamer, hideControls = false }: SentimentPanelProps) {
  const [streamerInput, setStreamerInput] = useState("test");
  const [localStreamer, setLocalStreamer] = useState("test");
  const activeStreamer = streamer ?? localStreamer;

  const feed = useLiveFeed<
    RecentSentimentQuery,
    OnSentimentSubscription,
    RecentSentimentQueryVariables,
    SentimentAnalysisEvent
  >({
    query: RECENT_SENTIMENT_QUERY,
    variables: { streamer: activeStreamer, limit: 20 },
    skip: !activeStreamer,
    selectHistory: (data) => data.recentSentiment,
    subscription: ON_SENTIMENT_SUBSCRIPTION,
    subscriptionVariables: { streamer: activeStreamer },
    selectEvent: (data) => data.onSentiment,
    getId: (event) => event.sentimentEventId,
    limit: 50,
    resetKey: activeStreamer,
  });
  const { items: events, loading, error, subscriptionError } = feed;

  function onLoad() {
    const nextStreamer = streamerInput.trim();
    if (!nextStreamer) {
      return;
    }
    setLocalStreamer(nextStreamer);
  }

  const counts = events.reduce(
    (acc, event) => {
      if (event.label === "POSITIVE") acc.positive += 1;
      else if (event.label === "NEGATIVE") acc.negative += 1;
      else acc.neutral += 1;
      return acc;
    },
    { positive: 0, neutral: 0, negative: 0 },
  );

  const averageScore = events.length === 0 ? 0 : events.reduce((sum, event) => sum + event.score, 0) / events.length;

  return (
    <section className="dashboard-panel">
      <div className="panel-title-row panel-heading">
        <div>
          <div className="eyebrow">Audience intelligence</div>
          <h2>Sentiment</h2>
          <p>Recent history plus live sentiment updates for @{activeStreamer}.</p>
        </div>
        <span className="status-pill">{events.length} signals</span>
      </div>

      {!hideControls && (
        <div className="panel-actions">
          <label>
            <span className="field-label">Streamer</span>
            <input
              className="text-input"
              value={streamerInput}
              onChange={(event) => setStreamerInput(event.target.value)}
            />
          </label>

          <button className="button-primary" onClick={onLoad}>
            Load sentiment
          </button>
        </div>
      )}

      <div className="status-line">
        Status:{" "}
        {subscriptionError ? `subscription error (${subscriptionError.message})` : `live (streamer=${activeStreamer})`}
      </div>

      <div className="metric-grid">
        <MetricCard label="Positive" value={counts.positive} tone="metric-positive" />
        <MetricCard label="Neutral" value={counts.neutral} tone="metric-neutral" />
        <MetricCard label="Negative" value={counts.negative} tone="metric-negative" />
        <MetricCard label="Avg score" value={averageScore.toFixed(2)} tone="metric-blue" />
      </div>

      {loading && events.length === 0 && <div>Loading sentiment history...</div>}

      {error && (
        <div className="error-state" role="alert">
          Failed to load sentiment history: {error.message}
        </div>
      )}

      {!loading && !error && events.length === 0 && <div className="empty-state">No sentiment history yet.</div>}

      <div className="event-list">
        {events.map((event) => (
          <article className="event-card" key={event.sentimentEventId}>
            <div className="event-card-header">
              <div className="event-meta">
                [{formatTime(event.chatTimestamp)}] {event.streamer} • source={event.sourceEventId}
              </div>
              <span
                className={`sentiment-label ${sentimentLabelClass(event.label)}`}
                style={{ color: sentimentColor(event.label) }}
              >
                {event.label}
              </span>
            </div>

            <strong>{event.user}</strong>
            <p>{event.message}</p>
            <div className="event-tags">
              <span className="tag">score={event.score.toFixed(2)}</span>
              <span className="tag">processed={formatTime(event.processedAt)}</span>
              <span className="tag">eventId={event.sentimentEventId}</span>
              <span className="tag">model={event.modelVersion}</span>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}
