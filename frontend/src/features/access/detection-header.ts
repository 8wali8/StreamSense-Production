/**
 * Geometry for the sign-in header: the sponsor detector's bounding box, turned on the wordmark.
 * Everything here is pure so the component only measures letters and picks a target.
 */

export const WORD = "StreamSense";
/** Letters from this index on are the lighter half of the wordmark ("Sense"). */
export const THIN_FROM = 6;

export type Rect = { x: number; y: number; w: number; h: number };

export type Target = { box: Rect; label: string; letter: number | null };

/** The letter whose centre is closest to a pointer x, with the distance to it. */
export function nearestLetter(rects: Rect[], x: number): { index: number; distance: number } {
  let index = 0;
  let distance = Number.POSITIVE_INFINITY;
  rects.forEach((r, i) => {
    const d = Math.abs(x - (r.x + r.w / 2));
    if (d < distance) {
      distance = d;
      index = i;
    }
  });
  return { index, distance };
}

/** A detector-style confidence that falls off with the pointer's distance from the letter. */
export function confidenceFor(distance: number): string {
  return (0.99 - (Math.min(distance, 60) / 60) * 0.3).toFixed(2);
}

/** A deterministic "confidence" for the idle scan, so the readout wanders without a random source. */
export function idleConfidence(step: number): string {
  return (0.6 + ((step * 37) % 35) / 100).toFixed(2);
}

export function letterTarget(rects: Rect[], index: number, confidence: string): Target {
  const r = rects[index];
  return {
    box: { x: r.x - 4, y: r.y - 2, w: r.w + 8, h: r.h + 4 },
    label: `${WORD[index].toUpperCase()} · ${confidence}`,
    letter: index,
  };
}

export function wordTarget(rects: Rect[], confidence: string): Target {
  const first = rects[0];
  const last = rects[rects.length - 1];
  return {
    box: { x: first.x - 8, y: first.y - 2, w: last.x + last.w - first.x + 16, h: first.h + 4 },
    label: `STREAMSENSE · ${confidence}`,
    letter: null,
  };
}

/** The idle scan walks the letters, then rests on the whole word for a few steps. */
export const IDLE_STEPS = WORD.length + 3;

export function idleTarget(rects: Rect[], step: number): Target {
  return step < WORD.length ? letterTarget(rects, step, idleConfidence(step)) : wordTarget(rects, "0.97");
}
