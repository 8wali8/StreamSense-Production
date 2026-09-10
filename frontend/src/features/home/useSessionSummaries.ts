import { useApolloClient } from "@apollo/client/react";
import { useEffect, useState } from "react";
import type { SessionSummaryQuery, SessionSummaryQueryVariables } from "../../graphql/generated";
import { SESSION_SUMMARY_QUERY } from "../../graphql/queries";

export type SummaryById = Record<string, NonNullable<SessionSummaryQuery["sessionSummary"]>>;

/**
 * One summary per session id, fetched in parallel through the Apollo client and kept by id. The
 * history list is short (a page of sessions), so a query per row is fine until a batch query exists.
 */
export function useSessionSummaries(
  sessionIds: string[],
  sponsor?: string,
): { summaries: SummaryById; loading: boolean } {
  const client = useApolloClient();
  const [summaries, setSummaries] = useState<SummaryById>({});
  const [loading, setLoading] = useState(false);
  const key = sessionIds.join(",") + "|" + (sponsor ?? "");

  useEffect(() => {
    if (sessionIds.length === 0) {
      setSummaries({});
      return;
    }
    let cancelled = false;
    setLoading(true);
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
      setSummaries(next);
      setLoading(false);
    });
    return () => {
      cancelled = true;
    };
    // The joined key captures every id and the sponsor; the array identity changes on every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [client, key]);

  return { summaries, loading };
}
