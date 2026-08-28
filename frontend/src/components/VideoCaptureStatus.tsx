import { getVideoCaptureStatus, type VideoCaptureStatus as CaptureStatus } from "../api/video";
import { usePolledResource } from "../hooks/usePolledResource";

function formatStatus(status: CaptureStatus | null, error: string | null): string {
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

function title(status: CaptureStatus | null, error: string | null): string | undefined {
  if (error) return error;
  const channelError = status?.channelStatuses?.find((channel) => channel.lastError)?.lastError;
  if (channelError) return channelError;
  const transcriptPreview = status?.channelStatuses?.find(
    (channel) => channel.lastTranscriptPreview,
  )?.lastTranscriptPreview;
  if (transcriptPreview) return `Latest transcript: ${transcriptPreview}`;
  if (status?.lastFrameAt) return `Last frame: ${new Date(status.lastFrameAt).toLocaleTimeString()}`;
  return undefined;
}

export function VideoCaptureStatus() {
  const { data: status, error } = usePolledResource(getVideoCaptureStatus, 10000);

  return (
    <span className="status-pill" title={title(status, error)}>
      {formatStatus(status, error)}
    </span>
  );
}
