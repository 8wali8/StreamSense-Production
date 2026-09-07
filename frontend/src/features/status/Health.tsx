import { describeError } from "../../lib/errors";
import { useQuery } from "@apollo/client/react";
import type { HealthQuery, HealthQueryVariables } from "../../graphql/generated";
import { HEALTH_QUERY } from "../../graphql/queries";

export function Health() {
  const { data, loading, error } = useQuery<HealthQuery, HealthQueryVariables>(HEALTH_QUERY, {
    fetchPolicy: "no-cache",
  });

  if (loading) return <div className="health-pill">Health: loading...</div>;
  if (error) return <div className="health-pill">Health: error - {describeError(error)}</div>;

  return <div className="health-pill">Health: {data?.health ?? "(no data)"}</div>;
}
