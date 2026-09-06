import { act, screen, waitFor } from "@testing-library/react";
import { gql } from "@apollo/client";
import { describe, expect, it } from "vitest";
import { currentSubscription, emitSubscription, renderWithApollo } from "../test/apollo";
import { graphqlResolver, HttpResponse, server } from "../test/msw";
import { useLiveFeed } from "./useLiveFeed";

const QUERY = gql`
  query Items($streamer: String!) {
    items(streamer: $streamer) {
      id
      streamer
    }
  }
`;
const SUBSCRIPTION = gql`
  subscription OnItem($streamer: String!) {
    onItem(streamer: $streamer) {
      id
      streamer
    }
  }
`;

type Item = { __typename: "Item"; id: string; streamer: string };
type ItemsQuery = { items: Item[] };
type OnItemSubscription = { onItem: Item };

const item = (id: string, streamer = "test"): Item => ({ __typename: "Item", id, streamer });

function Probe({ streamer }: { streamer: string }) {
  const feed = useLiveFeed<ItemsQuery, OnItemSubscription, { streamer: string }, Item>({
    query: QUERY,
    variables: { streamer },
    selectHistory: (data) => data.items,
    subscription: SUBSCRIPTION,
    selectEvent: (data) => data.onItem,
    getId: (entry) => entry.id,
    limit: 3,
    accept: (entry) => entry.streamer === streamer,
    resetKey: streamer,
  });
  return (
    <div>
      <span data-testid="items">{feed.items.map((entry) => entry.id).join(",")}</span>
      <span data-testid="live">{feed.live.length}</span>
      <span data-testid="loading">{String(feed.loading)}</span>
    </div>
  );
}

function historyFor(streamer: string): Item[] {
  return streamer === "test" ? [item("h1"), item("h2")] : [item("other-h1", streamer)];
}

describe("useLiveFeed", () => {
  it("shows live events newest-first on top of history, de-duplicated and capped", async () => {
    server.use(
      graphqlResolver("Items", ({ variables }) =>
        HttpResponse.json({ data: { items: historyFor(String(variables.streamer)) } }),
      ),
    );

    const apollo = renderWithApollo(<Probe streamer="test" />);
    expect(screen.getByTestId("loading")).toHaveTextContent("true");
    await screen.findByText("h1,h2");

    act(() => {
      emitSubscription(apollo, { onItem: item("l1") });
      emitSubscription(apollo, { onItem: item("h1") });
      emitSubscription(apollo, { onItem: item("l1") });
      emitSubscription(apollo, { onItem: item("l2") });
    });

    expect(await screen.findByText("l2,l1,h1")).toBeInTheDocument();
    expect(screen.getByTestId("live")).toHaveTextContent("2");
  });

  it("drops events that fail the accept filter", async () => {
    server.use(
      graphqlResolver("Items", ({ variables }) =>
        HttpResponse.json({ data: { items: historyFor(String(variables.streamer)) } }),
      ),
    );

    const apollo = renderWithApollo(<Probe streamer="test" />);
    await screen.findByText("h1,h2");

    act(() => {
      emitSubscription(apollo, { onItem: item("other", "someone-else") });
    });

    expect(screen.getByTestId("live")).toHaveTextContent("0");
    expect(screen.getByTestId("items")).toHaveTextContent("h1,h2");
  });

  it("starts with an empty live buffer and new history when the reset key changes", async () => {
    server.use(
      graphqlResolver("Items", ({ variables }) =>
        HttpResponse.json({ data: { items: historyFor(String(variables.streamer)) } }),
      ),
    );

    const apollo = renderWithApollo(<Probe streamer="test" />);
    await screen.findByText("h1,h2");
    act(() => {
      emitSubscription(apollo, { onItem: item("l1") });
    });
    await screen.findByText("l1,h1,h2");

    apollo.rerender(<Probe streamer="next" />);

    expect(screen.getByTestId("live")).toHaveTextContent("0");
    expect(await screen.findByText("other-h1")).toBeInTheDocument();
  });

  it("does not bring the old buffer back when the reset key returns to a previous value", async () => {
    server.use(
      graphqlResolver("Items", ({ variables }) =>
        HttpResponse.json({ data: { items: historyFor(String(variables.streamer)) } }),
      ),
    );

    const apollo = renderWithApollo(<Probe streamer="test" />);
    await screen.findByText("h1,h2");
    // The subscription must be opened with the selected streamer, and the event is only delivered
    // to a subscription that carries it.
    expect(currentSubscription(apollo)).toEqual({ operationName: "OnItem", variables: { streamer: "test" } });
    act(() => {
      emitSubscription(apollo, { onItem: item("l1") }, { operationName: "OnItem", variables: { streamer: "test" } });
    });
    await screen.findByText("l1,h1,h2");

    apollo.rerender(<Probe streamer="next" />);
    expect(screen.getByTestId("live")).toHaveTextContent("0");
    await waitFor(() => {
      expect(currentSubscription(apollo).variables).toEqual({ streamer: "next" });
    });

    // Back to the first streamer while the second one never emitted anything: the old live
    // event must not reappear.
    apollo.rerender(<Probe streamer="test" />);
    await screen.findByText("h1,h2");

    expect(screen.getByTestId("live")).toHaveTextContent("0");
  });
});
