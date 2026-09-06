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

/** The operation name and variables of the subscription the component most recently opened. */
export function currentSubscription(context: ApolloTestContext): {
  operationName: string;
  variables: Record<string, unknown>;
} {
  const operation = context.subscriptions.operation;
  if (!operation) {
    throw new Error("no subscription is active on the mock link");
  }
  return { operationName: operation.operationName ?? "", variables: operation.variables };
}

/**
 * Push one subscription payload to every active subscriber of the mock link. When `expected` is
 * given, the active subscription must match it first, so a hook that forgot its variables or kept
 * the previous streamer's cannot receive the event and pass anyway.
 */
export function emitSubscription(
  context: ApolloTestContext,
  data: Record<string, unknown>,
  expected?: { operationName?: string; variables?: Record<string, unknown> },
): void {
  if (expected) {
    const active = currentSubscription(context);
    if (expected.operationName !== undefined && active.operationName !== expected.operationName) {
      throw new Error(`active subscription is ${active.operationName}, expected ${expected.operationName}`);
    }
    if (expected.variables !== undefined) {
      const actual = JSON.stringify(active.variables);
      const wanted = JSON.stringify(expected.variables);
      if (actual !== wanted) {
        throw new Error(`active subscription variables are ${actual}, expected ${wanted}`);
      }
    }
  }
  context.subscriptions.simulateResult({ result: { data } });
}
