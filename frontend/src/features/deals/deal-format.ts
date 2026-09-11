/** Pure helpers for the deal pages. */

const DATE = new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric" });
const DATE_YEAR = new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric", year: "numeric" });

/**
 * "Sep 1 – Sep 30, 2026", or "since Sep 1, 2026" for an open-ended deal. `endsAt` is the exclusive bound
 * the form stores (the midnight after the chosen day), so the day shown is the instant just before it.
 */
export function dealDates(startsAt: number, endsAt: number | null | undefined): string {
  if (endsAt == null) return `since ${DATE_YEAR.format(startsAt)}`;
  const lastDay = endsAt - 1;
  const sameYear = new Date(startsAt).getFullYear() === new Date(lastDay).getFullYear();
  return `${(sameYear ? DATE : DATE_YEAR).format(startsAt)} – ${DATE_YEAR.format(lastDay)}`;
}

/** Media value over the fee, e.g. 1.4; null when either side is missing or the fee is zero. */
export function feeMultiple(mediaValue: number | null | undefined, fee: number | null | undefined): number | null {
  if (mediaValue == null || fee == null || fee <= 0) return null;
  return Math.round((mediaValue / fee) * 100) / 100;
}

/** "3 of 4 streams", or "3 streams" with nothing promised. */
export function streamsProgress(done: number, promised: number | null | undefined): string {
  const noun = done === 1 && promised == null ? "stream" : "streams";
  return promised == null ? `${done} ${noun}` : `${done} of ${promised} ${noun}`;
}

/** A local yyyy-mm-dd for a date input, from epoch millis. */
export function toDateInput(at: number): string {
  const d = new Date(at);
  const month = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${d.getFullYear()}-${month}-${day}`;
}

/** Epoch millis at local midnight for a yyyy-mm-dd input; null for an empty or malformed value. */
export function fromDateInput(value: string, endOfDay = false): number | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value.trim());
  if (!match) return null;
  const date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
  if (Number.isNaN(date.getTime())) return null;
  if (endOfDay) date.setDate(date.getDate() + 1);
  return date.getTime();
}
