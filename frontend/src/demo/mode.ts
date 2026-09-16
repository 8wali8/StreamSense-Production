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

/**
 * Where a bare `/demo` goes: the newest report in the snapshot, about the sponsor it is a deal for.
 * `sessions/latest` resolves the id from the snapshot, so a re-export needs nothing changed here.
 */
export const DEMO_LANDING = `sessions/latest?sponsor=${encodeURIComponent(DEMO_SPONSOR)}`;

/**
 * Point a bare `/demo` at the landing report before the router mounts, so the demo opens on a
 * finished stream rather than a live console with nothing in it. Rewritten rather than routed as a
 * redirect: the home page is still a page, and the sidebar's Home link has to reach it.
 */
export function openDemoLanding(win: Window = window): void {
  const { pathname } = win.location;
  if (pathname !== DEMO_BASE && pathname !== `${DEMO_BASE}/`) return;
  win.history.replaceState(win.history.state, "", `${DEMO_BASE}/${DEMO_LANDING}`);
}
