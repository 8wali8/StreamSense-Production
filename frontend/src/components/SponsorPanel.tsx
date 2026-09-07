import { useState } from "react";
import type { OnSponsorDetectionSubscription, SponsorDetectionsQuery, SponsorDetectionsQueryVariables } from "../graphql/generated";
import { RECENT_SPONSOR_DETECTIONS_QUERY } from "../graphql/queries";
import { ON_SPONSOR_DETECTION_SUBSCRIPTION } from "../graphql/subscriptions";
import { useLiveFeed } from "../hooks/useLiveFeed";
import { formatTime } from "../lib/format";
import { MetricCard } from "./MetricCard";

type SponsorDetectionEvent = SponsorDetectionsQuery["sponsorDetections"][number];

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
  const activeStreamer = streamer ?? localStreamer;

  const feed = useLiveFeed<SponsorDetectionsQuery, OnSponsorDetectionSubscription, SponsorDetectionsQueryVariables, SponsorDetectionEvent>({
    query: RECENT_SPONSOR_DETECTIONS_QUERY,
    variables: { streamer: activeStreamer, limit: 20 },
    skip: !activeStreamer,
    selectHistory: (data) => data.sponsorDetections,
    subscription: ON_SPONSOR_DETECTION_SUBSCRIPTION,
    subscriptionVariables: { streamer: activeStreamer },
    selectEvent: (data) => data.onSponsorDetection,
    getId: (event) => event.detectionEventId,
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
          <p>Recent sponsor detections with live video-capture updates for @{activeStreamer}.</p>
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
              {event.source && <span className="tag">source={event.source}</span>}
              {event.streamSessionId && <span className="tag">session={event.streamSessionId}</span>}
              {event.videoTimestampMs != null && <span className="tag">videoTs={Math.round(event.videoTimestampMs / 1000)}s</span>}
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
