import { describe, expect, it } from "vitest";
import { followedSponsor, homeSponsor } from "./home-sponsor";

const NOW = 1_788_950_000_000;
const DAY = 86_400_000;

const running = { sponsor: "Red Bull", startsAt: NOW - 10 * DAY, active: true };
const olderRunning = { sponsor: "Monster", startsAt: NOW - 20 * DAY, active: true };
const ended = { sponsor: "Logitech", startsAt: NOW - 60 * DAY, active: false };
const upcoming = { sponsor: "Razer", startsAt: NOW + 5 * DAY, active: false };
const laterUpcoming = { sponsor: "Prime", startsAt: NOW + 15 * DAY, active: false };

describe("homeSponsor", () => {
  it("takes the operations page entry over any deal", () => {
    expect(homeSponsor(" Nike ", [running], NOW)).toEqual({ kind: "manual", sponsor: "Nike" });
  });

  it("follows the newest running deal", () => {
    expect(homeSponsor("", [ended, olderRunning, running, upcoming], NOW)).toEqual({
      kind: "deal",
      sponsor: "Red Bull",
    });
  });

  it("names the soonest deal still to start when none is running", () => {
    expect(homeSponsor("", [ended, laterUpcoming, upcoming], NOW)).toEqual({
      kind: "upcoming",
      sponsor: "Razer",
      startsAt: upcoming.startsAt,
    });
  });

  it("has nothing to follow with only ended deals or none at all", () => {
    expect(homeSponsor("", [ended], NOW)).toEqual({ kind: "none" });
    expect(homeSponsor("  ", [], NOW)).toEqual({ kind: "none" });
  });

  it("only filters the numbers by a sponsor being followed right now", () => {
    expect(followedSponsor({ kind: "manual", sponsor: "Nike" })).toBe("Nike");
    expect(followedSponsor({ kind: "deal", sponsor: "Red Bull" })).toBe("Red Bull");
    expect(followedSponsor({ kind: "upcoming", sponsor: "Razer", startsAt: NOW })).toBeNull();
    expect(followedSponsor({ kind: "none" })).toBeNull();
  });
});
