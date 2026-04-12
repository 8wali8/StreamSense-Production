import { useState } from "react";
import { useQuery } from "@apollo/client/react";
import { RECOMMENDATIONS_QUERY } from "../graphql/queries";

type Recommendation = {
  recommendationId: string;
  streamer: string;
  title: string;
  category: string;
  score: number;
  reasonSummary: string;
  reasons: string[];
  experimentName: string;
  variantId: string;
  generatedAt: number;
};

type RecommendationsData = {
  recommendations: Recommendation[];
};

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

export function RecommendationPanel() {
  const [streamerInput, setStreamerInput] = useState("test");
  const [activeStreamer, setActiveStreamer] = useState("test");

  const { data, loading, error } = useQuery<RecommendationsData>(RECOMMENDATIONS_QUERY, {
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
    setActiveStreamer(nextStreamer);
  }

  return (
    <section
      style={{
        border: "1px solid #ddd6fe",
        borderRadius: 16,
        padding: 16,
        background: "#faf5ff",
        minHeight: 420,
      }}
    >
      <div style={{ display: "flex", flexWrap: "wrap", gap: 12, alignItems: "center", marginBottom: 16 }}>
        <div>
          <h2 style={{ margin: 0 }}>Recommendations</h2>
          <div style={{ fontSize: 13, opacity: 0.75 }}>Explainable guidance powered by recent sentiment, sponsors, and experiment config</div>
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
          Load recommendations
        </button>
      </div>

      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(110px, 1fr))",
          gap: 10,
          marginBottom: 16,
        }}
      >
        <MetricCard label="Recommendations" value={recommendations.length} tone="#ede9fe" />
        <MetricCard label="Strongest score" value={strongestScore.toFixed(2)} tone={scoreTone(strongestScore)} />
        <MetricCard label="Caution signals" value={cautionCount} tone="#fee4e2" />
        <MetricCard label="Variant" value={activeVariant} tone="#dbeafe" />
      </div>

      {loading && recommendations.length === 0 && <div>Loading recommendations...</div>}

      {error && (
        <div role="alert" style={{ color: "#b42318", marginBottom: 12 }}>
          Failed to load recommendations: {error.message}
        </div>
      )}

      {!loading && !error && recommendations.length === 0 && <div>No recommendations yet.</div>}

      <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
        {recommendations.map((recommendation) => (
          <article
            key={recommendation.recommendationId}
            style={{
              border: "1px solid #ddd6fe",
              borderRadius: 12,
              padding: 12,
              background: "white",
            }}
          >
            <div style={{ display: "flex", justifyContent: "space-between", gap: 12, marginBottom: 6, flexWrap: "wrap" }}>
              <div>
                <div style={{ fontSize: 12, opacity: 0.7 }}>
                  [{formatTime(recommendation.generatedAt)}] {recommendation.streamer} • {recommendation.experimentName}
                </div>
                <div style={{ fontWeight: 700, marginTop: 4 }}>{recommendation.title}</div>
              </div>

              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <span style={{ fontSize: 12, fontWeight: 700, color: categoryTone(recommendation.category) }}>{recommendation.category}</span>
                <span style={{ padding: "6px 10px", borderRadius: 999, background: scoreTone(recommendation.score), fontWeight: 700 }}>
                  {recommendation.score.toFixed(2)}
                </span>
              </div>
            </div>

            <div style={{ marginBottom: 10 }}>{recommendation.reasonSummary}</div>
            <div style={{ fontSize: 12, opacity: 0.75, marginBottom: 8 }}>Variant: {recommendation.variantId}</div>
            <ul style={{ margin: 0, paddingLeft: 18, display: "flex", flexDirection: "column", gap: 6 }}>
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

function MetricCard({ label, value, tone }: { label: string; value: string | number; tone: string }) {
  return (
    <div style={{ padding: 12, borderRadius: 12, background: tone }}>
      <div style={{ fontSize: 12, opacity: 0.8 }}>{label}</div>
      <div style={{ fontSize: 24, fontWeight: 700 }}>{value}</div>
    </div>
  );
}
