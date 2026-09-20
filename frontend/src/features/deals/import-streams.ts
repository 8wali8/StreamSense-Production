import type { VodImportStatus, VodListing } from "../../api/analytics";
import { formatDuration } from "../session/report-format";

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
      return "Imported";
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

/** A Stop is offered while a service may still be working on the import. */
export function canStop(status: VodImportStatus | undefined): boolean {
  return status?.state === "QUEUED" || status?.state === "IMPORTING";
}

/** A Resume is the import action again, from where the import got to. */
export function canResume(status: VodImportStatus | undefined): boolean {
  return status?.state === "STOPPED" || status?.state === "FAILED";
}
