# demo/01-sealed-snapshot

The public demo from `docs/planning/demo-page.md`: the whole console at `/demo`, on a sealed snapshot of one channel, reachable from the sign-in page without an account and without ever touching the gateway. Based on `main` at 0d9c784 (the Codex P2 fixes). Requested by the owner 2026-09-13.

## What changed

**The snapshot.** `tools/demo/export_snapshot.py` runs the console's own GraphQL operations (read from `frontend/src/graphql/queries.ts`, with `__typename` added the way Apollo's cache does) and the REST reads the pages make against a gateway, for one channel and one sponsor, and writes `frontend/src/demo/snapshot.json`: one recorded answer per operation and variable set, the REST bodies, and a chat feed derived from the sentiment feed (the live chat has no history query). Chat usernames are replaced by stable pseudonyms (`ivory_falcon19`); sessions shorter than ten minutes are dropped everywhere they appear; the output is sorted and deterministic, so a refresh is a reviewable diff. The committed snapshot is the Red Bull replay channel with a Red Bull deal: four streams (1h 12m to 11h 51m), 9,302 mentions, 2h 31m on screen, media value against an $800 fee, and full timelines. 1.7 MB on disk, 170 KB over the wire, loaded only on `/demo`.

**Demo mode in the console** (`src/demo/`). `mode.ts` decides demo mode from the path once per page load. `main.tsx` gives the demo its own Apollo client on `demo-link.ts`: queries are answered from the snapshot (`pickEntry` matches on every recorded variable except `limit`, so `sponsor: null` and `sponsor: "Red Bull"` stay apart), unknown operations error, and each subscription replays its feed's events oldest first every four seconds so the console moves. `src/lib/api-client.ts` answers REST reads from the recorded bodies and refuses writes with "the demo is read-only". `App` mounts the same `AppRoutes` under a router with `basename="/demo"`, so every existing link stays inside the demo; the access gate is not involved. `readStoredSelection` pins the channel and sponsor. The write controls are not rendered: no new deal, no share control, no import panel, no Operations link or route.

**The chrome** (`src/demo/DemoChrome.tsx`). A banner on every page saying whose data it is and that nothing is live; an introduction at the top of the demo home that says, in the product's terms, what a sponsor is shown and links to the deal and the latest report; a sign-in call to action in the sidebar footer where the access panel sits; and on the sign-in page, "No account yet? Explore the demo".

**Two bugs the export found, fixed on `main`'s code.** A capture session that opened and closed on the same millisecond made `sessionSummary` fail with "from must be before to", and one such session made the whole deal summary fail; `SessionSummaryService` now gives every session at least a millisecond of window. And a page of range events for a busy stream exceeded WebClient's 256 KB default buffer, which failed `sponsorMoments` for the long sessions with "downstream error status=200"; the shared WebClient customizer allows 4 MB and the timeline pages at 500 events.

## The Codex review (two P1, four P2, all fixed)

- **Share tokens in the snapshot.** The exporter nulls every `shareToken` field: a deal's share link is a credential, and the snapshot is public. The committed one had none, but a refresh after a share link was minted would have carried it.
- **Paging capacity.** Cutting the page from 2,000 to 500 while `RangePaging.MAX_PAGES` stayed at 25 would have cut a timeline off at 12,500 events; the cap is now 100 pages, 50,000 events, with the comment tying the two numbers together.
- **Deal totals versus a filtered session list.** Rather than recompute totals in the exporter, it refuses to export when a filter would hide a session that belongs to a deal, and says to curate the source data instead; `--max-sessions` defaults to keeping all.
- **A share token left in the tab.** Demo mode ignores session storage's share token, so `/demo` never renders the reduced shared shell.
- **Feeds that never ticked.** The replayed events carried the ids the feeds already had in their history, so `useLiveFeed` dropped every one. Each replayed event now gets a fresh id and the clock set to now (`asLiveEvent`).
- **Zero-length sessions on the timeline.** The gateway's `sponsorMoments` window gets the same one-millisecond minimum as the summary.

Also from the second export: the operations page's trailing-window query moved with the clock and made every refresh a 300-line diff, so it is no longer exported (the demo does not show it), and the exporter writes LF on every platform.

## Why this shape

A demo account on the live gateway would mean public, anonymous traffic against the services and a demo that looks empty when nobody streams. Here there is no credential and no request: the security property holds by construction, and the demo always looks its best. The snapshot is real replay data, curated: the only synthetic part is the viewer counts, which the replay cannot produce and which the media-value arithmetic needs, inserted into the local database as one sample a minute before the export (a smooth ramp with a mid-stream peak around 2,400).

## Deliberately left alone

- **No operations page and no channel switching** in the demo.
- **The player** is the Twitch embed of the replay's recording (the console already maps the replay alias to its VOD), which is public.
- **No visitor tracking.**
- **The snapshot is not regenerated in CI**; it is a reviewed file. The exporter's usage is in its docstring.

## Verification

| Check | Command | Result |
|---|---|---|
| Frontend gate | `npm run codegen:check`, `lint`, `format:check`, `test:coverage`, `build` | all pass; 117 tests; statements 87.46 %, branches 81.01 %; the snapshot builds as its own 665 KB chunk (89 KB gzipped) loaded only on `/demo` |
| Demo link | `demo-link.test.ts` | entry matching (limit ignored, sponsor distinguished, unknown ids refused), a query answered from the snapshot and an unknown one refused, a subscription replaying oldest first around the loop, the path rule |
| Gateway and analytics-service | `mvn -pl api-gateway,analytics-service -am verify` (Maven 3.9, JDK 21 in Docker) | `BUILD SUCCESS`: api-gateway 122 tests and analytics-service 43, JaCoCo floors met, Spotless and ArchUnit clean; the new tests cover the zero-length session (`SessionSummaryTest`) and a 1.2 MB page decoding (`DownstreamWebClientTimeoutTest`) |
| In a browser | `npm run dev`, Chrome on `/demo`, `/demo/deals/3`, `/demo/sessions/47?sponsor=Red%20Bull` | the home renders the banner, the introduction, the offline strip, the player on the recording, the pseudonymised mention feed, the deals and the history; the deal page shows the totals, the trend, and the four streams with no share or import control; the session report shows the four tiles, the full timeline, best and weakest moment, and the value strip. The network log shows no request to `/graphql`, `/api/`, or `/auth/`: only the page's own modules |

## What to check by hand

1. After the deploy, open `https://streamsense.dev/demo` in a private window: no sign-in page, the banner, the console on the Red Bull data; click through Home, the deal, a report and its four detail pages.
2. Open the developer tools' network tab while doing so: nothing to `/graphql`, `/api/`, or `/auth/`.
3. From the sign-in page, "Explore the demo" opens it; from the demo, "Sign in with Twitch" leaves it and starts a real sign-in.
4. Type `https://streamsense.dev/demo/ops`: "There is no page here".
