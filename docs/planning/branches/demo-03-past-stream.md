# demo/03-past-stream

The owner's first look at the live demo (#62, #63) on 2026-09-13: three things to change. Based on `main` after #63.

## What changed

- **The banner says less and does more.** "A snapshot of @redbull-testing with a mock Red Bull deal." and, as its actions, "Open the Red Bull deal" and "Open the latest report". The sentence about nothing being live is gone.
- **The demo is past streams.** The home no longer renders the live console (player, feeds, live strip) in demo mode; what remains is the last stream's strip with its report button, the deal, and the history. Subscriptions in the demo link stay quiet and complete. The exporter no longer exports the feed queries or any REST read, and the committed snapshot was trimmed to match (1.5 MB, 132 KB gzipped).
- **The introduction block is gone**, and with it `DemoIntro` and its styles.

## Verification

| Check | Command | Result |
|---|---|---|
| Frontend gate | `npm run lint`, `format:check`, `test:coverage`, `build` | all pass; 117 tests; statements 87.75 %, branches 81.28 % |
| In a browser | `npm run dev`, Chrome on `/demo` | the banner with the two actions, the last-stream strip, the deal, the track record, the four past streams; no player, no feeds |

## What to check by hand

1. `https://streamsense.dev/demo`: the banner's two buttons open the deal and the latest report; no player on the home.
