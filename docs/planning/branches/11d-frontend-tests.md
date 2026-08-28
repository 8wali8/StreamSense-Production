# hardening/11d-frontend-tests

Priority 10 (last frontend branch) from `docs/planning/production-hardening.md`: tests that exercise the real data path, and lint, formatting, and coverage gates in CI. Stacked on `hardening/11c-frontend-split`.

## What was wrong

- Every component test replaced `@apollo/client/react` with `vi.fn()`s and fed the component a hand-built `{ loading, data, error }`. That tests the JSX against a shape the test author guessed, not against what Apollo returns from a gateway response (cache normalisation, `__typename`, error shapes, subscription delivery). The `App` test also stubbed all eight child components and global `fetch`, so it could not fail if the console stopped fetching anything.
- ESLint ran without type information, so an un-awaited promise, an unsafe `any`, or a wrong enum comparison passed; there were no accessibility rules; formatting was by hand (mixed two- and four-space files); coverage was never measured.

## What changed

- **`src/test/msw.ts`**: one MSW `setupServer` for the run, started in `setup.ts` with `onUnhandledRequest: "error"`, plus helpers `graphqlData`, `graphqlPending`, `graphqlError`, `graphqlResolver`, `restJson`, `restProblem`, `restResolver`. Handlers are added per test with `server.use` and reset after each.
- **`src/test/apollo.tsx`**: `renderWithApollo` renders under a real `ApolloClient` whose HTTP link goes to `/graphql` (answered by MSW) and whose subscriptions go through Apollo's `MockSubscriptionLink`; `emitSubscription` pushes events. The provider is a `wrapper`, so `rerender` keeps it. `src/test/fixtures.ts` holds sample events with `__typename`, which the cache needs to return complete data.
- **`setup.ts`** also drops the abort signal above MSW's interceptor: vitest's jsdom environment swaps in jsdom's `AbortController` while `fetch` stays Node's, which rejects a signal from another realm. Signal and timeout behaviour is still covered by `api-client.test.ts` with a stubbed fetch, the one place `fetch` is mocked, on purpose.
- **Every component and hook test rewritten** against the network edge: panels (loading via an unresolved handler, history, empty, GraphQL error, live subscription event on top of history, fallback data), status pills (including a 503 problem detail surfacing as the tooltip), `SegmentationPreview` (request body captured in the handler, problem detail in the alert, disabled button until a frame ref exists), `useLiveFeed` (real client, per-streamer history from the handler), and `App` as an integration test: the whole console with all sixteen handlers, the redbull replay transcript kept visible after loading, the runtime status line, a failed `POST /api/chat/twitch/channels` reported without losing the console, and roster switching versus sponsor-only updates counted through the handlers. Interactions use `@testing-library/user-event`. No test mocks `@apollo/client/react` or a sibling component any more.
- **ESLint**: `tseslint.configs.recommendedTypeChecked` with `projectService`, `jsx-a11y` recommended, `no-floating-promises` (void allowed), `no-misused-promises` (attributes exempt), inline `type` imports; `generated.ts` ignored. The ten findings it raised were real: two enum comparisons across two `graphql` copies in the WebSocket split (now `isSubscriptionOperation` from Apollo), a promise handed to `setInterval`, async functions without `await`, and a type-only import.
- **Prettier** (`.prettierrc.json`: 120 columns, double quotes, trailing commas, LF) applied to the whole tree, `format:check` in CI.
- **Coverage**: `@vitest/coverage-v8` with floors in `vite.config.ts` (statements 90, branches 80, functions 80, lines 90; measured 94 / 85 / 84 / 94), `npm run test:coverage`, which CI now runs instead of `npm test`. Coverage output is git-ignored.
- **CLAUDE.md** and the frontend README state the rule: mock the network at the edge, never the module graph.

## Deliberately left alone

- Subscriptions are not mocked over a real WebSocket (MSW can, but the `graphql-transport-ws` handshake would be re-implemented in the test for no extra confidence). `MockSubscriptionLink` is Apollo's own tool for this.
- Panels stay in `src/components/`; now that nothing mocks them by path they can move under `features/` whenever a feature grows around them.
- No `strictTypeChecked`; `recommendedTypeChecked` already caught the real bugs and the stricter set is mostly style.
- `api-client.test.ts` keeps its stubbed `fetch` because it asserts on the exact `RequestInit` (headers, signal) the client builds.

## Verification

| Check | Command | Result |
|---|---|---|
| Lint | `npm run lint` | clean (type-aware + jsx-a11y) |
| Format | `npm run format:check` | clean |
| Type-check and bundle | `npm run build` | OK |
| Generated types current | `npm run codegen:check` | exit 0 |
| Tests with coverage floors | `npm run test:coverage` | 16 files, 61 tests passed; statements 94.4 %, branches 84.9 %, functions 84.4 %, lines 94.4 %, all floors met |
| No module mocks left | `grep -rl "vi.mock(\"@apollo" src` | none (`api-client.test.ts` is the only `fetch` stub) |
| Workflow syntax | `actionlint .github/workflows/ci.yml` | OK |
| Docker image | `docker build frontend/` | builds |

## Manual checks for the reviewer

1. Break a handler on purpose (return `{ data: { recentSentiment: null } }` from `graphqlData("RecentSentiment", …)` in `SentimentPanel.test.tsx`): the test fails on the rendered output, which the old mocked test could not do.
2. Add `fetch("/api/anything")` to a component and run its test: MSW fails it with an unhandled-request error.
3. `npx vitest run --coverage` then open `frontend/coverage/index.html` for the per-file report; `useConsoleFeeds.ts` and `StreamFrame.tsx` are the largest partly covered files.
4. In CI, a pull request that lowers coverage below a floor or leaves a file unformatted fails `frontend-checks`.

## Follow-ups

- Move the panels under `features/` and give `useConsoleFeeds` a subscription-driven test through `emitSubscription`.
- Surface `ApiError.problem.detail` and GraphQL `extensions.code` in every panel's error state (the status pills already show the detail as a tooltip).
