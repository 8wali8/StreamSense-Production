import { render, screen } from "@testing-library/react";
import { useQuery } from "@apollo/client/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { RecommendationPanel } from "./RecommendationPanel";

vi.mock("@apollo/client/react", () => ({
  useQuery: vi.fn(),
}));

const useQueryMock = vi.mocked(useQuery);

describe("RecommendationPanel", () => {
  beforeEach(() => {
    useQueryMock.mockReset();
  });

  it("renders the loading state", () => {
    useQueryMock.mockReturnValue({ loading: true, data: undefined, error: undefined } as never);

    render(<RecommendationPanel />);

    expect(screen.getByText("Loading recommendations...")).toBeInTheDocument();
  });

  it("renders recommendation results with reasons and variant metadata", async () => {
    useQueryMock.mockReturnValue({
      loading: false,
      error: undefined,
      data: {
        recommendations: [
          {
            recommendationId: "test:sponsor_alignment",
            streamer: "test",
            title: "Highlight Nike moments while they are landing",
            category: "SPONSOR_ALIGNMENT",
            score: 0.83,
            reasonSummary: "Nike is the most visible sponsor in the recent window.",
            reasons: [
              "Nike appeared in 67% of recent sponsor detections.",
              "Average confidence for Nike was 0.88.",
            ],
            experimentName: "recommendation-ranking-v1",
            variantId: "balanced",
            generatedAt: 1712890800000,
          },
        ],
      },
    } as never);

    render(<RecommendationPanel />);

    expect(await screen.findByText("Highlight Nike moments while they are landing")).toBeInTheDocument();
    expect(screen.getByText("Nike is the most visible sponsor in the recent window.")).toBeInTheDocument();
    expect(screen.getByText("Variant: balanced")).toBeInTheDocument();
    expect(screen.getByText("Average confidence for Nike was 0.88.")).toBeInTheDocument();
  });

  it("renders the empty state", async () => {
    useQueryMock.mockReturnValue({
      loading: false,
      error: undefined,
      data: { recommendations: [] },
    } as never);

    render(<RecommendationPanel />);

    expect(await screen.findByText("No recommendations yet.")).toBeInTheDocument();
  });

  it("renders GraphQL errors", () => {
    useQueryMock.mockReturnValue({
      loading: false,
      data: undefined,
      error: new Error("recommendation service unavailable"),
    } as never);

    render(<RecommendationPanel />);

    expect(screen.getByRole("alert")).toHaveTextContent("Failed to load recommendations");
  });
});
