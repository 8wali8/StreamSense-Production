import { useQuery } from "@apollo/client/react";
import { useState } from "react";
import { ErrorBoundary } from "../../components/ErrorBoundary";
import type {
  DealsQuery,
  DealsQueryVariables,
  SessionsQuery,
  SessionsQueryVariables,
  SessionSummaryQuery,
  SessionSummaryQueryVariables,
} from "../../graphql/generated";
import { DEALS_QUERY, SESSION_SUMMARY_QUERY, SESSIONS_QUERY } from "../../graphql/queries";
import { describeError } from "../../lib/errors";
import { LiveStreamConsole } from "../console/LiveStreamConsole";
import { dealStartDate } from "../deals/deal-format";
import { DealsPanel } from "../deals/DealsPanel";
import { useStreamer } from "../streamer/streamer-context";
import { HistoryPanel } from "./HistoryPanel";
import { followedSponsor, homeSponsor, type HomeSponsor } from "./home-sponsor";
import { LiveStrip } from "./LiveStrip";
import { useSessionSummaries } from "./useSessionSummaries";

const HISTORY_LIMIT = 8;
const DEALS_LIMIT = 8;
const LIVE_POLL_MS = 30_000;

/** The header pill: the sponsor being followed, the one about to be, or the fact that there is none. */
function SponsorPill({ home }: { home: HomeSponsor }) {
  if (home.kind === "none") return <span className="pill pill-dim">No sponsor yet</span>;
  if (home.kind === "upcoming") {
    return (
      <span className="pill pill-dim">
        {home.sponsor} from {dealStartDate(home.startsAt)}
      </span>
    );
  }
  return <span className="pill pill-teal">{home.sponsor}</span>;
}

/**
 * The streamer's home: the live strip over the player and brand-mention feed, then the deals
 * and the history. Everything is about the channel the runtime is pointed at and the sponsor its
 * current deal names (or the one an operator entered by hand).
 */
export function HomePage() {
  const { selectedStreamer, sponsorBrand } = useStreamer();

  const deals = useQuery<DealsQuery, DealsQueryVariables>(DEALS_QUERY, {
    variables: { streamer: selectedStreamer, limit: DEALS_LIMIT },
    fetchPolicy: "cache-and-network",
  });
  const dealList = deals.data?.deals ?? [];
  // The clock as of this visit: whether a deal has started is judged once, like the server's `active` flag.
  const [now] = useState(() => Date.now());
  const home = homeSponsor(sponsorBrand, dealList, now);
  const sponsor = followedSponsor(home);

  const sessions = useQuery<SessionsQuery, SessionsQueryVariables>(SESSIONS_QUERY, {
    variables: { streamer: selectedStreamer, limit: HISTORY_LIMIT },
    pollInterval: LIVE_POLL_MS,
    fetchPolicy: "cache-and-network",
  });
  const list = sessions.data?.sessions ?? [];
  const latest = list[0] ?? null;
  const live = latest?.live ? latest : null;

  const liveSummary = useQuery<SessionSummaryQuery, SessionSummaryQueryVariables>(SESSION_SUMMARY_QUERY, {
    variables: { sessionId: live?.id ?? "", sponsor },
    skip: !live,
    pollInterval: LIVE_POLL_MS,
    fetchPolicy: "cache-and-network",
  });

  const finishedIds = list.filter((session) => !session.live).map((session) => session.id);
  const history = useSessionSummaries(finishedIds, sponsor ?? undefined);

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <div className="eyebrow">Home</div>
          <h1>@{selectedStreamer}</h1>
        </div>
        <SponsorPill home={home} />
      </header>

      {sessions.error && (
        <div className="error-state" role="alert">
          Failed to load streams: {describeError(sessions.error)}
        </div>
      )}

      <LiveStrip
        session={latest}
        summary={liveSummary.data?.sessionSummary ?? null}
        sponsor={sponsor}
        loading={sessions.loading && !sessions.data}
      />

      <ErrorBoundary label="live console">
        <LiveStreamConsole streamer={selectedStreamer} sponsor={sponsor ?? undefined} />
      </ErrorBoundary>

      <ErrorBoundary label="deals">
        <DealsPanel
          streamer={selectedStreamer}
          deals={dealList}
          loading={deals.loading && !deals.data}
          error={deals.error}
          home={home}
          onCreated={() => void deals.refetch()}
        />
      </ErrorBoundary>

      <ErrorBoundary label="history">
        <HistoryPanel
          sessions={list}
          summaries={history.summaries}
          sponsor={sponsor}
          loading={(sessions.loading && !sessions.data) || history.loading}
        />
      </ErrorBoundary>
    </div>
  );
}
