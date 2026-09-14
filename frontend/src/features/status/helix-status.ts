/** Pure wording for the Helix status pill; the component only polls and renders. */
import type { HelixPollStatus as HelixStatus } from "../../api/analytics";

/** "40s ago", "3m ago", "2h ago": how long since an epoch-millisecond instant. */
export function formatAgo(at: number, now: number): string {
  const seconds = Math.max(0, Math.round((now - at) / 1000));
  if (seconds < 60) return `${seconds}s ago`;
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  return `${Math.round(minutes / 60)}h ago`;
}

function clock(at: number): string {
  return new Date(at).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

/** The pill's text for a status; exported so the wording is unit-testable without the poll. */
export function formatHelixStatus(status: HelixStatus | null, error: string | null, now: number): string {
  if (error) return "Helix: status unavailable";
  if (!status) return "Helix: checking";
  if (!status.enabled) return "Helix: disabled";
  if (status.pausedUntil != null && status.pausedUntil > now) return `Helix: paused until ${clock(status.pausedUntil)}`;
  // A failure after the last good poll is the current state; an older one is only in the tooltip.
  if (status.lastErrorAt != null && (status.lastPollAt == null || status.lastErrorAt > status.lastPollAt)) {
    return `Helix: failed ${formatAgo(status.lastErrorAt, now)}`;
  }
  if (status.lastPollAt == null) return "Helix: waiting for the first poll";
  return `Helix: ${status.watched} watched, ${status.live} live, ${formatAgo(status.lastPollAt, now)}`;
}
