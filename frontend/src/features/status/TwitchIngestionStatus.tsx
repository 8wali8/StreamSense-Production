import { getTwitchIngestionStatus, type TwitchIngestionStatus as TwitchStatus } from "../../api/chat";
import { usePolledResource } from "../../hooks/usePolledResource";

function formatStatus(status: TwitchStatus | null, error: string | null): string {
  if (error) return "Twitch: status unavailable";
  if (!status) return "Twitch: checking";
  if (!status.enabled) return "Twitch: disabled";
  if (status.state === "CONNECTED") return `Twitch: connected ${formatChannels(status.channels)}`;
  if (status.state === "RECONNECTING") return "Twitch: reconnecting";
  if (status.state === "FAILED") return "Twitch: failed";
  return `Twitch: ${status.state.toLowerCase()}`;
}

function formatChannels(channels: string[]): string {
  if (channels.length === 0) return "";
  return `@${channels.join(", @")}`;
}

export function TwitchIngestionStatus() {
  const { data: status, error } = usePolledResource(getTwitchIngestionStatus, 10000);

  return (
    <span className="status-pill" title={status?.lastError ?? error ?? undefined}>
      {formatStatus(status, error)}
    </span>
  );
}
