import type { RecentTranscriptSegmentsQuery } from "../graphql/generated";
import { ApiError, apiFetch, apiSend } from "../lib/api-client";

/**
 * GET /api/sentiment/transcript/recent returns the same TranscriptSegmentEvent the GraphQL query
 * selects from (the REST body is a superset), so the generated selection type is the contract here.
 */
export type TranscriptSegment = RecentTranscriptSegmentsQuery["recentTranscriptSegments"][number];

export async function getRecentTranscriptSegments(streamer: string, limit: number): Promise<TranscriptSegment[]> {
  const segments = await apiFetch<unknown>("/api/sentiment/transcript/recent", { params: { streamer, limit } });
  return Array.isArray(segments) ? (segments as TranscriptSegment[]) : [];
}

/**
 * POST /api/sentiment/relevance/sponsors: the sponsor profile relevance scoring uses for a streamer.
 * Aliases and semantic terms are merged with the ones configured for that sponsor in config-repo;
 * `minScore` overrides the configured relevance threshold when given.
 */
export type SponsorProfile = {
  streamer: string;
  sponsor: string;
  aliases: string[];
  semanticTerms: string[];
  minScore?: number;
};

export function updateSponsorProfile(profile: SponsorProfile): Promise<void> {
  return apiSend("/api/sentiment/relevance/sponsors", { body: profile });
}

/**
 * The sponsor catalog (`/api/sentiment/relevance/catalog`): what is known about a sponsor across every
 * channel. A deal that names the sponsor by name or by any alias borrows the entry's aliases and terms
 * into the channel's profile; `minScore` null means the configured default.
 */
export type SponsorCatalogEntry = {
  name: string;
  aliases: string[];
  semanticTerms: string[];
  minScore: number | null;
  updatedAt: number;
};

export function listSponsorCatalog(): Promise<SponsorCatalogEntry[]> {
  return apiFetch<SponsorCatalogEntry[]>("/api/sentiment/relevance/catalog");
}

/** PUT: replaces or adds the entry of that name (case does not matter) and refreshes the profiles that reach it. */
export function saveSponsorCatalogEntry(
  entry: Omit<SponsorCatalogEntry, "updatedAt" | "minScore"> & { minScore?: number },
): Promise<SponsorCatalogEntry> {
  return apiFetch<SponsorCatalogEntry>("/api/sentiment/relevance/catalog", { method: "PUT", body: entry });
}

/** DELETE: removes the entry; channel profiles keep what they borrowed. */
export function removeSponsorCatalogEntry(name: string): Promise<void> {
  return apiSend(`/api/sentiment/relevance/catalog/${encodeURIComponent(name)}`, { method: "DELETE" });
}

/** GET /api/sentiment/relevance/sponsors/{streamer}: the stored profile in effect, or null when the channel has none. */
export async function getSponsorProfile(streamer: string): Promise<SponsorProfile | null> {
  try {
    return await apiFetch<SponsorProfile>(`/api/sentiment/relevance/sponsors/${encodeURIComponent(streamer)}`);
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) return null;
    throw err;
  }
}
