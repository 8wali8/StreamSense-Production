# auth/02-sign-in-page

The sign-in page from `auth/01-access-link` (#52) trimmed to what the owner asked for after seeing it deployed, before Twitch sign-in is built on it. Based on `main` at 3764d2f.

## What changed

- **Content.** The brand lockup and the "Sign in to StreamSense" heading are gone. The page is one line, "Paste your access link or token below." (or "Your access link has expired. Paste a new one below." when the stored token has run out), the field, and the button.
- **Accent.** The page uses the teal (`--brand`, the sponsor-exposure colour from the prototype) for the button and the field's focus ring instead of the on-air red, by overriding the accent tokens inside `.login-page`; the button text is dark for contrast on teal. Nothing else in the console changes colour, so the red still means live state and the primary action everywhere else.
- **Test.** `AccessGate.test.tsx` asserts on the new text.

## Verification

| Check | Command | Result |
|---|---|---|
| Frontend gate | `npm run lint`, `format:check`, `test:coverage`, `build` | all pass; 104 tests, floors held |
| In a browser | `npm run dev` against the running Compose stack, Chrome | the page shows the single line, the field, and the teal button; nothing else on the page |

## What to check by hand

1. After the deploy, open `https://streamsense.dev/` in a private window: the trimmed page with the teal button.
2. Paste the link: the console opens as before.
