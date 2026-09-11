# auth/03-sign-in-header

An interactive header for the sign-in page, chosen by the owner from four proposals on 2026-09-11 (the comparison page: https://claude.ai/code/artifact/ac74ffb6-af9a-4e51-964e-d2bf042e36a8). Based on `main` at d3b28ab.

## What changed

- **`features/access/DetectionHeader.tsx`**: the StreamSense wordmark ("Stream" heavy, "Sense" light, the console's IBM Plex) with the sponsor detector's bounding box on it. Left alone, the box scans letter by letter every 700 ms with a wandering confidence readout, then rests on the whole word ("STREAMSENSE · 0.97"). With a pointer over the header it snaps to the letter underneath and the confidence reads off the distance (0.99 directly over a letter, down to 0.69 far from one). Four faint frame corners around it echo the on-screen overlay in the console. Under `prefers-reduced-motion` the idle scan is off and the box rests on the word, still following the pointer without transitions.
- **`features/access/detection-header.ts`**: the pure geometry (nearest letter, confidence, letter and word boxes, idle sequence), unit-tested. The component only measures the letters (on mount, when the fonts finish loading, and on resize) and picks a target.
- **`LoginPage`** renders the header above the instruction line, plus a visually hidden `h1` "StreamSense" so the page keeps an accessible name; the header itself is `aria-hidden`.
- **Styles** appended to `src/index.css` under the sign-in section, all from existing tokens (teal box and tag, `--line-strong` corners). No new dependencies, no canvas.

## Deliberately left alone

- **No click-to-lock.** The proposal pulsed and locked on click; a clickable decorative `div` fails the a11y lint rules for good reason, and a button that does nothing useful is worse. Hover and idle are enough.
- **Sign-in page only.** The header does not replace the brand lockup in the sidebar.

## Verification

| Check | Command | Result |
|---|---|---|
| Frontend gate | `npm run lint`, `format:check`, `test:coverage`, `build` | all pass; 107 tests; statements 88.88 %, branches 81.04 % |
| In a browser | `npm run dev` against the running Compose stack, Chrome | the box scans the letters while idle; hovering over the "s" in "Sense" snaps the box to it with "S · 0.98"; the page below is unchanged |

## What to check by hand

1. After the deploy, open `https://streamsense.dev/` in a private window and leave the pointer off the card: the box walks the letters and rests on the word.
2. Move the pointer along the wordmark: the box follows, the letter under it turns teal, the confidence rises to 0.99 over a letter.
3. With "Reduce motion" on in the OS, the box sits on the word until the pointer moves it.
