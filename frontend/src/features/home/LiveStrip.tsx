import { Link } from "react-router";
import type { SessionsQuery, SessionSummaryQuery } from "../../graphql/generated";
import { formatCount, formatDuration, formatScore, formatShare, formatStart } from "../session/report-format";

type Session = SessionsQuery["sessions"][number];
type Summary = NonNullable<SessionSummaryQuery["sessionSummary"]>;

type LiveStripProps = {
  session: Session | null;
  summary: Summary | null;
  sponsor: string;
  loading: boolean;
};

function riskClass(level: string): string {
  if (level === "HIGH") return "pill pill-high";
  if (level === "MEDIUM") return "pill pill-medium";
  if (level === "LOW") return "pill pill-low";
  return "pill pill-dim";
}

/** S1: is the sponsor getting what was promised, right now. Offline, it points at the last report. */
export function LiveStrip({ session, summary, sponsor, loading }: LiveStripProps) {
  if (!session) {
    return (
      <section className="panel live-strip live-strip-offline" aria-label="Live status">
        <span className="pill pill-dim">Offline</span>
        <div>
          <strong>{loading ? "Checking for a stream..." : "No streams recorded yet"}</strong>
          <span>{loading ? "" : "The live strip fills in once capture has run for this channel."}</span>
        </div>
      </section>
    );
  }

  if (!session.live) {
    return (
      <section className="panel live-strip live-strip-offline" aria-label="Live status">
        <span className="pill pill-dim">Offline</span>
        <div>
          <strong>
            Last stream ended {session.endedAt ? formatStart(session.endedAt) : formatStart(session.startedAt)}
          </strong>
          <span>
            {session.title ?? "Untitled stream"} · {formatDuration(session.durationMs)}
          </span>
        </div>
        <Link className="button-primary button-sm" to={`/sessions/${session.id}`}>
          View session report
        </Link>
      </section>
    );
  }

  return (
    <section className="panel live-strip" aria-label="Live status">
      <div className="live-strip-head">
        <span className="pill pill-live">
          <i className="live-pulse" /> LIVE
        </span>
        <strong>{session.title ?? "Live now"}</strong>
        <span className="mono">{formatDuration(session.durationMs)}</span>
        <span className="pill pill-teal">{sponsor}</span>
        <span className="live-strip-viewers mono">
          {session.averageViewers == null ? "viewers pending" : `${formatCount(session.averageViewers)} avg viewers`}
        </span>
      </div>
      <div className="live-strip-stats">
        <div className="stat">
          <div className="v tone-brand-text">{summary ? formatDuration(summary.onScreenMs) : "–"}</div>
          <div className="l">{sponsor} on screen so far</div>
          <div className="s">{summary ? `${formatShare(summary.onScreenShare)} of stream` : ""}</div>
        </div>
        <div className="stat">
          <div className="v">{summary ? formatCount(summary.mentions) : "–"}</div>
          <div className="l">Brand mentions so far</div>
          <div className="s">{summary ? `${summary.chatMentions} chat · ${summary.voiceMentions} voice` : ""}</div>
        </div>
        <div className="stat">
          <div className="v">{summary ? formatScore(summary.mentionSentiment) : "–"}</div>
          <div className="l">Mention sentiment</div>
          <div className="s">{summary ? `${formatShare(summary.mentionPositiveShare)} positive` : ""}</div>
        </div>
        <div className="stat">
          <div className="v">
            {summary ? (
              <span className={riskClass(summary.risk.level)}>
                {summary.risk.level === "LOW_DATA" ? "not enough data" : summary.risk.level}
              </span>
            ) : (
              "–"
            )}
          </div>
          <div className="l">Risk right now</div>
          <div className="s">
            <Link to={`/sessions/${session.id}`}>Open the live report</Link>
          </div>
        </div>
      </div>
    </section>
  );
}
