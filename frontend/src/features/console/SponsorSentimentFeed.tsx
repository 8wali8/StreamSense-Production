import { analysisClass, formatScore, formatTime, matchedContext } from "../../lib/format";
import type { SentimentEvent, TranscriptSentimentEvent } from "./useConsoleFeeds";

type SponsorSentimentFeedProps = {
  activeSponsor: string;
  sponsorBrand: string;
  chatSentiments: SentimentEvent[];
  transcriptSentiments: TranscriptSentimentEvent[];
  loading: boolean;
};

/** Chat and transcript sentiment that the relevance model tied to the active sponsor. */
export function SponsorSentimentFeed({
  activeSponsor,
  sponsorBrand,
  chatSentiments,
  transcriptSentiments,
  loading,
}: SponsorSentimentFeedProps) {
  return (
    <section className="sidecar-panel chat-feed">
      <div className="sidecar-heading">
        <div>
          <span className="eyebrow">Sponsor-specific tone</span>
          <h2>{activeSponsor || sponsorBrand} sentiment</h2>
        </div>
        <span className="status-pill">{chatSentiments.length + transcriptSentiments.length} relevant</span>
      </div>

      <div className="feed-stack compact-feed">
        {chatSentiments.map((event) => (
          <article className="chat-line" key={event.sentimentEventId}>
            <div className="line-meta">
              <span>{formatTime(event.chatTimestamp)}</span>
              <span>{event.user}</span>
              <span className={`analysis-chip ${analysisClass(event.label)}`}>
                {event.label} {formatScore(event.score)}
              </span>
              <span className="analysis-chip analysis-live">{formatScore(event.relevanceScore)} match</span>
            </div>
            <p>{event.message}</p>
            <div className="line-meta">Matched {matchedContext(event, activeSponsor)}</div>
          </article>
        ))}

        {transcriptSentiments.map((event) => (
          <article className="transcript-line" key={event.sentimentEventId}>
            <div className="line-meta">
              <span>{formatTime(event.segmentEndedAt)}</span>
              <span>transcript</span>
              <span className={`analysis-chip ${analysisClass(event.label)}`}>
                {event.label} {formatScore(event.score)}
              </span>
              <span className="analysis-chip analysis-live">{formatScore(event.relevanceScore)} match</span>
            </div>
            <p>{event.text}</p>
            <div className="line-meta">Matched {matchedContext(event, activeSponsor)}</div>
          </article>
        ))}

        {!loading && chatSentiments.length === 0 && transcriptSentiments.length === 0 && (
          <div className="empty-state">No sponsor-related sentiment yet.</div>
        )}
      </div>
    </section>
  );
}
