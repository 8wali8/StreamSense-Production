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

    if (loading) return <div className="health-pill">Health: loading...</div>;
    if (error) return <div className="health-pill">Health: error - {error.message}</div>;

    return <div className="health-pill">Health: {data?.health ?? "(no data)"}</div>;
}
