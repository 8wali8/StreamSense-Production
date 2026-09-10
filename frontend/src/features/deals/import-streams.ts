import type { VodListing } from "../../api/analytics";
import type { VodImportStatus } from "../../api/video";

const LOOKBACK_MS = 24 * 3_600_000;

/** Which recordings fall inside the deal, or all of them. Pure so it can be tested. */
export function recordingsForDeal(all: VodListing[], startsAt: number, endsAt: number | null, showAll: boolean) {
  if (showAll) return all;
  return all.filter((vod) => vod.createdAt >= startsAt - LOOKBACK_MS && (endsAt == null || vod.createdAt < endsAt));
}

export function importLabel(status: VodImportStatus | undefined): string | null {
  if (!status) return null;
  if (status.state === "DONE") return "Imported";
  if (status.state === "FAILED") return `Import failed: ${status.lastError ?? "unknown error"}`;
  if (status.state === "QUEUED") return "Queued";
  const share = status.durationSeconds > 0 ? Math.round((status.offsetSeconds / status.durationSeconds) * 100) : 0;
  return `Importing · ${share}%`;
}
