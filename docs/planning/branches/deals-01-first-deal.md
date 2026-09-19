# deals/01-first-deal

The new-deal form is the next thing a streamer meets after `console/02-empty-states` (#70), and it was the wrong shape for a first deal: ten fields at once, a placeholder that read as a value, a fee with no currency. Based on `main` at #71. The handoff is `docs/planning/handoffs/first-deal.md`.

## What changed

- **The form asks for what a first deal needs.** `NewDealForm` shows the sponsor (full width), the start and end dates, and the promised streams. The fee, the chat command, the CPM, the host read rate, the tracked link, and the channel point reward sit behind a native `<details>` headed "More settings", closed by default and reachable with the keyboard (the summary takes focus and toggles on Enter or Space; `.new-deal-more` styles it like the console drawer). Every field, the validation, and the request body are as before; this is layout and copy.
- **Copy.** The sponsor's placeholder is "The sponsor's name" instead of "Red Bull". The fee is "Fee in USD (private to you)", and the two rates carry ", USD" (amounts are dollars by assumption; the currency field stays deferred per `fix-01-review-p2s.md`). The chat command's placeholder is "!command" and the tracked link's is "https://", so neither reads as a filled-in value. Under "Ends": "Leave empty for an open-ended deal." Under the summary: what the hidden terms are for.
- **A disabled primary button looks disabled.** `.button-primary:disabled` was never styled, so "Create deal" with an empty sponsor looked live and a click did nothing. It now dims like the secondary button.
- **The import list on a channel with no recordings.** `ImportStreams` said "No recordings inside the deal's dates" when Twitch had none at all, which implied there were some outside them. Now, with nothing listed, it says Twitch has no recordings for the channel and that measured streams join the deal on their own; with recordings outside the dates it points at "Show all recordings".

## Verification

| Check | Command | Result |
|---|---|---|
| Frontend gate | `npm run codegen:check`, `lint`, `format:check`, `test:coverage`, `build` | all pass; 155 tests; statements 89.5 %, branches 84.82 % |
| The form's shape | `npm run dev` on 127.0.0.1:5173 with a local token, no backend | closed: sponsor, dates, promised streams, "More settings", the actions; open: the six terms in two columns; "Create deal" dimmed until a sponsor is typed |
| The owner's channel on the live site | `GET /api/analytics/streams/8wali8/vods`, `/deals`, `/api/video/capture/status` from the signed-in tab | no recordings, no deals, capture `DISABLED` with no channels (the home page says the channel is not being measured) |

The existing form test in `DealPage.test.tsx` now checks that the fee is hidden until "More settings" is opened and fills the fee and command after opening it.

## The live walk

Signed in as the owner on `https://streamsense.dev/`, the home page's "New deal" was opened and the form (still the old shape on the VM, which runs #70) was filled with sponsor "StreamSense test deal", 1 promised stream, fee 100, command `!sstest`, starting today. "Create deal" was left for the owner: creating the deal writes to the live site. Everything after it (the deal page with no streams, the empty import list, the share control) is still to be walked once the deal exists; on the code side the empty import list was the one rough edge found.

## What to check by hand

1. After the deploy, "New deal" on `https://streamsense.dev/`: four fields and "More settings", closed; Tab to the summary and press Enter; the six terms open in two columns. "Create deal" is dimmed until a sponsor is typed.
2. Click "Create deal" on the pre-filled test deal (or one of your own). The deal page opens with "No streams inside the deal yet." and "Earlier streams on Twitch" saying Twitch has no recordings for the channel.
3. On the deal page: "Share" mints a link; "Copy" and "Revoke" work; the header shows the dates, "0 of 1 streams", and `!sstest`.
4. View as `xqc` and create a deal starting before the eight Helix-only sessions: the deal page should fold those sessions in (the deal's dates decide), each with viewers but no on-screen time or mentions.
