import { act, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { emitSubscription, renderWithApollo } from "../test/apollo";
import { sponsorDetection } from "../test/fixtures";
import { graphqlData, graphqlError, graphqlPending, server } from "../test/msw";
import { SponsorPanel } from "./SponsorPanel";

describe("SponsorPanel", () => {
  it("renders the loading state", () => {
    server.use(graphqlPending("SponsorDetections"));

    renderWithApollo(<SponsorPanel />);

    expect(screen.getByText("Loading sponsor history...")).toBeInTheDocument();
  });

  it("renders history results", async () => {
    server.use(graphqlData("SponsorDetections", { sponsorDetections: [sponsorDetection()] }));

    renderWithApollo(<SponsorPanel />);

    expect(await screen.findByText("frames/test.png")).toBeInTheDocument();
    expect(screen.getAllByText("Nike").length).toBeGreaterThan(0);
    expect(screen.getByText("confidence=0.91")).toBeInTheDocument();
    expect(screen.getByText("session=test-1710000000000")).toBeInTheDocument();
  });

  it("renders the empty state", async () => {
    server.use(graphqlData("SponsorDetections", { sponsorDetections: [] }));

    renderWithApollo(<SponsorPanel />);

    expect(await screen.findByText("No sponsor detections yet.")).toBeInTheDocument();
  });

  it("renders GraphQL errors", async () => {
    server.use(graphqlError("SponsorDetections", "video service unavailable"));

    renderWithApollo(<SponsorPanel />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Failed to load sponsor history");
  });

  it("renders a live subscription event and counts fallbacks", async () => {
    server.use(graphqlData("SponsorDetections", { sponsorDetections: [] }));

    const apollo = renderWithApollo(<SponsorPanel />);
    expect(await screen.findByText("No sponsor detections yet.")).toBeInTheDocument();

    act(() => {
      emitSubscription(apollo, {
        onSponsorDetection: sponsorDetection({
          detectionEventId: "det-2",
          sourceFrameId: "frame-2",
          frameRef: "frames/live.png",
          frameSequence: 2,
          sponsor: "Prime",
          confidence: 0.77,
          modelVersion: "fallback",
        }),
      });
    });

    expect(await screen.findByText("frames/live.png")).toBeInTheDocument();
    expect(screen.getAllByText("Prime").length).toBeGreaterThan(0);
    expect(screen.getByText("confidence=0.77")).toBeInTheDocument();
    expect(screen.getByText("1 detections")).toBeInTheDocument();
  });
});
