import { useQuery } from "@apollo/client/react";
import type {
  SessionSummaryQuery,
  SessionSummaryQueryVariables,
  SponsorMomentsQuery,
  SponsorMomentsQueryVariables,
} from "../../graphql/generated";
import { SESSION_SUMMARY_QUERY, SPONSOR_MOMENTS_QUERY } from "../../graphql/queries";
import { describeError } from "../../lib/errors";

export type SessionSummary = NonNullable<SessionSummaryQuery["sessionSummary"]>;
export type SponsorMoments = NonNullable<SponsorMomentsQuery["sponsorMoments"]>;

export type SessionReport = {
  summary: SessionSummary | null;
  moments: SponsorMoments | null;
  loading: boolean;
  /** Plain-language failure, or null. The summary is the page; the timeline failing alone is reported separately. */
  error: string | null;
  momentsError: string | null;
  /** True when the session id resolved to nothing. */
  notFound: boolean;
};

/** The numbers and the timeline for one session, both keyed by the session id and optional sponsor. */
export function useSessionReport(sessionId: string, sponsor?: string): SessionReport {
  const summary = useQuery<SessionSummaryQuery, SessionSummaryQueryVariables>(SESSION_SUMMARY_QUERY, {
    variables: { sessionId, sponsor: sponsor ?? null },
    skip: !sessionId,
    fetchPolicy: "cache-and-network",
  });
  const moments = useQuery<SponsorMomentsQuery, SponsorMomentsQueryVariables>(SPONSOR_MOMENTS_QUERY, {
    variables: { sessionId, sponsor: sponsor ?? null },
    skip: !sessionId,
    fetchPolicy: "cache-and-network",
  });

  return {
    summary: summary.data?.sessionSummary ?? null,
    moments: moments.data?.sponsorMoments ?? null,
    loading: summary.loading && !summary.data,
    error: summary.error ? describeError(summary.error) : null,
    momentsError: moments.error ? describeError(moments.error) : null,
    notFound: !summary.loading && !summary.error && summary.data != null && summary.data.sessionSummary == null,
  };
}
