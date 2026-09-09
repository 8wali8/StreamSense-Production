import { formatTime, percent } from "../../lib/format";
import { twitchPlayerUrl } from "../streamer/streamer";
import type { SponsorDetectionEvent } from "./useConsoleFeeds";

type StreamFrameProps = {
  streamer: string;
  sponsorBrand: string;
  sponsors: SponsorDetectionEvent[];
  latestEventAt: number | undefined;
};

/** The embedded Twitch player with the latest sponsor detections drawn over it. */
export function StreamFrame({ streamer, sponsorBrand, sponsors, latestEventAt }: StreamFrameProps) {
  const latestFrame = sponsors.find((event) => event.frameRef);
  const frameOverlays = latestFrame
    ? sponsors.filter((event) => event.frameRef === latestFrame.frameRef).slice(0, 6)
    : [];
  const topSponsor = sponsors.find((event) => event.sponsor !== "UNKNOWN");

  return (
    <div className="video-console-card">
      <div className="video-topbar">
        <div>
          <span className="live-dot">LIVE</span>
          <strong>@{streamer}</strong>
          <span className="pill pill-teal">{sponsorBrand}</span>
        </div>
        <div className="video-clock">Last signal {formatTime(latestEventAt)}</div>
      </div>

      <div className="stream-frame-shell">
        <iframe
          className="stream-video-iframe"
          src={twitchPlayerUrl(streamer)}
          title={`Live Twitch stream for ${streamer}`}
          allow="autoplay; fullscreen; picture-in-picture"
          allowFullScreen
        />

        <div className="video-overlay video-overlay-bottom-left">
          <span>On screen</span>
          <strong>{topSponsor ? topSponsor.sponsor : "No sponsor yet"}</strong>
        </div>

        {frameOverlays.map((event) => (
          <div
            className="detection-box"
            key={event.detectionEventId}
            style={{
              left: percent(event.x),
              top: percent(event.y),
              width: percent(event.width),
              height: percent(event.height),
            }}
          >
            <span>{event.sponsor}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
