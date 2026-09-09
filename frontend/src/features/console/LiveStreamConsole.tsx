import { sponsorProfileFromInput } from "../streamer/streamer";
import { ChatFeed } from "./ChatFeed";
import { SponsorSentimentFeed } from "./SponsorSentimentFeed";
import { StreamFrame } from "./StreamFrame";
import { TranscriptFeed } from "./TranscriptFeed";
import { useConsoleFeeds } from "./useConsoleFeeds";

type LiveStreamConsoleProps = {
  streamer: string;
  sponsorBrand: string;
};

/**
 * The player with detections beside the brand-mention feed; the general chat and transcript sit
 * in a drawer below, closed by default, because the brand is what the page is about.
 */
export function LiveStreamConsole({ streamer, sponsorBrand }: LiveStreamConsoleProps) {
  const activeSponsor = sponsorProfileFromInput(streamer, sponsorBrand).sponsor;
  const feeds = useConsoleFeeds(streamer, activeSponsor);

  return (
    <section className="stream-console" aria-label="Live stream analysis console">
      <div className="console-main">
        <StreamFrame
          streamer={streamer}
          sponsorBrand={sponsorBrand}
          sponsors={feeds.sponsors}
          latestEventAt={feeds.latestEventAt}
        />
        <SponsorSentimentFeed
          activeSponsor={activeSponsor}
          sponsorBrand={sponsorBrand}
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
