import { sponsorProfileFromInput } from "../streamer/streamer";
import { ChatFeed } from "./ChatFeed";
import { SponsorSentimentFeed } from "./SponsorSentimentFeed";
import { StreamFrame } from "./StreamFrame";
import { TranscriptFeed } from "./TranscriptFeed";
import { useConsoleFeeds } from "./useConsoleFeeds";

type LiveStreamConsoleProps = {
  streamer: string;
  /** The sponsor field as entered, or undefined when none has been entered yet. */
  sponsor: string | undefined;
};

/**
 * The player with detections beside the brand-mention feed; the general chat and transcript sit
 * in a drawer below, closed by default, because the brand is what the page is about.
 */
export function LiveStreamConsole({ streamer, sponsor }: LiveStreamConsoleProps) {
  // A blank sponsor leaves the sponsor-sentiment queries unfiltered rather than searching for the placeholder word.
  const activeSponsor = sponsor ? sponsorProfileFromInput(streamer, sponsor).sponsor : "";
  const displayBrand = sponsor ?? "Sponsor";
  const feeds = useConsoleFeeds(streamer, activeSponsor);

  return (
    <section className="stream-console" aria-label="Live stream analysis console">
      <div className="console-main">
        <StreamFrame
          streamer={streamer}
          sponsorBrand={displayBrand}
          sponsors={feeds.sponsors}
          latestEventAt={feeds.latestEventAt}
        />
        <SponsorSentimentFeed
          activeSponsor={activeSponsor}
          chatSentiments={feeds.sponsorSentiments}
          transcriptSentiments={feeds.sponsorTranscriptSentiments}
          loading={feeds.sponsorSentimentLoading}
        />
      </div>

      <details className="console-drawer">
        <summary>All chat and transcript</summary>
        <div className="console-drawer-body">
          <TranscriptFeed
            lines={feeds.transcriptFeed}
            activeSponsor={activeSponsor}
            loading={feeds.transcript.loading}
            error={feeds.transcript.error}
          />
          <ChatFeed
            liveChat={feeds.liveChat}
            chatSentiments={feeds.chatSentiments}
            loading={feeds.chatSentimentLoading}
          />
        </div>
      </details>
    </section>
  );
}
