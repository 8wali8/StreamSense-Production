import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { beforeEach, describe, expect, it } from "vitest";
import { AppRoutes } from "../../App";
import { renderWithApollo } from "../../test/apollo";
import {
  captureChannelCapturing,
  channelIngestJoined,
  deal,
  dealSummary,
  streamAnalytics,
  twitchStatusConnected,
  videoStatusCapturing,
} from "../../test/fixtures";
import { HttpResponse, PNG_HEAD, graphqlData, restBytes, restJson, restResolver, server } from "../../test/msw";
import { EXPIRES_2030, fakeJwt } from "../../test/tokens";
import { SHARE_STORAGE_KEY } from "../../lib/share-token";

/** The deal page's owner view also lists the channel's recordings and polls the capture status. */
function dealPageHandlers() {
  return [
    restJson("get", "/api/analytics/streams/redbull-testing/vods", []),
    restJson("get", "/api/analytics/streams/redbull-testing/vods/imports", []),
    restJson("get", "/api/video/capture/status", videoStatusCapturing),
    // The fixture deal's logo, fetched for its picture.
    restBytes("/api/analytics/deals/3/logos/7", PNG_HEAD, "image/png"),
  ];
}

/**
 * The file name in a logo upload, read off the multipart body itself (the request's own parser would
 * build its file parts with jsdom's File class and reject them). Null unless the body is multipart.
 */
async function uploadedName(request: Request): Promise<string | null> {
  if (!request.headers.get("content-type")?.startsWith("multipart/form-data")) return null;
  return /filename="([^"]+)"/.exec(await request.text())?.[1] ?? null;
}

