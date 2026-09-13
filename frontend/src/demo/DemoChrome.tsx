import { useQuery } from "@apollo/client/react";
import { Link } from "react-router";
import { twitchSignInUrl } from "../api/auth";
import type { DealsQuery, DealsQueryVariables, SessionsQuery, SessionsQueryVariables } from "../graphql/generated";
import { DEALS_QUERY, SESSIONS_QUERY } from "../graphql/queries";
import { DEMO_CHANNEL, DEMO_SPONSOR } from "./mode";

/** Where the demo sends someone who wants the real thing: the sign-in page, then Twitch. Outside the demo's basename. */
const SIGN_IN_HREF = twitchSignInUrl("/");

/** The strip across the top of every demo page: whose data this is, and that none of it is live. */
export function DemoBanner() {
  return (
    <div className="demo-banner" role="note">
      <span className="demo-banner-mark">Demo</span>
      <span>
        A snapshot of <strong>@{DEMO_CHANNEL}</strong> with a <strong>{DEMO_SPONSOR}</strong> deal. Nothing here is
        live, and nothing you do leaves this page.
      </span>
      <a className="demo-banner-link" href={SIGN_IN_HREF}>
        Sign in with Twitch for your channel
      </a>
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

/**
 * The introduction at the top of the demo home: what a sponsor is shown, in the product's own terms,
 * and where each part of it lives. Short; the pages below are the argument.
 */
export function DemoIntro() {
  const deals = useQuery<DealsQuery, DealsQueryVariables>(DEALS_QUERY, {
    variables: { streamer: DEMO_CHANNEL, limit: 1 },
  });
  const sessions = useQuery<SessionsQuery, SessionsQueryVariables>(SESSIONS_QUERY, {
    variables: { streamer: DEMO_CHANNEL, limit: 1 },
  });
  const deal = deals.data?.deals[0] ?? null;
  const latest = sessions.data?.sessions[0] ?? null;
  return (
    <section className="panel demo-intro" aria-label="About this demo">
      <div className="panel-heading">
        <h2>Proof of performance for a sponsorship deal</h2>
        <p>
          StreamSense watches a Twitch channel while it streams and turns what happened into a report a sponsor can
          trust: how long the logo was on screen, how often the brand was said or typed and in what tone, what the risk
          near the brand was, and what that exposure was worth against the fee.
        </p>
      </div>
      <ul className="demo-intro-list">
        <li>
          <strong>Home</strong> is the streamer's view: the live strip over the player, the brand mentions as they
          happen, the deals, and the track record across streams.
        </li>
        <li>
          <strong>A deal</strong> rolls up every stream inside its dates: on-screen time, mentions and their sentiment,
          media value against the fee, and the streams still to come.
          {deal && (
            <>
              {" "}
              <Link to={`/deals/${deal.id}`}>Open the {deal.sponsor} deal.</Link>
            </>
          )}
        </li>
        <li>
          <strong>A session report</strong> is one stream, on one page, with a timeline of where the sponsor showed up,
          the best and weakest moment, and the numbers behind the value.
          {latest && (
            <>
              {" "}
              <Link to={`/sessions/${latest.id}?sponsor=${encodeURIComponent(DEMO_SPONSOR)}`}>
                Open the latest report.
              </Link>
            </>
          )}
        </li>
      </ul>
    </section>
  );
}
