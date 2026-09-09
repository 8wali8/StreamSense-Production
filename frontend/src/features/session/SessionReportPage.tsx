import { Link, useParams, useSearchParams } from "react-router";
import { ErrorBoundary } from "../../components/ErrorBoundary";
import {
  formatCount,
  formatDuration,
  formatMoney,
  formatOffset,
  formatShare,
  formatStart,
  vodUrl,
} from "./report-format";
import { SessionTimeline } from "./SessionTimeline";
import { useSessionReport } from "./useSessionReport";

function riskClass(level: string): string {
  if (level === "HIGH") return "pill pill-high";
  if (level === "MEDIUM") return "pill pill-medium";
  if (level === "LOW") return "pill pill-low";
  return "pill pill-dim";
}

function riskLabel(level: string): string {
  return level === "LOW_DATA" ? "Risk: not enough data" : `Risk ${level}`;
}

/**
 * The one-page session report: numbers first, the timeline, the two moments, one strip of counts,
 * and four links to the detail pages. No explanatory text on the page.
 */
export function SessionReportPage() {
  const { sessionId = "" } = useParams();
  const [search] = useSearchParams();
  const sponsorParam = search.get("sponsor") ?? undefined;
  const report = useSessionReport(sessionId, sponsorParam);

  if (report.loading) {
    return (
      <div className="page">
        <div className="empty-state">Loading the session report...</div>
      </div>
    );
  }
  if (report.error) {
    return (
      <div className="page">
        <div className="error-state" role="alert">
          Failed to load the session report: {report.error}
        </div>
      </div>
    );
  }
  if (report.notFound || !report.summary) {
    return (
      <div className="page">
        <div className="empty-state">There is no session with this id.</div>
      </div>
    );
  }

  const summary = report.summary;
  const session = summary.session;
  const sponsor = summary.sponsor ?? "Sponsor";
  const querySuffix = sponsorParam ? `?sponsor=${encodeURIComponent(sponsorParam)}` : "";
  const best = report.moments?.best ?? null;
  const weakest = report.moments?.weakest ?? null;
  const value = summary.value;

  return (
    <div className="page report">
      <header className="page-header">
        <div>
          <div className="eyebrow">
            {sponsor} · @{session.streamer}
          </div>
          <h1>{session.title ?? `Stream on ${formatStart(session.startedAt)}`}</h1>
          <p className="page-lede">
            {formatStart(session.startedAt)} · {formatDuration(session.durationMs)}
            {session.live ? " · live now" : ""}
          </p>
        </div>
        <span className={riskClass(summary.risk.level)}>{riskLabel(summary.risk.level)}</span>
      </header>

      <div className="report-tiles">
        <div className="rstat">
          <div className="v tone-brand-text">{formatDuration(summary.onScreenMs)}</div>
          <div className="l">On screen · {formatShare(summary.onScreenShare)} of stream</div>
        </div>
        <div className="rstat">
          <div className="v">{formatCount(summary.mentions)}</div>
          <div className="l">Mentions · {formatShare(summary.mentionPositiveShare)} positive</div>
        </div>
        <div className="rstat">
          <div className="v">{formatCount(summary.averageViewers)}</div>
          <div className="l">Avg viewers · peak {formatCount(summary.peakViewers)}</div>
        </div>
        <div className="rstat rstat-value">
          <div className="v tone-brand-text">{value.mediaValue == null ? "–" : formatMoney(value.mediaValue)}</div>
          <div className="l">
            {value.mediaValue == null ? "Media value · needs viewer data" : "Media value · estimate"}
          </div>
        </div>
      </div>

      <ErrorBoundary label="timeline">
        {report.moments ? (
          <SessionTimeline
            sponsor={sponsor}
            session={session}
            input={{
              durationMs: session.durationMs,
              segments: report.moments.segments,
              voiceMentions: report.moments.voiceMentions,
              chatMoments: report.moments.chatMoments,
              riskSpikes: report.moments.riskSpikes,
            }}
          />
        ) : (
          <section className="report-card timeline">
            <h2>Where {sponsor} showed up</h2>
            <div
              className={report.momentsError ? "error-state" : "empty-state"}
              role={report.momentsError ? "alert" : undefined}
            >
              {report.momentsError ? `Failed to load the timeline: ${report.momentsError}` : "Loading the timeline..."}
            </div>
          </section>
        )}
      </ErrorBoundary>

      <div className="moment-row">
        <MomentCard kind="best" label="Best" moment={best} session={session} />
        <MomentCard kind="weakest" label="Weakest" moment={weakest} session={session} />
      </div>

      <div className="report-card number-strip">
        <div>
          <span className="mono">{value.logoValue == null ? "–" : formatMoney(value.logoValue)}</span>
          <span>Logo value</span>
        </div>
        <div>
          <span className="mono">{value.hostReadValue == null ? "–" : formatMoney(value.hostReadValue)}</span>
          <span>Host reads</span>
        </div>
        <div>
          <span className="mono">{formatCount(summary.voiceMentions)}</span>
          <span>Voice mentions</span>
        </div>
        <div>
          <span className="mono">{formatCount(summary.response.linkPosts)}</span>
          <span>{summary.response.trackedLinkHost ? `${summary.response.trackedLinkHost} links` : "Link posts"}</span>
        </div>
        <div>
          <span className="mono">{formatCount(summary.response.commandUses)}</span>
          <span>{summary.response.chatCommand ? `${summary.response.chatCommand} uses` : "Command uses"}</span>
        </div>
        <div>
          <span className="mono">{formatCount(summary.response.commandUsers)}</span>
          <span>People using it</span>
        </div>
      </div>

      <nav className="report-links" aria-label="Report detail">
        <Link to={`/sessions/${session.id}/value${querySuffix}`}>How value is estimated</Link>
        <Link to={`/sessions/${session.id}/mentions${querySuffix}`}>All {formatCount(summary.mentions)} mentions</Link>
        <Link to={`/sessions/${session.id}/risk${querySuffix}`}>Risk factors</Link>
        <Link to={`/sessions/${session.id}/stream${querySuffix}`}>Full stream stats</Link>
      </nav>
    </div>
  );
}

type MomentCardProps = {
  kind: "best" | "weakest";
  label: string;
  moment: { offsetMs: number; title: string; detail: string } | null;
  session: { source: string; twitchStreamId?: string | null };
};

function MomentCard({ kind, label, moment, session }: MomentCardProps) {
  const link = moment ? vodUrl(session, moment.offsetMs) : null;
  return (
    <div className={`report-card moment-card moment-${kind}`}>
      <div>
        <div className="eyebrow">
          {label}
          {moment ? ` · ${formatOffset(moment.offsetMs)}` : ""}
        </div>
        <strong>{moment ? moment.title : `No ${label.toLowerCase()} moment yet`}</strong>
        {moment?.detail && <p>{moment.detail}</p>}
      </div>
      {link && (
        <a href={link} target="_blank" rel="noreferrer">
          Open VOD
        </a>
      )}
    </div>
  );
}
