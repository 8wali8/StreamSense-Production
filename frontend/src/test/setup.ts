import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterAll, afterEach, beforeAll } from "vitest";
import { server } from "./msw";

let fetchBeforeTests: typeof globalThis.fetch;

beforeAll(() => {
  // Every HTTP request a test makes must be answered by an MSW handler; anything else fails the test.
  server.listen({ onUnhandledRequest: "error" });

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
});

afterAll(() => {
  globalThis.fetch = fetchBeforeTests;
  server.close();
});
