import { ApolloClient, HttpLink, InMemoryCache, split } from "@apollo/client";
import { SetContextLink } from "@apollo/client/link/context";
import { GraphQLWsLink } from "@apollo/client/link/subscriptions";
import { isSubscriptionOperation } from "@apollo/client/utilities";
import { createClient } from "graphql-ws";
import type { ClientOptions } from "graphql-ws";
import { graphqlHttpUrl, graphqlWsUrl } from "../config/env";
import { authHeaders, type TokenStorage } from "../lib/auth-token";

type BrowserLocation = Pick<Location, "protocol" | "host">;
type Headers = Record<string, string>;

export function makeWsUrl(location: BrowserLocation = window.location): string {
  return graphqlWsUrl(location);
}

// Same token source as the REST client (src/lib/api-client.ts), so every transport stays in lockstep.
export function buildConnectionParams(storage: TokenStorage = window.localStorage): Headers {
  return authHeaders(storage);
}

export function buildAuthHeaders(previousHeaders?: Headers, storage: TokenStorage = window.localStorage): Headers {
  return { ...(previousHeaders ?? {}), ...buildConnectionParams(storage) };
}

export function createAuthLink(storage: TokenStorage = window.localStorage): SetContextLink {
  return new SetContextLink((previousContext) => ({
    headers: buildAuthHeaders(previousContext.headers as Headers | undefined, storage),
  }));
}

export function buildWsClientOptions(
  location: BrowserLocation = window.location,
  storage: TokenStorage = window.localStorage,
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
    uri: graphqlHttpUrl(),
  }),
);

const wsLink = new GraphQLWsLink(createClient(buildWsClientOptions()));

const splitLink = split(({ query }) => isSubscriptionOperation(query), wsLink, httpLink);

export const apolloClient = new ApolloClient({
  link: splitLink,
  cache: new InMemoryCache(),
});
