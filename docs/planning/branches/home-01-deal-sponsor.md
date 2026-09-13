# home/01-deal-sponsor

The home page follows the sponsor its current deal names. Follows d6e8b94 (no default sponsor), which removed the seeded "Nike" but left a streamer signed in with Twitch with no way to name a sponsor at all: the sponsor field lives on the operations page they cannot open, and creating a deal pointed the backend's relevance scoring at its sponsor without the home page noticing. Based on `main` at c9215d4.

## What changed

**Frontend only.** No schema, backend, or generated-type change.

- `features/home/home-sponsor.ts`: the one rule for which sponsor the home page is about. An entry on the operations page wins (an operator re-points relevance by hand, and the backend honours the latest pointing). Otherwise the newest deal running now, which is what relevance follows once a deal begins. Otherwise the soonest deal still to start. Otherwise none. `followedSponsor` narrows that to the sponsor the numbers are filtered by: only a manual entry or a running deal, never an upcoming one.
- `HomePage` fetches the channel's deals itself (one query; the deals panel takes them as props and asks for a refetch after creating one) and derives the sponsor from them plus the operations field, judged against the clock as of the visit, like the server's `active` flag. The header pill shows the sponsor being followed, "<sponsor> from <date>" for a deal not yet started, or "No sponsor yet".
- With nothing followed: the live strip and the player pill say "No sponsor yet", the sentiment panel reads "Sponsor sentiment" with "No sponsor is being followed yet.", the history rows link to the plain report, and the deals panel's lead line asks for a deal: "No deals yet. Create one and the home page follows its sponsor from the start date: exposure, mentions, and the report." The new-deal form stays closed; the line is the only prompt. With an upcoming deal the lead line names it and its start date instead, so the streamer does not create it twice.
- `NewDealForm` takes the sponsor to prefill or an empty string; the "Sponsor" placeholder sentinel it used to compare against is gone. `LiveStrip`, `HistoryPanel`, and `StreamFrame` take `null`/empty for no sponsor rather than the placeholder word.

## Deliberately left alone

- **The stored selection and the operations page.** The sponsor field there still drives runtime relevance by hand and still wins on the home page when set.
- **Deal, session, and share pages.** They carry their own sponsor already.
- **The streamer default of `test`** for a browser with no selection and no Twitch sign-in.
- **Auto-opening the new-deal form** on an empty channel was considered and rejected: the lead line is the prompt.

## Verification

Run in a Linux clone of the branch (`npm ci`), because the Windows checkout's `node_modules` lacks the Linux rollup binary.

| Check | Command | Result |
|---|---|---|
| Types, lint, format | `tsc -b`, `eslint .`, `prettier --check` on the touched files | clean (`react-hooks/purity` refused `Date.now()` in render; the clock is read once with `useState`) |
| Unit and page tests | `vitest run --maxWorkers=2 --testTimeout=30000` | 31 files, 124 tests pass: 6 new for `homeSponsor`/`followedSponsor`, 5 new home page cases (running deal followed, nothing followed with the sponsor feed off, deals failing to load, a running deal behind nine newer ones, upcoming deal named). With the default 5 s timeout and full parallelism the suite timed out at random on a host under load average 40; the same tests pass in isolation |
| Coverage floors | `vitest run --coverage` (same flags) | floors met |
| Build | `vite build` | succeeds |

## Review (Codex on #61)

One P1 and four P2s, all taken in the follow-up commit:

- **Deal boundaries while the page is open (P1).** The deals query now polls every 30 s like the sessions, and the clock the "not started yet" judgement uses advances on the same interval, so a deal starting or ending is picked up within a minute of the backend pointing relevance at it.
- **Truncated deal set (P2).** The sponsor rule sees every deal (the query asks for the service's page cap of 200, newest first); the panel still lists the newest eight.
- **Sponsor feed buffers (P2).** `useConsoleFeeds` keys the two sponsor feeds by streamer and sponsor, so one brand's buffered events never show under another's heading.
- **No sponsor followed (P2).** The sponsor feeds are skipped rather than queried with an empty filter, which the gateway treats as every brand.
- **Deals failing to load (P2).** `homeSponsor` has an `unknown` state while the deals have not loaded; the header shows no pill, the live strip and player show no "No sponsor yet", and the deals panel keeps its neutral line next to the error. A failed refetch keeps the last list.

## What to check by hand

1. Sign in with a Twitch account that has no deals: the home page header says "No sponsor yet", the deals panel asks for a deal, the form is closed.
2. Create a deal starting today: the header pill, live strip, and sentiment panel switch to its sponsor without a reload; history rows link with `?sponsor=`.
3. Create a deal starting next week on a channel with no running deal: the header shows "<sponsor> from <date>" and the deals lead line names it; the numbers still say no sponsor is followed.
4. As an operator, type a sponsor on the operations page for a channel with a running deal: the home page shows the typed one.
