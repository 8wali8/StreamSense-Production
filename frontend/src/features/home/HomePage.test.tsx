import { screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { beforeEach, describe, expect, it } from "vitest";
import { AppRoutes } from "../../App";
import { renderWithApollo } from "../../test/apollo";
import {
  deal,
  sessionSummary,
  streamAnalytics,
  twitchStatusConnected,
  videoStatusCapturing,
} from "../../test/fixtures";
import { HttpResponse, graphqlData, graphqlResolver, restJson, server } from "../../test/msw";

const finished = {
  __typename: "StreamSession",
  id: "7",
  streamer: "redbull-testing",
  source: "CAPTURE",
  twitchStreamId: "2750461300",
  title: "F1 Replay Night: Monza highlights",
  category: "Formula 1",
  startedAt: 1788816420000,
  endedAt: 1788824460000,
  live: false,
  durationMs: 8040000,
  peakViewers: 1842,
  averageViewers: 1310,
  viewerSamples: 134,
  vodId: null,
};

const live = {
  ...finished,
  id: "8",
  title: "Quali watch-along",
  startedAt: 1788900000000,
  endedAt: null,
  live: true,
  durationMs: 4320000,
  averageViewers: 1412,
  peakViewers: 1500,
};

function consoleHandlers() {
  return [
    graphqlData("SponsorDetections", { sponsorDetections: [] }),
    graphqlData("RecentSentiment", { recentSentiment: [] }),
    graphqlData("RecentSponsorSentiment", { recentSponsorSentiment: [] }),
    graphqlData("RecentTranscriptSegments", { recentTranscriptSegments: [] }),
    graphqlData("RecentTranscriptSentiment", { recentTranscriptSentiment: [] }),
    graphqlData("RecentSponsorTranscriptSentiment", { recentSponsorTranscriptSentiment: [] }),
    graphqlData("StreamAnalytics", streamAnalytics()),
    graphqlData("Health", { health: "ok" }),
    graphqlData("Deals", { deals: [] }),
    restJson("get", "/api/sentiment/transcript/recent", []),
    restJson("get", "/api/chat/twitch/status", twitchStatusConnected),
    restJson("get", "/api/video/capture/status", videoStatusCapturing),
  ];
}

function renderHome(sponsor = "Red Bull") {
  window.localStorage.setItem("streamsense.selection", JSON.stringify({ streamer: "redbull-testing", sponsor }));
  return renderWithApollo(
    <MemoryRouter initialEntries={["/"]}>
      <AppRoutes />
    </MemoryRouter>,
  );
}

describe("HomePage", () => {
  beforeEach(() => {
    window.localStorage.clear();
    server.use(...consoleHandlers());
  });

  it("shows the live strip from the live session's summary and the history from the finished ones", async () => {
    server.use(
      graphqlData("Sessions", { sessions: [live, finished] }),
      graphqlResolver("SessionSummary", ({ variables }) =>
        HttpResponse.json({
          data: {
            sessionSummary:
              variables.sessionId === "8"
                ? sessionSummary({ onScreenMs: 1_300_000, mentions: 26, chatMentions: 17, voiceMentions: 9 })
                : sessionSummary(),
          },
        }),
      ),
    );
    renderHome();

    const strip = within(await screen.findByLabelText("Live status"));
    expect(strip.getByText("LIVE")).toBeInTheDocument();
    expect(strip.getByText("Quali watch-along")).toBeInTheDocument();
    expect(await strip.findByText("21m 40s")).toBeInTheDocument();
    expect(strip.getByText("17 chat · 9 voice")).toBeInTheDocument();
    expect(strip.getByRole("link", { name: "Open the live report" })).toHaveAttribute("href", "/sessions/8");

    const history = within(screen.getByLabelText("History"));
    const row = await history.findByRole("link", { name: /F1 Replay Night/ });
    // The row shows the selected sponsor's numbers, so the report it opens is about the same sponsor.
    expect(row).toHaveAttribute("href", "/sessions/7?sponsor=Red%20Bull");
    expect(row).toHaveTextContent("38m 12s");
    expect(row).toHaveTextContent("47 · 0.62");
    // Track record: 38m 12s over 2h 14m is about 17 minutes an hour.
    expect(await history.findByText("17m 06s")).toBeInTheDocument();
    expect(history.getByText("Across 1 sponsored stream")).toBeInTheDocument();
    expect(history.queryByRole("link", { name: /Quali watch-along/ })).not.toBeInTheDocument();
  });

  it("points at the last report when the channel is offline", async () => {
    server.use(
      graphqlData("Sessions", { sessions: [finished] }),
      graphqlData("SessionSummary", { sessionSummary: sessionSummary() }),
    );
    renderHome();

    const strip = within(await screen.findByLabelText("Live status"));
    expect(strip.getByText("Offline")).toBeInTheDocument();
    expect(strip.getByRole("link", { name: "View session report" })).toHaveAttribute("href", "/sessions/7");
    expect(screen.getByText("All chat and transcript")).toBeInTheDocument();
  });

  it("follows the running deal's sponsor when the operations page names none", async () => {
    const sponsorQueries: unknown[] = [];
    server.use(
      graphqlData("Sessions", { sessions: [live, finished] }),
      graphqlData("SessionSummary", { sessionSummary: sessionSummary() }),
      graphqlData("Deals", {
        deals: [deal({ sponsor: "Red Bull" }), deal({ id: "2", sponsor: "Logitech", active: false })],
      }),
      graphqlResolver("RecentSponsorSentiment", ({ variables }) => {
        sponsorQueries.push(variables.sponsor);
        return HttpResponse.json({ data: { recentSponsorSentiment: [] } });
      }),
    );
    renderHome("");

    expect(await screen.findByText("Red Bull", { selector: ".page-header .pill" })).toBeInTheDocument();
    const strip = within(await screen.findByLabelText("Live status"));
    expect(await strip.findByText("Red Bull on screen so far")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Red Bull sentiment" })).toBeInTheDocument();
    const history = within(screen.getByLabelText("History"));
    expect(await history.findByRole("link", { name: /F1 Replay Night/ })).toHaveAttribute(
      "href",
      "/sessions/7?sponsor=Red%20Bull",
    );
    expect(sponsorQueries).toContain("Red Bull");
  });

  it("says there is no sponsor and asks for a deal when nothing follows one", async () => {
    server.use(
      graphqlData("Sessions", { sessions: [live, finished] }),
      graphqlData("SessionSummary", { sessionSummary: sessionSummary() }),
    );
    renderHome("");

    expect(
      await screen.findByText(/No deals yet\. Create one and the home page follows its sponsor/),
    ).toBeInTheDocument();
    expect(screen.getAllByText("No sponsor yet").length).toBeGreaterThan(0);
    expect(screen.getByRole("heading", { name: "Sponsor sentiment" })).toBeInTheDocument();
    expect(screen.getByText("No sponsor is being followed yet.")).toBeInTheDocument();
    const history = within(screen.getByLabelText("History"));
    expect(await history.findByRole("link", { name: /F1 Replay Night/ })).toHaveAttribute("href", "/sessions/7");
    // The form stays closed; the line above is the only prompt.
    expect(screen.queryByRole("button", { name: /create deal/i })).not.toBeInTheDocument();
  });

  it("names a deal that has not started yet instead of asking for another", async () => {
    const startsAt = Date.now() + 7 * 86_400_000;
    server.use(
      graphqlData("Sessions", { sessions: [finished] }),
      graphqlData("SessionSummary", { sessionSummary: sessionSummary() }),
      graphqlData("Deals", { deals: [deal({ sponsor: "Razer", startsAt, endsAt: null, active: false })] }),
    );
    renderHome("");

    expect(
      await screen.findByText(/Your deal with Razer starts on .*; the home page follows it from then\./),
    ).toBeInTheDocument();
    expect(screen.getByText(/^Razer from /, { selector: ".page-header .pill" })).toBeInTheDocument();
    expect(screen.queryByText(/No deals yet/)).not.toBeInTheDocument();
    // Nothing is followed until it starts, so the numbers still say so.
    expect(screen.getAllByText("No sponsor yet").length).toBeGreaterThan(0);
  });
});
