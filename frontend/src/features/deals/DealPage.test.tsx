import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { beforeEach, describe, expect, it } from "vitest";
import { AppRoutes } from "../../App";
import { renderWithApollo } from "../../test/apollo";
import { deal, dealSummary, streamAnalytics, twitchStatusConnected, videoStatusCapturing } from "../../test/fixtures";
import { HttpResponse, graphqlData, restJson, restResolver, server } from "../../test/msw";
import { SHARE_STORAGE_KEY } from "../../lib/share-token";

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
    window.sessionStorage.clear();
  });

  it("mints a share link for the owner, and a shared tab sees the deal without the fee or navigation", async () => {
    let shared = false;
    server.use(
      graphqlData("DealSummary", { dealSummary: dealSummary() }),
      restResolver("post", "/api/analytics/deals/3/share", () => {
        shared = true;
        server.use(
          graphqlData("DealSummary", {
            dealSummary: dealSummary({ deal: deal({ shareToken: "tok-abc" }) }),
          }),
        );
        return HttpResponse.json({ dealId: 3, token: "tok-abc" });
      }),
    );
    const user = userEvent.setup();
    const owner = renderAt("/deals/3");

    await user.click(await screen.findByRole("button", { name: "Share" }));
    expect(await screen.findByLabelText("Share link")).toHaveValue("http://localhost:3000/deals/3?share=tok-abc");
    expect(shared).toBe(true);
    expect(screen.getByRole("link", { name: "Home" })).toBeInTheDocument();
    owner.unmount();

    // The sponsor opens the link: the gateway strips the fee, the app hides navigation and the share control.
    window.sessionStorage.setItem(SHARE_STORAGE_KEY, "tok-abc");
    server.use(
      graphqlData("DealSummary", { dealSummary: dealSummary({ deal: deal({ fee: null, shareToken: null }) }) }),
    );
    renderAt("/deals/3");
    expect(await screen.findByRole("heading", { name: "Red Bull" })).toBeInTheDocument();
    expect(screen.getByText("Media value · estimate")).toBeInTheDocument();
    expect(screen.queryByText(/fee/)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Share" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Home" })).not.toBeInTheDocument();
    expect(screen.getByLabelText("Shared view")).toBeInTheDocument();
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
    expect(screen.getByRole("button", { name: "Share" })).toBeEnabled();
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
