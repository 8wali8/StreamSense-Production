import { describe, expect, it } from "vitest";
import { buildTranscriptFeed } from "./transcript-lines";

const segment = (id: string, at: number, text = `segment ${id}`) => ({
  segmentId: id,
  text,
  startedAt: at,
  transcriptSequence: at / 1000,
  source: "TWITCH",
});

const sentiment = (id: string, at: number, sponsorRelevant: boolean, text = `analysed ${id}`) => ({
  segmentId: id,
  text,
  segmentStartedAt: at,
  transcriptSequence: at / 1000,
  sponsorRelevant,
  label: "POSITIVE",
});

describe("buildTranscriptFeed", () => {
  it("pairs segments with their analysis, newest first, preferring the analysed text", () => {
    const feed = buildTranscriptFeed([segment("a", 1000), segment("b", 3000)], [sentiment("a", 1000, false)], [], 10);

    expect(feed.map((line) => line.id)).toEqual(["b", "a"]);
    expect(feed[1].text).toBe("analysed a");
    expect(feed[1].analysis?.segmentId).toBe("a");
    expect(feed[0].analysis).toBeUndefined();
    expect(feed[0].source).toBe("TWITCH");
  });

  it("adds lines for sentiment whose segment is missing, without a source", () => {
    const feed = buildTranscriptFeed([segment("a", 1000)], [sentiment("orphan", 2000, false)], [], 10);

    expect(feed.map((line) => line.id)).toEqual(["orphan", "a"]);
    expect(feed[0].source).toBeUndefined();
    expect(feed[0].analysis?.segmentId).toBe("orphan");
  });

  it("marks sponsor lines from the sponsor feed or from a sponsor-relevant general analysis", () => {
    const feed = buildTranscriptFeed(
      [segment("a", 1000), segment("b", 2000), segment("c", 3000)],
      [sentiment("a", 1000, true), sentiment("b", 2000, false), sentiment("c", 3000, false)],
      [sentiment("b", 2000, true, "sponsor read b")],
      10,
    );

    const byId = Object.fromEntries(feed.map((line) => [line.id, line]));
    expect(byId.a.sponsorAnalysis?.segmentId).toBe("a");
    expect(byId.b.sponsorAnalysis?.text).toBe("sponsor read b");
    expect(byId.c.sponsorAnalysis).toBeUndefined();
  });

  it("caps the feed", () => {
    const segments = [1, 2, 3, 4].map((n) => segment(`s${n}`, n * 1000));

    expect(buildTranscriptFeed(segments, [], [], 2).map((line) => line.id)).toEqual(["s4", "s3"]);
  });
});
