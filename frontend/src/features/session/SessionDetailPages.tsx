import { Link, useParams, useSearchParams } from "react-router";
import {
  formatCount,
  formatDuration,
  formatMoney,
  formatOffset,
  formatScore,
  formatShare,
  riskFactorName,
  vodUrl,
} from "./report-format";
import { useSessionReport } from "./useSessionReport";

/** The four pages behind the report's links. Each opens with the same header and a way back. */
function useDetail() {
  const { sessionId = "" } = useParams();
  const [search] = useSearchParams();
  const sponsor = search.get("sponsor") ?? undefined;
  const report = useSessionReport(sessionId, sponsor);
  const back = `/sessions/${sessionId}${sponsor ? `?sponsor=${encodeURIComponent(sponsor)}` : ""}`;
  return { report, back };
}

function DetailShell({ title, back, children }: { title: string; back: string; children: React.ReactNode }) {
  return (
    <div className="page report">
      <header className="page-header">
        <div>
          <div className="eyebrow">
            <Link to={back}>Back to the report</Link>
          </div>
          <h1>{title}</h1>
        </div>
      </header>
      {children}
    </div>
  );
}

function Pending({ report }: { report: ReturnType<typeof useSessionReport> }) {
  if (report.loading) return <div className="empty-state">Loading...</div>;
  if (report.error)
    return (
      <div className="error-state" role="alert">
        {report.error}
      </div>
    );
  return <div className="empty-state">There is no session with this id.</div>;
}

export function SessionValuePage() {
  const { report, back } = useDetail();
  if (!report.summary) {
    return (
      <DetailShell title="How value is estimated" back={back}>
        <Pending report={report} />
      </DetailShell>
    );
  }
  const { value, response, voiceMentions, averageViewers } = report.summary;
  return (
    <DetailShell title="How value is estimated" back={back}>
      <section className="report-card detail-table">
        <div className="detail-row">
          <div>
            <strong>Logo on screen</strong>
            <span>
              {value.weightedLogoViewerMinutes == null
                ? "Needs viewer samples from the Twitch poller"
                : `${formatCount(value.weightedLogoViewerMinutes)} weighted viewer-minutes, average prominence ${formatScore(
                    value.averageProminence,
                  )}, at $${value.cpmPer30sEquivalent} CPM per 30-second equivalent`}
            </span>
          </div>
          <span className="mono">{value.logoValue == null ? "–" : formatMoney(value.logoValue)}</span>
        </div>
        <div className="detail-row">
          <div>
            <strong>Host reads</strong>
            <span>
              {formatCount(voiceMentions)} voice mention{voiceMentions === 1 ? "" : "s"} to about{" "}
              {formatCount(averageViewers)} viewers each, at ${value.hostReadRatePer1000} per 1,000 listeners
            </span>
          </div>
          <span className="mono">{value.hostReadValue == null ? "–" : formatMoney(value.hostReadValue)}</span>
        </div>
        <div className="detail-row detail-total">
          <div>
            <strong>Estimated media value</strong>
            <span>Sentiment is not folded into this figure.</span>
          </div>
          <span className="mono tone-brand-text">{value.mediaValue == null ? "–" : formatMoney(value.mediaValue)}</span>
        </div>
      </section>
      <section className="report-card detail-table">
        <div className="detail-row">
          <div>
            <strong>{response.chatCommand ? `${response.chatCommand} in chat` : "Chat command"}</strong>
            <span>
              {response.chatCommand
                ? `${formatCount(response.commandUsers)} different people`
                : "No command is configured for this stream"}
            </span>
          </div>
          <span className="mono">{formatCount(response.commandUses)}</span>
        </div>
        <div className="detail-row">
          <div>
            <strong>{response.trackedLinkHost ? `Links to ${response.trackedLinkHost}` : "Tracked link"}</strong>
            <span>{response.trackedLinkHost ? "Posted in chat" : "No tracked link is configured for this stream"}</span>
          </div>
          <span className="mono">{formatCount(response.linkPosts)}</span>
        </div>
      </section>
      <p className="muted-text">{value.basis}</p>
    </DetailShell>
  );
}

