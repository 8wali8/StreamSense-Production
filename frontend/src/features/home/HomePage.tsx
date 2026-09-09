import { useQuery } from "@apollo/client/react";
import { ErrorBoundary } from "../../components/ErrorBoundary";
import type {
  SessionsQuery,
  SessionsQueryVariables,
  SessionSummaryQuery,
  SessionSummaryQueryVariables,
} from "../../graphql/generated";
import { SESSION_SUMMARY_QUERY, SESSIONS_QUERY } from "../../graphql/queries";
import { describeError } from "../../lib/errors";
import { LiveStreamConsole } from "../console/LiveStreamConsole";
import { useStreamer } from "../streamer/streamer-context";
import { HistoryPanel } from "./HistoryPanel";
import { LiveStrip } from "./LiveStrip";
import { useSessionSummaries } from "./useSessionSummaries";

const HISTORY_LIMIT = 8;
const LIVE_POLL_MS = 30_000;

/**
 * The streamer's home: the live strip over the player and brand-mention feed, then the deals
 * placeholder and the history. Everything is about the channel the runtime is pointed at.
 */
export function HomePage() {
  const { selectedStreamer, displayBrand } = useStreamer();
  const sponsor = displayBrand === "Sponsor" ? undefined : displayBrand;

  const sessions = useQuery<SessionsQuery, SessionsQueryVariables>(SESSIONS_QUERY, {
    variables: { streamer: selectedStreamer, limit: HISTORY_LIMIT },
    pollInterval: LIVE_POLL_MS,
    fetchPolicy: "cache-and-network",
  });
  const list = sessions.data?.sessions ?? [];
  const latest = list[0] ?? null;
  const live = latest?.live ? latest : null;

  const liveSummary = useQuery<SessionSummaryQuery, SessionSummaryQueryVariables>(SESSION_SUMMARY_QUERY, {
    variables: { sessionId: live?.id ?? "", sponsor: sponsor ?? null },
    skip: !live,
    pollInterval: LIVE_POLL_MS,
    fetchPolicy: "cache-and-network",
  });

  const finishedIds = list.filter((session) => !session.live).map((session) => session.id);
  const history = useSessionSummaries(finishedIds, sponsor);

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <div className="eyebrow">Home</div>
          <h1>@{selectedStreamer}</h1>
        </div>
        <span className="pill pill-teal">{displayBrand}</span>
      </header>

      {sessions.error && (
        <div className="error-state" role="alert">
          Failed to load streams: {describeError(sessions.error)}
        </div>
      )}

      <LiveStrip
        session={latest}
        summary={liveSummary.data?.sessionSummary ?? null}
        sponsor={displayBrand}
        loading={sessions.loading && !sessions.data}
      />

      <ErrorBoundary label="live console">
        <LiveStreamConsole streamer={selectedStreamer} sponsorBrand={displayBrand} />
      </ErrorBoundary>

      <section className="panel deals-placeholder" aria-label="Active deals">
        <div className="panel-heading">
          <h2>Active deals</h2>
          <p>Deals with a sponsor, dates, and promised streams arrive in the next release.</p>
        </div>
      </section>

      <ErrorBoundary label="history">
        <HistoryPanel
          sessions={list}
          summaries={history.summaries}
          sponsor={displayBrand}
          loading={(sessions.loading && !sessions.data) || history.loading}
        />
      </ErrorBoundary>
    </div>
  );
}
