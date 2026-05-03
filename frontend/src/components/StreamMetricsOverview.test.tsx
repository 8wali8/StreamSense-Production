import { render, screen } from "@testing-library/react";
import { useQuery } from "@apollo/client/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { StreamMetricsOverview } from "./StreamMetricsOverview";

vi.mock("@apollo/client/react", () => ({
  useQuery: vi.fn(),
}));

const useQueryMock = vi.mocked(useQuery);

describe("StreamMetricsOverview", () => {
  beforeEach(() => {
    useQueryMock.mockReset();
  });

  it("renders the loading state", () => {
    useQueryMock.mockReturnValue({ loading: true, data: undefined, error: undefined } as never);

    render(<StreamMetricsOverview streamer="test" />);

    expect(screen.getByText("Loading aggregate metrics...")).toBeInTheDocument();
  });

  it("renders aggregate metric values", () => {
    useQueryMock.mockReturnValue({
      loading: false,
      error: undefined,
      data: {
        streamMetricsSummary: {
          streamer: "test",
          windowMinutes: 15,
          chat: {
            totalMessages: 42,
            messagesPerMinute: 2.8,
            uniqueChatters: 11,
            peakMessagesPerMinute: 9,
          },
          chatSentiment: {
            positive: 12,
            neutral: 20,
            negative: 10,
            averageScore: 0.12,
            negativeRatio: 0.238,
          },
          transcriptSentiment: {
            positive: 2,
            neutral: 4,
            negative: 1,
            averageScore: 0.3,
            negativeRatio: 0.143,
          },
          sponsorExposure: {
            totalDetections: 3,
            acceptedDetections: 3,
            estimatedExposureMs: 30000,
            topSponsors: [
              {
                sponsor: "Nike",
                detectionCount: 3,
                acceptedDetectionCount: 3,
                estimatedExposureMs: 30000,
                averageConfidence: 0.81,
                maxConfidence: 0.9,
                fallbackDetectionCount: 0,
                lowConfidenceDetectionCount: 0,
              },
            ],
          },
          engagement: {
            spikeCount: 1,
            latestSpikeAt: 1710000600000,
          },
          risk: {
            level: "LOW",
            score: 0.2,
            factors: [{ name: "chatNegativeRatio", value: 0.238, weight: 0.35 }],
          },
          dataQuality: {
            lowData: false,
            latestEventAt: 1710000600000,
            aggregationLagMs: 1500,
          },
        },
        streamMetricsTimeseries: [
          {
            bucketStart: 1710000000000,
            chatMessageCount: 7,
            chatAverageScore: -0.2,
            transcriptAverageScore: 0.1,
            sponsorDetectionCount: 1,
            estimatedSponsorExposureMs: 10000,
            engagementSpike: true,
            negativeSpike: false,
          },
        ],
      },
    } as never);

    render(<StreamMetricsOverview streamer="test" />);

    expect(screen.getByText("Stream Metrics Overview")).toBeInTheDocument();
    expect(screen.getByText("2.8")).toBeInTheDocument();
    expect(screen.getByText("11")).toBeInTheDocument();
    expect(screen.getByText("LOW 0.20")).toBeInTheDocument();
    expect(screen.getByText("Nike (30s)")).toBeInTheDocument();
    expect(screen.getByText("chatNegativeRatio=0.24 w=0.35")).toBeInTheDocument();
  });

  it("renders GraphQL errors", () => {
    useQueryMock.mockReturnValue({
      loading: false,
      data: undefined,
      error: new Error("analytics unavailable"),
    } as never);

    render(<StreamMetricsOverview streamer="test" />);

    expect(screen.getByRole("alert")).toHaveTextContent("Failed to load aggregate metrics");
  });
});
