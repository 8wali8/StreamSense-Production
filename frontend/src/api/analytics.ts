import type { DealsQuery } from "../graphql/generated";
import { apiFetch, apiSend } from "../lib/api-client";

/** A deal as the gateway returns it; the REST create response has the same shape. */
export type Deal = DealsQuery["deals"][number];

/**
 * POST /api/analytics/deals. Rates default to the configured media value assumptions when absent;
 * the chat command is normalised to `!name` and the tracked link must be an absolute http(s) URL.
 */
export type DealCreateRequest = {
  streamer: string;
  sponsor: string;
  startsAt: number;
  endsAt?: number;
  promisedStreams?: number;
  fee?: number;
  currency?: string;
  cpmPer30sEquivalent?: number;
  hostReadRatePer1000?: number;
  trackedLink?: string;
  chatCommand?: string;
  channelPointReward?: string;
};

export function createDeal(request: DealCreateRequest): Promise<Deal> {
  return apiFetch<Deal>("/api/analytics/deals", { method: "POST", body: request });
}

/** GET /api/analytics/streams/{streamer}/vods: the channel's recordings on Twitch, newest first. */
export type VodListing = {
  vodId: string;
  streamId: string | null;
  title: string | null;
  createdAt: number;
  durationMs: number;
  url: string | null;
  viewCount: number;
  /** The session the recording was imported into, once it has been. */
  sessionId: number | null;
};

export function listVods(streamer: string, limit = 20): Promise<VodListing[]> {
  return apiFetch<VodListing[]>(`/api/analytics/streams/${encodeURIComponent(streamer)}/vods`, { params: { limit } });
}

/** POST .../vods/{vodId}/import: creates the session and starts the chat and capture replays. */
export type VodImport = {
  session: { id: string | number };
  streamSessionId: string;
  chatReplayStarted: boolean;
  captureReplayStarted: boolean;
  problems: string[];
};

export function importVod(streamer: string, vodId: string, averageViewers?: number): Promise<VodImport> {
  return apiFetch<VodImport>(
    `/api/analytics/streams/${encodeURIComponent(streamer)}/vods/${encodeURIComponent(vodId)}/import`,
    { method: "POST", body: averageViewers == null ? {} : { averageViewers } },
  );
}

export type ShareLink = { dealId: number; token: string };

/** POST /api/analytics/deals/{id}/share: mints the deal's read-only share token, or returns the existing one. */
export function createShareLink(dealId: string): Promise<ShareLink> {
  return apiFetch<ShareLink>(`/api/analytics/deals/${encodeURIComponent(dealId)}/share`, { method: "POST" });
}

/** DELETE /api/analytics/deals/{id}/share: every link carrying the token stops working. */
export function revokeShareLink(dealId: string): Promise<void> {
  return apiSend(`/api/analytics/deals/${encodeURIComponent(dealId)}/share`, { method: "DELETE" });
}
