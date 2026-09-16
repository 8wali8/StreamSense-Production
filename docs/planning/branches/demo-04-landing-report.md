# demo/04-landing-report

`/demo` opened on the streamer home. The demo is past streams, so the live strip and the console
under it have nothing to show, and the first thing a visitor saw was the emptiest page in the
product. It now opens on the newest session report, about the sponsor the snapshot is a deal for:
`/demo/sessions/47?sponsor=Red%20Bull` for the snapshot committed today.

## What changed

- **`src/demo/mode.ts`:** `DEMO_LANDING` (`sessions/latest?sponsor=<DEMO_SPONSOR>`) and
  `openDemoLanding(win)`, which rewrites a bare `/demo` (or `/demo/`) to it with
  `history.replaceState` and leaves every deeper address alone.
- **`src/App.tsx`:** the demo branch calls it before mounting the router, next to where demo mode is
  already decided from the path.
- **`src/features/session/LatestSessionRedirect.tsx`:** the redirect carries the incoming query
  through, so `/sessions/latest?sponsor=X` lands on the newest report *about X*. The sidebar's
  "Session report" link sends no query and is unchanged.

## Why a rewrite, not a redirect route

Routing the index to a redirect component was the shorter change, and it breaks the sidebar: "Home"
points at `/`, which would have bounced straight back to the report, so the home page — the deals
panel and the history with its track record — would have been unreachable in the demo. Rewriting the
address once per load, the way `mode.ts` already decides demo mode once per load, lands the visitor
on the report and leaves `/` a page you can navigate to.

Going through `sessions/latest` rather than naming session 47 means the snapshot can be re-exported
with a newer stream and nothing here needs editing; the id in the address comes from the snapshot's
own `Sessions` answer.

## Verification

| Check | Result |
|---|---|
| `vitest run --coverage` | 147 tests, floors held; two new in `App.test.tsx` |
| `prettier --check`, `eslint`, `tsc -b`, `vite build` | clean |
| Both new tests against the unchanged code | fail (no report heading; `/demo/` left as it was) |

The landing test walks the whole chain the browser does: the window is put at `/demo`,
`openDemoLanding` runs, and the routes are mounted at whatever the address became — so it covers the
rewrite, the `sessions/latest` hop, and the sponsor arriving in the report's query variables
(`{ sessionId: "47", sponsor: "Red Bull" }`).

## Deliberately left alone

- **The banner's "Open the latest report" action.** Redundant on the landing page itself, still the
  way back from the deal page and the home page.
- **The sidebar's "Session report" link.** It carries no sponsor, in the demo as everywhere else, so
  it shows the channel's unattributed figures. Making the shell's navigation demo-aware to fix that
  is a bigger change than this one, and it is how the link already behaved.
- **The snapshot.** Unchanged; this branch only picks where the demo opens.
