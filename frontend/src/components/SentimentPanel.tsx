import { useState } from "react";
import { useQuery, useSubscription } from "@apollo/client/react";
import { RECENT_SENTIMENT_QUERY } from "../graphql/queries";
import { ON_SENTIMENT_SUBSCRIPTION } from "../graphql/subscriptions";

type SentimentAnalysisEvent = {
  sentimentEventId: string;
  sourceEventId: string;
  streamer: string;
  user: string;
  message: string;
  chatTimestamp: number;
  processedAt: number;
  label: "POSITIVE" | "NEUTRAL" | "NEGATIVE" | string;
  score: number;
  modelVersion: string;
};

type RecentSentimentData = {
  recentSentiment: SentimentAnalysisEvent[];
};

type OnSentimentData = {
  onSentiment: SentimentAnalysisEvent;
};

function formatTime(ts: number): string {
  return new Date(ts).toLocaleTimeString();
}

function labelColor(label: string): string {
  if (label === "POSITIVE") return "#157f3b";
  if (label === "NEGATIVE") return "#b42318";
  return "#7a5c00";
}

export function SentimentPanel() {
  const [streamerInput, setStreamerInput] = useState("test");
  const [activeStreamer, setActiveStreamer] = useState("test");
  const [liveEvents, setLiveEvents] = useState<SentimentAnalysisEvent[]>([]);

  const { data, loading, error } = useQuery<RecentSentimentData>(RECENT_SENTIMENT_QUERY, {
    variables: { streamer: activeStreamer, limit: 20 },
    skip: !activeStreamer,
    fetchPolicy: "network-only",
  });

  const historyEvents = data?.recentSentiment ?? [];
  const historyIds = new Set(historyEvents.map((event) => event.sentimentEventId));
  const events = [...liveEvents.filter((event) => !historyIds.has(event.sentimentEventId)), ...historyEvents].slice(0, 50);

  const { error: subscriptionError } = useSubscription<OnSentimentData>(ON_SENTIMENT_SUBSCRIPTION, {
    variables: { streamer: activeStreamer },
    skip: !activeStreamer,
    onData: ({ data: subscriptionData }) => {
      const event = subscriptionData.data?.onSentiment;
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
    setActiveStreamer(nextStreamer);
  }

  const counts = events.reduce(
    (acc, event) => {
      if (event.label === "POSITIVE") acc.positive += 1;
      else if (event.label === "NEGATIVE") acc.negative += 1;
      else acc.neutral += 1;
      return acc;
    },
    { positive: 0, neutral: 0, negative: 0 }
  );

  const averageScore =
    events.length === 0 ? 0 : events.reduce((sum, event) => sum + event.score, 0) / events.length;

  return (
    <section
      style={{
        border: "1px solid #d9e1ec",
        borderRadius: 16,
        padding: 16,
        background: "#f8fbff",
        minHeight: 420,
      }}
    >
      <div style={{ display: "flex", flexWrap: "wrap", gap: 12, alignItems: "center", marginBottom: 16 }}>
        <div>
          <h2 style={{ margin: 0 }}>Sentiment</h2>
          <div style={{ fontSize: 13, opacity: 0.75 }}>Recent history plus live sentiment updates</div>
        </div>

        <label style={{ marginLeft: "auto", display: "flex", flexDirection: "column", gap: 4 }}>
          <span style={{ fontSize: 12, opacity: 0.7 }}>Streamer</span>
          <input
            value={streamerInput}
            onChange={(event) => setStreamerInput(event.target.value)}
            style={{ padding: 8, width: 220 }}
          />
        </label>

        <button onClick={onLoad} style={{ padding: "10px 14px", cursor: "pointer" }}>
          Load sentiment
        </button>
      </div>

      <div style={{ fontSize: 12, opacity: 0.8, marginBottom: 14 }}>
        Status: {subscriptionError ? `subscription error (${subscriptionError.message})` : `live (streamer=${activeStreamer})`}
      </div>

      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(110px, 1fr))",
          gap: 10,
          marginBottom: 16,
        }}
      >
        <MetricCard label="Positive" value={counts.positive} tone="#d1fadf" />
        <MetricCard label="Neutral" value={counts.neutral} tone="#fef0c7" />
        <MetricCard label="Negative" value={counts.negative} tone="#fee4e2" />
        <MetricCard label="Avg score" value={averageScore.toFixed(2)} tone="#dbeafe" />
      </div>

      {loading && events.length === 0 && <div>Loading sentiment history...</div>}

      {error && (
        <div role="alert" style={{ color: "#b42318", marginBottom: 12 }}>
          Failed to load sentiment history: {error.message}
        </div>
      )}

      {!loading && !error && events.length === 0 && <div>No sentiment history yet.</div>}

      <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
        {events.map((event) => (
          <article
            key={event.sentimentEventId}
            style={{
              border: "1px solid #d9e1ec",
              borderRadius: 12,
              padding: 12,
              background: "white",
            }}
          >
            <div style={{ display: "flex", justifyContent: "space-between", gap: 12, marginBottom: 6 }}>
              <div style={{ fontSize: 12, opacity: 0.7 }}>
                [{formatTime(event.chatTimestamp)}] {event.streamer} • source={event.sourceEventId}
              </div>
              <span
                style={{
                  fontSize: 12,
                  fontWeight: 700,
                  color: labelColor(event.label),
                }}
              >
                {event.label}
              </span>
            </div>

            <div style={{ fontWeight: 700 }}>{event.user}</div>
            <div style={{ margin: "4px 0 8px" }}>{event.message}</div>
            <div style={{ display: "flex", gap: 12, flexWrap: "wrap", fontSize: 12, opacity: 0.8 }}>
              <span>score={event.score.toFixed(2)}</span>
              <span>processed={formatTime(event.processedAt)}</span>
              <span>eventId={event.sentimentEventId}</span>
              <span>model={event.modelVersion}</span>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}

function MetricCard({ label, value, tone }: { label: string; value: string | number; tone: string }) {
  return (
    <div style={{ padding: 12, borderRadius: 12, background: tone }}>
      <div style={{ fontSize: 12, opacity: 0.8 }}>{label}</div>
      <div style={{ fontSize: 24, fontWeight: 700 }}>{value}</div>
    </div>
  );
}
