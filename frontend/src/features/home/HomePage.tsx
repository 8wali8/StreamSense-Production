import { useQuery } from "@apollo/client/react";
import { useEffect, useState } from "react";
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
import { isDemoMode } from "../../demo/mode";
import { canStartMeasurement } from "../../lib/auth-token";
import { describeError } from "../../lib/errors";
import { MeasureChannel } from "../capture/MeasureChannel";
import { LiveStreamConsole } from "../console/LiveStreamConsole";
import { dealStartDate } from "../deals/deal-format";
import { DealsPanel } from "../deals/DealsPanel";
import { useStreamer } from "../streamer/streamer-context";
import { HistoryPanel } from "./HistoryPanel";
import { followedSponsor, homeSponsor, type HomeSponsor } from "./home-sponsor";
import { LiveStrip } from "./LiveStrip";
import { useSessionSummaries } from "./useSessionSummaries";

const HISTORY_LIMIT = 8;
/** Every deal the channel has (the service caps a page at 200), so the current one is never hidden behind newer ones. */
const DEALS_FETCH_LIMIT = 200;
const DEALS_SHOWN = 8;
const LIVE_POLL_MS = 30_000;

/** The header pill: the sponsor being followed, the one about to be, or the fact that there is none. */
function SponsorPill({ home }: { home: HomeSponsor }) {
  if (home.kind === "unknown") return null;
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

  // Polled like the sessions, so a deal starting or ending while the page is open is picked up
  // within a minute of the backend pointing relevance at it.
  const deals = useQuery<DealsQuery, DealsQueryVariables>(DEALS_QUERY, {
    variables: { streamer: selectedStreamer, limit: DEALS_FETCH_LIMIT },
    pollInterval: LIVE_POLL_MS,
    fetchPolicy: "cache-and-network",
  });
  // The clock advances with the poll, so "not started yet" is judged against the same moment as `active`.
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), LIVE_POLL_MS);
    return () => window.clearInterval(timer);
  }, []);
  // Until the deals have loaded once, nothing is claimed about them; a failed refetch keeps the last list.
  const home = homeSponsor(sponsorBrand, deals.data?.deals, now);
  const sponsor = followedSponsor(home);
  const known = home.kind !== "unknown";

  const sessions = useQuery<SessionsQuery, SessionsQueryVariables>(SESSIONS_QUERY, {
    variables: { streamer: selectedStreamer, limit: HISTORY_LIMIT },
    pollInterval: LIVE_POLL_MS,
    fetchPolicy: "cache-and-network",
  });
  const list = sessions.data?.sessions ?? [];
  const latest = list[0] ?? null;
  const live = latest?.live ? latest : null;

  // Without a sponsor the service would summarise whichever brand it finds, under a strip that says there is none.
  const liveSummary = useQuery<SessionSummaryQuery, SessionSummaryQueryVariables>(SESSION_SUMMARY_QUERY, {
    variables: { sessionId: live?.id ?? "", sponsor },
    skip: !live || sponsor === null,
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

      {/* The demo is a snapshot; there is nothing to point at a channel there, and an access link steers nothing. */}
      {!isDemoMode() && canStartMeasurement() && (
        <ErrorBoundary label="measurement">
          <MeasureChannel channel={selectedStreamer} channelLive={sessions.data ? live != null : null} />
        </ErrorBoundary>
      )}

      <LiveStrip
        session={latest}
        summary={liveSummary.data?.sessionSummary ?? null}
        sponsor={known ? sponsor : undefined}
        loading={sessions.loading && !sessions.data}
      />

      {/* Only while the channel is live: offline, the strip above points at the last report, and a player
          showing "offline" under a LIVE badge would only distract. The demo is past streams and never has it. */}
      {!isDemoMode() && live && (
        <ErrorBoundary label="live console">
          <LiveStreamConsole streamer={selectedStreamer} sponsor={known ? sponsor : undefined} />
        </ErrorBoundary>
      )}

      <ErrorBoundary label="deals">
        <DealsPanel
          streamer={selectedStreamer}
          deals={(deals.data?.deals ?? []).slice(0, DEALS_SHOWN)}
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
