/** Pure rules for the sponsor's logo: what may be picked, how many, and what the deal page says about them. */

/** The image types a logo may be; the service decides from the bytes, this only filters the picker. */
export const LOGO_TYPES = new Set(["image/png", "image/jpeg"]);
export const MAX_LOGOS = 2;

/** Keeps the PNGs and JPEGs of a selection or a drop, at most `remaining` of them. */
export function pickLogoFiles(list: ArrayLike<File> | null | undefined, remaining: number): File[] {
  return Array.from(list ?? [])
    .filter((file) => LOGO_TYPES.has(file.type))
    .slice(0, Math.max(0, remaining));
}

/** The one sentence under the deal page's "Sponsor logo" heading: what the logos mean for the report. */
export function logosLead(sponsor: string, count: number, sharedView: boolean): string {
  if (count === 0) {
    return sharedView
      ? "On-screen tracking is off for this deal: no logo has been added."
      : "On-screen tracking is off until you add the sponsor's logo.";
  }
  return sharedView
    ? `Tracking the ${sponsor} logo on screen.`
    : "What on-screen detection looks for from now on. Changing it affects streams from this moment; earlier reports keep their numbers.";
}
