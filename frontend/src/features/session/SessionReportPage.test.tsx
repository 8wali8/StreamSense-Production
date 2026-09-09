import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router";
import { describe, expect, it } from "vitest";
import { renderWithApollo } from "../../test/apollo";
import { sessionSummary, sponsorMoments } from "../../test/fixtures";
import { graphqlData, graphqlError, server } from "../../test/msw";
import { SessionMentionsPage, SessionValuePage } from "./SessionDetailPages";
import { SessionReportPage } from "./SessionReportPage";

function renderAt(path: string) {
  return renderWithApollo(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/sessions/:sessionId" element={<SessionReportPage />} />
        <Route path="/sessions/:sessionId/value" element={<SessionValuePage />} />
        <Route path="/sessions/:sessionId/mentions" element={<SessionMentionsPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("SessionReportPage", () => {
  it("shows the numbers, the timeline with a selectable moment, the highlights, and the detail links", async () => {
    server.use(
      graphqlData("SessionSummary", { sessionSummary: sessionSummary() }),
      graphqlData("SponsorMoments", { sponsorMoments: sponsorMoments() }),
    );
    const user = userEvent.setup();
    renderAt("/sessions/7");

    expect(await screen.findByRole("heading", { name: "F1 Replay Night: Monza highlights" })).toBeInTheDocument();
    expect(screen.getByText("38m 12s")).toBeInTheDocument();
    expect(screen.getByText("On screen · 28% of stream")).toBeInTheDocument();
    expect(screen.getByText("$1,190")).toBeInTheDocument();
    expect(screen.getByText("Risk LOW")).toBeInTheDocument();
    expect(screen.getByText("Podium celebration, logo on screen 15 minutes")).toBeInTheDocument();
    // Nothing explanatory on the page: the value basis lives behind the link.
    expect(screen.queryByText(/weighted viewer-minutes/)).not.toBeInTheDocument();

    // Clicking a voice moment selects it and offers the VOD at its offset.
    const timeline = within(screen.getByLabelText("Where Red Bull showed up"));
    await user.click(timeline.getByRole("button", { name: /^18:00 Voice mention/ }));
    expect(timeline.getByText("Voice mention, positive 0.84")).toBeInTheDocument();
    expect(timeline.getByRole("link", { name: "Open VOD" })).toHaveAttribute(
      "href",
      "https://www.twitch.tv/videos/2750461300?t=0h18m0s",
    );

    await user.click(screen.getByRole("link", { name: "How value is estimated" }));
    expect(await screen.findByRole("heading", { name: "How value is estimated" })).toBeInTheDocument();
    expect(screen.getByText(/36,400 weighted viewer-minutes/)).toBeInTheDocument();
    expect(screen.getByText("!redbull in chat")).toBeInTheDocument();
  });

  it("renders the timeline error without losing the numbers, and a missing session plainly", async () => {
    server.use(
      graphqlData("SessionSummary", {
        sessionSummary: sessionSummary({ value: { ...sessionSummary().value, mediaValue: null } }),
      }),
      graphqlError("SponsorMoments", "analytics-service unavailable"),
    );
    renderAt("/sessions/7");

    expect(await screen.findByText("Media value · needs viewer data")).toBeInTheDocument();
    expect(await screen.findByRole("alert")).toHaveTextContent("Failed to load the timeline");

    server.use(
      graphqlData("SessionSummary", { sessionSummary: null }),
      graphqlData("SponsorMoments", { sponsorMoments: null }),
    );
    renderAt("/sessions/999");
    expect(await screen.findByText("There is no session with this id.")).toBeInTheDocument();
  });

  it("lists every mention in stream order with the chat minutes counted", async () => {
    server.use(
      graphqlData("SessionSummary", { sessionSummary: sessionSummary() }),
      graphqlData("SponsorMoments", { sponsorMoments: sponsorMoments() }),
    );
    renderAt("/sessions/7/mentions");

    expect(await screen.findByRole("heading", { name: "Every Red Bull mention" })).toBeInTheDocument();
    const lines = screen.getAllByRole("article");
    expect(lines).toHaveLength(3);
    expect(lines[0]).toHaveTextContent("18:00");
    expect(lines[1]).toHaveTextContent("38 in this minute");
    expect(lines[2]).toHaveTextContent("Negative -0.71");
  });
});
