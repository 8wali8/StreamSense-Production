import { useEffect, useState } from "react";

type VideoCaptureStatusResponse = {
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

function formatStatus(status: VideoCaptureStatusResponse | null, error: string | null): string {
  if (error) return "Video: status unavailable";
  if (!status) return "Video: checking";
  if (!status.enabled) return "Video: disabled";
  if (status.state === "CAPTURING") return `Video: capturing ${formatChannels(status.channels)}`;
  if (status.state === "IDLE_OFFLINE") return "Video: stream offline";
  if (status.state.startsWith("DEGRADED")) return `Video: ${status.state.toLowerCase().replace("_", " ")}`;
  if (status.state === "FAILED") return "Video: failed";
  return `Video: ${status.state.toLowerCase().replace("_", " ")}`;
}

function formatChannels(channels: string[]): string {
  const visibleChannels = channels.filter((channel) => channel !== "disabled");
  if (visibleChannels.length === 0) return "";
  return `@${visibleChannels.join(", @")}`;
}

function title(status: VideoCaptureStatusResponse | null, error: string | null): string | undefined {
  if (error) return error;
  const channelError = status?.channelStatuses?.find((channel) => channel.lastError)?.lastError;
  if (channelError) return channelError;
  const transcriptPreview = status?.channelStatuses?.find((channel) => channel.lastTranscriptPreview)?.lastTranscriptPreview;
  if (transcriptPreview) return `Latest transcript: ${transcriptPreview}`;
  if (status?.lastFrameAt) return `Last frame: ${new Date(status.lastFrameAt).toLocaleTimeString()}`;
  return undefined;
}

export function VideoCaptureStatus() {
  const [status, setStatus] = useState<VideoCaptureStatusResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function loadStatus() {
      try {
        const response = await fetch("/api/video/capture/status");
        if (!response.ok) {
          throw new Error(`status ${response.status}`);
        }
        const body = (await response.json()) as VideoCaptureStatusResponse;
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
    <span className="status-pill" title={title(status, error)}>
      {formatStatus(status, error)}
    </span>
  );
}
