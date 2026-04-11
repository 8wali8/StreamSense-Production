import { ApolloClient, HttpLink, InMemoryCache, split } from "@apollo/client";
import { GraphQLWsLink } from "@apollo/client/link/subscriptions";
import { getMainDefinition } from "@apollo/client/utilities";
import { createClient } from "graphql-ws";
import type { ClientOptions } from "graphql-ws";

type BrowserLocation = Pick<Location, "protocol" | "host">;
type TokenStorage = Pick<Storage, "getItem">;

export function makeWsUrl(location: BrowserLocation = window.location): string {
    const isHttps = location.protocol === "https:";
    const wsProtocol = isHttps ? "wss" : "ws";
    return `${wsProtocol}://${location.host}/graphql`;
}

export function buildConnectionParams(storage: TokenStorage = window.localStorage): Record<string, string> {
    const token = storage.getItem("streamsense.authToken");
    if (!token) {
        return {};
    }

    return {
        Authorization: `Bearer ${token}`,
    };
}

export function buildWsClientOptions(
    location: BrowserLocation = window.location,
    storage: TokenStorage = window.localStorage
): ClientOptions {
    return {
        url: makeWsUrl(location),
        connectionParams: () => buildConnectionParams(storage),
    };
}

const httpLink = new HttpLink({
    uri: "/graphql",
});

const wsLink = new GraphQLWsLink(createClient(buildWsClientOptions()));

const splitLink = split(
    ({ query }) => {
        const definition = getMainDefinition(query);
        return definition.kind === "OperationDefinition" && definition.operation === "subscription";
    },
    wsLink,
    httpLink
);

export const apolloClient = new ApolloClient({
    link: splitLink,
    cache: new InMemoryCache(),
});
