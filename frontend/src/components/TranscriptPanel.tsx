import { useState } from "react";
import { useQuery, useSubscription } from "@apollo/client/react";
import { RECENT_TRANSCRIPT_SEGMENTS_QUERY } from "../graphql/queries";
import { ON_TRANSCRIPT_SEGMENT_SUBSCRIPTION } from "../graphql/subscriptions";
import type {
  OnTranscriptSegmentSubscription,
  OnTranscriptSegmentSubscriptionVariables,
  RecentTranscriptSegmentsQuery,
  RecentTranscriptSegmentsQueryVariables,
} from "../graphql/generated";

type TranscriptSegmentEvent = RecentTranscriptSegmentsQuery["recentTranscriptSegments"][number];

type TranscriptPanelProps = {
  streamer?: string;
  hideControls?: boolean;
};

function formatTime(ts: number): string {
  return new Date(ts).toLocaleTimeString();
}

export function TranscriptPanel({ streamer, hideControls = false }: TranscriptPanelProps) {
  const [streamerInput, setStreamerInput] = useState("test");
  const [localStreamer, setLocalStreamer] = useState("test");
  const [liveSegments, setLiveSegments] = useState<TranscriptSegmentEvent[]>([]);
  const activeStreamer = streamer ?? localStreamer;

  const { data, loading, error } = useQuery<RecentTranscriptSegmentsQuery, RecentTranscriptSegmentsQueryVariables>(RECENT_TRANSCRIPT_SEGMENTS_QUERY, {
    variables: { streamer: activeStreamer, limit: 20 },
    skip: !activeStreamer,
    fetchPolicy: "network-only",
  });

  const historySegments = data?.recentTranscriptSegments ?? [];
  const historyIds = new Set(historySegments.map((event) => event.segmentId));
  const segments = [...liveSegments.filter((event) => !historyIds.has(event.segmentId)), ...historySegments].slice(0, 50);

  const { error: subscriptionError } = useSubscription<OnTranscriptSegmentSubscription, OnTranscriptSegmentSubscriptionVariables>(ON_TRANSCRIPT_SEGMENT_SUBSCRIPTION, {
    variables: { streamer: activeStreamer },
    skip: !activeStreamer,
    onData: ({ data: subscriptionData }) => {
      const event = subscriptionData.data?.onTranscriptSegment;
      if (!event) {
        return;
      }

      setLiveSegments((prev) => {
        if (historyIds.has(event.segmentId) || prev.some((existing) => existing.segmentId === event.segmentId)) {
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
    setLiveSegments([]);
    setLocalStreamer(nextStreamer);
  }

  return (
    <section className="dashboard-panel transcript-panel">
      <div className="panel-title-row panel-heading">
        <div>
          <div className="eyebrow">Streamer voice</div>
          <h2>Transcript</h2>
          <p>Local Whisper transcript segments for @{activeStreamer}, separate from chat.</p>
        </div>
        <span className="status-pill">{segments.length} segments</span>
      </div>

      {!hideControls && (
        <div className="panel-actions">
          <label>
            <span className="field-label">Streamer</span>
            <input className="text-input" value={streamerInput} onChange={(event) => setStreamerInput(event.target.value)} />
          </label>
          <button className="button-primary" onClick={onLoad}>Load transcript</button>
        </div>
      )}

      <div className="status-line">
        Status: {subscriptionError ? `subscription error (${subscriptionError.message})` : `live transcript (streamer=${activeStreamer})`}
      </div>

      {loading && segments.length === 0 && <div>Loading transcript history...</div>}
      {error && <div className="error-state" role="alert">Failed to load transcript history: {error.message}</div>}
      {!loading && !error && segments.length === 0 && <div className="empty-state">No transcript segments yet.</div>}

      <div className="event-list transcript-list">
        {segments.map((segment) => (
          <article className="event-card transcript-card" key={segment.segmentId}>
            <div className="event-card-header">
              <div className="event-meta">
                [{formatTime(segment.startedAt)}-{formatTime(segment.endedAt)}] seq={segment.transcriptSequence}
              </div>
              <span className="tag">{segment.language ?? "unknown"}</span>
            </div>
            <p>{segment.text}</p>
            <div className="event-tags">
              <span className="tag">confidence={segment.confidence?.toFixed(2) ?? "n/a"}</span>
              <span className="tag">model={segment.modelVersion}</span>
              <span className="tag">session={segment.streamSessionId}</span>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}
