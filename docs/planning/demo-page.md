# The public demo

Written 2026-09-13. A person who reaches the sign-in page without an account should be able to see what StreamSense does, on real-looking data, without getting any access to the service. This is the plan; the branch note under `docs/planning/branches/` records what was built and how it was verified.

## What it is

A `/demo` route on the console, linked from the sign-in page ("Explore the demo, no account needed"). It is the real console, every page of it, running on a **sealed snapshot**: a curated export of one channel's data (the Red Bull replay: a deal, its streams, the session reports with their timelines, the mention feeds, the chat and transcript feeds, the detections) bundled with the frontend as a static file. In demo mode the console never talks to the gateway: an Apollo link answers every query from the snapshot, subscriptions replay the snapshot's live events on a timer so the console moves, and the REST reads (ingest status, capture status, the transcript feed) come from the snapshot too. Nothing the visitor does sends a request to the service.

The pages themselves are unchanged: the streamer home with the live strip, player, mention feed, deals, and history; the deal page with totals, trend, streams, and the recordings list; the session report with its timeline, best and weakest moment, the value strip, and the four detail pages. That is the point: the demo is the product, not a brochure. Around it: a banner that says whose data it is and that it is a snapshot, a short introduction at the top of the demo home explaining what a sponsor sees (proof of performance for one deal: logo exposure, brand mentions with sentiment, risk near the brand, media value against the fee), and a "Sign in with Twitch" call to action in the sidebar where the access panel normally sits.

## Why a snapshot, not a demo account

A demo account or demo token on the real gateway would mean public, anonymous traffic against the live services: rate limits to tune, a role to design, a database that anyone can query, and a demo that looks empty whenever nobody is streaming. A snapshot has none of that. The security property is by construction: there is no credential, the gateway is never called, and `/demo` cannot reach anything a signed-in user can. The trade is that the demo is a moment in time, which is what a demo should be: curated, always looking its best, and the same for everyone. The snapshot is refreshed by running the exporter against a stack with good data, reviewing the file, and committing it.

## How it fits the code

- **Routing.** `App` decides demo mode from the path (`/demo` or `/demo/…`) before mounting the router, and mounts the same `AppRoutes` under a `BrowserRouter` with `basename="/demo"`, so every existing `<Link to="/sessions/7">` lands inside the demo. `AccessGate` lets demo paths through without a token. nginx already serves `index.html` for unknown paths.
- **Data.** `src/demo/snapshot.json` (lazy-loaded, so the console bundle does not grow) holds one document per GraphQL operation the pages use, keyed by operation name and the variables that matter (streamer, sponsor, session id, deal id), plus the REST reads. `src/demo/demo-link.ts` is an Apollo link that resolves from it; for subscriptions it emits the snapshot's recent events one by one on a timer, newest last, so feeds tick. `src/lib/api-client.ts` answers REST reads from the snapshot in demo mode and refuses writes with a friendly error; the write controls (new deal, share link, import, the operations page) are not rendered in demo mode in the first place.
- **Who the visitor is.** A `DemoProvider` pins the streamer selection to the snapshot's channel and exposes `useDemo()`; `AppShell` shows the banner and the sign-in call to action, hides Operations; `LoginPage` gets the demo link.
- **The snapshot.** `tools/demo/export_snapshot.py` runs the same operations the console runs against a stack (local Compose with the replay, auth off, or the VM with a token) and writes the file, pseudonymising chat usernames and dropping anything that is not shown (event ids stay because the cache keys on them). It is deterministic given the data, so a refresh is a reviewable diff.

## Order of work

1. The exporter and a first snapshot from the local replay stack (the Red Bull deal, two or three streams with reports).
2. Demo mode in the console: routing, the link, the REST branch, the provider, the hidden controls.
3. The chrome: banner, introduction, call to action, and the sign-in page link.
4. Tests: the demo link answers the operations it holds and errors on the ones it does not; `AccessGate` passes `/demo`; the demo home renders from the snapshot with the write controls absent.
5. Deploy, then open `/demo` in a private window and walk every page.

## Not in this

- No demo of the operations page or of channel switching; those are the operator's, and a demo that steers a pipeline is not a demo.
- No synthetic data. The snapshot is real replay data, curated; if a page needs data the replay does not produce, the replay is the thing to improve.
- No tracking of demo visitors.
