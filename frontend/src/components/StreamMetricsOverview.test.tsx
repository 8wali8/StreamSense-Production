import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { renderWithApollo } from "../test/apollo";
import { streamAnalytics } from "../test/fixtures";
import { graphqlData, graphqlError, graphqlPending, server } from "../test/msw";
import { StreamMetricsOverview } from "./StreamMetricsOverview";

describe("StreamMetricsOverview", () => {
  it("renders the loading state", () => {
    server.use(graphqlPending("StreamAnalytics"));

    renderWithApollo(<StreamMetricsOverview streamer="test" />);

    expect(screen.getByText("Loading aggregate metrics...")).toBeInTheDocument();
  });

  it("renders aggregate metric values", async () => {
    server.use(graphqlData("StreamAnalytics", streamAnalytics()));

    renderWithApollo(<StreamMetricsOverview streamer="test" />);

    expect(await screen.findByText("2.8")).toBeInTheDocument();
    expect(screen.getByText("Stream Metrics Overview")).toBeInTheDocument();
    expect(screen.getByText("11")).toBeInTheDocument();
    expect(screen.getByText("LOW 0.20")).toBeInTheDocument();
    expect(screen.getByText("Nike (30s)")).toBeInTheDocument();
    expect(screen.getByText("chatNegativeRatio=0.24 w=0.35")).toBeInTheDocument();
    expect(screen.getByText("15m window")).toBeInTheDocument();
  });

  it("renders GraphQL errors", async () => {
    server.use(graphqlError("StreamAnalytics", "analytics unavailable"));

    renderWithApollo(<StreamMetricsOverview streamer="test" />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Failed to load aggregate metrics");
  });
});
