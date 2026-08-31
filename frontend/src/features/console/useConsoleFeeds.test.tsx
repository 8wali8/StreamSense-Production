import { act, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import { emitSubscription, renderWithApollo } from "../../test/apollo";
import {
  chatMessage,
  sentimentEvent,
  sponsorDetection,
  transcriptSegment,
  transcriptSentiment,
} from "../../test/fixtures";
import { graphqlData, graphqlError, restJson, server } from "../../test/msw";
import { useConsoleFeeds } from "./useConsoleFeeds";

function Probe({ streamer }: { streamer: string }) {
  const feeds = useConsoleFeeds(streamer, "Nike");
  return (
    <div>
      <span data-testid="chat">{feeds.liveChat.map((event) => event.message).join("|")}</span>
      <span data-testid="sentiment">{feeds.chatSentiments.map((event) => event.sentimentEventId).join("|")}</span>
      <span data-testid="sponsors">{feeds.sponsors.map((event) => event.detectionEventId).join("|")}</span>
      <span data-testid="transcript">{feeds.transcriptFeed.map((line) => line.id).join("|")}</span>
      <span data-testid="transcript-error">{feeds.transcript.error ?? "none"}</span>
      <span data-testid="latest">{feeds.latestEventAt ?? "none"}</span>
    </div>
  );
}

function historyHandlers() {
  return [
    graphqlData("SponsorDetections", { sponsorDetections: [sponsorDetection({ detectionEventId: "det-h" })] }),
    graphqlData("RecentTranscriptSegments", { recentTranscriptSegments: [transcriptSegment({ segmentId: "seg-h" })] }),
    graphqlData("RecentSentiment", { recentSentiment: [sentimentEvent({ sentimentEventId: "sent-h" })] }),
    graphqlData("RecentTranscriptSentiment", {
      recentTranscriptSentiment: [transcriptSentiment({ segmentId: "seg-h" })],
    }),
    graphqlData("RecentSponsorSentiment", { recentSponsorSentiment: [] }),
    graphqlData("RecentSponsorTranscriptSentiment", { recentSponsorTranscriptSentiment: [] }),
    restJson("get", "/api/sentiment/transcript/recent", []),
  ];
}

describe("useConsoleFeeds", () => {
  beforeEach(() => {
    server.use(...historyHandlers());
  });

  it("layers live subscription events over the history queries", async () => {
    const apollo = renderWithApollo(<Probe streamer="test" />);
    expect(await screen.findByText("det-h")).toBeInTheDocument();
    expect(screen.getByTestId("transcript")).toHaveTextContent("seg-h");

    act(() => {
      emitSubscription(apollo, { onChatMessage: chatMessage({ eventId: "evt-live", message: "hello live" }) });
      emitSubscription(apollo, { onSentiment: sentimentEvent({ sentimentEventId: "sent-live" }) });
      emitSubscription(apollo, {
        onSponsorDetection: sponsorDetection({ detectionEventId: "det-live", capturedAt: 1710000009000 }),
      });
    });

    expect(await screen.findByText("hello live")).toBeInTheDocument();
    expect(screen.getByTestId("sentiment")).toHaveTextContent("sent-live|sent-h");
    expect(screen.getByTestId("sponsors")).toHaveTextContent("det-live|det-h");
    expect(screen.getByTestId("latest")).toHaveTextContent("1710000009000");
  });

  it("ignores live events for another streamer", async () => {
    const apollo = renderWithApollo(<Probe streamer="test" />);
    await screen.findByText("det-h");

    act(() => {
      emitSubscription(apollo, {
        onChatMessage: chatMessage({ eventId: "evt-other", streamer: "someone-else", message: "not mine" }),
      });
      emitSubscription(apollo, {
        onSentiment: sentimentEvent({ sentimentEventId: "sent-other", streamer: "someone-else" }),
      });
    });

    expect(screen.getByTestId("chat")).toHaveTextContent("");
    expect(screen.getByTestId("sentiment")).toHaveTextContent("sent-h");
  });

  it("describes a transcript query failure with the gateway's code", async () => {
    server.use(graphqlError("RecentTranscriptSegments", "Downstream service unavailable", "DOWNSTREAM_UNAVAILABLE"));

    renderWithApollo(<Probe streamer="test" />);

    expect(await screen.findByText("a downstream service is unavailable")).toBeInTheDocument();
  });
});
