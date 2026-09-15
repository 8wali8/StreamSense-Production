import type { TwitchChannelIngest } from "../../api/chat";
import type { CaptureChannelStatus } from "../../api/video";

/** Capture states that mean nothing is running for the channel. */
const CAPTURE_OFF = new Set(["DISABLED", "STOPPED"]);

export type Measurement = {
  /** Whether anything is being ingested for this channel right now. */
  measuring: boolean;
  /** The one line the control shows: what is running, or why nothing is. */
  detail: string;
  /** Both halves are switched off for the whole deployment, so the button cannot help. */
  unavailable: boolean;
};

/**
 * What the two per-channel reads say about one channel, in a sentence a streamer can act on.
 * Half-running is reported as such rather than rounded to on or off: a channel whose chat is
 * ingested but whose video is not produces a report with no exposure in it.
 */
export function describeMeasurement(
  chat: TwitchChannelIngest | null,
  capture: CaptureChannelStatus | null,
): Measurement {
  const chatOn = chat?.joined === true;
  const captureOn = capture != null && !CAPTURE_OFF.has(capture.state);
  const chatOff = chat != null && !chat.enabled;
  const captureOff = capture?.state === "DISABLED";

  if (chatOff && captureOff) {
    return {
      measuring: false,
      unavailable: true,
      detail: "Chat ingest and video capture are switched off here. An operator turns them on.",
    };
  }
  if (chatOn && captureOn) {
    return { measuring: true, unavailable: false, detail: "Chat and video are being measured." };
  }
  if (chatOn) {
    return {
      measuring: true,
      unavailable: false,
      detail: captureOff
        ? "Chat is being measured. Video capture is switched off here, so there is no on-screen exposure."
        : "Chat is being measured, but video is not: the report will have no on-screen exposure.",
    };
  }
  if (captureOn) {
    return {
      measuring: true,
      unavailable: false,
      detail: chatOff
        ? "Video is being captured. Chat ingest is switched off here, so there are no chat mentions."
        : "Video is being captured, but chat is not: the report will have no chat mentions.",
    };
  }
  return { measuring: false, unavailable: false, detail: "This channel is not being measured." };
}
