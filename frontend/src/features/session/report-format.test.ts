import { describe, expect, it } from "vitest";
import { formatDuration, formatMoney, formatOffset, formatShare, vodUrl } from "./report-format";
import { buildTimeline, ticksFor } from "./timeline";

describe("report formatting", () => {
  it("formats durations, offsets, shares, and money the way the report shows them", () => {
    expect(formatDuration(8_040_000)).toBe("2h 14m");
    expect(formatDuration(2_292_000)).toBe("38m 12s");
    expect(formatDuration(-5)).toBe("0s");
    expect(formatOffset(4_800_000)).toBe("1:20:00");
    expect(formatOffset(3_725_000)).toBe("1:02:05");
    expect(formatShare(0.284)).toBe("28%");
    expect(formatShare(null)).toBe("–");
    expect(formatMoney(1190)).toBe("$1,190");
    expect(formatMoney(12.5)).toBe("$12.50");
    expect(formatMoney(null)).toBe("–");
  });

  it("links to the VOD only for a replay capture session, at the moment's offset", () => {
    expect(vodUrl({ source: "CAPTURE", twitchStreamId: "2750461300" }, 3_725_000)).toBe(
      "https://www.twitch.tv/videos/2750461300?t=1h2m5s",
    );
    expect(vodUrl({ source: "HELIX", twitchStreamId: "41" }, 1000)).toBeNull();
    expect(vodUrl({ source: "HELIX", twitchStreamId: "41", vodId: "99" }, 1000)).toBe(
      "https://www.twitch.tv/videos/99?t=0h0m1s",
    );
    expect(vodUrl({ source: "CAPTURE", twitchStreamId: null }, 1000)).toBeNull();
  });
});

describe("timeline layout", () => {
  it("places every moment as a percentage of the stream and keeps short segments visible", () => {
    const { moments, ticks } = buildTimeline({
      durationMs: 8_040_000,
      segments: [{ offsetMs: 240_000, durationMs: 360_000, detections: 36, peakConfidence: 0.91, sponsor: "Red Bull" }],
      voiceMentions: [{ sentimentEventId: "v1", offsetMs: 1_080_000, label: "POSITIVE", score: 0.84, text: "hi" }],
      chatMoments: [
        { offsetMs: 4_800_000, count: 38, positiveShare: 0.84, negativeShare: 0.05, sample: "eating well" },
      ],
      riskSpikes: [{ offsetMs: 4_140_000, chatNegativeRatio: 0.6, chatMessageCount: 21 }],
    });
    expect(moments.map((moment) => [moment.kind, moment.left, moment.width])).toEqual([
      ["segment", 3, 4.5],
      ["voice", 13.4, 0],
      ["chat", 59.7, 0],
      ["risk", 51.5, 0],
    ]);
    expect(moments[0].title).toBe("Red Bull on screen, 6 minutes");
    expect(moments[2].tone).toBe("positive");
    expect(ticks.map((tick) => tick.label)).toEqual(["0:00", "30:00", "1:00:00", "1:30:00", "2:00:00"]);
    // A one-second run still draws as a sliver rather than vanishing.
    const sliver = buildTimeline({
      durationMs: 8_040_000,
      segments: [{ offsetMs: 0, durationMs: 1000, detections: 1, peakConfidence: 0.5, sponsor: "Nike" }],
      voiceMentions: [],
      chatMoments: [],
      riskSpikes: [],
    });
    expect(sliver.moments[0].width).toBe(0.6);
  });

  it("uses hourly ticks for long streams", () => {
    expect(ticksFor(5 * 3_600_000).map((tick) => tick.label)).toEqual([
      "0:00",
      "1:00:00",
      "2:00:00",
      "3:00:00",
      "4:00:00",
      "5:00:00",
    ]);
  });
});
