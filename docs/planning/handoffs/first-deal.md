# Handoff: a streamer's first deal

Written 2026-09-19 for a fresh session. Read `CLAUDE.md` first for the repository's conventions, `docs/contracts/deals.md` for the deal model, and `docs/planning/branches/console-02-empty-states.md` for the walkthrough this continues. A sibling session is retiring access links at the same time (`retire-access-links.md`); the two do not share files beyond one sentence each in `CLAUDE.md`.

## The problem

On 2026-09-18 the owner signed in with Twitch on the live site and, using "View as a streamer", walked the path a new streamer takes: an empty home, start measuring, create a deal, import a recording, open the report. The empty states were fixed on `console/02-empty-states` (#70). The deal was never created: the new-deal form is the next thing a streamer meets, and it is the wrong shape for a first deal. Everything after it (the deal page, the recording import, the report inside a deal) has been checked only on the demo's snapshot data, never live as a streamer.

## What exists today

- **The form** is `frontend/src/features/deals/NewDealForm.tsx`, rendered inline in the Deals panel (`DealsPanel.tsx`) when "New deal" is pressed, and it posts to analytics-service through `src/api/deals.ts`. Its fields, in order: Sponsor, Promised streams, Starts, Ends, Fee (private to you), Chat command, CPM per 30-second equivalent, Host read rate per 1,000 listeners, Tracked link, Channel point reward, then "Create deal". All ten are shown at once. The sponsor field's placeholder is "Red Bull", which reads as a filled-in value; the fee's placeholder is "2500" with no currency anywhere (the report later prints "$0.00"). Starts defaults to today.
- **What a deal does.** The home page follows the deal's sponsor from its start date (`features/home/home-sponsor.ts`, branch note `home-01-deal-sponsor.md`): exposure, mentions, and the report are all about that sponsor. analytics-service's `DealService` points sentiment-service's relevance at the sponsor when a deal is created. The deal page (`DealPage.tsx`) shows totals against the fee, the per-stream trend (`trend.ts`), the streams inside the deal, the share control (`ShareControl.tsx`, a read-only link per deal), and the owner's list of Twitch recordings to import (`ImportStreams.tsx`, helpers in `import-streams.ts`; the list comes from Helix through analytics-service, the import replays the recording through chat-service and video-capture-service, see `docs/contracts/sessions.md`).
- **Who may create one.** A streamer signed in with Twitch creates deals for their own channel only (gateway `AuthScope`, analytics `ChannelScopeFilter`); an operator for any channel. The owner (`8wali8`) is the only operator on the live site.
- **The demo** (`https://streamsense.dev/demo`) shows what a finished deal looks like: one Red Bull deal over four sessions, exported from the local stack (`docs/planning/demo-page.md`). It is the reference for the deal page, not for the form (the demo has no write controls).
- **Live state.** The VM runs `main` at `eff6d05` (#70). The owner's channel has measurement switched on and no streams, no deals. Viewing as `xqc` shows a channel with eight Helix-only sessions (viewer counts, no chat or video) and no deal.

## What is unknown

- Whether a streamer with no recordings sees a sensible empty import list, and whether a streamer with recordings can import one from the deal page end to end (the replay takes as long as the recording; a short one is the test).
- Whether creating a deal on a channel with existing Helix-only sessions folds those sessions into the deal correctly (the deal's dates decide, per `docs/contracts/deals.md`).
- The currency question is deferred product work (`docs/planning/branches/fix-01-review-p2s.md`): amounts are dollars by assumption. Label them as such; do not add a currency field.

## Approach, in order

1. Branch `deals/01-first-deal` from `origin/main` in a worktree. Reshape `NewDealForm.tsx`: sponsor, starts, ends, and promised streams up front; fee, chat command, CPM, host read rate, tracked link, and channel point reward behind a "More settings" disclosure that is closed by default (a `<details>` or a button toggling a section; whichever, keyboard-reachable). A placeholder for the sponsor that cannot be mistaken for a value. The fee labelled as USD. Every field, validation, and the request body stay as they are; this is layout and copy. `DealPage.test.tsx` holds the existing form test; extend rather than restate.
2. Frontend gate: `npm run codegen:check`, `lint`, `format:check`, `test:coverage`, `build`. Look at the form on `npm run dev` (port 5173, or the next free one) with any token in local storage; the backend need not be up to see the form's shape.
3. Walk the path on the live site as the owner: home, "New deal", create a deal on `8wali8` with a clearly named test sponsor, open the deal page, try the import list, open a report from inside the deal. Fix what is rough in `DealPage.tsx`, `ImportStreams.tsx`, `ShareControl.tsx`, and their helpers. Creating the deal is a write to the live site: the auto-mode permission classifier refuses form submissions there, so either the owner clicks "Create deal" or allows it when asked. Do not work around the refusal.
4. Branch note at `docs/planning/branches/deals-01-first-deal.md` (what changed, verification table, what to check by hand), one sentence in the frontend paragraph of `CLAUDE.md` if the form's shape is worth recording, PR, merge on the owner's word, deploy.

## Conventions and workflow to keep

- One branch, one PR, merged with a merge commit (`gh pr merge --merge`) after the owner says so. The owner likes to see a screenshot of visual work before it is pushed. Deploy only after CI on `main` is green: `gcloud compute ssh streamsense-demo --zone us-central1-a --command "sudo streamsense-deploy"`. `gh` and `gcloud` run from WSL (`wsl.exe -e bash -lc '...'`, `~/.local/bin/gh`, from the main checkout under `/mnt/c/...`), not from Git Bash.
- Commits carry the owner's identity (`GIT_AUTHOR_NAME="Ujjawal Prasad"`, `GIT_AUTHOR_EMAIL="ujjawalprasad111@gmail.com"`, committer likewise) and the attribution trailers the session's system reminder gives.
- Never print a token or an access link in chat; redact `#token=` from any command output. Never move the owner's token between browser tabs.
- The Chrome tool loses its tab handle after a page reload; call `tabs_context_mcp` again before the next action. It has one browser connected.
- Tests render with a real Apollo client and MSW handlers (`src/test/`); unhandled requests fail the test; never mock modules.
