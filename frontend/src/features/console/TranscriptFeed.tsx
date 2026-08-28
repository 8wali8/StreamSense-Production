import { analysisClass, formatScore, formatTime, matchedContext } from "../../lib/format";
import type { TranscriptLine } from "./transcript-lines";
import type { TranscriptSentimentEvent } from "./useConsoleFeeds";

type TranscriptFeedProps = {
  lines: TranscriptLine<TranscriptSentimentEvent>[];
  activeSponsor: string;
  loading: boolean;
  error: string | undefined;
};

/** Streamer audio: every transcript line with its sentiment and sponsor read. */
export function TranscriptFeed({ lines, activeSponsor, loading, error }: TranscriptFeedProps) {
  const sponsorCount = lines.filter((line) => line.sponsorAnalysis).length;

  return (
    <section className="sidecar-panel transcript-feed">
      <div className="sidecar-heading">
        <div>
          <span className="eyebrow">Streamer audio</span>
          <h2>All transcript</h2>
        </div>
        <span className="status-pill">
          {lines.length} lines · {sponsorCount} sponsor
        </span>
      </div>

      <div className="feed-stack">
        {error && lines.length === 0 && <div className="error-state">Failed to load transcript: {error}</div>}
        {loading && lines.length === 0 && <div className="empty-state">Loading transcript...</div>}
        {!loading && !error && lines.length === 0 && <div className="empty-state">No transcript yet.</div>}
        {lines.map((line) => {
          const analysis = line.analysis;
          const sponsorAnalysis = line.sponsorAnalysis;
          return (
            <article className={`transcript-line${sponsorAnalysis ? " transcript-line-sponsor" : ""}`} key={line.id}>
              <div className="line-meta">
                <span>{formatTime(line.at)}</span>
                <span>seq {line.sequence}</span>
                {line.source && <span>{line.source}</span>}
                <span className={`analysis-chip ${analysisClass(analysis?.label)}`}>
                  {analysis?.label ?? "pending"}
                </span>
                {sponsorAnalysis && (
                  <span className="analysis-chip analysis-live">
                    sponsor {formatScore(sponsorAnalysis.relevanceScore)}
                  </span>
                )}
              </div>
              <p>{line.text}</p>
              {sponsorAnalysis && (
                <div className="line-meta">Matched {matchedContext(sponsorAnalysis, activeSponsor)}</div>
              )}
            </article>
          );
        })}
      </div>
    </section>
  );
}
