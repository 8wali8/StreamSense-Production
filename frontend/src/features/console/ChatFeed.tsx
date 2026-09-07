import { analysisClass, formatScore, formatTime } from "../../lib/format";
import type { ChatMessageEvent, SentimentEvent } from "./useConsoleFeeds";

type ChatFeedProps = {
  liveChat: ChatMessageEvent[];
  chatSentiments: SentimentEvent[];
  loading: boolean;
};

/** Raw audience messages as they arrive, followed by the analysed ones. */
export function ChatFeed({ liveChat, chatSentiments, loading }: ChatFeedProps) {
  return (
    <section className="sidecar-panel chat-feed">
      <div className="sidecar-heading">
        <div>
          <span className="eyebrow">Audience layer</span>
          <h2>Chat + sentiment</h2>
        </div>
        <span className="status-pill">{liveChat.length + chatSentiments.length} signals</span>
      </div>

      <div className="feed-stack compact-feed">
        {liveChat.slice(0, 5).map((event) => (
          <article className="chat-line" key={event.eventId}>
            <div className="line-meta">
              <span>{formatTime(event.timestamp)}</span>
              <span>{event.user}</span>
              <span className="analysis-chip analysis-live">raw</span>
            </div>
            <p>{event.message}</p>
          </article>
        ))}

        {chatSentiments.map((event) => (
          <article className="chat-line" key={event.sentimentEventId}>
            <div className="line-meta">
              <span>{formatTime(event.chatTimestamp)}</span>
              <span>{event.user}</span>
              <span className={`analysis-chip ${analysisClass(event.label)}`}>{event.label} {formatScore(event.score)}</span>
            </div>
            <p>{event.message}</p>
          </article>
        ))}

        {!loading && liveChat.length === 0 && chatSentiments.length === 0 && <div className="empty-state">No chat signals yet.</div>}
      </div>
    </section>
  );
}
