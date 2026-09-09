# 05-streamer-home: the home page around the live strip and the history

Branch `redesign/05-streamer-home`, cut from `redesign/main` after 04. Frontend only. Serves S1 (is the sponsor getting what was promised right now) and S5 (what the channel's past streams add up to). The deals section is a placeholder until 06.

## What changed

- **Home** (`features/home/HomePage.tsx`) is one page about the channel the runtime is pointed at: the live strip, the live console, the deals placeholder, and the history. It lists the channel's latest sessions (`Sessions`, eight of them, polled every 30 seconds) and, while the newest one is live, its `SessionSummary` on the same cadence. The sponsor is the selection's brand; with no brand chosen the summary is about the most visible sponsor.
- **Live strip** (`LiveStrip.tsx`): when the newest session is live, the pulsing LIVE mark, title, category, viewers, and four numbers (time on screen and share of stream, mentions split chat and voice, mention sentiment, and the risk level) with "Open the live report". When it is not, an Offline pill, the last stream's title and date, and "View session report" pointing at it. Before capture has ever run it says so.
- **Console** (`features/console/LiveStreamConsole.tsx`): the player and detections sit beside the brand-mention feed, since the brand is what the page is about. The general chat and the full transcript moved into a drawer (`<details>`) under the player, closed by default; nothing there was removed.
- **History** (`HistoryPanel.tsx`, arithmetic in `track-record.ts`): the track record over the finished sessions that have a summary (sponsor time on screen per sponsored hour, mention-weighted mention sentiment, average viewers over sessions with viewer data, total mentions, session count) beside a row per past stream (title, date, length, time on screen, mentions with sentiment, average viewers), each row a link to that session's report. Summaries for the history come one query per session through the Apollo client (`useSessionSummaries.ts`); the list is a page long so that is fine until a batch query exists.
- **Styles** for the strip, drawer, and history appended to `index.css`; the old sidecar rules they replaced were removed.
- **Tests** (minimal): the track-record arithmetic (`track-record.test.ts`); the home with a live session (strip numbers, the report link, history rows and the track record, the live session excluded from history) and with the channel offline.

## Verification

Run on 2026-09-09.

| Check | Result |
|---|---|
| `npm run test:coverage` | 21 files, 80 tests passed. Statements and lines 88.0%, branches 83.1%, functions 90.3% (floors 85/80/80/85) |
| `npm run lint`, `format:check`, `codegen:check` | clean |
| `npm run build` | `tsc -b` clean |

## Manual checks for the reviewer

1. With the replay alias running, open `http://localhost:3000/`. The strip shows LIVE with the replay's title, the on-screen time climbing, and "Open the live report" going to the session's report.
2. Stop capture and wait for the idle close (`captureSessionIdleCloseMinutes`). The strip switches to Offline and points at that session's report; the session appears in the history with its numbers.
3. Open "All chat and transcript" under the player: the chat and transcript feeds are the ones the old console showed.

## Left for later branches

- The deals panel is a placeholder; 06 replaces it with the channel's active deals.
- One summary query per history row; a `sessionSummaries(ids)` gateway query would make it one round trip.
