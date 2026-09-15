import { apiFetch, apiSend, apiUrl } from "../lib/api-client";

/** GET /api/video/capture/status (video-capture-service, proxied by the gateway). */
export type VideoCaptureStatus = {
  enabled: boolean;
  state: string;
  channels: string[];
  lastFrameAt: number | null;
  lastTranscriptAt: number | null;
  channelStatuses?: Array<{
    channel: string;
    state: string;
    lastError: string | null;
    lastTranscriptPreview: string | null;
  }>;
  /** Recordings being imported with their original timestamps (see features/deals/ImportStreams). */
  imports?: VodImportStatus[];
};

export type VodImportStatus = {
  vodId: string;
  channel: string;
  state: string;
  offsetSeconds: number;
  durationSeconds: number;
  framesPublished: number;
  transcriptSegmentsPublished: number;
  failures: number;
  lastError: string | null;
};

export function getVideoCaptureStatus(): Promise<VideoCaptureStatus> {
  return apiFetch<VideoCaptureStatus>("/api/video/capture/status");
}

/** POST /api/video/capture/channels: point frame and transcript capture at these channels. */
export function switchCaptureChannels(channels: string[]): Promise<void> {
  return apiSend("/api/video/capture/channels", { body: { channels } });
}

/** URL of a captured frame image, for an <img> tag. */
export function frameImageUrl(frameRef: string): string {
  return apiUrl("/api/video/capture/frame", { frameRef });
}

/** One channel's capture, from the per-channel routes a streamer may call for their own channel. */
export type CaptureChannelStatus = {
  channel: string;
  state: string;
  captureSessionId: string | null;
  lastFrameAt: number | null;
  lastError: string | null;
};

/** GET /api/video/capture/channels/{channel}: is this channel being captured. */
export function getCaptureChannel(channel: string): Promise<CaptureChannelStatus> {
  return apiFetch<CaptureChannelStatus>(`/api/video/capture/channels/${encodeURIComponent(channel)}`);
}

/** PUT /api/video/capture/channels/{channel}: start capturing it, leaving the other channels alone. */
export function startCaptureChannel(channel: string): Promise<CaptureChannelStatus> {
  return apiFetch<CaptureChannelStatus>(`/api/video/capture/channels/${encodeURIComponent(channel)}`, {
    method: "PUT",
  });
}

/** DELETE /api/video/capture/channels/{channel}: stop capturing it. */
export function stopCaptureChannel(channel: string): Promise<CaptureChannelStatus> {
  return apiFetch<CaptureChannelStatus>(`/api/video/capture/channels/${encodeURIComponent(channel)}`, {
    method: "DELETE",
  });
}
