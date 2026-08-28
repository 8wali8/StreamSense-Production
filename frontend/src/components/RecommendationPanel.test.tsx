import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { renderWithApollo } from "../test/apollo";
import { recommendation } from "../test/fixtures";
import { graphqlData, graphqlError, graphqlPending, server } from "../test/msw";
import { RecommendationPanel } from "./RecommendationPanel";

describe("RecommendationPanel", () => {
  it("renders the loading state", () => {
    server.use(graphqlPending("Recommendations"));

    renderWithApollo(<RecommendationPanel />);

    expect(screen.getByText("Loading recommendations...")).toBeInTheDocument();
  });

  it("renders recommendation results with reasons and variant metadata", async () => {
    server.use(graphqlData("Recommendations", { recommendations: [recommendation()] }));

    renderWithApollo(<RecommendationPanel />);

    expect(await screen.findByText("Highlight Nike moments while they are landing")).toBeInTheDocument();
    expect(screen.getByText("Nike is the most visible sponsor in the recent window.")).toBeInTheDocument();
    expect(screen.getByText("Variant: balanced")).toBeInTheDocument();
    expect(screen.getByText("Average confidence for Nike was 0.88.")).toBeInTheDocument();
  });

  it("renders the empty state", async () => {
    server.use(graphqlData("Recommendations", { recommendations: [] }));

    renderWithApollo(<RecommendationPanel />);

    expect(await screen.findByText("No recommendations yet.")).toBeInTheDocument();
  });

  it("renders GraphQL errors", async () => {
    server.use(graphqlError("Recommendations", "recommendation service unavailable"));

    renderWithApollo(<RecommendationPanel />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Failed to load recommendations");
  });
});