export function SessionMentionsPage() {
  const { report, back } = useDetail();
  if (!report.summary) {
    return (
      <DetailShell title="Every mention" back={back}>
        <Pending report={report} />
      </DetailShell>
    );
  }
  const session = report.summary.session;
  const moments = report.moments;
  const lines = [
    ...(moments?.voiceMentions ?? []).map((mention) => ({
      key: `v-${mention.sentimentEventId}`,
      offsetMs: mention.offsetMs,
      channel: "voice",
      label: mention.label,
      score: mention.score,
      text: mention.text,
      count: 1,
    })),
    ...(moments?.chatMoments ?? []).map((moment) => ({
      key: `c-${moment.offsetMs}`,
      offsetMs: moment.offsetMs,
      channel: moment.user ? `chat · ${moment.user}` : "chat",
      label: moment.positiveShare >= 0.5 ? "POSITIVE" : moment.negativeShare >= 0.5 ? "NEGATIVE" : "NEUTRAL",
      score: moment.averageScore,
      text: moment.sample ?? "",
      count: moment.count,
    })),
  ].sort((a, b) => a.offsetMs - b.offsetMs);
  return (
    <DetailShell title={`Every ${report.summary.sponsor ?? "sponsor"} mention`} back={back}>
      <section className="report-card">
        {lines.length === 0 && <div className="empty-state">No mentions were recorded in this stream.</div>}
        {lines.map((line) => {
          const link = vodUrl(session, line.offsetMs);
          return (
            <article className="mention-line" key={line.key}>
              <div className="line-meta">
                <span className="mono">{formatOffset(line.offsetMs)}</span>
                <span className="chip chip-voice">{line.channel}</span>
                <span className={`chip chip-${line.label.toLowerCase()}`}>
                  {line.label.charAt(0) + line.label.slice(1).toLowerCase()} {formatScore(line.score)}
                </span>
                {line.count > 1 && <span className="chip">{line.count} in this minute</span>}
                {link && (
                  <a href={link} target="_blank" rel="noreferrer">
                    VOD
                  </a>
                )}
              </div>
              <p>{line.text}</p>
            </article>
          );
        })}
      </section>
    </DetailShell>
  );
}

export function SessionRiskPage() {
  const { report, back } = useDetail();
  if (!report.summary) {
    return (
      <DetailShell title="Risk factors" back={back}>
        <Pending report={report} />
      </DetailShell>
    );
  }
  const { risk } = report.summary;
  return (
    <DetailShell title={risk.level === "LOW_DATA" ? "Risk: not enough data" : `Why risk is ${risk.level}`} back={back}>
      <section className="report-card detail-table">
        {risk.factors.length === 0 && <div className="empty-state">Too few signals in this stream to score risk.</div>}
        {risk.factors.map((factor) => (
          <div className="detail-row" key={factor.name}>
            <div>
              <strong>{riskFactorName(factor.name)}</strong>
              <span>weight {formatScore(factor.weight)}</span>
            </div>
            <span className="mono">{formatScore(factor.value)}</span>
          </div>
        ))}
        {risk.score != null && (
          <div className="detail-row detail-total">
            <div>
              <strong>Weighted score</strong>
              <span>below 0.34 is LOW, 0.67 and above is HIGH</span>
            </div>
            <span className="mono">{formatScore(risk.score)}</span>
          </div>
        )}
      </section>
    </DetailShell>
  );
}

export function SessionStreamPage() {
  const { report, back } = useDetail();
  if (!report.summary) {
    return (
      <DetailShell title="The stream overall" back={back}>
        <Pending report={report} />
      </DetailShell>
    );
  }
  const { session, chat, chatSentiment, transcriptSentiment, engagement, mentionSentiment } = report.summary;
  const delta =
    mentionSentiment != null && chatSentiment.averageScore != null
      ? mentionSentiment - chatSentiment.averageScore
      : null;
  return (
    <DetailShell title="The stream overall" back={back}>
      <section className="report-card detail-table">
        <div className="detail-row">
          <div>
            <strong>Length</strong>
            <span>{session.category ?? "No category"}</span>
          </div>
          <span className="mono">{formatDuration(session.durationMs)}</span>
        </div>
        <div className="detail-row">
          <div>
            <strong>Chat</strong>
            <span>
              {formatCount(chat.uniqueChatters)} people, peak {formatCount(chat.peakMessagesPerMinute)} messages a
              minute
            </span>
          </div>
          <span className="mono">{formatCount(chat.totalMessages)}</span>
        </div>
        <div className="detail-row">
          <div>
            <strong>Chat sentiment</strong>
            <span>{formatShare(chatSentiment.negativeRatio)} negative</span>
          </div>
          <span className="mono">{formatScore(chatSentiment.averageScore)}</span>
        </div>
        <div className="detail-row">
          <div>
            <strong>Voice sentiment</strong>
            <span>{formatShare(transcriptSentiment.negativeRatio)} negative</span>
          </div>
          <span className="mono">{formatScore(transcriptSentiment.averageScore)}</span>
        </div>
        <div className="detail-row">
          <div>
            <strong>Engagement spikes</strong>
            <span>
              {engagement.latestSpikeAt
                ? `latest at ${formatOffset(engagement.latestSpikeAt - session.startedAt)}`
                : "none"}
            </span>
          </div>
          <span className="mono">{formatCount(engagement.spikeCount)}</span>
        </div>
        {delta != null && (
          <div className="detail-row detail-total">
            <div>
              <strong>Brand mentions against the stream</strong>
              <span>mention sentiment minus overall chat sentiment</span>
            </div>
            <span className="mono">
              {delta >= 0 ? "+" : ""}
              {formatScore(delta)}
            </span>
          </div>
        )}
      </section>
    </DetailShell>
  );
}
