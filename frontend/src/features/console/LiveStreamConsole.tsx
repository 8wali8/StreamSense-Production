import { sponsorProfileFromInput } from "../streamer/streamer";
import { ChatFeed } from "./ChatFeed";
import { SponsorSentimentFeed } from "./SponsorSentimentFeed";
import { StreamFrame } from "./StreamFrame";
import { TranscriptFeed } from "./TranscriptFeed";
import { useConsoleFeeds } from "./useConsoleFeeds";

type LiveStreamConsoleProps = {
  streamer: string;
  sponsorBrand: string;
  campaignGoal: string;
};

/** The player with detections on the left, transcript / sponsor / chat feeds on the right. */
export function LiveStreamConsole({ streamer, sponsorBrand, campaignGoal }: LiveStreamConsoleProps) {
  const activeSponsor = sponsorProfileFromInput(streamer, sponsorBrand, campaignGoal).sponsor;
  const feeds = useConsoleFeeds(streamer, activeSponsor);

  return (
    <section className="stream-console" aria-label="Live stream analysis console">
      <StreamFrame
        streamer={streamer}
        sponsorBrand={sponsorBrand}
        campaignGoal={campaignGoal}
        sponsors={feeds.sponsors}
        latestEventAt={feeds.latestEventAt}
      />

      <aside className="stream-sidecar" aria-label="Transcript and chat analysis">
        <TranscriptFeed
          lines={feeds.transcriptFeed}
          activeSponsor={activeSponsor}
          loading={feeds.transcript.loading}
          error={feeds.transcript.error}
        />
        <SponsorSentimentFeed
          activeSponsor={activeSponsor}
          sponsorBrand={sponsorBrand}
          chatSentiments={feeds.sponsorSentiments}
          transcriptSentiments={feeds.sponsorTranscriptSentiments}
          loading={feeds.sponsorSentimentLoading}
        />
        <ChatFeed
          liveChat={feeds.liveChat}
          chatSentiments={feeds.chatSentiments}
          loading={feeds.chatSentimentLoading}
        />
      </aside>
    </section>
  );
}
