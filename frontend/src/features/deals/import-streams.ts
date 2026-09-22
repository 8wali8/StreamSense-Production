import type { VodImportStatus, VodListing } from "../../api/analytics";
import { formatCount, formatDuration } from "../session/report-format";

const LOOKBACK_MS = 24 * 3_600_000;

/** Which recordings fall inside the deal, or all of them. Pure so it can be tested. */
export function recordingsForDeal(all: VodListing[], startsAt: number, endsAt: number | null, showAll: boolean) {
  if (showAll) return all;
  return all.filter((vod) => vod.createdAt >= startsAt - LOOKBACK_MS && (endsAt == null || vod.createdAt < endsAt));
}

/** The one line the row shows for an import, from what analytics-service last recorded. */
export function importLabel(status: VodImportStatus | undefined): string | null {
  if (!status) return null;
  switch (status.state) {
    case "DONE":
      return status.chatState === "NONE" ? "Imported (video and audio; no chat)" : "Imported";
    case "FAILED":
      return `Import failed: ${status.lastError ?? "unknown error"}`;
    case "QUEUED":
      return "Queued";
    case "STOPPING":
      return "Stopping…";
    case "STOPPED":
      return `Stopped at ${formatDuration(status.offsetSeconds * 1000)} of ${formatDuration(status.durationSeconds * 1000)}`;
    default: {
      const share = status.durationSeconds > 0 ? Math.round((status.offsetSeconds / status.durationSeconds) * 100) : 0;
      return `Importing · ${share}%`;
    }
  }
}

/**
 * What the row says about the recording's chat. Twitch offers no download of a recording's chat, so it
 * comes from a log the streamer hands over, or the import has video and audio only.
 */
export function chatLogNote(vod: VodListing, status: VodImportStatus | undefined): string {
  if (vod.chatLog) {
    const lines = `${formatCount(vod.chatLog.lineCount)} chat lines`;
    // A log handed over after the import finished is replayed on Resume.
    return status && !isActive(status) && status.chatState === "NONE" ? `${lines}, replayed on Resume` : lines;
  }
  return status?.chatState === "NONE" ? "No chat log: video and audio only" : "No chat log";
}

/** A Stop is offered while a service may still be working on the import. */
export function canStop(status: VodImportStatus | undefined): boolean {
  return status?.state === "QUEUED" || status?.state === "IMPORTING";
}

/** A Resume is the import action again, from where the import got to. */
export function canResume(status: VodImportStatus | undefined): boolean {
  return status?.state === "STOPPED" || status?.state === "FAILED";
}

/** A finished import with a log it has not replayed yet is resumed to fill the chat in. */
export function canFillChat(vod: VodListing, status: VodImportStatus | undefined): boolean {
  return vod.chatLog != null && status?.state === "DONE" && status.chatState === "NONE";
}

function isActive(status: VodImportStatus): boolean {
  return status.state === "QUEUED" || status.state === "IMPORTING" || status.state === "STOPPING";
}
