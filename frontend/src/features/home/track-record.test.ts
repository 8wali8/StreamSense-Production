import { describe, expect, it } from "vitest";
import { trackRecord } from "./track-record";

describe("trackRecord", () => {
  it("weights sentiment by mentions, rates exposure per hour, and skips sessions without viewers", () => {
    const record = trackRecord([
      { durationMs: 3_600_000, onScreenMs: 1_200_000, mentions: 30, mentionSentiment: 0.5, averageViewers: 1000 },
      { durationMs: 7_200_000, onScreenMs: 1_800_000, mentions: 10, mentionSentiment: 0.9, averageViewers: null },
      { durationMs: 3_600_000, onScreenMs: 0, mentions: 0, mentionSentiment: null, averageViewers: 3000 },
    ]);
    expect(record.sessions).toBe(3);
    expect(record.onScreenMsPerHour).toBeCloseTo(750_000, 0);
    expect(record.mentionSentiment).toBeCloseTo(0.6, 5);
    expect(record.averageViewers).toBe(2000);
    expect(record.totalMentions).toBe(40);
  });

  it("is all nulls and zeros for no sessions", () => {
    expect(trackRecord([])).toEqual({
      sessions: 0,
      onScreenMsPerHour: null,
      mentionSentiment: null,
      averageViewers: null,
      totalMentions: 0,
    });
  });
});
