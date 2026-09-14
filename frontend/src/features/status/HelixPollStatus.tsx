import { getHelixPollStatus, type HelixPollStatus as HelixStatus } from "../../api/analytics";
import { formatHelixStatus } from "./helix-status";
import { usePolledResource } from "../../hooks/usePolledResource";

type Polled = { status: HelixStatus; at: number };

/** The status with the moment it was fetched: the ages on the pill are as of that moment, refreshed each poll. */
function loadStatus(): Promise<Polled> {
  return getHelixPollStatus().then((status) => ({ status, at: Date.now() }));
}

/** The Twitch Helix poller on the operations page: what the last poll did, or why it did nothing. */
export function HelixPollStatus() {
  const { data, error } = usePolledResource(loadStatus, 10000);

  return (
    <span className="status-pill" title={data?.status.lastError ?? error ?? undefined}>
      {formatHelixStatus(data?.status ?? null, error, data?.at ?? 0)}
    </span>
  );
}
