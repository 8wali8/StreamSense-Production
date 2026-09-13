/**
 * Demo mode: the console served under `/demo`, running on the sealed snapshot in `snapshot.json`
 * instead of the gateway. Decided once per page load from the path, before the router mounts, so
 * every transport (Apollo link, REST client, streamer selection) agrees.
 */

export const DEMO_BASE = "/demo";

/** The channel and sponsor the snapshot is about; duplicated here so the shell need not load the snapshot. */
export const DEMO_CHANNEL = "redbull-testing";
export const DEMO_SPONSOR = "Red Bull";

export function isDemoPath(pathname: string): boolean {
  return pathname === DEMO_BASE || pathname.startsWith(`${DEMO_BASE}/`);
}

/** Whether this page load is the demo. Read from the address once; the demo never navigates out of `/demo`. */
export function isDemoMode(): boolean {
  return typeof window !== "undefined" && isDemoPath(window.location.pathname);
}
