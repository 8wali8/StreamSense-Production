# 01-ops-route: the shell, the tokens, and the operations page

Branch `redesign/01-ops-route`, cut from `redesign/main`. Frontend only. Serves no story directly; it clears the customer pages so stories S1 to S7 can be built without diagnostics in the way.

## What changed

- **Routing.** `react-router` 7. `src/App.tsx` is the router: `/` is the streamer's home (`features/home/HomePage.tsx`), `/ops` is the operator page (`features/ops/OpsPage.tsx`), anything else is a not-found page. `AppRoutes` is mounted under `BrowserRouter` in production and `MemoryRouter` in tests. nginx already served `index.html` for unknown paths, so deep links work in Docker without a config change.
- **Shell and tokens.** `src/components/AppShell.tsx` is the left navigation from the prototype: Home at the top, Operations at the bottom, the current channel between them. `src/index.css` is rewritten on the approved tokens (graphite grounds, on-air red only for live state and the primary action, teal for the sponsor being on screen, green/amber/red only for sentiment and risk). The `--ui-scale` transform, the grid background, and every purple are gone. IBM Plex Sans and Mono are bundled from `@fontsource` so the nginx CSP (`font-src 'self'`) holds; a Google Fonts import would have been blocked.
- **Selection state.** `useStreamerSelection` is provided once by `StreamerProvider` and read anywhere with `useStreamer()`. The selection persists in `localStorage` under `streamsense.selection`, so a reload or a new tab shows the same channel. Runtime switching semantics are unchanged: a new streamer re-points chat, capture, and relevance; the same streamer re-sends relevance only.
- **Moved to `/ops`.** Health, Twitch ingestion, and video capture pills; the streamer and sponsor form (now `ChannelControl`, submit is "Point capture here"); the runtime switching status line; the metrics overview; raw sentiment and detection event lists with every diagnostic field; the segmentation preview. New on `/ops`: `SponsorProfileEditor`, a form over the existing `POST /api/sentiment/relevance/sponsors` with sponsor, aliases, semantic terms, and minimum score.
- **Deleted.** The campaign goal field and its only effect (an extra relevance term), the per-panel streamer inputs and load buttons, the recommendations panel, its GraphQL query, and its fixture (`generated.ts` regenerated), the hard-coded roster with fake owner and risk, the campaign and frame-number overlays on the player, and the raw confidence number on the detection overlay. `App.css` (empty) is gone.
- **Home** keeps the player with detections, the transcript feed, the sponsor-mention feed, and the chat feed until branch 05 rebuilds it around the live strip.
- **Tests.** Per the decision on 2026-09-09 to keep tests minimal until a later pass, `App.test.tsx` holds three tests for the non-obvious logic only: runtime switching (new streamer vs same streamer, shared selection, persistence), sponsor profile field parsing, and stored-selection restore with no diagnostics on home. Moved panels keep their existing tests. `CLAUDE.md` describes the new layout.

## Verification

Run from `frontend/` on 2026-09-09 with Node 24.15.

| Check | Result |
|---|---|
| `npm run test:coverage` | 17 files, 69 tests passed. Statements 91.32%, branches 85.55%, functions 90.78%, lines 91.32% (floors 90/80/80/90) |
| `npm run lint` | clean |
| `npm run format:check` | clean |
| `npm run codegen:check` | clean (recommendations query removed, `generated.ts` regenerated) |
| `npm run build` | `tsc -b` clean, bundle 477 kB JS, 23 kB CSS, fonts under `/assets` |
| Visual, `npm run dev` with no backend | Home and `/ops` render in the new theme with the expected error states; screenshots reviewed |

## Manual checks for the reviewer

1. `make up`, open `http://localhost:3000/`. Home shows `@test` with the player and feeds and nothing diagnostic. Refresh on `/ops` directly: the page loads (nginx fallback).
2. On `/ops`, point capture at `redbull-testing` with sponsor `Red Bull`. The status line reports all three runtime updates; the channel in the sidebar changes; Home follows. Reload: the selection is still `redbull-testing`.
3. Save a sponsor profile with aliases and terms; the sentiment service's relevance log shows the merged profile.
4. The fonts are IBM Plex in Docker (check the network tab: woff2 from `/assets`, no request to Google).
5. Nothing purple anywhere.

## Left for later branches

- Home's live strip, deals, and history: branch 05.
- The segmentation preview on `/ops` loads its own short detections feed to find the latest frame, in addition to the raw detections panel. Two subscriptions to the same topic on one page is acceptable for an operator page; fold them if it ever matters.
- `recommendation-service` still runs in the stack; nothing queries it now. Removal is a separate branch (Compose, k8s, network policies, CI matrix, gateway schema).
