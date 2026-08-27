import { render, screen } from "@testing-library/react";
import { useQuery, useSubscription } from "@apollo/client/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TranscriptSentimentPanel } from "./TranscriptSentimentPanel";

vi.mock("@apollo/client/react", () => ({
  useQuery: vi.fn(),
  useSubscription: vi.fn(),
}));

const useQueryMock = vi.mocked(useQuery);
const useSubscriptionMock = vi.mocked(useSubscription);

describe("TranscriptSentimentPanel", () => {
  beforeEach(() => {
    useSubscriptionMock.mockReturnValue({ error: undefined } as never);
  });

  it("renders transcript sentiment history separately from chat sentiment", async () => {
    useQueryMock.mockReturnValue({
      loading: false,
      error: undefined,
      data: {
        recentTranscriptSentiment: [
          {
            sentimentEventId: "transcript-sent-1",
            segmentId: "segment-1",
            streamer: "test",
            text: "hello stream",
            segmentStartedAt: 1710000000000,
            segmentEndedAt: 1710000005000,
            processedAt: 1710000005500,
            label: "POSITIVE",
            score: 0.8,
            modelVersion: "stub-v1",
            transcriptModelVersion: "faster-whisper-small.en-int8",
            streamSessionId: "test-session",
            transcriptSequence: 1,
          },
        ],
      },
    } as never);

    render(<TranscriptSentimentPanel />);

    expect(await screen.findByText("hello stream")).toBeInTheDocument();
    expect(screen.getByText("POSITIVE")).toBeInTheDocument();
    expect(screen.getByText("score=0.80")).toBeInTheDocument();
  });
});
