import { ApolloClient, ApolloLink, InMemoryCache, Observable } from "@apollo/client";
import type { FetchResult, Operation } from "@apollo/client";
import { isSubscriptionOperation } from "@apollo/client/utilities";
import { print } from "graphql";

/** One recorded answer: the variables the exporter used and the data the gateway returned. */
type Entry = { variables: Record<string, unknown>; data: Record<string, unknown> };

export type Snapshot = {
  meta: { channel: string; sponsor: string; dealId: string | null; latestSessionId: string | null };
  graphql: Record<string, Entry[]>;
  rest: Record<string, { status: number; body: unknown }>;
  chat: Array<Record<string, unknown>>;
};

/** Which recorded query feeds each subscription, and under which field the events are pushed. */
const SUBSCRIPTION_SOURCES: Record<string, { query: string; field: string; source: string }> = {
  OnChatMessage: { query: "chat", field: "onChatMessage", source: "chat" },
  OnSentiment: { query: "RecentSentiment", field: "onSentiment", source: "recentSentiment" },
  OnSponsorSentiment: {
    query: "RecentSponsorSentiment",
    field: "onSponsorSentiment",
    source: "recentSponsorSentiment",
  },
  OnTranscriptSegment: {
    query: "RecentTranscriptSegments",
    field: "onTranscriptSegment",
    source: "recentTranscriptSegments",
  },
  OnTranscriptSentiment: {
    query: "RecentTranscriptSentiment",
    field: "onTranscriptSentiment",
    source: "recentTranscriptSentiment",
  },
  OnSponsorTranscriptSentiment: {
    query: "RecentSponsorTranscriptSentiment",
    field: "onSponsorTranscriptSentiment",
    source: "recentSponsorTranscriptSentiment",
  },
  OnSponsorDetection: { query: "SponsorDetections", field: "onSponsorDetection", source: "sponsorDetections" },
};

/** Milliseconds between replayed live events, so the feeds tick without racing. */
const REPLAY_INTERVAL_MS = 4000;

let loaded: Promise<Snapshot> | null = null;

/** The snapshot, fetched once and only on the demo (it is not in the console's main bundle). */
export function loadSnapshot(): Promise<Snapshot> {
  loaded ??= import("./snapshot.json").then((module) => module.default as unknown as Snapshot);
  return loaded;
}

/**
 * The recorded entry that best matches a request: every variable the request sets that the entry also
 * recorded must agree, except `limit`, which the exporter recorded generously. Among the candidates, the
 * one agreeing on the most variables wins, so `sponsor: null` and `sponsor: "Red Bull"` stay apart.
 */
export function pickEntry(entries: Entry[] | undefined, variables: Record<string, unknown>): Entry | null {
  if (!entries || entries.length === 0) return null;
  let best: Entry | null = null;
  let bestScore = -1;
  for (const entry of entries) {
    let score = 0;
    let mismatch = false;
    for (const [key, value] of Object.entries(variables)) {
      if (key === "limit" || !(key in entry.variables)) continue;
      if (scalar(entry.variables[key]) === scalar(value)) score += 1;
      else mismatch = true;
    }
    if (!mismatch && score > bestScore) {
      best = entry;
      bestScore = score;
    }
  }
  return best;
}

/** Variables compare as text; null, undefined, and an empty string are the same absence. */
function scalar(value: unknown): string {
  return typeof value === "string" || typeof value === "number" || typeof value === "boolean" ? String(value) : "";
}

function operationName(operation: Operation): string {
  return operation.operationName || print(operation.query).slice(0, 40);
}

/** An Apollo link that answers from the snapshot and replays its feeds as subscriptions. */
export function createDemoLink(snapshot: () => Promise<Snapshot> = loadSnapshot): ApolloLink {
  return new ApolloLink(
    (operation) =>
      new Observable<FetchResult>((observer) => {
        let timer: number | null = null;
        let cancelled = false;
        snapshot()
          .then((data) => {
            if (cancelled) return;
            const name = operationName(operation);
            if (isSubscriptionOperation(operation.query)) {
              const source = SUBSCRIPTION_SOURCES[name];
              const items = source ? replayItems(data, source, operation.variables) : [];
              let index = 0;
              const tick = () => {
                if (items.length === 0) return;
                // Oldest first so the feed builds up the way a live one does, then around again. Each replayed
                // event gets a fresh id and timestamp: the feeds drop anything whose id is already in their
                // history, and the recorded events are exactly that history.
                const item = asLiveEvent(items[items.length - 1 - (index % items.length)], index);
                index += 1;
                observer.next({ data: { [source.field]: item } });
                timer = window.setTimeout(tick, REPLAY_INTERVAL_MS);
              };
              timer = window.setTimeout(tick, REPLAY_INTERVAL_MS);
              return;
            }
            const entry = pickEntry(data.graphql[name], operation.variables);
            if (!entry) {
              observer.error(new Error(`The demo has no data for ${name}`));
              return;
            }
            observer.next({ data: entry.data });
            observer.complete();
          })
          .catch((error: unknown) => observer.error(error));
        return () => {
          cancelled = true;
          if (timer !== null) window.clearTimeout(timer);
        };
      }),
  );
}

const ID_KEYS = ["eventId", "sentimentEventId", "segmentId", "detectionEventId"];
const TIME_KEYS = ["timestamp", "chatTimestamp", "capturedAt", "processedAt", "segmentStartedAt", "segmentEndedAt"];

/** A recorded event as if it had just happened: a new id, so the feeds accept it, and the clock set to now. */
export function asLiveEvent(item: Record<string, unknown>, sequence: number): Record<string, unknown> {
  const now = Date.now();
  const live: Record<string, unknown> = { ...item };
  for (const key of ID_KEYS) {
    if (typeof live[key] === "string") live[key] = `${live[key]}-live-${sequence}`;
  }
  for (const key of TIME_KEYS) {
    if (typeof live[key] === "number") live[key] = now;
  }
  return live;
}

function replayItems(
  snapshot: Snapshot,
  source: { query: string; source: string },
  variables: Record<string, unknown>,
): Array<Record<string, unknown>> {
  if (source.query === "chat") return snapshot.chat;
  const entry = pickEntry(snapshot.graphql[source.query], variables);
  const items = entry?.data[source.source];
  return Array.isArray(items) ? (items as Array<Record<string, unknown>>) : [];
}

/** The client the demo runs on: the snapshot link, a fresh cache, no network. */
export function createDemoClient(): ApolloClient {
  return new ApolloClient({ link: createDemoLink(), cache: new InMemoryCache() });
}
