import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { beforeEach, describe, expect, it } from "vitest";
import { AppRoutes } from "./App";
import { renderWithApollo } from "./test/apollo";
import { streamAnalytics, twitchStatusConnected, videoStatusCapturing } from "./test/fixtures";
import { HttpResponse, graphqlData, restJson, restResolver, server } from "./test/msw";

/** Every request the app makes, answered empty; the tests below assert on behaviour, not data. */
function stackHandlers() {
  return [
    graphqlData("Health", { health: "ok" }),
    graphqlData("StreamAnalytics", streamAnalytics()),
    graphqlData("SponsorDetections", { sponsorDetections: [] }),
    graphqlData("RecentSentiment", { recentSentiment: [] }),
    graphqlData("RecentSponsorSentiment", { recentSponsorSentiment: [] }),
    graphqlData("RecentTranscriptSegments", { recentTranscriptSegments: [] }),
    graphqlData("RecentTranscriptSentiment", { recentTranscriptSentiment: [] }),
    graphqlData("RecentSponsorTranscriptSentiment", { recentSponsorTranscriptSentiment: [] }),
    restJson("get", "/api/chat/twitch/status", twitchStatusConnected),
    restJson("get", "/api/video/capture/status", videoStatusCapturing),
    restJson("get", "/api/sentiment/transcript/recent", []),
    restJson("post", "/api/chat/twitch/channels", ["redbull-testing"]),
    restJson("post", "/api/video/capture/channels", { channels: ["redbull-testing"] }),
    restJson("post", "/api/sentiment/relevance/sponsors", {}),
  ];
}

function renderApp(path: string) {
  return renderWithApollo(
    <MemoryRouter initialEntries={[path]}>
      <AppRoutes />
    </MemoryRouter>,
  );
}

describe("App", () => {
  beforeEach(() => {
    window.localStorage.clear();
    server.use(...stackHandlers());
  });

  it("switches the runtime only when the streamer changes, and the whole app follows the selection", async () => {
    const posted: string[] = [];
    server.use(
      restResolver("post", "/api/chat/twitch/channels", async ({ request }) => {
        const body = (await request.json()) as { channels?: string[] };
        posted.push(`chat:${(body.channels ?? []).join(",")}`);
        return HttpResponse.json(body.channels ?? []);
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
    renderApp("/ops");
    await screen.findByText("Health: ok");

    const control = within(screen.getByLabelText("Channel control"));
    await user.clear(control.getByLabelText("Streamer"));
    await user.type(control.getByLabelText("Streamer"), "@RedBull-Testing");
    await user.click(control.getByRole("button", { name: /point capture here/i }));

    expect(await screen.findByText(/pointed at @redbull-testing/)).toBeInTheDocument();
    // A streamer switch moves chat and video capture and updates relevance; the three requests are
    // issued concurrently, so only the set is asserted.
    expect([...posted].sort()).toEqual(["capture:redbull-testing", "chat:redbull-testing", "relevance"]);

    await user.click(control.getByRole("button", { name: /point capture here/i }));
    expect(await screen.findByText(/Sponsor relevance updated for @redbull-testing/)).toBeInTheDocument();
    // Reloading the same streamer must not re-point chat or capture; only relevance is sent again.
    expect([...posted].sort()).toEqual(["capture:redbull-testing", "chat:redbull-testing", "relevance", "relevance"]);

    // The selection is shared through the shell and persisted for the next load.
    expect(within(screen.getByLabelText("Primary navigation")).getByText("@redbull-testing")).toBeInTheDocument();
    expect(window.localStorage.getItem("streamsense.selection")).toContain("redbull-testing");
    await user.click(screen.getByRole("link", { name: /home/i }));
    expect(await screen.findByRole("heading", { name: "@redbull-testing" })).toBeInTheDocument();
  });

  it("parses the sponsor profile fields before sending them", async () => {
    let received: unknown = null;
    server.use(
      restResolver("post", "/api/sentiment/relevance/sponsors", async ({ request }) => {
        received = await request.json();
        return HttpResponse.json({});
      }),
    );
    const user = userEvent.setup();
    renderApp("/ops");
    await screen.findByText("Health: ok");

    const editor = within(screen.getByLabelText("Sponsor profile"));
    await user.clear(editor.getByLabelText("Sponsor"));
    await user.type(editor.getByLabelText("Sponsor"), "Red Bull");
    await user.type(editor.getByLabelText(/Aliases/), "red bull, , rb ");
    await user.type(editor.getByLabelText(/Semantic terms/), "energy drink");
    await user.type(editor.getByLabelText(/Minimum relevance score/), "0.6");
    await user.click(editor.getByRole("button", { name: /save profile/i }));

    expect(await editor.findByText(/Relevance profile for Red Bull saved for @test/)).toBeInTheDocument();
    expect(received).toEqual({
      streamer: "test",
      sponsor: "Red Bull",
      aliases: ["red bull", "rb"],
      semanticTerms: ["energy drink"],
      minScore: 0.6,
    });
  });

  it("restores a stored selection and keeps diagnostics off the home page", async () => {
    window.localStorage.setItem(
      "streamsense.selection",
      JSON.stringify({ streamer: "redbull-testing", sponsor: "Red Bull" }),
    );
    renderApp("/");

    expect(await screen.findByRole("heading", { name: "@redbull-testing" })).toBeInTheDocument();
    expect(screen.queryByText(/Health:/)).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Streamer")).not.toBeInTheDocument();
  });
});
