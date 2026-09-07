# hardening/23-frontend-error-surfacing

Item 23 of `docs/planning/production-hardening-followups.md` (follow-ups from branches 09, 11b, 11c, 11d): the console tells the operator what actually failed, the panels live with their features, and the console's feed hook has a subscription-driven test. Stacked on `hardening/22-session-fields-persisted`.

## What was wrong

Branch 09 made every REST service answer with RFC 9457 problem details and the gateway attach a stable `extensions.code` to GraphQL errors, and branch 11b's `ApiError` already parsed the problem body, but every panel still rendered `error.message`: "Failed to load sentiment history: Downstream service unavailable" with no hint which service, and a REST 409 showed as "/api/chat/twitch/channels returned 409: …" with the path in front of the useful part. The panels also still sat in `src/components/` next to the two building blocks, a leftover from when tests mocked them by path, and `useConsoleFeeds`, the hook behind the whole console, had no test that drove a subscription through it.

## What changed

- **`src/lib/errors.ts`**: `describeError(error)` returns one sentence for any failure: the `detail` of an `ApiError`'s problem body when there is one; for a `CombinedGraphQLErrors` the gateway's codes translated (`DOWNSTREAM_UNAVAILABLE` → "<host> is unavailable", `DOWNSTREAM_ERROR` → "<host> answered <status>", `BAD_REQUEST` → "the request was rejected as invalid"), unknown codes keeping their message; otherwise the raw message. Five unit tests.
- **Every user-visible error goes through it**: the sentiment, sponsor, and recommendation panels, the metrics overview, the health pill, the segmentation panel, the polled status pills (their tooltip is now just the detail), and the console's transcript feed (`useConsoleFeeds` describes the transcript query errors). Tests assert the described text (for example "Failed to load sentiment history: a downstream service is unavailable", "chat-service is restarting" as the pill tooltip).
- **Panels moved under `features/`** with `git mv` so history follows: `evidence/` (SentimentPanel, SponsorPanel, RecommendationPanel), `metrics/` (StreamMetricsOverview), `status/` (Health, TwitchIngestionStatus, VideoCaptureStatus), and SegmentationPreview into `console/` next to the `StreamFrame` that renders it. `src/components/` keeps only `MetricCard` and `ErrorBoundary`. Relative imports rewritten; nothing else changed in the moved files.
- **`useConsoleFeeds.test.tsx`**: with the six history queries and the REST transcript fallback answered by MSW, subscription events pushed through `emitSubscription` appear on top of history (chat, sentiment, sponsor detection, and `latestEventAt` follows the newest frame), events for another streamer are ignored, and a transcript query failure surfaces as the described text.
- **README and CLAUDE.md** describe the layout and the rule: never render `error.message` directly.

## Deliberately left alone

- The `TranscriptFeed` and `ChatFeed` components take already-described strings from the hook; they did not change.
- No toast or global error banner: each panel owns its error state, and `ErrorBoundary` still catches render failures.
- Codes are translated in the frontend from the gateway's documented set; if the gateway adds a code, the fallback shows the gateway's message until the map is extended.

## Verification

| Check | Command | Result |
|---|---|---|
| Lint, format, type-check, bundle | `npm run lint`, `npm run format:check`, `npm run build` | clean, clean, OK |
| Tests with coverage floors | `npx vitest run --coverage` | 18 files, 68 tests passed (7 new: `errors.test.ts` ×4 after Prettier merged two, `useConsoleFeeds.test.tsx` ×3); statements 94.4 %, branches 86.3 %, functions 92.7 %, lines 94.4 % |
| Generated types current | `npm run codegen:check` | exit 0 (no operation change) |
| Moves preserved history | `git log --follow frontend/src/features/evidence/SentimentPanel.tsx` | history continues under the old path |

## Manual checks for the reviewer

1. `make up`, then `docker compose stop sentiment-service` and reload the console: the sentiment panel says "Failed to load sentiment history: sentiment-service is unavailable" (the host from the gateway's `DOWNSTREAM_UNAVAILABLE` extension), not a generic message.
2. `docker compose stop chat-service` and change the streamer: the Twitch status pill's tooltip reads the service's problem `detail`, and the runtime status line still counts the failed update.
3. `git diff --stat origin/hardening/22-session-fields-persisted -- frontend/src/features frontend/src/components` reads as renames plus the small `describeError` edits.

## Follow-ups

- Select and show the session fields exposed by branch 22 in the sentiment feed.
