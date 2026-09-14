import { useApolloClient } from "@apollo/client/react";
import { useEffect, useState } from "react";
import type { SessionSummaryQuery, SessionSummaryQueryVariables } from "../../graphql/generated";
import { SESSION_SUMMARY_QUERY } from "../../graphql/queries";

export type SummaryById = Record<string, NonNullable<SessionSummaryQuery["sessionSummary"]>>;

/** What was last fetched; `key` is null until the first fetch lands. */
type Loaded = { key: string | null; summaries: SummaryById };

/**
 * One summary per session id, fetched in parallel through the Apollo client and kept by id. The
 * history list is short (a page of sessions), so a query per row is fine until a batch query exists.
 * The summaries are keyed by the ids and the sponsor they were fetched for: when either changes,
 * the previous ones are not shown under the new sponsor's label while the new ones load.
 */
export function useSessionSummaries(
  sessionIds: string[],
  sponsor?: string,
): { summaries: SummaryById; loading: boolean } {
  const client = useApolloClient();
  const key = sessionIds.join(",") + "|" + (sponsor ?? "");
  const [loaded, setLoaded] = useState<Loaded>({ key: null, summaries: {} });

  useEffect(() => {
    if (sessionIds.length === 0) {
      setLoaded({ key, summaries: {} });
      return;
    }
    let cancelled = false;
    void Promise.all(
      sessionIds.map((sessionId) =>
        client
          .query<SessionSummaryQuery, SessionSummaryQueryVariables>({
            query: SESSION_SUMMARY_QUERY,
            variables: { sessionId, sponsor: sponsor ?? null },
            fetchPolicy: "network-only",
          })
          .then((result) => [sessionId, result.data?.sessionSummary ?? null] as const)
          .catch(() => [sessionId, null] as const),
      ),
    ).then((entries) => {
      if (cancelled) return;
      const next: SummaryById = {};
      for (const [sessionId, summary] of entries) {
        if (summary) next[sessionId] = summary;
      }
      setLoaded({ key, summaries: next });
    });
    return () => {
      cancelled = true;
    };
    // The joined key captures every id and the sponsor; the array identity changes on every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [client, key]);

  const current = loaded.key === key;
  return {
    summaries: current ? loaded.summaries : {},
    loading: sessionIds.length > 0 && !current,
  };
}
