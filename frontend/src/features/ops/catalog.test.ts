import { describe, expect, it } from "vitest";
import { catalogEntryFor, unknownSponsors } from "./catalog";

const redBull = {
  name: "Red Bull",
  aliases: ["red bull", "redbull", "rb"],
  semanticTerms: ["energy drink"],
  minScore: null,
  updatedAt: 1,
};
const nike = { name: "Nike", aliases: [], semanticTerms: ["shoes"], minScore: 0.6, updatedAt: 1 };

describe("catalog", () => {
  it("reaches an entry by its name or by any alias, whatever the case", () => {
    expect(catalogEntryFor("red bull", [redBull, nike])?.name).toBe("Red Bull");
    expect(catalogEntryFor("REDBULL", [redBull, nike])?.name).toBe("Red Bull");
    expect(catalogEntryFor(" nike ", [redBull, nike])?.name).toBe("Nike");
    expect(catalogEntryFor("Prime", [redBull, nike])).toBeNull();
    expect(catalogEntryFor("", [redBull, nike])).toBeNull();
  });

  it("lists the sponsors deals name that no entry reaches", () => {
    const usage = [
      { sponsor: "redbull", deals: 2, channels: 1, latestStartsAt: 3 },
      { sponsor: "Prime", deals: 1, channels: 1, latestStartsAt: 2 },
      { sponsor: "NIKE", deals: 1, channels: 1, latestStartsAt: 1 },
    ];
    expect(unknownSponsors(usage, [redBull, nike]).map((used) => used.sponsor)).toEqual(["Prime"]);
  });
});
