import { describeError } from "../../lib/errors";
import { useQuery } from "@apollo/client/react";
import { MetricCard } from "../../components/MetricCard";
import type { StreamAnalyticsQuery, StreamAnalyticsQueryVariables } from "../../graphql/generated";
import { STREAM_ANALYTICS_QUERY } from "../../graphql/queries";

type StreamMetricsSummary = StreamAnalyticsQuery["streamMetricsSummary"];
type SentimentMetricSummary = StreamMetricsSummary["chatSentiment"];

type StreamMetricsOverviewProps = {
  streamer: string;
};

export function StreamMetricsOverview({ streamer }: StreamMetricsOverviewProps) {
  const { data, loading, error } = useQuery<StreamAnalyticsQuery, StreamAnalyticsQueryVariables>(
    STREAM_ANALYTICS_QUERY,
    {
      variables: { streamer, windowMinutes: 15, bucketSeconds: 60 },
      skip: !streamer,
      fetchPolicy: "network-only",
      pollInterval: 15000,
    },
  );

  const summary = data?.streamMetricsSummary;
  const buckets = data?.streamMetricsTimeseries ?? [];
  const latestBuckets = buckets.slice(-12);
  const topSponsor = summary?.sponsorExposure.topSponsors[0];

  return (
    <section id="stream-metrics" className="dashboard-panel stream-metrics-panel" aria-label="Stream metrics overview">
      <div className="panel-title-row panel-heading">
        <div>
          <div className="eyebrow">Product analytics</div>
          <h2>Stream Metrics Overview</h2>
          <p>
            Backend aggregates over chat, sentiment, transcript voice sentiment, and sponsor detections for @{streamer}.
          </p>
        </div>
        <span className="status-pill">{summary ? `${summary.windowMinutes}m window` : "waiting"}</span>
      </div>

      {loading && !summary && <div className="empty-state">Loading aggregate metrics...</div>}

      {error && (
        <div className="error-state" role="alert">
          Failed to load aggregate metrics: {describeError(error)}
        </div>
      )}

      {summary && (
        <>
          <div className="status-line">
            Status: {summary.dataQuality.lowData ? "low data" : "aggregating"}
            {summary.dataQuality.aggregationLagMs != null
              ? ` • lag=${Math.round(summary.dataQuality.aggregationLagMs / 1000)}s`
              : ""}
          </div>

          <div className="metric-grid">
            <MetricCard label="Chat/min" value={summary.chat.messagesPerMinute.toFixed(1)} tone="metric-blue" />
            <MetricCard label="Unique chatters" value={summary.chat.uniqueChatters} tone="metric-violet" />
            <MetricCard
              label="Risk"
              value={riskLabel(summary.risk.level, summary.risk.score)}
              tone={riskTone(summary.risk.level)}
            />
            <MetricCard
              label="Sponsor exposure"
              value={formatDuration(summary.sponsorExposure.estimatedExposureMs)}
              tone="metric-positive"
            />
          </div>

          <div className="analytics-layout">
            <div className="trend-card">
              <div className="field-label">Chat volume trend</div>
              <div className="trend-bars analytics-bars">
                {latestBuckets.length === 0 && <div className="muted-text">No metric buckets yet.</div>}
                {latestBuckets.map((bucket) => (
                  <div className="trend-bar-wrap" key={bucket.bucketStart}>
                    <div
                      className={`trend-bar${bucket.engagementSpike ? " analytics-spike-bar" : ""}`}
                      title={`${formatTime(bucket.bucketStart)} chat=${bucket.chatMessageCount}`}
                      style={{ height: `${Math.max(8, Math.min(112, bucket.chatMessageCount * 8))}px` }}
                    />
                    <div className="trend-label">{bucket.chatMessageCount}</div>
                  </div>
                ))}
              </div>
            </div>

            <div className="trend-card analytics-stack">
              <MetricRow label="Chat sentiment" value={formatSentiment(summary.chatSentiment)} />
              <MetricRow label="Voice sentiment" value={formatSentiment(summary.transcriptSentiment)} />
              <MetricRow label="Engagement spikes" value={`${summary.engagement.spikeCount}`} />
              <MetricRow
                label="Top sponsor"
                value={
                  topSponsor ? `${topSponsor.sponsor} (${formatDuration(topSponsor.estimatedExposureMs)})` : "None yet"
                }
              />
            </div>
          </div>

          {summary.risk.factors.length > 0 && (
            <div className="event-tags analytics-factors" aria-label="Risk factors">
              {summary.risk.factors.map((factor) => (
                <span className="tag" key={factor.name}>
                  {factor.name}={factor.value.toFixed(2)} w={factor.weight.toFixed(2)}
                </span>
              ))}
            </div>
          )}
        </>
      )}
    </section>
  );
}

function MetricRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="analytics-row">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function formatSentiment(summary: SentimentMetricSummary): string {
  const total = summary.positive + summary.neutral + summary.negative;
  if (total === 0 || summary.averageScore == null) return "No data";
  return `${summary.averageScore.toFixed(2)} avg, ${Math.round((summary.negativeRatio ?? 0) * 100)}% negative`;
}

function formatDuration(ms: number): string {
  if (ms <= 0) return "0s";
  const seconds = Math.round(ms / 1000);
  if (seconds < 60) return `${seconds}s`;
  return `${Math.floor(seconds / 60)}m ${seconds % 60}s`;
}

function formatTime(ts: number): string {
  return new Date(ts).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

function riskLabel(level: string, score?: number | null): string {
  if (score == null) return level;
  return `${level} ${score.toFixed(2)}`;
}

function riskTone(level: string): string {
  if (level === "HIGH") return "metric-negative";
  if (level === "MEDIUM") return "metric-neutral";
  if (level === "LOW_DATA") return "metric-violet";
  return "metric-positive";
}