/** The home page's reads, for a test that lands there. */
function homeHandlers() {
  return [
    graphqlData("Sessions", { sessions: [] }),
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
    restJson("get", "/api/chat/twitch/channels/*", channelIngestJoined),
    restJson("get", "/api/video/capture/channels/*", captureChannelCapturing),
  ];
}

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
      ...dealPageHandlers(),
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
    server.use(...dealPageHandlers(), graphqlData("DealSummary", { dealSummary: dealSummary() }));
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

  it("edits a deal as a whole, ends it today, and lets an operator delete it once unshared", async () => {
    let updated: Record<string, unknown> | null = null;
    let deleted = false;
    server.use(
      ...dealPageHandlers(),
      ...homeHandlers(),
      graphqlData("DealSummary", { dealSummary: dealSummary() }),
      restResolver("put", "/api/analytics/deals/3", async ({ request }) => {
        updated = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(deal({ sponsor: updated.sponsor as string }));
      }),
      restResolver("delete", "/api/analytics/deals/3", () => {
        deleted = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();

    // A streamer can edit and end their deal; deleting is not theirs.
    window.localStorage.setItem(
      "streamsense.authToken",
      fakeJwt({ sub: "redbull-testing", login: "redbull-testing", role: "streamer", exp: EXPIRES_2030 }),
    );
    const streamer = renderAt("/deals/3");
    expect(await screen.findByRole("button", { name: "Edit" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "End today" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Delete" })).not.toBeInTheDocument();
    streamer.unmount();
    window.localStorage.removeItem("streamsense.authToken");

    renderAt("/deals/3");
    await user.click(await screen.findByRole("button", { name: "Edit" }));
    const form = within(screen.getByLabelText("Edit deal"));
    // The form is the deal's: its terms are filled in and, since it has some, "More settings" is open.
    expect(form.getByLabelText("Sponsor")).toHaveValue("Red Bull");
    expect(form.getByLabelText("Fee in USD (private to you)")).toBeVisible();
    expect(form.getByLabelText("Chat command")).toHaveValue("!redbull");
    await user.clear(form.getByLabelText("Sponsor"));
    await user.type(form.getByLabelText("Sponsor"), "Red Bull Racing");
    await user.click(form.getByRole("button", { name: "Save changes" }));
    expect(await screen.findByText(/priced with the new terms/)).toBeInTheDocument();
    // The whole deal went back, without the streamer.
    expect(updated).toMatchObject({ sponsor: "Red Bull Racing", fee: 2500, chatCommand: "!redbull" });
    expect(updated).not.toHaveProperty("streamer");

    await user.click(screen.getByRole("button", { name: "End today" }));
    expect(await screen.findByText(/ended today/)).toBeInTheDocument();
    expect(updated).toMatchObject({ sponsor: "Red Bull", fee: 2500 });
    expect((updated as Record<string, unknown> | null)?.endsAt).toBeTypeOf("number");

    // Deleting asks first, then leaves for the home page.
    await user.click(screen.getByRole("button", { name: "Delete" }));
    expect(screen.getByText(/Delete this deal for good/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Delete" }));
    expect(await screen.findByLabelText("Deals")).toBeInTheDocument();
    expect(deleted).toBe(true);
  });

  it("adds and removes the sponsor's logo on the deal page, and says when tracking is off", async () => {
    let uploaded: string | null = null;
    let removed = false;
    server.use(
      ...dealPageHandlers(),
      graphqlData("DealSummary", { dealSummary: dealSummary({ deal: deal({ logos: [] }) }) }),
      restResolver("post", "/api/analytics/deals/3/logos", async ({ request }) => {
        uploaded = await uploadedName(request);
        server.use(graphqlData("DealSummary", { dealSummary: dealSummary() }));
        return HttpResponse.json(
          { id: 7, dealId: 3, contentType: "image/png", width: 512, height: 192, sizeBytes: 3, uploadedAt: 1 },
          { status: 201 },
        );
      }),
      restResolver("delete", "/api/analytics/deals/3/logos/7", () => {
        removed = true;
        server.use(graphqlData("DealSummary", { dealSummary: dealSummary({ deal: deal({ logos: [] }) }) }));
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderAt("/deals/3");

    // Without a logo the deal says so, and the media value is not a number.
    expect(await screen.findByText("On-screen tracking is off until you add the sponsor's logo.")).toBeInTheDocument();
    expect(screen.getByText("Media value · on-screen tracking is off")).toBeInTheDocument();

    await user.upload(
      screen.getByLabelText("Choose a logo file"),
      new File(["png"], "redbull.png", { type: "image/png" }),
    );
    // The image is fetched with the token (an <img src> would carry none) and shown as a data URL.
    expect(await screen.findByRole("img", { name: "Red Bull logo 1" })).toHaveAttribute(
      "src",
      "data:image/png;base64,iVBORw==",
    );
    expect(uploaded).toBe("redbull.png");
    expect(screen.getByText("Media value · 1.4× the $2,500 fee")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Remove Red Bull logo 1" }));
    expect(await screen.findByText("On-screen tracking is off until you add the sponsor's logo.")).toBeInTheDocument();
    expect(removed).toBe(true);
  });

  it("creates a deal from the home page and lists it", async () => {
    let created: Record<string, unknown> | null = null;
    let uploaded: string | null = null;
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
      restJson("get", "/api/chat/twitch/channels/*", channelIngestJoined),
      restJson("get", "/api/video/capture/channels/*", captureChannelCapturing),
      graphqlData("Deals", { deals: created ? [deal()] : [] }),
      restResolver("post", "/api/analytics/deals", async ({ request }) => {
        created = (await request.json()) as Record<string, unknown>;
        server.use(graphqlData("Deals", { deals: [deal()] }));
        return HttpResponse.json(deal({ logos: [] }), { status: 201 });
      }),
      restResolver("post", "/api/analytics/deals/3/logos", async ({ request }) => {
        // The logo follows the deal: it is uploaded once the deal has an id.
        expect(created).not.toBeNull();
        uploaded = await uploadedName(request);
        return HttpResponse.json({ id: 7, dealId: 3 }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderAt("/");

    const deals = within(await screen.findByLabelText("Deals"));
    await user.click(deals.getByRole("button", { name: "New deal" }));
    const form = within(deals.getByLabelText("New deal"));
    // The sponsor is pre-filled from the selection and the dates and promised streams sit up front.
    expect(form.getByLabelText("Sponsor")).toHaveValue("Red Bull");
    await user.type(form.getByLabelText("Promised streams"), "4");
    // The fee and the chat signals wait behind "More settings", closed until opened.
    expect(form.getByLabelText("Fee in USD (private to you)")).not.toBeVisible();
    await user.click(form.getByText("More settings"));
    expect(form.getByLabelText("Fee in USD (private to you)")).toBeVisible();
    await user.type(form.getByLabelText("Fee in USD (private to you)"), "2500");
    await user.type(form.getByLabelText("Chat command"), "redbull");
    await user.upload(form.getByLabelText("Choose a logo file"), new File(["png"], "logo.png", { type: "image/png" }));
    expect(form.getByText("logo.png")).toBeInTheDocument();
    await user.click(form.getByRole("button", { name: "Create deal" }));

    expect(await deals.findByRole("link", { name: /Red Bull/ })).toHaveAttribute("href", "/deals/3");
    expect(uploaded).toBe("logo.png");
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
