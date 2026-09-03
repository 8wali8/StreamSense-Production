import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import App from "./App";
import { renderWithApollo } from "./test/apollo";
import {
  recommendation,
  streamAnalytics,
  transcriptSegment,
  transcriptSentiment,
  twitchStatusConnected,
  videoStatusCapturing,
} from "./test/fixtures";
import { HttpResponse, graphqlData, graphqlResolver, restJson, restProblem, restResolver, server } from "./test/msw";

const redbullSegment = transcriptSegment({
  segmentId: "segment-redbull-1",
  streamer: "redbull-testing",
  text: "Red Bull replay transcript stays visible after load.",
  startedAt: 1778734101283,
  endedAt: 1778734103736,
  source: "TWITCH_VOD_REPLAY",
  channelLogin: "redbull-testing",
  streamSessionId: "redbull-testing-2750461300",
  videoTimestampMs: 2436268,
  transcriptSequence: 30,
});

const redbullTranscriptSentiment = transcriptSentiment({
  sentimentEventId: "sentiment-redbull-1",
  segmentId: redbullSegment.segmentId,
  streamer: "redbull-testing",
  text: redbullSegment.text,
  segmentStartedAt: redbullSegment.startedAt,
  segmentEndedAt: redbullSegment.endedAt,
  processedAt: redbullSegment.endedAt + 500,
  streamSessionId: redbullSegment.streamSessionId,
  transcriptSequence: redbullSegment.transcriptSequence,
  sponsorRelevant: true,
  matchedSponsor: "Red Bull",
  relevanceScore: 0.8,
});

/** Every request the whole console makes, with data only for the redbull replay streamer. */
function stackHandlers() {
  const forRedbull = <T,>(variables: Record<string, unknown>, value: T[]): T[] =>
    variables.streamer === "redbull-testing" ? value : [];
  return [
    graphqlData("Health", { health: "ok" }),
    graphqlData("StreamAnalytics", streamAnalytics()),
    graphqlData("Recommendations", { recommendations: [recommendation()] }),
    graphqlData("SponsorDetections", { sponsorDetections: [] }),
    graphqlData("RecentSentiment", { recentSentiment: [] }),
    graphqlData("RecentSponsorSentiment", { recentSponsorSentiment: [] }),
    graphqlResolver("RecentTranscriptSegments", ({ variables }) =>
      HttpResponse.json({ data: { recentTranscriptSegments: forRedbull(variables, [redbullSegment]) } }),
    ),
    graphqlResolver("RecentTranscriptSentiment", ({ variables }) =>
      HttpResponse.json({ data: { recentTranscriptSentiment: forRedbull(variables, [redbullTranscriptSentiment]) } }),
    ),
    graphqlResolver("RecentSponsorTranscriptSentiment", ({ variables }) =>
      HttpResponse.json({
        data: { recentSponsorTranscriptSentiment: forRedbull(variables, [redbullTranscriptSentiment]) },
      }),
    ),
    restJson("get", "/api/chat/twitch/status", twitchStatusConnected),
    restJson("get", "/api/video/capture/status", videoStatusCapturing),
    restResolver("get", "/api/sentiment/transcript/recent", ({ request }) =>
      HttpResponse.json(
        new URL(request.url).searchParams.get("streamer") === "redbull-testing" ? [redbullSegment] : [],
      ),
    ),
    restJson("post", "/api/chat/twitch/channels", ["redbull-testing"]),
    restJson("post", "/api/video/capture/channels", { channels: ["redbull-testing"] }),
    restJson("post", "/api/sentiment/relevance/sponsors", {}),
  ];
}

async function loadRedbullConsole() {
  const user = userEvent.setup();
  const streamerInput = screen.getByDisplayValue("test");
  await user.clear(streamerInput);
  await user.type(streamerInput, "redbull-testing");
  const sponsorInput = screen.getByDisplayValue("Nike");
  await user.clear(sponsorInput);
  await user.type(sponsorInput, "Red Bull");
  await user.click(screen.getByRole("button", { name: /load console/i }));
}

describe("App live console", () => {
  beforeEach(() => {
    server.use(...stackHandlers());
  });

  it("keeps all transcript visible after loading the redbull replay and points the runtime at it", async () => {
    renderWithApollo(<App />);
    expect(await screen.findByText("Health: ok")).toBeInTheDocument();

    await loadRedbullConsole();

    const transcript = await screen.findByRole("heading", { name: "All transcript" });
    expect(transcript).toBeInTheDocument();
    // Scoped to the raw transcript feed: the sponsor sentiment feed renders the same text, so a
    // page-wide search would stay green even if the raw feed lost every line.
    const transcriptFeed = transcript.closest("section");
    expect(transcriptFeed).not.toBeNull();
    expect((await within(transcriptFeed as HTMLElement).findAllByText(redbullSegment.text)).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Matched Red Bull/).length).toBeGreaterThan(0);
    await waitFor(() => {
      expect(
        screen.getByText(
          /Chat, video frames, transcript capture, and sponsor relevance are pointed at @redbull-testing/,
        ),
      ).toBeInTheDocument();
    });
    expect(screen.getByText("Twitch: connected @testchannel")).toBeInTheDocument();
    expect(within(screen.getByLabelText("Primary navigation")).getByText("@redbull-testing")).toBeInTheDocument();
  });

  it("reports a failed runtime update without losing the loaded console", async () => {
    server.use(restProblem("post", "/api/chat/twitch/channels", 409, "Twitch chat ingestion is disabled"));
    renderWithApollo(<App />);
    await screen.findByText("Health: ok");

    await loadRedbullConsole();

    expect(await screen.findByText(/Loaded @redbull-testing; 1 runtime update failed/)).toBeInTheDocument();
    expect((await screen.findAllByText(redbullSegment.text)).length).toBeGreaterThan(0);
  });

  it("switches streamer from the roster and only updates sponsor relevance when the streamer is unchanged", async () => {
    const posted: string[] = [];
    server.use(
      restResolver("post", "/api/chat/twitch/channels", () => {
        posted.push("chat");
        return HttpResponse.json([]);
      }),
      restResolver("post", "/api/video/capture/channels", async ({ request }) => {
        const body = (await request.json()) as { channels?: string[] };
        posted.push(`capture:${(body.channels ?? []).join(",")}`);
        return HttpResponse.json({ channels: body.channels ?? [] });
      }),
      restResolver("post", "/api/sentiment/relevance/sponsors", () => {
        posted.push("relevance");
        return HttpResponse.json({});
      }),
    );
    const user = userEvent.setup();
    renderWithApollo(<App />);
    await screen.findByText("Health: ok");

    await user.click(screen.getByRole("button", { name: /@speedrun-lab/ }));
    expect(await screen.findByText(/pointed at @speedrun-lab/)).toBeInTheDocument();
    // A streamer switch moves chat and video capture to the new channel and updates relevance; the
    // three requests are issued concurrently, so only the set is asserted.
    expect([...posted].sort()).toEqual(["capture:speedrun-lab", "chat", "relevance"]);

    await user.click(screen.getByRole("button", { name: /load console/i }));
    expect(await screen.findByText(/Sponsor relevance updated for @speedrun-lab/)).toBeInTheDocument();
    // Reloading the same streamer must not re-point chat or capture; only relevance is sent again.
    expect([...posted].sort()).toEqual(["capture:speedrun-lab", "chat", "relevance", "relevance"]);
  });
});
