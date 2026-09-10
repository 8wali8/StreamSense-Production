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

/** GET /api/sentiment/relevance/sponsors/{streamer}: the stored profile in effect, or null when the channel has none. */
export async function getSponsorProfile(streamer: string): Promise<SponsorProfile | null> {
  try {
    return await apiFetch<SponsorProfile>(`/api/sentiment/relevance/sponsors/${encodeURIComponent(streamer)}`);
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) return null;
    throw err;
  }
}
