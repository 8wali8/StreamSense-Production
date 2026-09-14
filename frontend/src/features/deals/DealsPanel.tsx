import { useState } from "react";
import { Link } from "react-router";
import type { DealsQuery } from "../../graphql/generated";
import { describeError } from "../../lib/errors";
import { isDemoMode } from "../../demo/mode";
import type { HomeSponsor } from "../home/home-sponsor";
import { dealDates, dealStartDate } from "./deal-format";
import { NewDealForm } from "./NewDealForm";

type DealsPanelProps = {
  streamer: string;
  /** The newest deals; the sponsor rule saw the whole list. */
  deals: DealsQuery["deals"];
  error: { message: string } | undefined;
  home: HomeSponsor;
  onCreated: () => void;
};

/** The one line under the heading: what the deals do for the channel, or what to do when there are none. */
function dealsLead(home: HomeSponsor, count: number): string {
  if (home.kind === "upcoming") {
    return `Your deal with ${home.sponsor} starts on ${dealStartDate(home.startsAt)}; the home page follows it from then.`;
  }
  // "none" means the deals loaded and there is nothing to follow; a failed load stays neutral.
  if (count === 0 && home.kind === "none") {
    return "No deals yet. Create one and the home page follows its sponsor from the start date: exposure, mentions, and the report.";
  }
  return "Each deal rolls up the streams inside its dates.";
}

/** The channel's deals on the home page, each a link to its page, and the form for a new one. */
export function DealsPanel({ streamer, deals, error, home, onCreated }: DealsPanelProps) {
  const [creating, setCreating] = useState(false);
  const [status, setStatus] = useState<string | null>(null);

  return (
    <section className="panel deals" aria-label="Deals">
      <div className="panel-heading deals-heading">
        <div>
          <h2>Deals</h2>
          <p>{dealsLead(home, deals.length)}</p>
        </div>
        {!creating && !isDemoMode() && (
          <button className="button-primary button-sm" type="button" onClick={() => setCreating(true)}>
            New deal
          </button>
        )}
      </div>

      {creating && (
        <NewDealForm
          streamer={streamer}
          defaultSponsor={home.kind === "manual" ? home.sponsor : ""}
          onCancel={() => setCreating(false)}
          onCreated={(deal) => {
            setCreating(false);
            setStatus(`${deal.sponsor} deal created; relevance scoring now follows it.`);
            onCreated();
          }}
        />
      )}
      {status && <div className="status-line">{status}</div>}
      {error && (
        <div className="error-state" role="alert">
          Failed to load deals: {describeError(error)}
        </div>
      )}

      {deals.map((deal) => (
        <Link className="deal-row" to={`/deals/${deal.id}`} key={deal.id}>
          <span>
            <strong>{deal.sponsor}</strong>
            <small>
              {dealDates(deal.startsAt, deal.endsAt)}
              {deal.promisedStreams != null ? ` · ${deal.promisedStreams} streams promised` : ""}
              {deal.chatCommand ? ` · ${deal.chatCommand}` : ""}
            </small>
          </span>
          <span className={deal.active ? "pill pill-teal" : "pill pill-dim"}>{deal.active ? "Active" : "Ended"}</span>
        </Link>
      ))}
    </section>
  );
}
