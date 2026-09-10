import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { describe, expect, it } from "vitest";
import type { VodListing } from "../../api/analytics";
import { AppRoutes } from "../../App";
import { renderWithApollo } from "../../test/apollo";
import { deal, dealSummary, videoStatusCapturing } from "../../test/fixtures";
import { HttpResponse, graphqlData, restJson, restResolver, server } from "../../test/msw";
import { importLabel, recordingsForDeal } from "./import-streams";

const inside: VodListing = {
  vodId: "2750461300",
  streamId: "41",
  title: "Monza highlights",
  createdAt: 1789000000000,
  durationMs: 7_200_000,
  url: "https://www.twitch.tv/videos/2750461300",
  viewCount: 1200,
  sessionId: null,
};
const before: VodListing = { ...inside, vodId: "1", title: "Old stream", createdAt: 1700000000000 };

describe("ImportStreams", () => {
  it("offers the recordings inside the deal's dates unless asked for all", () => {
    expect(recordingsForDeal([inside, before], deal().startsAt, deal().endsAt, false)).toEqual([inside]);
    expect(recordingsForDeal([inside, before], deal().startsAt, deal().endsAt, true)).toHaveLength(2);
    // A recording from the night before the deal started still counts.
    expect(
      recordingsForDeal([{ ...inside, createdAt: deal().startsAt - 3_600_000 }], deal().startsAt, null, false),
    ).toHaveLength(1);
  });

  it("describes an import's progress", () => {
    const base = {
      vodId: "1",
      channel: "c",
      framesPublished: 0,
      transcriptSegmentsPublished: 0,
      failures: 0,
      lastError: null,
    };
    expect(importLabel(undefined)).toBeNull();
    expect(importLabel({ ...base, state: "QUEUED", offsetSeconds: 0, durationSeconds: 100 })).toBe("Queued");
    expect(importLabel({ ...base, state: "RUNNING", offsetSeconds: 50, durationSeconds: 200 })).toBe("Importing · 25%");
    expect(importLabel({ ...base, state: "RUNNING", offsetSeconds: 0, durationSeconds: 0 })).toBe("Importing · 0%");
    expect(importLabel({ ...base, state: "DONE", offsetSeconds: 200, durationSeconds: 200 })).toBe("Imported");
    expect(importLabel({ ...base, state: "FAILED", offsetSeconds: 9, durationSeconds: 200, lastError: "ffmpeg" })).toBe(
      "Import failed: ffmpeg",
    );
    expect(importLabel({ ...base, state: "FAILED", offsetSeconds: 9, durationSeconds: 200 })).toBe(
      "Import failed: unknown error",
    );
  });

  it("imports a recording with the streamer's viewer figure and then links to its report", async () => {
    let request: Record<string, unknown> | null = null;
    let listed: VodListing[] = [inside];
    window.localStorage.setItem(
      "streamsense.selection",
      JSON.stringify({ streamer: "redbull-testing", sponsor: "Red Bull" }),
    );
    server.use(
      graphqlData("DealSummary", { dealSummary: dealSummary() }),
      restResolver("get", "/api/analytics/streams/redbull-testing/vods", () => HttpResponse.json(listed)),
      restResolver(
        "post",
        "/api/analytics/streams/redbull-testing/vods/2750461300/import",
        async ({ request: req }) => {
          request = (await req.json()) as Record<string, unknown>;
          listed = [{ ...inside, sessionId: 12 }];
          return HttpResponse.json(
            {
              session: { id: 12 },
              streamSessionId: "x",
              chatReplayStarted: true,
              captureReplayStarted: true,
              problems: [],
            },
            { status: 202 },
          );
        },
      ),
      restJson("get", "/api/video/capture/status", {
        ...videoStatusCapturing,
        imports: [
          {
            vodId: "2750461300",
            channel: "redbull-testing",
            state: "FAILED",
            offsetSeconds: 1800,
            durationSeconds: 7200,
            framesPublished: 180,
            transcriptSegmentsPublished: 0,
            failures: 20,
            lastError: "ffmpeg frame capture timed out",
          },
        ],
      }),
    );
    const user = userEvent.setup();
    renderWithApollo(
      <MemoryRouter initialEntries={["/deals/3"]}>
        <AppRoutes />
      </MemoryRouter>,
    );

    const panel = within(await screen.findByLabelText("Earlier streams on Twitch"));
    expect(await panel.findByText("Monza highlights")).toBeInTheDocument();
    await user.type(panel.getByLabelText("Average viewers for Monza highlights"), "850");
    await user.click(panel.getByRole("button", { name: "Import" }));

    expect(await panel.findByRole("link", { name: "Open report" })).toHaveAttribute("href", "/sessions/12");
    expect(request).toEqual({ averageViewers: 850 });
    // The capture service reports the import stopped; it can be resumed from where it was.
    expect(panel.getByText("Import failed: ffmpeg frame capture timed out")).toBeInTheDocument();
    expect(panel.getByRole("button", { name: "Resume" })).toBeEnabled();
  });
});
