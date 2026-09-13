import type { DealsQuery } from "../../graphql/generated";

type Deal = Pick<DealsQuery["deals"][number], "sponsor" | "startsAt" | "active">;

/** Which sponsor the home page is about, and why. */
export type HomeSponsor =
  | { kind: "manual"; sponsor: string }
  | { kind: "deal"; sponsor: string }
  | { kind: "upcoming"; sponsor: string; startsAt: number }
  | { kind: "none" };

/**
 * An entry on the operations page wins, because an operator re-points relevance by hand. Otherwise
 * the newest deal running now, which is what relevance follows once a deal begins. Otherwise the
 * soonest deal still to start, so the streamer is not asked to create it twice. Otherwise nothing.
 */
export function homeSponsor(manual: string, deals: readonly Deal[], now: number): HomeSponsor {
  const entered = manual.trim();
  if (entered !== "") return { kind: "manual", sponsor: entered };
  const running = [...deals].filter((deal) => deal.active).sort((a, b) => b.startsAt - a.startsAt)[0];
  if (running) return { kind: "deal", sponsor: running.sponsor };
  const upcoming = [...deals].filter((deal) => deal.startsAt > now).sort((a, b) => a.startsAt - b.startsAt)[0];
  if (upcoming) return { kind: "upcoming", sponsor: upcoming.sponsor, startsAt: upcoming.startsAt };
  return { kind: "none" };
}

/** The sponsor the numbers are filtered by: only one being followed right now. */
export function followedSponsor(home: HomeSponsor): string | null {
  return home.kind === "manual" || home.kind === "deal" ? home.sponsor : null;
}
