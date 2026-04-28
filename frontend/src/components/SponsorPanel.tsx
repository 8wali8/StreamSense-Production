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

type SponsorPanelProps = {
  streamer?: string;
  hideControls?: boolean;
};

export function SponsorPanel({ streamer, hideControls = false }: SponsorPanelProps) {
  const [streamerInput, setStreamerInput] = useState("test");
  const [localStreamer, setLocalStreamer] = useState("test");
  const [liveEvents, setLiveEvents] = useState<SponsorDetectionEvent[]>([]);
  const activeStreamer = streamer ?? localStreamer;

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
    setLocalStreamer(nextStreamer);
  }

  const averageConfidence =
    events.length === 0 ? 0 : events.reduce((sum, event) => sum + event.confidence, 0) / events.length;

  const fallbackCount = events.filter((event) => event.modelVersion === "fallback").length;
  const recentTrend = events.slice(0, 8);

  return (
    <section className="dashboard-panel">
      <div className="panel-title-row panel-heading">
        <div>
          <div className="eyebrow">Sponsor visibility</div>
          <h2>Sponsors</h2>
          <p>Recent sponsor detections with live auto-reconnect updates for @{activeStreamer}.</p>
        </div>
        <span className="status-pill">{events.length} detections</span>
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

          <button className="button-primary" onClick={onLoad}>Load sponsors</button>
        </div>
      )}

      <div className="status-line">
        Status: {subscriptionError ? `subscription error (${subscriptionError.message})` : `live with auto-reconnect (streamer=${activeStreamer})`}
      </div>

      <div className="metric-grid">
        <MetricCard label="Detections" value={events.length} tone="metric-violet" />
        <MetricCard label="Avg confidence" value={averageConfidence.toFixed(2)} tone="metric-blue" />
        <MetricCard label="Fallbacks" value={fallbackCount} tone="metric-neutral" />
      </div>

      <div className="trend-card">
        <div className="field-label">Confidence trend</div>
        <div className="trend-bars">
          {recentTrend.length === 0 && <div className="muted-text">No confidence data yet.</div>}
          {recentTrend.map((event) => (
            <div className="trend-bar-wrap" key={event.detectionEventId}>
              <div
                title={`${event.sponsor} ${event.confidence.toFixed(2)}`}
                className="trend-bar"
                style={{ height: `${Math.max(10, Math.round(event.confidence * 100))}px`, background: sponsorTone(event.sponsor) }}
              />
              <div className="trend-label">
                {event.sponsor}
              </div>
            </div>
          ))}
        </div>
      </div>

      {loading && events.length === 0 && <div>Loading sponsor history...</div>}

      {error && (
        <div className="error-state" role="alert">
          Failed to load sponsor history: {error.message}
        </div>
      )}

      {!loading && !error && events.length === 0 && <div className="empty-state">No sponsor detections yet.</div>}

      <div className="event-list">
        {events.map((event) => (
          <article className="event-card" key={event.detectionEventId}>
            <div className="event-card-header">
              <div className="event-meta">
                [{formatTime(event.capturedAt)}] seq={event.frameSequence} • frame={event.sourceFrameId}
              </div>
              <span className="category-label" style={{ color: sponsorTone(event.sponsor) }}>{event.sponsor}</span>
            </div>

            <strong>{event.frameRef}</strong>
            <div className="event-tags">
              <span className="tag">confidence={event.confidence.toFixed(2)}</span>
              <span className="tag">processed={formatTime(event.processedAt)}</span>
              <span className="tag">model={event.modelVersion}</span>
              <span className="tag">
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
    <div className={`metric-card ${tone}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}
