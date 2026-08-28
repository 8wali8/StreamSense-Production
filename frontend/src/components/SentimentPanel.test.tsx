import { act, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { emitSubscription, renderWithApollo } from "../test/apollo";
import { sentimentEvent } from "../test/fixtures";
import { graphqlData, graphqlError, graphqlPending, server } from "../test/msw";
import { SentimentPanel } from "./SentimentPanel";

describe("SentimentPanel", () => {
  it("renders the loading state", () => {
    server.use(graphqlPending("RecentSentiment"));

    renderWithApollo(<SentimentPanel />);

    expect(screen.getByText("Loading sentiment history...")).toBeInTheDocument();
  });

  it("renders history results", async () => {
    server.use(graphqlData("RecentSentiment", { recentSentiment: [sentimentEvent()] }));

    renderWithApollo(<SentimentPanel />);

    expect(await screen.findByText("great stream")).toBeInTheDocument();
    expect(screen.getByText("POSITIVE")).toBeInTheDocument();
    expect(screen.getByText("score=0.82")).toBeInTheDocument();
  });

  it("renders the empty state", async () => {
    server.use(graphqlData("RecentSentiment", { recentSentiment: [] }));

    renderWithApollo(<SentimentPanel />);

    expect(await screen.findByText("No sentiment history yet.")).toBeInTheDocument();
  });

  it("renders GraphQL errors", async () => {
    server.use(graphqlError("RecentSentiment", "sentiment service unavailable"));

    renderWithApollo(<SentimentPanel />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Failed to load sentiment history");
  });

  it("renders a live subscription event on top of history", async () => {
    server.use(graphqlData("RecentSentiment", { recentSentiment: [sentimentEvent()] }));

    const apollo = renderWithApollo(<SentimentPanel />);
    expect(await screen.findByText("great stream")).toBeInTheDocument();

    act(() => {
      emitSubscription(apollo, {
        onSentiment: sentimentEvent({
          sentimentEventId: "sent-2",
          sourceEventId: "src-2",
          user: "u2",
          message: "that was rough",
          chatTimestamp: 1710000001000,
          processedAt: 1710000001500,
          label: "NEGATIVE",
          score: -0.74,
        }),
      });
    });

    expect(await screen.findByText("that was rough")).toBeInTheDocument();
    expect(screen.getByText("NEGATIVE")).toBeInTheDocument();
    expect(screen.getByText("score=-0.74")).toBeInTheDocument();
    const messages = screen.getAllByRole("article").map((card) => card.querySelector("p")?.textContent);
    expect(messages).toEqual(["that was rough", "great stream"]);
    expect(screen.getByText("2 signals")).toBeInTheDocument();
  });

  it("renders fallback sentiment as normal analytics data", async () => {
    server.use(
      graphqlData("RecentSentiment", {
        recentSentiment: [
          sentimentEvent({
            sentimentEventId: "sent-fallback",
            sourceEventId: "src-fallback",
            user: "u3",
            message: "ml fallback case",
            label: "NEUTRAL",
            score: 0,
            modelVersion: "fallback",
          }),
        ],
      }),
    );

    renderWithApollo(<SentimentPanel />);

    expect(await screen.findByText("ml fallback case")).toBeInTheDocument();
    expect(screen.getByText("NEUTRAL")).toBeInTheDocument();
    expect(screen.getByText("score=0.00")).toBeInTheDocument();
    expect(screen.getByText("model=fallback")).toBeInTheDocument();
  });
});
