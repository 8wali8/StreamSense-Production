import { act, render, screen } from "@testing-library/react";
import { useQuery, useSubscription } from "@apollo/client/react";
import { gql } from "@apollo/client";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useLiveFeed } from "./useLiveFeed";

vi.mock("@apollo/client/react", () => ({
  useQuery: vi.fn(),
  useSubscription: vi.fn(),
}));

const useQueryMock = vi.mocked(useQuery);
const useSubscriptionMock = vi.mocked(useSubscription);

const QUERY = gql`
  query Items($streamer: String!) {
    items(streamer: $streamer) {
      id
    }
  }
`;
const SUBSCRIPTION = gql`
  subscription OnItem($streamer: String!) {
    onItem(streamer: $streamer) {
      id
    }
  }
`;

type Item = { id: string; streamer: string };
type ItemsQuery = { items: Item[] };
type OnItemSubscription = { onItem: Item };
type OnData = (value: { data: { data?: OnItemSubscription } }) => void;

function Probe({ streamer }: { streamer: string }) {
  const feed = useLiveFeed<ItemsQuery, OnItemSubscription, { streamer: string }, Item>({
    query: QUERY,
    variables: { streamer },
    selectHistory: (data) => data.items,
    subscription: SUBSCRIPTION,
    selectEvent: (data) => data.onItem,
    getId: (item) => item.id,
    limit: 3,
    accept: (item) => item.streamer === streamer,
    resetKey: streamer,
  });
  return (
    <div>
      <span data-testid="items">{feed.items.map((item) => item.id).join(",")}</span>
      <span data-testid="live">{feed.live.length}</span>
      <span data-testid="loading">{String(feed.loading)}</span>
    </div>
  );
}

describe("useLiveFeed", () => {
  let onData: OnData | undefined;

  beforeEach(() => {
    onData = undefined;
    useSubscriptionMock.mockImplementation((_, options) => {
      onData = (options as { onData?: OnData } | undefined)?.onData;
      return { error: undefined } as never;
    });
    useQueryMock.mockReturnValue({
      loading: false,
      error: undefined,
      data: { items: [{ id: "h1", streamer: "test" }, { id: "h2", streamer: "test" }] },
    } as never);
  });

  it("shows live events newest-first on top of history, de-duplicated and capped", () => {
    render(<Probe streamer="test" />);
    expect(screen.getByTestId("items")).toHaveTextContent("h1,h2");

    act(() => {
      onData?.({ data: { data: { onItem: { id: "l1", streamer: "test" } } } });
      onData?.({ data: { data: { onItem: { id: "h1", streamer: "test" } } } });
      onData?.({ data: { data: { onItem: { id: "l1", streamer: "test" } } } });
      onData?.({ data: { data: { onItem: { id: "l2", streamer: "test" } } } });
    });

    expect(screen.getByTestId("live")).toHaveTextContent("2");
    expect(screen.getByTestId("items")).toHaveTextContent("l2,l1,h1");
  });

  it("drops events that fail the accept filter", () => {
    render(<Probe streamer="test" />);

    act(() => {
      onData?.({ data: { data: { onItem: { id: "other", streamer: "someone-else" } } } });
    });

    expect(screen.getByTestId("live")).toHaveTextContent("0");
  });

  it("starts with an empty live buffer when the reset key changes", () => {
    const { rerender } = render(<Probe streamer="test" />);
    act(() => {
      onData?.({ data: { data: { onItem: { id: "l1", streamer: "test" } } } });
    });
    expect(screen.getByTestId("live")).toHaveTextContent("1");

    rerender(<Probe streamer="next" />);

    expect(screen.getByTestId("live")).toHaveTextContent("0");
  });

  it("does not bring the old buffer back when the reset key returns to a previous value", () => {
    const { rerender } = render(<Probe streamer="test" />);
    act(() => {
      onData?.({ data: { data: { onItem: { id: "l1", streamer: "test" } } } });
    });
    expect(screen.getByTestId("live")).toHaveTextContent("1");

    rerender(<Probe streamer="next" />);
    expect(screen.getByTestId("live")).toHaveTextContent("0");

    // Back to the first streamer while the second one never emitted anything.
    rerender(<Probe streamer="test" />);

    expect(screen.getByTestId("live")).toHaveTextContent("0");
  });

  it("passes the query options through to Apollo", () => {
    render(<Probe streamer="test" />);

    expect(useQueryMock).toHaveBeenCalledWith(
      QUERY,
      expect.objectContaining({ variables: { streamer: "test" }, fetchPolicy: "network-only" }),
    );
    expect(useSubscriptionMock).toHaveBeenCalledWith(SUBSCRIPTION, expect.objectContaining({ variables: { streamer: "test" } }));
  });
});
