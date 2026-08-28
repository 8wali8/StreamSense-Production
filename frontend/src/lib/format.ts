/** Small display helpers shared by the console and the panels. Pure, no React. */

export function formatTime(ts?: number | null): string {
  if (!ts) return "--:--:--";
  return new Date(ts).toLocaleTimeString();
}

export function formatScore(value?: number | null): string {
  return typeof value === "number" && Number.isFinite(value) ? value.toFixed(2) : "--";
}

/** 0..1 (or an already-percent value) to a CSS percentage, clamped. */
export function percent(value: number): string {
  const normalized = value <= 1 ? value * 100 : value;
  return `${Math.max(0, Math.min(100, normalized))}%`;
}

/** CSS class for the compact analysis chips in the live console. */
export function analysisClass(label?: string | null): string {
  const normalized = label?.toLowerCase();
  if (normalized === "positive") return "analysis-positive";
  if (normalized === "negative") return "analysis-negative";
  return "analysis-neutral";
}

/** CSS class for the sentiment badge in the evidence panels. */
export function sentimentLabelClass(label: string): string {
  if (label === "POSITIVE") return "label-positive";
  if (label === "NEGATIVE") return "label-negative";
  return "label-neutral";
}

export function sentimentColor(label: string): string {
  if (label === "POSITIVE") return "#157f3b";
  if (label === "NEGATIVE") return "#b42318";
  return "#7a5c00";
}

/** What a sponsor-relevant event matched on, for the "Matched …" line. */
export function matchedContext(
  event: { matchedTerms?: string[] | null; matchedSponsor?: string | null },
  fallback: string,
): string {
  const terms = Array.isArray(event.matchedTerms) ? event.matchedTerms.filter(Boolean) : [];
  return terms.join(", ") || event.matchedSponsor || fallback || "sponsor context";
}

/** Newest-first merge of live items over history, first occurrence of an id wins, capped at `limit`. */
export function mergeById<T>(
  liveItems: readonly T[],
  historyItems: readonly T[],
  getId: (item: T) => string,
  limit: number,
): T[] {
  const seen = new Set<string>();
  const merged: T[] = [];

  for (const item of [...liveItems, ...historyItems]) {
    const id = getId(item);
    if (seen.has(id)) continue;
    seen.add(id);
    merged.push(item);
  }

  return merged.slice(0, limit);
}
