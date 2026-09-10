import { useQuery } from "@apollo/client/react";
import { useState } from "react";
import { Link } from "react-router";
import type { DealsQuery, DealsQueryVariables } from "../../graphql/generated";
import { DEALS_QUERY } from "../../graphql/queries";
import { describeError } from "../../lib/errors";
import { useStreamer } from "../streamer/streamer-context";
import { dealDates } from "./deal-format";
import { NewDealForm } from "./NewDealForm";

const DEALS_LIMIT = 8;

/** The channel's deals on the home page, each a link to its page, and the form for a new one. */
export function DealsPanel() {
  const { selectedStreamer, displayBrand } = useStreamer();
  const [creating, setCreating] = useState(false);
  const [status, setStatus] = useState<string | null>(null);
  const deals = useQuery<DealsQuery, DealsQueryVariables>(DEALS_QUERY, {
    variables: { streamer: selectedStreamer, limit: DEALS_LIMIT },
    fetchPolicy: "cache-and-network",
  });
  const list = deals.data?.deals ?? [];

  return (
    <section className="panel deals" aria-label="Deals">
      <div className="panel-heading deals-heading">
        <div>
          <h2>Deals</h2>
          <p>
            {list.length === 0 && !deals.loading
              ? "No deals yet for this channel."
              : "Each deal rolls up the streams inside its dates."}
          </p>
        </div>
        {!creating && (
          <button className="button-primary button-sm" type="button" onClick={() => setCreating(true)}>
            New deal
          </button>
        )}
      </div>

      {creating && (
        <NewDealForm
          streamer={selectedStreamer}
          defaultSponsor={displayBrand}
          onCancel={() => setCreating(false)}
          onCreated={(deal) => {
            setCreating(false);
            setStatus(`${deal.sponsor} deal created; relevance scoring now follows it.`);
            void deals.refetch();
          }}
        />
      )}
      {status && <div className="status-line">{status}</div>}
      {deals.error && (
        <div className="error-state" role="alert">
          Failed to load deals: {describeError(deals.error)}
        </div>
      )}

      {list.map((deal) => (
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
