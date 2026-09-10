import { describeError } from "../../lib/errors";
import type {
  OnSponsorDetectionSubscription,
  SponsorDetectionsQuery,
  SponsorDetectionsQueryVariables,
} from "../../graphql/generated";
import { RECENT_SPONSOR_DETECTIONS_QUERY } from "../../graphql/queries";
import { ON_SPONSOR_DETECTION_SUBSCRIPTION } from "../../graphql/subscriptions";
import { useLiveFeed } from "../../hooks/useLiveFeed";
import { formatTime } from "../../lib/format";
import { MetricCard } from "../../components/MetricCard";

type SponsorDetectionEvent = SponsorDetectionsQuery["sponsorDetections"][number];

/** Raw sponsor detections with every diagnostic field, for checking what the detector produced. */
export function SponsorPanel({ streamer }: { streamer: string }) {
  const feed = useLiveFeed<
    SponsorDetectionsQuery,
    OnSponsorDetectionSubscription,
    SponsorDetectionsQueryVariables,
    SponsorDetectionEvent
  >({
    query: RECENT_SPONSOR_DETECTIONS_QUERY,
    variables: { streamer, limit: 20 },
    skip: !streamer,
    selectHistory: (data) => data.sponsorDetections,
    subscription: ON_SPONSOR_DETECTION_SUBSCRIPTION,
    subscriptionVariables: { streamer },
    selectEvent: (data) => data.onSponsorDetection,
    getId: (event) => event.detectionEventId,
    limit: 50,
    resetKey: streamer,
  });
  const { items: events, loading, error, subscriptionError } = feed;

  const averageConfidence =
    events.length === 0 ? 0 : events.reduce((sum, event) => sum + event.confidence, 0) / events.length;

  const fallbackCount = events.filter((event) => event.modelVersion === "fallback").length;
  const recentTrend = events.slice(0, 8);

  return (
    <section className="panel ops-section">
      <div className="panel-title-row panel-heading">
        <div>
          <div className="eyebrow">Raw events</div>
          <h2>Sponsor detections</h2>
          <p>Recent sponsor detections with live video-capture updates for @{streamer}.</p>
        </div>
        <span className="status-pill">{events.length} detections</span>
      </div>

      <div className="status-line">
        Status:{" "}
        {subscriptionError
          ? `subscription error (${describeError(subscriptionError)})`
          : `live with auto-reconnect (streamer=${streamer})`}
      </div>

      <div className="metric-grid">
        <MetricCard label="Detections" value={events.length} tone="metric-teal" />
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
                className={`trend-bar${event.sponsor === "UNKNOWN" ? " trend-bar-muted" : ""}`}
                style={{ height: `${Math.max(10, Math.round(event.confidence * 100))}px` }}
              />
              <div className="trend-label">{event.sponsor}</div>
            </div>
          ))}
        </div>
      </div>

      {loading && events.length === 0 && <div>Loading sponsor history...</div>}

      {error && (
        <div className="error-state" role="alert">
          Failed to load sponsor history: {describeError(error)}
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
              <span className="category-label">{event.sponsor}</span>
            </div>

            <strong>{event.frameRef}</strong>
            <div className="event-tags">
              <span className="tag">confidence={event.confidence.toFixed(2)}</span>
              <span className="tag">processed={formatTime(event.processedAt)}</span>
              <span className="tag">model={event.modelVersion}</span>
              {event.source && <span className="tag">source={event.source}</span>}
              {event.streamSessionId && <span className="tag">session={event.streamSessionId}</span>}
              {event.videoTimestampMs != null && (
                <span className="tag">videoTs={Math.round(event.videoTimestampMs / 1000)}s</span>
              )}
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
