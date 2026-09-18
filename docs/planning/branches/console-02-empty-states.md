# console/02-empty-states

The first walkthrough of the streamer's path on the live site (2026-09-18, signed in as the owner, viewing as a fresh channel and as a Helix-only channel) found four things a new streamer meets before anything else. Based on `main` after #69. The new-deal form's shape is the next branch.

## What changed

- **The live console renders only while the channel is live.** Offline, the home showed a Twitch "is offline" placeholder under a LIVE badge, taking the whole first screen. Now `HomePage` mounts `LiveStreamConsole` only when the newest session is live; offline, the strip above already points at the last report. The demo never had the console; this makes the real console match.
- **A report with no sponsor says so.** `SessionReportPage` used "Sponsor" as the name of a missing sponsor: "Sponsor · @xqc", "Where Sponsor showed up", "No Sponsor moments were recorded", "Mentions · – positive", "$0.00 Media value · estimate". Now the eyebrow reads "No sponsor tracked · @xqc", the timeline is headed "Sponsor moments" with "No sponsor was tracked in this stream, so there are no moments to show", the media value tile shows "–" with "Media value · no sponsor tracked", and the mentions tile drops the positive share when there is none. `SessionTimeline` takes `sponsor: string | null`.
- **Measurement of an offline channel says nothing arrives yet.** `MeasureChannel` takes `channelLive` from the home's sessions; while measuring an offline channel it adds "Nothing arrives until @channel goes live." to its line.
- **Long titles are clamped.** A Twitch title runs to 140 characters of emoji and made each history row a paragraph. The history row's title and the offline strip's title clamp to two lines with the whole title on hover (`title` attribute).

## Verification

| Check | Command | Result |
|---|---|---|
| Frontend gate | `npm run codegen:check`, `lint`, `format:check`, `test:coverage`, `build` | all pass; 155 tests; statements 89.5 %, branches 84.79 % |

The no-sponsor report and the offline home were seen on the live site before the change (viewing as `xqc` and as `8wali8`); after the deploy the same two pages are the check.

## What to check by hand

1. `https://streamsense.dev/` on an offline channel: no player, no LIVE badge; the measurement card, the offline strip, the deals, the history.
2. View as `xqc`, open the session report: "No sponsor tracked · @xqc", "Sponsor moments", a dash for media value.
3. The history rows on `xqc`: two lines per title, the full title on hover.
