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
};

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

/**
 * An Apollo link that answers from the snapshot. The demo shows past streams, so a subscription (the
 * live console's feeds, which the demo does not render) simply stays quiet and completes.
 */
export function createDemoLink(snapshot: () => Promise<Snapshot> = loadSnapshot): ApolloLink {
  return new ApolloLink(
    (operation) =>
      new Observable<FetchResult>((observer) => {
        let cancelled = false;
        if (isSubscriptionOperation(operation.query)) {
          observer.complete();
          return () => undefined;
        }
        snapshot()
          .then((data) => {
            if (cancelled) return;
            const name = operationName(operation);
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
        };
      }),
  );
}

/** The client the demo runs on: the snapshot link, a fresh cache, no network. */
export function createDemoClient(): ApolloClient {
  return new ApolloClient({ link: createDemoLink(), cache: new InMemoryCache() });
}
