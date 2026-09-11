import { describe, expect, it } from "vitest";
import {
  confidenceFor,
  IDLE_STEPS,
  idleTarget,
  letterTarget,
  nearestLetter,
  type Rect,
  WORD,
  wordTarget,
} from "./detection-header";

/** Eleven 20px-wide letters in a row starting at x = 100. */
const rects: Rect[] = [...WORD].map((_, i) => ({ x: 100 + i * 20, y: 40, w: 20, h: 36 }));

describe("detection-header", () => {
  it("snaps to the letter under the pointer and reads confidence off the distance", () => {
    expect(nearestLetter(rects, 111)).toEqual({ index: 0, distance: 1 });
    expect(nearestLetter(rects, 245)).toEqual({ index: 7, distance: 5 });
    expect(nearestLetter(rects, 5000).index).toBe(WORD.length - 1);
    expect(confidenceFor(0)).toBe("0.99");
    expect(confidenceFor(30)).toBe("0.84");
    expect(confidenceFor(600)).toBe("0.69");
  });

  it("frames one letter or the whole word with the detector's padding and label", () => {
    expect(letterTarget(rects, 6, "0.91")).toEqual({
      box: { x: 216, y: 38, w: 28, h: 40 },
      label: "S · 0.91",
      letter: 6,
    });
    expect(wordTarget(rects, "0.97")).toEqual({
      box: { x: 92, y: 38, w: 236, h: 40 },
      label: "STREAMSENSE · 0.97",
      letter: null,
    });
  });

  it("walks the letters while idle, then rests on the word", () => {
    expect(idleTarget(rects, 0).letter).toBe(0);
    expect(idleTarget(rects, WORD.length - 1).letter).toBe(WORD.length - 1);
    expect(idleTarget(rects, WORD.length).letter).toBeNull();
    expect(idleTarget(rects, IDLE_STEPS - 1).label).toBe("STREAMSENSE · 0.97");
  });
});
