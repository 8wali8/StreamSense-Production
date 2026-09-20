import { useQuery } from "@apollo/client/react";
import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { deleteDeal, updateDeal } from "../../api/analytics";
import type { DealSummaryQuery, DealSummaryQueryVariables } from "../../graphql/generated";
import { DEAL_SUMMARY_QUERY } from "../../graphql/queries";
import { describeError } from "../../lib/errors";
import { isOperatorView } from "../../lib/view-as";
import {
  formatCount,
  formatDuration,
  formatMoney,
  formatScore,
  formatShare,
  formatStart,
} from "../session/report-format";
import { dealDates, feeMultiple, streamsProgress } from "./deal-format";
import { isDemoMode } from "../../demo/mode";
import { readShareToken } from "../../lib/share-token";
import { DealForm } from "./DealForm";
import { DealTrend } from "./DealTrend";
import { ImportStreams } from "./ImportStreams";
import { ShareControl } from "./ShareControl";

type DealAction = "idle" | "editing" | "confirming-delete";

/**
 * How one deal is going: totals, the trend, every stream inside it, and, for the owner, the controls to
 * share it, change its terms, end it today, or (an operator) delete it once it is unshared.
 */
export function DealPage() {
  const { dealId = "" } = useParams();
  const navigate = useNavigate();
  const query = useQuery<DealSummaryQuery, DealSummaryQueryVariables>(DEAL_SUMMARY_QUERY, {
    variables: { id: dealId },
    skip: !dealId,
    fetchPolicy: "cache-and-network",
  });
  const [action, setAction] = useState<DealAction>("idle");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  async function run(work: () => Promise<void>) {
    setBusy(true);
    setActionError(null);
    try {
      await work();
    } catch (err) {
      setActionError(describeError(err instanceof Error ? err : new Error("the change failed")));
    } finally {
      setBusy(false);
    }
  }

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
  // A shared tab and the demo are both read-only views: no share control, no imports, no changes.
  const sharedView = readShareToken() != null || isDemoMode();
  const sponsorQuery = `?sponsor=${encodeURIComponent(deal.sponsor)}`;
  // Ending is open until the deal has ended; deleting is the operator's call, and only once it is unshared.
  const canEnd = deal.endsAt == null || deal.endsAt > Date.now();
  const canDelete = !sharedView && isOperatorView();

  function endToday() {
    void run(async () => {
      // The whole deal goes back with only the end changed: an update is not a patch.
      await updateDeal(deal.id, {
        sponsor: deal.sponsor,
        startsAt: deal.startsAt,
        endsAt: Date.now(),
        ...(deal.promisedStreams != null ? { promisedStreams: deal.promisedStreams } : {}),
        ...(deal.fee != null ? { fee: deal.fee } : {}),
        ...(deal.currency ? { currency: deal.currency } : {}),
        cpmPer30sEquivalent: deal.cpmPer30sEquivalent,
        hostReadRatePer1000: deal.hostReadRatePer1000,
        ...(deal.trackedLink ? { trackedLink: deal.trackedLink } : {}),
        ...(deal.chatCommand ? { chatCommand: deal.chatCommand } : {}),
        ...(deal.channelPointReward ? { channelPointReward: deal.channelPointReward } : {}),
      });
      setNotice("The deal ended today. Edit it to pick another end date.");
      await query.refetch();
    });
  }

  function remove() {
    void run(async () => {
      await deleteDeal(deal.id);
      await navigate("/");
    });
  }

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
          {!sharedView && action === "idle" && (
            <>
              <button
                className="button-secondary button-sm"
                type="button"
                disabled={busy}
                onClick={() => {
                  setNotice(null);
                  setAction("editing");
                }}
              >
                Edit
              </button>
              {canEnd && (
                <button className="button-secondary button-sm" type="button" disabled={busy} onClick={endToday}>
                  {busy ? "Ending..." : "End today"}
                </button>
              )}
              {canDelete && (
                <button
                  className="button-secondary button-sm"
                  type="button"
                  disabled={busy}
                  onClick={() => {
                    setNotice(null);
                    setAction("confirming-delete");
                  }}
                >
                  Delete
                </button>
              )}
            </>
          )}
        </div>
      </header>

      {action === "editing" && (
        <section className="panel deal-edit" aria-label="Edit this deal">
          <DealForm
            streamer={deal.streamer}
            deal={deal}
            onCancel={() => setAction("idle")}
            onSaved={() => {
              setAction("idle");
              setNotice("Saved. Every report inside the deal is priced with the new terms from now on.");
              void query.refetch();
            }}
          />
        </section>
      )}
      {action === "confirming-delete" && (
        <div className="status-line deal-confirm" role="alert">
          <span>
            Delete this deal for good? Its streams stay; they just stop belonging to it.
            {deal.shareToken ? " Revoke the share link first." : ""}
          </span>
          <button
            className="button-primary button-sm"
            type="button"
            disabled={busy || deal.shareToken != null}
            onClick={remove}
          >
            {busy ? "Deleting..." : "Delete"}
          </button>
          <button
            className="button-secondary button-sm"
            type="button"
            disabled={busy}
            onClick={() => setAction("idle")}
          >
            Keep
          </button>
        </div>
      )}
      {notice && <div className="status-line">{notice}</div>}
      {actionError && (
        <div className="error-state" role="alert">
          {actionError}
        </div>
      )}

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

      {!sharedView && (
        <ImportStreams
          streamer={deal.streamer}
          startsAt={deal.startsAt}
          endsAt={deal.endsAt ?? null}
          onImported={() => void query.refetch()}
        />
      )}

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
