import { useQuery } from "@apollo/client/react";
import { Link, useParams } from "react-router";
import type { DealSummaryQuery, DealSummaryQueryVariables } from "../../graphql/generated";
import { DEAL_SUMMARY_QUERY } from "../../graphql/queries";
import { describeError } from "../../lib/errors";
import {
  formatCount,
  formatDuration,
  formatMoney,
  formatScore,
  formatShare,
  formatStart,
} from "../session/report-format";
import { dealDates, feeMultiple, streamsProgress } from "./deal-format";
import { readShareToken } from "../../lib/share-token";
import { DealTrend } from "./DealTrend";
import { ShareControl } from "./ShareControl";

/** S5: how one deal is going. Totals, the trend, and every stream inside it. The share control waits for 07. */
export function DealPage() {
  const { dealId = "" } = useParams();
  const query = useQuery<DealSummaryQuery, DealSummaryQueryVariables>(DEAL_SUMMARY_QUERY, {
    variables: { id: dealId },
    skip: !dealId,
    fetchPolicy: "cache-and-network",
  });

  if (query.loading && !query.data) {
    return (
      <div className="page">
        <div className="empty-state">Loading the deal...</div>
      </div>
    );
  }
  if (query.error) {
    return (
      <div className="page">
        <div className="error-state" role="alert">
          Failed to load the deal: {describeError(query.error)}
        </div>
      </div>
    );
  }
  const summary = query.data?.dealSummary;
  if (!summary) {
    return (
      <div className="page">
        <div className="empty-state">There is no deal with this id.</div>
      </div>
    );
  }

  const { deal, totals, sessions } = summary;
  const multiple = feeMultiple(totals.mediaValue, deal.fee);
  const sharedView = readShareToken() != null;
  const sponsorQuery = `?sponsor=${encodeURIComponent(deal.sponsor)}`;

  return (
    <div className="page report deal-page">
      <header className="page-header">
        <div>
          <div className="eyebrow">Deal · @{deal.streamer}</div>
          <h1>{deal.sponsor}</h1>
          <p className="page-lede">
            {dealDates(deal.startsAt, deal.endsAt)} · {streamsProgress(totals.streams, deal.promisedStreams)}
            {deal.chatCommand ? ` · ${deal.chatCommand}` : ""}
            {deal.trackedLinkHost ? ` · ${deal.trackedLinkHost}` : ""}
          </p>
        </div>
        <div className="deal-actions">
          <span className={deal.active ? "pill pill-teal" : "pill pill-dim"}>{deal.active ? "Active" : "Ended"}</span>
          {!sharedView && (
            <ShareControl
              dealId={deal.id}
              shareToken={deal.shareToken ?? null}
              onChanged={() => void query.refetch()}
            />
          )}
        </div>
      </header>

      <div className="report-tiles">
        <div className="rstat">
          <div className="v tone-brand-text">{formatDuration(totals.onScreenMs)}</div>
          <div className="l">
            On screen · {formatShare(totals.onScreenShare)} of {formatDuration(totals.streamedMs)}
          </div>
        </div>
        <div className="rstat">
          <div className="v">{formatCount(totals.mentions)}</div>
          <div className="l">
            Mentions · {formatCount(totals.chatMentions)} chat, {formatCount(totals.voiceMentions)} voice · sentiment{" "}
            {formatScore(totals.mentionSentiment)}
          </div>
        </div>
        <div className="rstat">
          <div className="v">{formatCount(totals.averageViewers)}</div>
          <div className="l">
            Avg viewers · {formatCount(totals.commandUses)} command uses, {formatCount(totals.linkPosts)} link posts
          </div>
        </div>
        <div className="rstat rstat-value">
          <div className="v tone-brand-text">{totals.mediaValue == null ? "–" : formatMoney(totals.mediaValue)}</div>
          <div className="l">
            {totals.mediaValue == null
              ? "Media value · needs viewer data"
              : multiple == null
                ? "Media value · estimate"
                : `Media value · ${multiple}× the ${formatMoney(deal.fee)} fee`}
          </div>
        </div>
      </div>

      <DealTrend
        sponsor={deal.sponsor}
        sessions={sessions.map((entry) => ({
          id: entry.session.id,
          startedAt: entry.session.startedAt,
          value: entry.onScreenMs,
        }))}
      />

      <section className="panel past-streams" aria-label="Streams in this deal">
        <div className="panel-heading">
          <h2>Streams</h2>
        </div>
        <div className="history-head">
          <span>Stream</span>
          <span>On screen</span>
          <span>Mentions</span>
          <span>Value</span>
        </div>
        {sessions.length === 0 && <div className="empty-state">No streams inside the deal yet.</div>}
        {sessions.map((entry) => (
          <Link className="history-row" to={`/sessions/${entry.session.id}${sponsorQuery}`} key={entry.session.id}>
            <span>
              <strong>
                {entry.session.title ?? "Untitled stream"}
                {entry.session.live ? " · live" : ""}
              </strong>
              <small>
                {formatStart(entry.session.startedAt)} · {formatDuration(entry.session.durationMs)} ·{" "}
                {formatCount(entry.averageViewers)} viewers
              </small>
            </span>
            <span className="mono">
              {formatDuration(entry.onScreenMs)} <em className="tone-muted">· {formatShare(entry.onScreenShare)}</em>
            </span>
            <span className="mono">
              {formatCount(entry.mentions)}
              {entry.mentionSentiment != null && (
                <em className={entry.mentionSentiment >= 0.4 ? "tone-good" : "tone-warn"}>
                  {" "}
                  · {formatScore(entry.mentionSentiment)}
                </em>
              )}
            </span>
            <span className="mono">{entry.value.mediaValue == null ? "–" : formatMoney(entry.value.mediaValue)}</span>
          </Link>
        ))}
      </section>
    </div>
  );
}
