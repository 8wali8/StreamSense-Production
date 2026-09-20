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

/**
 * Where a recording's import stands, as analytics-service records it (it owns the state; the two
 * replay services only run their halves). `offsetSeconds` follows the slower half; a resume
 * continues from there. STOPPING means a stop was asked for and one half has yet to confirm.
 */
export type VodImportStatus = {
  streamer: string;
  vodId: string;
  sessionId: number | null;
  state: "QUEUED" | "IMPORTING" | "STOPPING" | "STOPPED" | "DONE" | "FAILED" | (string & {});
  offsetSeconds: number;
  durationSeconds: number;
  chatState: string;
  captureState: string;
  lastError: string | null;
  updatedAt: number;
};

/** GET .../vods/imports: every import the channel asked for, the active ones brought up to date. */
export function listVodImports(streamer: string): Promise<VodImportStatus[]> {
  return apiFetch<VodImportStatus[]>(`/api/analytics/streams/${encodeURIComponent(streamer)}/vods/imports`);
}

/** POST .../vods/{vodId}/import: creates the session and starts (or resumes) the chat and capture replays. */
export type VodImport = {
  session: { id: string | number };
  streamSessionId: string;
  chatReplayStarted: boolean;
  captureReplayStarted: boolean;
  problems: string[];
  status: VodImportStatus;
};

export function importVod(streamer: string, vodId: string, averageViewers?: number): Promise<VodImport> {
  return apiFetch<VodImport>(
    `/api/analytics/streams/${encodeURIComponent(streamer)}/vods/${encodeURIComponent(vodId)}/import`,
    { method: "POST", body: averageViewers == null ? {} : { averageViewers } },
  );
}

/** POST .../vods/{vodId}/stop: ends both halves of the import; what was published stays. */
export function stopVodImport(streamer: string, vodId: string): Promise<VodImportStatus> {
  return apiFetch<VodImportStatus>(
    `/api/analytics/streams/${encodeURIComponent(streamer)}/vods/${encodeURIComponent(vodId)}/stop`,
    { method: "POST" },
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

/**
 * GET /api/analytics/helix/status: what the Twitch Helix poller last did. Counts only; the watched
 * logins are not exposed. Times are epoch milliseconds, null when the event has not happened.
 */
export type HelixPollStatus = {
  enabled: boolean;
  pollIntervalMs: number;
  lastAttemptAt: number | null;
  lastPollAt: number | null;
  watched: number;
  live: number;
  closed: number;
  pausedUntil: number | null;
  lastError: string | null;
  lastErrorAt: number | null;
};

export function getHelixPollStatus(): Promise<HelixPollStatus> {
  return apiFetch<HelixPollStatus>("/api/analytics/helix/status");
}
