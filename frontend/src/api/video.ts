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
