import { ApolloClient, type ApolloLink, HttpLink, InMemoryCache, split } from "@apollo/client";
import { ApolloProvider } from "@apollo/client/react";
import { MockSubscriptionLink } from "@apollo/client/testing";
import { isSubscriptionOperation } from "@apollo/client/utilities";
import { render, type RenderOptions, type RenderResult } from "@testing-library/react";
import type { ReactElement, ReactNode } from "react";

export type ApolloTestContext = {
  client: ApolloClient;
  /** Push subscription events into the components under test. */
  subscriptions: MockSubscriptionLink;
};

/**
 * A real Apollo client for tests: queries go over HTTP to `/graphql` (answered by MSW) and
 * subscriptions go through a MockSubscriptionLink the test drives with `simulateResult`.
 * Nothing in `@apollo/client/react` is mocked, so hooks behave exactly as in the browser.
 */
export function createTestApollo(): ApolloTestContext {
  const subscriptions = new MockSubscriptionLink();
  const httpLink = new HttpLink({ uri: "http://localhost/graphql" });
  const link = split(({ query }) => isSubscriptionOperation(query), subscriptions as unknown as ApolloLink, httpLink);
  const client = new ApolloClient({ link, cache: new InMemoryCache() });
  return { client, subscriptions };
}

/** Render with a fresh test client. Returns the client context alongside the usual render result. */
export function renderWithApollo(
  ui: ReactElement,
  options?: Omit<RenderOptions, "wrapper">,
): RenderResult & ApolloTestContext {
  const context = createTestApollo();
  // A wrapper (not inline JSX) so `rerender` keeps the provider around the new element.
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <ApolloProvider client={context.client}>{children}</ApolloProvider>
  );
  const result = render(ui, { ...options, wrapper: Wrapper });
  return { ...result, ...context };
}

/** Push one subscription payload to every active subscriber of the mock link. */
export function emitSubscription(context: ApolloTestContext, data: Record<string, unknown>): void {
  context.subscriptions.simulateResult({ result: { data } });
}
