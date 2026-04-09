import { act, render, screen } from "@testing-library/react";
import { useQuery, useSubscription } from "@apollo/client/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SponsorPanel } from "./SponsorPanel";

type SubscriptionOptions = {
  onData?: (value: {
    data?: {
      data?: {
        onSponsorDetection?: {
          detectionEventId: string;
          sourceFrameId: string;
          streamer: string;
          frameRef: string;
          frameSequence: number;
          capturedAt: number;
          processedAt: number;
          sponsor: string;
          confidence: number;
          modelVersion: string;
          x: number;
          y: number;
          width: number;
          height: number;
        };
      };
    };
  }) => void;
};

vi.mock("@apollo/client/react", () => ({
  useQuery: vi.fn(),
  useSubscription: vi.fn(),
}));

const useQueryMock = vi.mocked(useQuery);
const useSubscriptionMock = vi.mocked(useSubscription);

describe("SponsorPanel", () => {
  let lastSubscriptionOptions: SubscriptionOptions | undefined;

  beforeEach(() => {
    lastSubscriptionOptions = undefined;
    useSubscriptionMock.mockImplementation((_, options) => {
      lastSubscriptionOptions = options as SubscriptionOptions;
      return { error: undefined } as never;
    });
  });

  it("renders the loading state", () => {
    useQueryMock.mockReturnValue({ loading: true, data: undefined, error: undefined } as never);

    render(<SponsorPanel />);

    expect(screen.getByText("Loading sponsor history...")).toBeInTheDocument();
  });

  it("renders history results", async () => {
    useQueryMock.mockReturnValue({
      loading: false,
      error: undefined,
      data: {
        sponsorDetections: [
          {
            detectionEventId: "det-1",
            sourceFrameId: "frame-1",
            streamer: "test",
            frameRef: "frames/test.png",
            frameSequence: 1,
            capturedAt: 1710000000000,
            processedAt: 1710000000500,
            sponsor: "Nike",
            confidence: 0.91,
            modelVersion: "stub-v1",
            x: 0.12,
            y: 0.18,
            width: 0.31,
            height: 0.24,
          },
        ],
      },
    } as never);

    render(<SponsorPanel />);

    expect(await screen.findByText("frames/test.png")).toBeInTheDocument();
    expect(screen.getAllByText("Nike").length).toBeGreaterThan(0);
    expect(screen.getByText("confidence=0.91")).toBeInTheDocument();
  });

  it("renders the empty state", async () => {
    useQueryMock.mockReturnValue({
      loading: false,
      error: undefined,
      data: { sponsorDetections: [] },
    } as never);

    render(<SponsorPanel />);

    expect(await screen.findByText("No sponsor detections yet.")).toBeInTheDocument();
  });

  it("renders GraphQL errors", () => {
    useQueryMock.mockReturnValue({
      loading: false,
      data: undefined,
      error: new Error("video service unavailable"),
    } as never);

    render(<SponsorPanel />);

    expect(screen.getByRole("alert")).toHaveTextContent("Failed to load sponsor history");
  });

  it("renders a live subscription event", async () => {
    useQueryMock.mockReturnValue({
      loading: false,
      error: undefined,
      data: { sponsorDetections: [] },
    } as never);

    render(<SponsorPanel />);

    act(() => {
      lastSubscriptionOptions?.onData?.({
        data: {
          data: {
            onSponsorDetection: {
              detectionEventId: "det-2",
              sourceFrameId: "frame-2",
              streamer: "test",
              frameRef: "frames/live.png",
              frameSequence: 2,
              capturedAt: 1710000001000,
              processedAt: 1710000001500,
              sponsor: "Prime",
              confidence: 0.77,
              modelVersion: "stub-v1",
              x: 0.2,
              y: 0.15,
              width: 0.25,
              height: 0.2,
            },
          },
        },
      });
    });

    expect(await screen.findByText("frames/live.png")).toBeInTheDocument();
    expect(screen.getAllByText("Prime").length).toBeGreaterThan(0);
    expect(screen.getByText("confidence=0.77")).toBeInTheDocument();
  });
});
