import { ApolloClient, HttpLink, InMemoryCache, split } from "@apollo/client";
import { GraphQLWsLink } from "@apollo/client/link/subscriptions";
import { getMainDefinition } from "@apollo/client/utilities";
import { createClient } from "graphql-ws";

function makeWsUrl(): string {
    const isHttps = window.location.protocol === "https:";
    const wsProto = isHttps ? "wss" : "ws";
    // Same host/port as the frontend (nginx), which proxies /graphql to api-gateway
    return `${wsProto}://${window.location.host}/graphql`;
}

const httpLink = new HttpLink({
    uri: "/graphql",
    // If you ever need cookies/auth later:
    // credentials: "include",
});

const wsLink = new GraphQLWsLink(
    createClient({
        url: makeWsUrl(),
        // optional but helpful
        retryAttempts: Infinity,
        shouldRetry: () => true,
        // Keep it simple: automatic reconnect
        // (graphql-ws handles graphql-transport-ws protocol)
    })
);

const splitLink = split(
    ({ query }) => {
        const def = getMainDefinition(query);
        return def.kind === "OperationDefinition" && def.operation === "subscription";
    },
    wsLink,
    httpLink
);

export const apolloClient = new ApolloClient({
    link: splitLink,
    cache: new InMemoryCache(),
});