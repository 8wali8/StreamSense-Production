# hardening/11c-frontend-split

Priority 10 (third frontend branch) from `docs/planning/production-hardening.md`: `App.tsx` becomes a shell, the live console becomes a feature folder, and the "history plus live events" pattern becomes one tested hook. Stacked on `hardening/11b-frontend-api-layer`. This is the large-diff branch of the series; the console is meant to render identically.

## What was wrong

- `App.tsx` was 790 lines: helpers, the streamer selection state machine, runtime switching, the roster, the player with overlays, three feeds, and seven copies of the same "keep live subscription events in `useState`, de-duplicate against the history query, cap the list" block. `SentimentPanel` and `SponsorPanel` carried the same block again with their own `MetricCard`, `formatTime`, and label-colour helpers, as did `RecommendationPanel` and `StreamMetricsOverview`.
- Three components (`TranscriptPanel`, `TranscriptSentimentPanel`, `pages/LiveChat`) and their tests were never rendered by anything.
- One rendering error anywhere blanked the whole page.

## What changed

- **`src/hooks/useLiveFeed.ts`**: `useLiveFeed` (history query, optionally polled, plus a subscription whose events sit on top, de-duplicated by id and against history, capped, filtered by `accept`, buffer keyed by `resetKey` and cleared in an effect when the key changes, so switching streamer starts clean and switching back does not resurrect the old buffer) and `useLiveEvents` for subscription-only feeds such as raw chat. Both go through Apollo's `useQuery`/`useSubscription`, so the existing tests that mock `@apollo/client/react` keep working. Four unit tests cover ordering, de-duplication, the accept filter, the reset key, and the options passed to Apollo.
- **`src/features/console/`**: `LiveStreamConsole` composes `StreamFrame` (player, overlays, segmentation preview), `TranscriptFeed`, `SponsorSentimentFeed`, and `ChatFeed`; `useConsoleFeeds` owns the six live feeds, the raw chat events, and the REST transcript fallback; `transcript-lines.ts` is the pure segment-plus-sentiment merge with four unit tests. Subscriptions now receive only their own variables (the old code passed the query's `limit` too).
- **`src/features/streamer/`**: `streamer.ts` (handle normalisation, sponsor profile parsing, Twitch player URL, roster data, tested), `useStreamerSelection` (the selection state and the runtime switching that used to be inline in `App`), `StreamerControls`, `Roster`.
- **`src/lib/format.ts`** and **`src/components/MetricCard.tsx`** replace the per-file copies of `formatTime`, `formatScore`, `percent`, the sentiment class/colour helpers, `matchedContext`, `mergeById`, and `MetricCard`.
- **`src/components/ErrorBoundary.tsx`** wraps the console, the metrics overview, and each evidence panel; a failure renders a named inline error with a retry button instead of a blank page (tested). It already earned its keep during this branch: a bad import in the console rendered as "The live console failed to render" while the rest of the page stayed up.
- **`App.tsx`** is 88 lines of layout. `SentimentPanel` and `SponsorPanel` use `useLiveFeed`; `RecommendationPanel` and `StreamMetricsOverview` use the shared `MetricCard`. The three unused components and their tests are deleted.
- **CLAUDE.md** and the frontend README describe the layout and the rule: never hand-roll a subscription buffer, keep list logic in a pure tested function, delete unrendered components.

## Deliberately left alone

- Subscription events are not written into the Apollo cache (`subscribeToMore` / cache updates). That would change the render path of every panel and the tests that assert on it; `useLiveFeed` gives one implementation now and can be switched to a cache-backed one behind the same interface later.
- Panels stay in `src/components/` (not under `features/`) so the existing `vi.mock("./components/...")` paths in tests still resolve. Moving them is a branch 11d job once MSW replaces those mocks.
- The console's live chat buffer now resets when the streamer changes instead of keeping other streamers' messages in memory and filtering them out at render. Same visible behaviour, less retained state.
- CSS is untouched; every class name is preserved.

## Merge note

The user's main checkout has uncommitted edits to `frontend/src/App.tsx` and `App.test.tsx`. `App.tsx` is rewritten here, so those edits will not merge automatically: re-apply them against the feature files (`features/console/*` for the console, `features/streamer/*` for the controls and roster). `App.test.tsx` is unchanged on this branch and still passes.

## Verification

| Check | Command | Result |
|---|---|---|
| Lint | `npm run lint` | clean |
| Type-check and bundle | `npm run build` | OK |
| Tests | `npx vitest run` | 16 files, 56 tests passed (13 new: `useLiveFeed` ×4, `transcript-lines` ×4, `streamer` ×3, `ErrorBoundary` ×2; 6 deleted with the unused components) |
| Existing console test | `App.test.tsx` "keeps all transcript visible after loading redbull replay" | passes unchanged against the split console |
| Generated types current | `npm run codegen:check` | exit 0 |
| Docker image | `docker build frontend/` | builds |
| Size | `wc -l` | `App.tsx` 790 → 88 lines; net 57 insertions, 1366 deletions across the rewritten and deleted files, plus 1109 lines in the new feature, hook, and helper files (about 500 of them tests) |

## Manual checks for the reviewer

1. `make up`, open `http://localhost:3000` on `main` and on this branch: same layout, same panels, same status pills. Switch streamer via the roster and via the form; the runtime status line reads the same as before.
2. With the VOD replay alias running, the "All transcript" feed shows sponsor-tagged lines and the sponsor sentiment feed fills, as before.
3. Temporarily throw inside `TranscriptFeed` (or block `/graphql` in devtools and reload): only that section shows "failed to render" / its loading state; the player, metrics, and evidence panels keep working.
4. `git diff origin/main --stat -- frontend/src`: no CSS changes.

## Follow-ups (branch 11d)

- MSW handlers and a real `ApolloProvider` in tests, replacing every `vi.mock("@apollo/client/react")`; then move the panels under `features/`.
- Type-aware ESLint, jsx-a11y, Prettier, coverage thresholds.
- Surface `ApiError.problem.detail` and GraphQL `extensions.code` in the panel error states.
