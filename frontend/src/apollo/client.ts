import { ApolloClient, HttpLink, InMemoryCache, split } from "@apollo/client";
import { SetContextLink } from "@apollo/client/link/context";
import { GraphQLWsLink } from "@apollo/client/link/subscriptions";
import { getMainDefinition } from "@apollo/client/utilities";
import { createClient } from "graphql-ws";
import type { ClientOptions } from "graphql-ws";

type BrowserLocation = Pick<Location, "protocol" | "host">;
type TokenStorage = Pick<Storage, "getItem">;
type Headers = Record<string, string>;

export function makeWsUrl(location: BrowserLocation = window.location): string {
    const isHttps = location.protocol === "https:";
    const wsProtocol = isHttps ? "wss" : "ws";
    return `${wsProtocol}://${location.host}/graphql`;
}

export function buildConnectionParams(storage: TokenStorage = window.localStorage): Headers {
    const token = storage.getItem("streamsense.authToken");
    if (!token) {
        return {};
    }

    return {
        Authorization: `Bearer ${token}`,
    };
}

// Same token source as the WebSocket connectionParams, so both transports stay in lockstep.
export function buildAuthHeaders(previousHeaders: Headers = {}, storage: TokenStorage = window.localStorage): Headers {
    return { ...previousHeaders, ...buildConnectionParams(storage) };
}

export function createAuthLink(storage: TokenStorage = window.localStorage): SetContextLink {
    return new SetContextLink((previousContext) => ({
        headers: buildAuthHeaders(previousContext.headers as Headers | undefined, storage),
    }));
}

export function buildWsClientOptions(
    location: BrowserLocation = window.location,
    storage: TokenStorage = window.localStorage
): ClientOptions {
    return {
        url: makeWsUrl(location),
        keepAlive: 15000,
        retryAttempts: Infinity,
        shouldRetry: () => true,
        retryWait: async (retries) => {
            const delayMs = Math.min(1000 * 2 ** retries, 10000);
            await new Promise((resolve) => window.setTimeout(resolve, delayMs));
        },
        connectionParams: () => buildConnectionParams(storage),
    };
}

const httpLink = createAuthLink().concat(
    new HttpLink({
        uri: "/graphql",
    })
);

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
