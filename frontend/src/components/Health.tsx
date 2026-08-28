import { useQuery } from "@apollo/client/react";
import type { HealthQuery } from "../graphql/generated";
import { HEALTH_QUERY } from "../graphql/queries";

export function Health() {
  const { data, loading, error } = useQuery<HealthQuery>(HEALTH_QUERY, {
    fetchPolicy: "no-cache",
  });

  if (loading) return <div className="health-pill">Health: loading...</div>;
  if (error) return <div className="health-pill">Health: error - {error.message}</div>;

  return <div className="health-pill">Health: {data?.health ?? "(no data)"}</div>;
}
