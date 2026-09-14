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

/** One channel's chat ingest, from the per-channel routes a streamer may call for their own channel. */
export type TwitchChannelIngest = {
  channel: string;
  joined: boolean;
  enabled: boolean;
  state: string;
};

/** GET /api/chat/twitch/channels/{channel}: is this channel's chat being ingested. */
export function getTwitchChannelIngest(channel: string): Promise<TwitchChannelIngest> {
  return apiFetch<TwitchChannelIngest>(`/api/chat/twitch/channels/${encodeURIComponent(channel)}`);
}

/** PUT /api/chat/twitch/channels/{channel}: start ingesting it, leaving the other channels alone. */
export function joinTwitchChannel(channel: string): Promise<TwitchChannelIngest> {
  return apiFetch<TwitchChannelIngest>(`/api/chat/twitch/channels/${encodeURIComponent(channel)}`, { method: "PUT" });
}

/** DELETE /api/chat/twitch/channels/{channel}: stop ingesting it. */
export function partTwitchChannel(channel: string): Promise<TwitchChannelIngest> {
  return apiFetch<TwitchChannelIngest>(`/api/chat/twitch/channels/${encodeURIComponent(channel)}`, {
    method: "DELETE",
  });
}
