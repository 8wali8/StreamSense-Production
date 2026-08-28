import { useState } from "react";
import { useQuery } from "@apollo/client/react";
import type { RecommendationsQuery } from "../graphql/generated";
import { RECOMMENDATIONS_QUERY } from "../graphql/queries";

function categoryTone(category: string): string {
  if (category === "SPONSOR_ALIGNMENT") return "#7c3aed";
  if (category === "CONTENT_MOMENTUM") return "#157f3b";
  if (category === "AUDIENCE_TONE") return "#1d4ed8";
  return "#b42318";
}

function scoreTone(score: number): string {
  if (score >= 0.75) return "#d1fadf";
  if (score >= 0.5) return "#dbeafe";
  if (score >= 0.3) return "#fef0c7";
  return "#fee4e2";
}

function formatTime(ts: number): string {
  return new Date(ts).toLocaleTimeString();
}

type RecommendationPanelProps = {
  streamer?: string;
  hideControls?: boolean;
};

export function RecommendationPanel({ streamer, hideControls = false }: RecommendationPanelProps) {
  const [streamerInput, setStreamerInput] = useState("test");
  const [localStreamer, setLocalStreamer] = useState("test");
  const activeStreamer = streamer ?? localStreamer;

  const { data, loading, error } = useQuery<RecommendationsQuery>(RECOMMENDATIONS_QUERY, {
    variables: { streamer: activeStreamer, limit: 4 },
    skip: !activeStreamer,
    fetchPolicy: "network-only",
  });

  const recommendations = data?.recommendations ?? [];
  const strongestScore = recommendations[0]?.score ?? 0;
  const cautionCount = recommendations.filter((recommendation) => recommendation.category === "CAUTION_SIGNAL").length;
  const activeVariant = recommendations[0]?.variantId ?? "n/a";

  function onLoad() {
    const nextStreamer = streamerInput.trim();
    if (!nextStreamer) {
      return;
    }
    setLocalStreamer(nextStreamer);
  }

  return (
    <section className="dashboard-panel">
      <div className="panel-title-row panel-heading">
        <div>
          <div className="eyebrow">Action plan</div>
          <h2>Recommendations</h2>
          <p>Explainable guidance powered by recent sentiment, sponsors, and experiment config.</p>
        </div>
        <span className="status-pill">@{activeStreamer}</span>
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

          <button className="button-primary" onClick={onLoad}>Load recommendations</button>
        </div>
      )}

      <div className="metric-grid">
        <MetricCard label="Recommendations" value={recommendations.length} tone="metric-violet" />
        <MetricCard label="Strongest score" value={strongestScore.toFixed(2)} tone={scoreClass(strongestScore)} />
        <MetricCard label="Caution signals" value={cautionCount} tone="metric-negative" />
        <MetricCard label="Variant" value={activeVariant} tone="metric-blue" />
      </div>

      {loading && recommendations.length === 0 && <div>Loading recommendations...</div>}

      {error && (
        <div className="error-state" role="alert">
          Failed to load recommendations: {error.message}
        </div>
      )}

      {!loading && !error && recommendations.length === 0 && <div className="empty-state">No recommendations yet.</div>}

      <div className="event-list">
        {recommendations.map((recommendation) => (
          <article className="event-card" key={recommendation.recommendationId}>
            <div className="event-card-header">
              <div>
                <div className="event-meta">
                  [{formatTime(recommendation.generatedAt)}] {recommendation.streamer} • {recommendation.experimentName}
                </div>
                <strong>{recommendation.title}</strong>
              </div>

              <div className="event-tags">
                <span className="category-label" style={{ color: categoryTone(recommendation.category) }}>{recommendation.category}</span>
                <span className="tag" style={{ background: scoreTone(recommendation.score), color: "#07111f" }}>
                  {recommendation.score.toFixed(2)}
                </span>
              </div>
            </div>

            <p>{recommendation.reasonSummary}</p>
            <div className="event-tags"><span className="tag">Variant: {recommendation.variantId}</span></div>
            <ul className="recommendation-reasons">
              {recommendation.reasons.map((reason) => (
                <li key={reason}>{reason}</li>
              ))}
            </ul>
          </article>
        ))}
      </div>
    </section>
  );
}

function scoreClass(score: number): string {
  if (score >= 0.75) return "metric-positive";
  if (score >= 0.5) return "metric-blue";
  if (score >= 0.3) return "metric-neutral";
  return "metric-negative";
}

function MetricCard({ label, value, tone }: { label: string; value: string | number; tone: string }) {
  return (
    <div className={`metric-card ${tone}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}
