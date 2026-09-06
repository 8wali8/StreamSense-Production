import { describe, expect, it } from "vitest";
import { normalizeStreamerHandle, sponsorProfileFromInput, twitchPlayerUrl } from "./streamer";

describe("streamer helpers", () => {
  it("normalises handles the way chat-service expects them", () => {
    expect(normalizeStreamerHandle("  @RedBull-Testing ")).toBe("redbull-testing");
    expect(normalizeStreamerHandle("#Test")).toBe("test");
  });

  it("splits a sponsor input into the sponsor and its semantic terms", () => {
    expect(sponsorProfileFromInput("@Test", "Red Bull, redbull, energy drink", "Launch")).toEqual({
      streamer: "test",
      sponsor: "Red Bull",
      aliases: [],
      semanticTerms: ["redbull", "energy drink"],
      campaignGoal: "Launch",
    });
    expect(sponsorProfileFromInput("test", "   ", "").sponsor).toBe("");
  });

  it("embeds the VOD for replay aliases and the live channel otherwise", () => {
    expect(twitchPlayerUrl("redbull-testing", "localhost")).toContain("video=2750461300");
    expect(twitchPlayerUrl("austincs", "demo.streamsense.dev")).toBe(
      "https://player.twitch.tv/?channel=austincs&parent=demo.streamsense.dev&muted=true&autoplay=true",
    );
    expect(twitchPlayerUrl("austincs", "")).toContain("parent=localhost");
  });
});
