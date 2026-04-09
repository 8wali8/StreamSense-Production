import { useState } from "react";
import { useQuery, useSubscription } from "@apollo/client/react";
import { RECENT_SPONSOR_DETECTIONS_QUERY } from "../graphql/queries";
import { ON_SPONSOR_DETECTION_SUBSCRIPTION } from "../graphql/subscriptions";

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
};

type RecentSponsorDetectionsData = {
  sponsorDetections: SponsorDetectionEvent[];
};

type OnSponsorDetectionData = {
  onSponsorDetection: SponsorDetectionEvent;
};

function formatTime(ts: number): string {
  return new Date(ts).toLocaleTimeString();
}

function sponsorTone(sponsor: string): string {
  if (sponsor === "UNKNOWN") return "#7a5c00";
  if (sponsor === "Nike") return "#0f172a";
  if (sponsor === "Red Bull") return "#1d4ed8";
  if (sponsor === "Razer") return "#157f3b";
  if (sponsor === "Prime") return "#7c3aed";
  return "#0f766e";
}

export function SponsorPanel() {
  const [streamerInput, setStreamerInput] = useState("test");
  const [activeStreamer, setActiveStreamer] = useState("test");
  const [liveEvents, setLiveEvents] = useState<SponsorDetectionEvent[]>([]);

  const { data, loading, error } = useQuery<RecentSponsorDetectionsData>(RECENT_SPONSOR_DETECTIONS_QUERY, {
    variables: { streamer: activeStreamer, limit: 20 },
    skip: !activeStreamer,
    fetchPolicy: "network-only",
  });

  const historyEvents = data?.sponsorDetections ?? [];
  const historyIds = new Set(historyEvents.map((event) => event.detectionEventId));
  const events = [...liveEvents.filter((event) => !historyIds.has(event.detectionEventId)), ...historyEvents].slice(0, 50);

  const { error: subscriptionError } = useSubscription<OnSponsorDetectionData>(ON_SPONSOR_DETECTION_SUBSCRIPTION, {
    variables: { streamer: activeStreamer },
    skip: !activeStreamer,
    onData: ({ data: subscriptionData }) => {
      const event = subscriptionData.data?.onSponsorDetection;
      if (!event) {
        return;
      }

      setLiveEvents((prev) => {
        if (historyIds.has(event.detectionEventId) || prev.some((existing) => existing.detectionEventId === event.detectionEventId)) {
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

  const averageConfidence =
    events.length === 0 ? 0 : events.reduce((sum, event) => sum + event.confidence, 0) / events.length;

  const fallbackCount = events.filter((event) => event.modelVersion === "fallback").length;
  const recentTrend = events.slice(0, 8);

  return (
    <section
      style={{
        border: "1px solid #d9e1ec",
        borderRadius: 16,
        padding: 16,
        background: "#fff8f3",
        minHeight: 420,
      }}
    >
      <div style={{ display: "flex", flexWrap: "wrap", gap: 12, alignItems: "center", marginBottom: 16 }}>
        <div>
          <h2 style={{ margin: 0 }}>Sponsors</h2>
          <div style={{ fontSize: 13, opacity: 0.75 }}>Recent sponsor detections with live auto-reconnect updates</div>
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
          Load sponsors
        </button>
      </div>

      <div style={{ fontSize: 12, opacity: 0.8, marginBottom: 14 }}>
        Status: {subscriptionError ? `subscription error (${subscriptionError.message})` : `live with auto-reconnect (streamer=${activeStreamer})`}
      </div>

      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(110px, 1fr))",
          gap: 10,
          marginBottom: 16,
        }}
      >
        <MetricCard label="Detections" value={events.length} tone="#fde7d9" />
        <MetricCard label="Avg confidence" value={averageConfidence.toFixed(2)} tone="#dbeafe" />
        <MetricCard label="Fallbacks" value={fallbackCount} tone="#fef0c7" />
      </div>

      <div style={{ marginBottom: 16 }}>
        <div style={{ fontSize: 12, opacity: 0.75, marginBottom: 8 }}>Confidence trend</div>
        <div style={{ display: "flex", alignItems: "end", gap: 6, minHeight: 90 }}>
          {recentTrend.length === 0 && <div style={{ opacity: 0.7 }}>No confidence data yet.</div>}
          {recentTrend.map((event) => (
            <div key={event.detectionEventId} style={{ flex: 1, minWidth: 0 }}>
              <div
                title={`${event.sponsor} ${event.confidence.toFixed(2)}`}
                style={{
                  height: `${Math.max(10, Math.round(event.confidence * 100))}px`,
                  borderRadius: 8,
                  background: sponsorTone(event.sponsor),
                }}
              />
              <div style={{ marginTop: 4, fontSize: 11, opacity: 0.7, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                {event.sponsor}
              </div>
            </div>
          ))}
        </div>
      </div>

      {loading && events.length === 0 && <div>Loading sponsor history...</div>}

      {error && (
        <div role="alert" style={{ color: "#b42318", marginBottom: 12 }}>
          Failed to load sponsor history: {error.message}
        </div>
      )}

      {!loading && !error && events.length === 0 && <div>No sponsor detections yet.</div>}

      <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
        {events.map((event) => (
          <article
            key={event.detectionEventId}
            style={{
              border: "1px solid #ead8c8",
              borderRadius: 12,
              padding: 12,
              background: "white",
            }}
          >
            <div style={{ display: "flex", justifyContent: "space-between", gap: 12, marginBottom: 6 }}>
              <div style={{ fontSize: 12, opacity: 0.7 }}>
                [{formatTime(event.capturedAt)}] seq={event.frameSequence} • frame={event.sourceFrameId}
              </div>
              <span style={{ fontSize: 12, fontWeight: 700, color: sponsorTone(event.sponsor) }}>{event.sponsor}</span>
            </div>

            <div style={{ fontSize: 14, fontWeight: 700, marginBottom: 6 }}>{event.frameRef}</div>
            <div style={{ display: "flex", gap: 12, flexWrap: "wrap", fontSize: 12, opacity: 0.8 }}>
              <span>confidence={event.confidence.toFixed(2)}</span>
              <span>processed={formatTime(event.processedAt)}</span>
              <span>model={event.modelVersion}</span>
              <span>
                box={event.x.toFixed(2)},{event.y.toFixed(2)} {event.width.toFixed(2)}x{event.height.toFixed(2)}
              </span>
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
