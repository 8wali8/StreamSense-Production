import { gql } from "@apollo/client";
import { useQuery } from "@apollo/client/react";

type HealthQueryData = {
    health: string;
};

const HEALTH_QUERY = gql`
  query Health {
    health
  }
`;

export function Health() {
    const { data, loading, error } = useQuery<HealthQueryData>(HEALTH_QUERY, {
        fetchPolicy: "no-cache",
    });

    if (loading) return <div>Health: loading…</div>;
    if (error) return <div>Health: error — {error.message}</div>;

    return <div>Health: {data?.health ?? "(no data)"}</div>;
}