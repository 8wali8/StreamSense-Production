import { useEffect, useState } from "react";

type TwitchStatus = {
  enabled: boolean;
  state: string;
  channels: string[];
  lastMessageAt: number;
  lastError: string | null;
  reconnectAttempts: number;
};

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
  const [status, setStatus] = useState<TwitchStatus | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function loadStatus() {
      try {
        const response = await fetch("/api/chat/twitch/status");
        if (!response.ok) {
          throw new Error(`status ${response.status}`);
        }
        const body = (await response.json()) as TwitchStatus;
        if (!cancelled) {
          setStatus(body);
          setError(null);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "unknown error");
        }
      }
    }

    void loadStatus();
    const interval = window.setInterval(loadStatus, 10000);
    return () => {
      cancelled = true;
      window.clearInterval(interval);
    };
  }, []);

  return (
    <span className="status-pill" title={status?.lastError ?? error ?? undefined}>
      {formatStatus(status, error)}
    </span>
  );
}
