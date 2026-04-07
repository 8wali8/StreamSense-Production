import { act, render, screen } from "@testing-library/react";
import { useQuery, useSubscription } from "@apollo/client/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SentimentPanel } from "./SentimentPanel";

type SubscriptionOptions = {
  onData?: (value: {
    data?: {
      data?: {
        onSentiment?: {
          sentimentEventId: string;
          sourceEventId: string;
          streamer: string;
          user: string;
          message: string;
          chatTimestamp: number;
          processedAt: number;
          label: string;
          score: number;
          modelVersion: string;
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

describe("SentimentPanel", () => {
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

    render(<SentimentPanel />);

    expect(screen.getByText("Loading sentiment history...")).toBeInTheDocument();
  });

  it("renders history results", async () => {
    useQueryMock.mockReturnValue({
      loading: false,
      error: undefined,
      data: {
        recentSentiment: [
          {
            sentimentEventId: "sent-1",
            sourceEventId: "src-1",
            streamer: "test",
            user: "u1",
            message: "great stream",
            chatTimestamp: 1710000000000,
            processedAt: 1710000000500,
            label: "POSITIVE",
            score: 0.82,
            modelVersion: "stub-v1",
          },
        ],
      },
    } as never);

    render(<SentimentPanel />);

    expect(await screen.findByText("great stream")).toBeInTheDocument();
    expect(screen.getByText("POSITIVE")).toBeInTheDocument();
    expect(screen.getByText("score=0.82")).toBeInTheDocument();
  });

  it("renders the empty state", async () => {
    useQueryMock.mockReturnValue({
      loading: false,
      error: undefined,
      data: { recentSentiment: [] },
    } as never);

    render(<SentimentPanel />);

    expect(await screen.findByText("No sentiment history yet.")).toBeInTheDocument();
  });

  it("renders GraphQL errors", () => {
    useQueryMock.mockReturnValue({
      loading: false,
      data: undefined,
      error: new Error("sentiment service unavailable"),
    } as never);

    render(<SentimentPanel />);

    expect(screen.getByRole("alert")).toHaveTextContent("Failed to load sentiment history");
  });

  it("renders a live subscription event", async () => {
    useQueryMock.mockReturnValue({
      loading: false,
      error: undefined,
      data: { recentSentiment: [] },
    } as never);

    render(<SentimentPanel />);

    act(() => {
      lastSubscriptionOptions?.onData?.({
        data: {
          data: {
            onSentiment: {
              sentimentEventId: "sent-2",
              sourceEventId: "src-2",
              streamer: "test",
              user: "u2",
              message: "that was rough",
              chatTimestamp: 1710000001000,
              processedAt: 1710000001500,
              label: "NEGATIVE",
              score: -0.74,
              modelVersion: "stub-v1",
            },
          },
        },
      });
    });

    expect(await screen.findByText("that was rough")).toBeInTheDocument();
    expect(screen.getByText("NEGATIVE")).toBeInTheDocument();
    expect(screen.getByText("score=-0.74")).toBeInTheDocument();
  });
});
