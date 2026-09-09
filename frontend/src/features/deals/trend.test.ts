import { describe, expect, it } from "vitest";
import { trendLayout } from "./trend";

describe("trendLayout", () => {
  it("orders bars by start, scales to the tallest, and keeps a sliver for tiny values", () => {
    const layout = trendLayout(
      [
        { id: "b", startedAt: 200, value: 50 },
        { id: "a", startedAt: 100, value: 100 },
        { id: "c", startedAt: 300, value: 0.01 },
        { id: "d", startedAt: 400, value: 0 },
      ],
      100,
      40,
      0,
    );
    expect(layout.bars.map((bar) => bar.id)).toEqual(["a", "b", "c", "d"]);
    expect(layout.bars[0]).toMatchObject({ x: 0, width: 25, height: 40, y: 0 });
    expect(layout.bars[1]).toMatchObject({ x: 25, height: 20, y: 20 });
    expect(layout.bars[2].height).toBe(2);
    expect(layout.bars[3].height).toBe(0);
  });

  it("handles an empty list", () => {
    expect(trendLayout([], 100, 40)).toMatchObject({ bars: [], max: 0 });
  });
});
