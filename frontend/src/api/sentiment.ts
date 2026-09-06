import type { RecentTranscriptSegmentsQuery } from "../graphql/generated";
import { apiFetch, apiSend } from "../lib/api-client";

/**
 * GET /api/sentiment/transcript/recent returns the same TranscriptSegmentEvent the GraphQL query
 * selects from (the REST body is a superset), so the generated selection type is the contract here.
 */
export type TranscriptSegment = RecentTranscriptSegmentsQuery["recentTranscriptSegments"][number];

export async function getRecentTranscriptSegments(streamer: string, limit: number): Promise<TranscriptSegment[]> {
  const segments = await apiFetch<unknown>("/api/sentiment/transcript/recent", { params: { streamer, limit } });
  return Array.isArray(segments) ? (segments as TranscriptSegment[]) : [];
}

/** POST /api/sentiment/relevance/sponsors: the sponsor profile relevance scoring uses for a streamer. */
export type SponsorProfile = {
  streamer: string;
  sponsor: string;
  aliases: string[];
  semanticTerms: string[];
  campaignGoal: string;
};

export function updateSponsorProfile(profile: SponsorProfile): Promise<void> {
  return apiSend("/api/sentiment/relevance/sponsors", { body: profile });
}
