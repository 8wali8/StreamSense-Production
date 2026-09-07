import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterAll, afterEach, beforeAll } from "vitest";
import { server } from "./msw";

let fetchBeforeTests: typeof globalThis.fetch;
const unhandledRequests: string[] = [];

beforeAll(() => {
  // Every HTTP request a test makes must be answered by an MSW handler. "error" rejects the request,
  // but a component that swallows the resulting network error (Apollo, the polling hook) would let
  // the test pass anyway, so unhandled requests are also recorded and failed in afterEach.
  server.listen({ onUnhandledRequest: "error" });
  server.events.on("request:unhandled", ({ request }) => {
    unhandledRequests.push(`${request.method} ${request.url}`);
  });

  // vitest's jsdom environment replaces AbortController/AbortSignal with jsdom's, but `fetch` (and
  // MSW's interceptor around it) is Node's, which rejects a signal from another realm. Requests in
  // tests are answered by MSW and never cancelled, so the signal is dropped above the interceptor;
  // timeout and signal behaviour is covered by api-client.test.ts with a stubbed fetch.
  fetchBeforeTests = globalThis.fetch;
  const interceptedFetch = fetchBeforeTests.bind(globalThis);
  globalThis.fetch = (input, init) => interceptedFetch(input, init ? { ...init, signal: undefined } : init);
});

afterEach(() => {
  cleanup();
  server.resetHandlers();
  if (unhandledRequests.length > 0) {
    const requests = unhandledRequests.splice(0).join(", ");
    throw new Error(`Unhandled request(s) during the test: ${requests}. Add an MSW handler with server.use(...).`);
  }
});

afterAll(() => {
  globalThis.fetch = fetchBeforeTests;
  server.close();
});
