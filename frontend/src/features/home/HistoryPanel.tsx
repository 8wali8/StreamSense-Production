import { Link } from "react-router";
import type { SessionsQuery } from "../../graphql/generated";
import { formatCount, formatDuration, formatScore, formatStart } from "../session/report-format";
import { trackRecord } from "./track-record";
import type { SummaryById } from "./useSessionSummaries";

type Session = SessionsQuery["sessions"][number];

type HistoryPanelProps = {
  sessions: Session[];
  summaries: SummaryById;
  sponsor: string;
  loading: boolean;
};

/** S7: the track record across past streams, and the list of them, each linking to its report. */
export function HistoryPanel({ sessions, summaries, sponsor, loading }: HistoryPanelProps) {
  // The rows show the selected sponsor's numbers; the report they open must be about the same sponsor.
  const sponsorQuery = sponsor.trim() === "" ? "" : `?sponsor=${encodeURIComponent(sponsor)}`;
  const finished = sessions.filter((session) => !session.live);
  const record = trackRecord(
    finished
      .map((session) => summaries[session.id])
      .filter((summary) => summary != null)
      .map((summary) => ({
        durationMs: summary.session.durationMs,
        onScreenMs: summary.onScreenMs,
        mentions: summary.mentions,
        mentionSentiment: summary.mentionSentiment,
        averageViewers: summary.averageViewers,
      })),
  );

  return (
    <section className="history" aria-label="History">
      <div className="panel track-record">
        <div className="panel-heading">
          <h2>Track record</h2>
          <p>
            {record.sessions === 0
              ? "Fills in as streams finish."
              : `Across ${record.sessions} sponsored stream${record.sessions === 1 ? "" : "s"}`}
          </p>
        </div>
        <div className="stat">
          <div className="v tone-brand-text">
            {record.onScreenMsPerHour == null ? "–" : formatDuration(record.onScreenMsPerHour)}
          </div>
          <div className="l">{sponsor} on screen per sponsored hour</div>
        </div>
        <div className="stat">
          <div className="v">{formatScore(record.mentionSentiment)}</div>
          <div className="l">Mention sentiment, average</div>
          <div className="s">{formatCount(record.totalMentions)} mentions in total</div>
        </div>
        <div className="stat">
          <div className="v">{formatCount(record.averageViewers)}</div>
          <div className="l">Average viewers on sponsored streams</div>
        </div>
      </div>

      <div className="panel past-streams">
        <div className="panel-heading">
          <h2>Past streams</h2>
        </div>
        <div className="history-head">
          <span>Stream</span>
          <span>On screen</span>
          <span>Mentions</span>
          <span>Viewers</span>
        </div>
        {loading && finished.length === 0 && <div className="empty-state">Loading streams...</div>}
        {!loading && finished.length === 0 && <div className="empty-state">No finished streams yet.</div>}
        {finished.map((session) => {
          const summary = summaries[session.id];
          return (
            <Link className="history-row" to={`/sessions/${session.id}${sponsorQuery}`} key={session.id}>
              <span>
                <strong>{session.title ?? "Untitled stream"}</strong>
                <small>
                  {formatStart(session.startedAt)} · {formatDuration(session.durationMs)}
                </small>
              </span>
              <span className="mono">{summary ? formatDuration(summary.onScreenMs) : "–"}</span>
              <span className="mono">
                {summary ? formatCount(summary.mentions) : "–"}
                {summary?.mentionSentiment != null && (
                  <em className={summary.mentionSentiment >= 0.4 ? "tone-good" : "tone-warn"}>
                    {" "}
                    · {formatScore(summary.mentionSentiment)}
                  </em>
                )}
              </span>
              <span className="mono">{formatCount(session.averageViewers)}</span>
            </Link>
          );
        })}
      </div>
    </section>
  );
}
