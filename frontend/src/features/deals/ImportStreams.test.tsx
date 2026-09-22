import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { describe, expect, it } from "vitest";
import type { VodImportStatus, VodListing } from "../../api/analytics";
import { AppRoutes } from "../../App";
import { renderWithApollo } from "../../test/apollo";
import { deal, dealSummary } from "../../test/fixtures";
import { HttpResponse, PNG_HEAD, graphqlData, restBytes, restJson, restResolver, server } from "../../test/msw";
import { canResume, canStop, importLabel, recordingsForDeal } from "./import-streams";

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

const base: VodImportStatus = {
  streamer: "redbull-testing",
  vodId: "2750461300",
  sessionId: 12,
  state: "IMPORTING",
  offsetSeconds: 0,
  durationSeconds: 7200,
  chatState: "RUNNING",
  captureState: "RUNNING",
  lastError: null,
  updatedAt: 1789000100000,
};

function selectChannel() {
  window.localStorage.setItem(
    "streamsense.selection",
    JSON.stringify({ streamer: "redbull-testing", sponsor: "Red Bull" }),
  );
}

describe("ImportStreams", () => {
  it("offers the recordings inside the deal's dates unless asked for all", () => {
    expect(recordingsForDeal([inside, before], deal().startsAt, deal().endsAt, false)).toEqual([inside]);
    expect(recordingsForDeal([inside, before], deal().startsAt, deal().endsAt, true)).toHaveLength(2);
    // A recording from the night before the deal started still counts.
    expect(
      recordingsForDeal([{ ...inside, createdAt: deal().startsAt - 3_600_000 }], deal().startsAt, null, false),
    ).toHaveLength(1);
  });

  it("describes an import's progress and which controls it gets", () => {
    expect(importLabel(undefined)).toBeNull();
    expect(importLabel({ ...base, state: "QUEUED" })).toBe("Queued");
    expect(importLabel({ ...base, offsetSeconds: 1800 })).toBe("Importing · 25%");
    expect(importLabel({ ...base, durationSeconds: 0 })).toBe("Importing · 0%");
    expect(importLabel({ ...base, state: "STOPPING", offsetSeconds: 1800 })).toBe("Stopping…");
    // The offset reached and the recording's length, the way the row reads after a stop.
    expect(importLabel({ ...base, state: "STOPPED", offsetSeconds: 4320, durationSeconds: 43_380 })).toBe(
      "Stopped at 1h 12m of 12h 03m",
    );
    expect(importLabel({ ...base, state: "DONE", offsetSeconds: 7200 })).toBe("Imported");
    expect(importLabel({ ...base, state: "FAILED", lastError: "ffmpeg" })).toBe("Import failed: ffmpeg");
    expect(importLabel({ ...base, state: "FAILED" })).toBe("Import failed: unknown error");

    expect(canStop({ ...base, state: "QUEUED" })).toBe(true);
    expect(canStop(base)).toBe(true);
    expect(canStop({ ...base, state: "STOPPING" })).toBe(false);
    expect(canStop({ ...base, state: "STOPPED" })).toBe(false);
    expect(canResume({ ...base, state: "STOPPED" })).toBe(true);
    expect(canResume({ ...base, state: "FAILED" })).toBe(true);
    expect(canResume(base)).toBe(false);
    expect(canResume(undefined)).toBe(false);
  });

  it("imports a recording with the streamer's viewer figure and then links to its report", async () => {
    let request: Record<string, unknown> | null = null;
    let listed: VodListing[] = [inside];
    let imports: VodImportStatus[] = [];
    selectChannel();
    server.use(
      restBytes("/api/analytics/deals/3/logos/7", PNG_HEAD, "image/png"),
      graphqlData("DealSummary", { dealSummary: dealSummary() }),
      restResolver("get", "/api/analytics/streams/redbull-testing/vods", () => HttpResponse.json(listed)),
      restResolver("get", "/api/analytics/streams/redbull-testing/vods/imports", () => HttpResponse.json(imports)),
      restResolver(
        "post",
        "/api/analytics/streams/redbull-testing/vods/2750461300/import",
        async ({ request: req }) => {
          request = (await req.json()) as Record<string, unknown>;
          listed = [{ ...inside, sessionId: 12 }];
          // The capture half failed after half an hour; the import can be resumed from there.
          imports = [{ ...base, state: "FAILED", offsetSeconds: 1800, lastError: "ffmpeg frame capture timed out" }];
          return HttpResponse.json(
            {
              session: { id: 12 },
              streamSessionId: "x",
              chatReplayStarted: true,
              captureReplayStarted: true,
              problems: [],
              status: { ...base, state: "QUEUED" },
            },
            { status: 202 },
          );
        },
      ),
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
    expect(await panel.findByText("Import failed: ffmpeg frame capture timed out")).toBeInTheDocument();
    expect(panel.getByRole("button", { name: "Resume" })).toBeEnabled();
    expect(panel.queryByRole("button", { name: "Stop" })).not.toBeInTheDocument();
  });

  it("stops a running import from its row and offers to resume it from where it got to", async () => {
    let imports: VodImportStatus[] = [{ ...base, offsetSeconds: 1800 }];
    let stopped = false;
    selectChannel();
    server.use(
      restBytes("/api/analytics/deals/3/logos/7", PNG_HEAD, "image/png"),
      graphqlData("DealSummary", { dealSummary: dealSummary() }),
      restJson("get", "/api/analytics/streams/redbull-testing/vods", [{ ...inside, sessionId: 12 }]),
      restResolver("get", "/api/analytics/streams/redbull-testing/vods/imports", () => HttpResponse.json(imports)),
      restResolver("post", "/api/analytics/streams/redbull-testing/vods/2750461300/stop", () => {
        stopped = true;
        // Chat confirmed, capture is still winding down: the row says so until the next poll.
        imports = [{ ...base, state: "STOPPING", offsetSeconds: 1810, chatState: "STOPPED" }];
        return HttpResponse.json(imports[0]);
      }),
    );
    const user = userEvent.setup();
    renderWithApollo(
      <MemoryRouter initialEntries={["/deals/3"]}>
        <AppRoutes />
      </MemoryRouter>,
    );

    const panel = within(await screen.findByLabelText("Earlier streams on Twitch"));
    expect(await panel.findByText("Importing · 25%")).toBeInTheDocument();
    // No confirmation: stopping is cheap and reversible.
    await user.click(panel.getByRole("button", { name: "Stop" }));
    expect(stopped).toBe(true);
    expect(await panel.findByText("Stopping…", { selector: "em" })).toBeInTheDocument();
    expect(panel.getByRole("button", { name: "Stopping…" })).toBeDisabled();
    expect(panel.queryByRole("button", { name: "Resume" })).not.toBeInTheDocument();
  });

  it("offers a retry when analytics has no record of an import", async () => {
    selectChannel();
    server.use(
      restBytes("/api/analytics/deals/3/logos/7", PNG_HEAD, "image/png"),
      graphqlData("DealSummary", { dealSummary: dealSummary() }),
      restJson("get", "/api/analytics/streams/redbull-testing/vods", [{ ...inside, sessionId: 12 }]),
      // A session exists, but nothing was recorded about its import (imported before this state existed).
      restJson("get", "/api/analytics/streams/redbull-testing/vods/imports", []),
    );
    renderWithApollo(
      <MemoryRouter initialEntries={["/deals/3"]}>
        <AppRoutes />
      </MemoryRouter>,
    );

    const panel = within(await screen.findByLabelText("Earlier streams on Twitch"));
    expect(await panel.findByText("Import not confirmed")).toBeInTheDocument();
    expect(panel.getByRole("button", { name: "Retry import" })).toBeEnabled();
    expect(panel.getByRole("link", { name: "Open report" })).toHaveAttribute("href", "/sessions/12");
  });
});
