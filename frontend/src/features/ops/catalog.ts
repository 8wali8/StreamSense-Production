import type { SponsorUsage } from "../../api/analytics";
import type { SponsorCatalogEntry } from "../../api/sentiment";

function key(value: string): string {
  return value.trim().toLowerCase();
}

/** The entry a sponsor's name reaches, by name first and then by any entry's alias; case does not matter. */
export function catalogEntryFor(sponsor: string, catalog: SponsorCatalogEntry[]): SponsorCatalogEntry | null {
  const wanted = key(sponsor);
  if (wanted === "") return null;
  const byName = catalog.find((entry) => key(entry.name) === wanted);
  if (byName) return byName;
  return catalog.find((entry) => entry.aliases.some((alias) => key(alias) === wanted)) ?? null;
}

/** The sponsors deals name that no catalog entry reaches: what the operator has yet to describe. */
export function unknownSponsors(usage: SponsorUsage[], catalog: SponsorCatalogEntry[]): SponsorUsage[] {
  return usage.filter((used) => catalogEntryFor(used.sponsor, catalog) === null);
}
