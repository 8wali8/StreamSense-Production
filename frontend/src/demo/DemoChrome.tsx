import { useQuery } from "@apollo/client/react";
import { Link } from "react-router";
import { twitchSignInUrl } from "../api/auth";
import type { DealsQuery, DealsQueryVariables, SessionsQuery, SessionsQueryVariables } from "../graphql/generated";
import { DEALS_QUERY, SESSIONS_QUERY } from "../graphql/queries";
import { DEMO_CHANNEL, DEMO_SPONSOR } from "./mode";

/** Where the demo sends someone who wants the real thing: the sign-in page, then Twitch. Outside the demo's basename. */
const SIGN_IN_HREF = twitchSignInUrl("/");

/**
 * The strip across the top of every demo page: whose data this is, and the two places worth going,
 * the deal and the latest stream's report.
 */
export function DemoBanner() {
  const deals = useQuery<DealsQuery, DealsQueryVariables>(DEALS_QUERY, {
    variables: { streamer: DEMO_CHANNEL, limit: 1 },
  });
  const sessions = useQuery<SessionsQuery, SessionsQueryVariables>(SESSIONS_QUERY, {
    variables: { streamer: DEMO_CHANNEL, limit: 1 },
  });
  const deal = deals.data?.deals[0] ?? null;
  const latest = sessions.data?.sessions[0] ?? null;
  return (
    <div className="demo-banner" role="note">
      <span className="demo-banner-mark">Demo</span>
      <span>
        A snapshot of <strong>@{DEMO_CHANNEL}</strong> with a mock <strong>{DEMO_SPONSOR}</strong> deal.
      </span>
      <span className="demo-banner-actions">
        {deal && (
          <Link className="button-primary button-sm" to={`/deals/${deal.id}`}>
            Open the {deal.sponsor} deal
          </Link>
        )}
        {latest && (
          <Link
            className="button-secondary button-sm"
            to={`/sessions/${latest.id}?sponsor=${encodeURIComponent(DEMO_SPONSOR)}`}
          >
            Open the latest report
          </Link>
        )}
      </span>
    </div>
  );
}

/** The sidebar footer in the demo, where the access panel normally sits. */
export function DemoSidebarCallToAction() {
  return (
    <div className="signed-in" aria-label="Demo">
      <span className="eyebrow">Demo</span>
      <strong>Looking at a snapshot</strong>
      <a className="text-button" href={SIGN_IN_HREF}>
        Sign in with Twitch
      </a>
    </div>
  );
}
