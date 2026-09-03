import { useState } from "react";
import type { OperationVariables } from "@apollo/client";
import { useQuery, useSubscription } from "@apollo/client/react";
import type { DocumentNode } from "graphql";
import { mergeById } from "../lib/format";

type ErrorLike = { message: string } | undefined;

export type LiveEventsOptions<TSubscription, TItem> = {
  subscription: DocumentNode;
  variables?: OperationVariables;
  skip?: boolean;
  /** Pick the event out of the subscription payload. */
  selectEvent: (data: TSubscription) => TItem | null | undefined;
  getId: (item: TItem) => string;
  /** Newest events kept in memory. */
  limit: number;
  /** Ids already on screen from history; a live copy of one of these is dropped. */
  knownIds?: ReadonlySet<string>;
  /** Only keep events that pass (for example, the selected streamer). */
  accept?: (item: TItem) => boolean;
  /** The buffer is cleared whenever this changes (for example, the selected streamer). */
  resetKey?: string;
};

type Buffer<TItem> = { key: string; items: TItem[] };

/**
 * Keeps the newest events from a GraphQL subscription, newest first, de-duplicated by id and
 * against `knownIds`. The buffer is keyed by `resetKey`, so switching streamer starts empty.
 */
export function useLiveEvents<TSubscription, TItem>(options: LiveEventsOptions<TSubscription, TItem>): {
  live: TItem[];
  error: ErrorLike;
} {
  const resetKey = options.resetKey ?? "";
  const [buffer, setBuffer] = useState<Buffer<TItem>>({ key: resetKey, items: [] });
  // Drop the previous key's buffer as soon as the key changes, using React's "adjust state during
  // render" pattern. Hiding it at return time is not enough: switching A -> B -> A while B stays
  // quiet would otherwise bring A's old events back.
  if (buffer.key !== resetKey) {
    setBuffer({ key: resetKey, items: [] });
  }
  const { selectEvent, getId, limit, knownIds, accept } = options;

  const { error } = useSubscription<TSubscription, OperationVariables>(options.subscription, {
    variables: options.variables,
    skip: options.skip,
    onData: ({ data }) => {
      const payload = data.data;
      const event = payload ? selectEvent(payload) : undefined;
      if (!event || (accept && !accept(event))) return;
      const id = getId(event);
      setBuffer((previous) => {
        const items = previous.key === resetKey ? previous.items : [];
        if (knownIds?.has(id) || items.some((item) => getId(item) === id)) {
          return previous;
        }
        return { key: resetKey, items: [event, ...items].slice(0, limit) };
      });
    },
  });

  return { live: buffer.key === resetKey ? buffer.items : [], error };
}

export type LiveFeedOptions<TQuery, TSubscription, TVariables extends OperationVariables, TItem> = {
  query: DocumentNode;
  variables: TVariables;
  skip?: boolean;
  pollInterval?: number;
  /** Pick the history list out of the query result. */
  selectHistory: (data: TQuery) => TItem[];
  subscription: DocumentNode;
  /** Defaults to `variables`. */
  subscriptionVariables?: OperationVariables;
  selectEvent: (data: TSubscription) => TItem | null | undefined;
  getId: (item: TItem) => string;
  /** Items returned after merging live over history. */
  limit: number;
  accept?: (item: TItem) => boolean;
  resetKey?: string;
};

export type LiveFeed<TItem> = {
  /** Live events (newest first) merged over the query history, de-duplicated, capped at `limit`. */
  items: TItem[];
  history: TItem[];
  live: TItem[];
  loading: boolean;
  error: ErrorLike;
  subscriptionError: ErrorLike;
};

/**
 * The one pattern every live panel needs: a history query (optionally polled) plus a subscription
 * whose events are shown on top of it. Both go through Apollo's hooks, so tests that mock
 * `@apollo/client/react` keep working.
 */
export function useLiveFeed<TQuery, TSubscription, TVariables extends OperationVariables, TItem>(
  options: LiveFeedOptions<TQuery, TSubscription, TVariables, TItem>,
): LiveFeed<TItem> {
  const queryResult = useQuery<TQuery, TVariables>(options.query, {
    variables: options.variables,
    skip: options.skip,
    fetchPolicy: "network-only",
    pollInterval: options.pollInterval,
  });

  const history = queryResult.data ? options.selectHistory(queryResult.data) : [];
  const historyIds = new Set(history.map(options.getId));

  const { live, error: subscriptionError } = useLiveEvents<TSubscription, TItem>({
    subscription: options.subscription,
    variables: options.subscriptionVariables ?? options.variables,
    skip: options.skip,
    selectEvent: options.selectEvent,
    getId: options.getId,
    limit: options.limit,
    knownIds: historyIds,
    accept: options.accept,
    resetKey: options.resetKey,
  });

  return {
    items: mergeById(live, history, options.getId, options.limit),
    history,
    live,
    loading: queryResult.loading,
    error: queryResult.error,
    subscriptionError,
  };
}
