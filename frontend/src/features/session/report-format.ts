/** Pure formatting for the session report. Numbers in, short strings out. */

/** "2h 14m", "38m 12s", "45s". Negative or missing durations read as zero. */
export function formatDuration(ms: number | null | undefined): string {
  const total = Math.max(0, Math.round((ms ?? 0) / 1000));
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const seconds = total % 60;
  if (hours > 0) return `${hours}h ${minutes.toString().padStart(2, "0")}m`;
  if (minutes > 0) return `${minutes}m ${seconds.toString().padStart(2, "0")}s`;
  return `${seconds}s`;
}

/** A point in the stream as a clock offset: "1:20" for 80 minutes, "0:04" for 4 minutes, "1:02:05" past an hour. */
export function formatOffset(offsetMs: number): string {
  const total = Math.max(0, Math.floor(offsetMs / 1000));
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const seconds = total % 60;
  if (hours > 0) {
    return `${hours}:${minutes.toString().padStart(2, "0")}:${seconds.toString().padStart(2, "0")}`;
  }
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

/** "28%" from a 0..1 share; "–" when unknown. */
export function formatShare(share: number | null | undefined): string {
  if (share == null || !Number.isFinite(share)) return "–";
  return `${Math.round(share * 100)}%`;
}

/** "$1,190" or "$12.50"; "–" when unknown. Whole dollars above 100, cents below. */
export function formatMoney(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) return "–";
  const rounded = Math.abs(value) >= 100 ? Math.round(value) : Math.round(value * 100) / 100;
  return `$${rounded.toLocaleString("en-US", {
    minimumFractionDigits: Math.abs(value) >= 100 ? 0 : 2,
    maximumFractionDigits: Math.abs(value) >= 100 ? 0 : 2,
  })}`;
}

/** "1,310"; "–" when unknown. */
export function formatCount(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) return "–";
  return Math.round(value).toLocaleString("en-US");
}

/** "0.62"; "–" when unknown. */
export function formatScore(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) return "–";
  return value.toFixed(2);
}

/** "Mon 7 Sep 2026, 21:27" in the viewer's locale. */
export function formatStart(startedAt: number): string {
  return new Date(startedAt).toLocaleString([], {
    weekday: "short",
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

type VodSession = { source: string; twitchStreamId?: string | null };

/**
 * A link that opens the recording at a moment. Only a capture session of a replay alias carries a
 * VOD id today (its twitchStreamId); a Helix session's stream id is not a video id, so no link.
 */
export function vodUrl(session: VodSession, offsetMs: number): string | null {
  if (session.source !== "CAPTURE" || !session.twitchStreamId || !/^\d+$/.test(session.twitchStreamId)) {
    return null;
  }
  const total = Math.max(0, Math.floor(offsetMs / 1000));
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const seconds = total % 60;
  return `https://www.twitch.tv/videos/${session.twitchStreamId}?t=${hours}h${minutes}m${seconds}s`;
}

/** Plain-language names for the risk factors the analytics service reports. */
export function riskFactorName(name: string): string {
  switch (name) {
    case "chatNegativeRatio":
      return "Share of chat that was negative";
    case "transcriptNegativeRatio":
      return "Share of voice that was negative";
    case "negativeSpikeScore":
      return "Negative spikes in chat";
    case "sponsorQualityRisk":
      return "Low-confidence or fallback detections";
    case "negativeEngagementSpikeRisk":
      return "Negative reaction during engagement spikes";
    default:
      return name;
  }
}
