import { apiFetch, apiSend } from "../lib/api-client";

/** GET /api/chat/twitch/status (chat-service, proxied by the gateway). */
export type TwitchIngestionStatus = {
  enabled: boolean;
  state: string;
  channels: string[];
  lastMessageAt: number;
  lastError: string | null;
  reconnectAttempts: number;
};

export function getTwitchIngestionStatus(): Promise<TwitchIngestionStatus> {
  return apiFetch<TwitchIngestionStatus>("/api/chat/twitch/status");
}

/** POST /api/chat/twitch/channels: point live chat ingest (or VOD replay) at these channels. */
export function switchTwitchChannels(channels: string[]): Promise<void> {
  return apiSend("/api/chat/twitch/channels", { body: { channels } });
}
