import { useQuery } from "@apollo/client/react";
import { Navigate } from "react-router";
import type { SessionsQuery, SessionsQueryVariables } from "../../graphql/generated";
import { SESSIONS_QUERY } from "../../graphql/queries";
import { describeError } from "../../lib/errors";
import { useStreamer } from "../streamer/streamer-context";

/** Sends the sidebar's "Session report" link to the current channel's newest session. */
export function LatestSessionRedirect() {
  const { selectedStreamer } = useStreamer();
  const { data, loading, error } = useQuery<SessionsQuery, SessionsQueryVariables>(SESSIONS_QUERY, {
    variables: { streamer: selectedStreamer, limit: 1 },
    fetchPolicy: "network-only",
  });

  if (loading) {
    return (
      <div className="page">
        <div className="empty-state">Finding the latest stream...</div>
      </div>
    );
  }
  if (error) {
    return (
      <div className="page">
        <div className="error-state" role="alert">
          Failed to find the latest stream: {describeError(error)}
        </div>
      </div>
    );
  }
  const latest = data?.sessions[0];
  if (!latest) {
    return (
      <div className="page">
        <div className="empty-state">
          No streams have been recorded for @{selectedStreamer} yet. A report appears once capture has run.
        </div>
      </div>
    );
  }
  return <Navigate to={`/sessions/${latest.id}`} replace />;
}
