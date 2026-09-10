/** The per-stream trend on the deal page: one bar per session, oldest first, scaled to the tallest. */

export type TrendInput = {
  id: string;
  startedAt: number;
  /** The bar height. */
  value: number;
};

export type TrendBar = TrendInput & {
  x: number;
  width: number;
  height: number;
  y: number;
};

export type TrendLayout = {
  width: number;
  height: number;
  bars: TrendBar[];
  max: number;
};

export function trendLayout(inputs: TrendInput[], width: number, height: number, gap = 6): TrendLayout {
  const ordered = [...inputs].sort((a, b) => a.startedAt - b.startedAt);
  const max = ordered.reduce((m, bar) => Math.max(m, bar.value), 0);
  const count = ordered.length;
  const slot = count === 0 ? width : (width - gap * (count - 1)) / count;
  const bars = ordered.map((bar, index) => {
    const barHeight = max === 0 ? 0 : Math.max(bar.value > 0 ? 2 : 0, (bar.value / max) * height);
    return {
      ...bar,
      x: index * (slot + gap),
      width: Math.max(1, slot),
      height: barHeight,
      y: height - barHeight,
    };
  });
  return { width, height, bars, max };
}
