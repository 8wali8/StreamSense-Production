import { act, render, screen } from "@testing-library/react";
import { useQuery, useSubscription } from "@apollo/client/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TranscriptPanel } from "./TranscriptPanel";

type SubscriptionOptions = {
  onData?: (value: {
    data?: {
      data?: {
        onTranscriptSegment?: {
          segmentId: string;
          streamer: string;
          text: string;
          startedAt: number;
          endedAt: number;
          language: string;
          confidence: number;
          modelVersion: string;
          source: string;
          channelLogin: string;
          streamSessionId: string;
          videoTimestampMs: number;
          transcriptSequence: number;
          captureWorkerId: string;
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

describe("TranscriptPanel", () => {
  let lastSubscriptionOptions: SubscriptionOptions | undefined;

  beforeEach(() => {
    lastSubscriptionOptions = undefined;
    useSubscriptionMock.mockImplementation((_, options) => {
      lastSubscriptionOptions = options as SubscriptionOptions;
      return { error: undefined } as never;
    });
  });

  it("renders transcript history", async () => {
    useQueryMock.mockReturnValue({
      loading: false,
      error: undefined,
      data: {
        recentTranscriptSegments: [transcriptSegment("segment-1", "hello stream")],
      },
    } as never);

    render(<TranscriptPanel />);

    expect(await screen.findByText("hello stream")).toBeInTheDocument();
    expect(screen.getByText("confidence=0.91")).toBeInTheDocument();
  });

  it("renders live transcript segments", async () => {
    useQueryMock.mockReturnValue({
      loading: false,
      error: undefined,
      data: { recentTranscriptSegments: [] },
    } as never);

    render(<TranscriptPanel />);

    act(() => {
      lastSubscriptionOptions?.onData?.({
        data: {
          data: {
            onTranscriptSegment: transcriptSegment("segment-2", "live words"),
          },
        },
      });
    });

    expect(await screen.findByText("live words")).toBeInTheDocument();
  });
});

function transcriptSegment(segmentId: string, text: string) {
  return {
    segmentId,
    streamer: "test",
    text,
    startedAt: 1710000000000,
    endedAt: 1710000005000,
    language: "en",
    confidence: 0.91,
    modelVersion: "faster-whisper-small.en-int8",
    source: "TWITCH",
    channelLogin: "test",
    streamSessionId: "test-session",
    videoTimestampMs: 0,
    transcriptSequence: 1,
    captureWorkerId: "worker-1",
  };
}
