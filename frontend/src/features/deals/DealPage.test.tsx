import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { beforeEach, describe, expect, it } from "vitest";
import { AppRoutes } from "../../App";
import { renderWithApollo } from "../../test/apollo";
import { deal, dealSummary, streamAnalytics, twitchStatusConnected, videoStatusCapturing } from "../../test/fixtures";
import { HttpResponse, graphqlData, restJson, restResolver, server } from "../../test/msw";

function renderAt(path: string) {
  window.localStorage.setItem(
    "streamsense.selection",
    JSON.stringify({ streamer: "redbull-testing", sponsor: "Red Bull" }),
  );
  return renderWithApollo(
    <MemoryRouter initialEntries={[path]}>
      <AppRoutes />
    </MemoryRouter>,
  );
}

describe("deals", () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it("shows the deal's totals against the fee, the trend, and the streams inside it", async () => {
    server.use(graphqlData("DealSummary", { dealSummary: dealSummary() }));
    renderAt("/deals/3");

    expect(await screen.findByRole("heading", { name: "Red Bull" })).toBeInTheDocument();
    expect(screen.getByText(/2 of 4 streams/)).toBeInTheDocument();
    expect(screen.getByText("58m 20s")).toBeInTheDocument();
    expect(screen.getByText("$3,500")).toBeInTheDocument();
    expect(screen.getByText("Media value · 1.4× the $2,500 fee")).toBeInTheDocument();
    expect(screen.getByRole("img", { name: "On-screen time per stream" }).querySelectorAll("rect")).toHaveLength(2);
    const streams = within(screen.getByLabelText("Streams in this deal"));
    expect(streams.getByRole("link", { name: /Singapore GP/ })).toHaveAttribute(
      "href",
      "/sessions/9?sponsor=Red%20Bull",
    );
    expect(screen.getByRole("button", { name: "Share" })).toBeDisabled();
  });

  it("creates a deal from the home page and lists it", async () => {
    let created: Record<string, unknown> | null = null;
    server.use(
      graphqlData("Sessions", { sessions: [] }),
      graphqlData("SponsorDetections", { sponsorDetections: [] }),
      graphqlData("RecentSentiment", { recentSentiment: [] }),
      graphqlData("RecentSponsorSentiment", { recentSponsorSentiment: [] }),
      graphqlData("RecentTranscriptSegments", { recentTranscriptSegments: [] }),
      graphqlData("RecentTranscriptSentiment", { recentTranscriptSentiment: [] }),
      graphqlData("RecentSponsorTranscriptSentiment", { recentSponsorTranscriptSentiment: [] }),
      graphqlData("StreamAnalytics", streamAnalytics()),
      graphqlData("Health", { health: "ok" }),
      restJson("get", "/api/sentiment/transcript/recent", []),
      restJson("get", "/api/chat/twitch/status", twitchStatusConnected),
      restJson("get", "/api/video/capture/status", videoStatusCapturing),
      graphqlData("Deals", { deals: created ? [deal()] : [] }),
      restResolver("post", "/api/analytics/deals", async ({ request }) => {
        created = (await request.json()) as Record<string, unknown>;
        server.use(graphqlData("Deals", { deals: [deal()] }));
        return HttpResponse.json(deal(), { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderAt("/");

    const deals = within(await screen.findByLabelText("Deals"));
    await user.click(deals.getByRole("button", { name: "New deal" }));
    const form = within(deals.getByLabelText("New deal"));
    // The sponsor is pre-filled from the selection; the command and fee are typed.
    expect(form.getByLabelText("Sponsor")).toHaveValue("Red Bull");
    await user.type(form.getByLabelText("Fee (private to you)"), "2500");
    await user.type(form.getByLabelText("Chat command"), "redbull");
    await user.type(form.getByLabelText("Promised streams"), "4");
    await user.click(form.getByRole("button", { name: "Create deal" }));

    expect(await deals.findByRole("link", { name: /Red Bull/ })).toHaveAttribute("href", "/deals/3");
    expect(created).toMatchObject({
      streamer: "redbull-testing",
      sponsor: "Red Bull",
      fee: 2500,
      chatCommand: "redbull",
      promisedStreams: 4,
    });
    expect(created).not.toHaveProperty("endsAt");
  });
});
