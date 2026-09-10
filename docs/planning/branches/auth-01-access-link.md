# auth/01-access-link

Option 1 of `docs/planning/handoffs/console-auth.md`, grown into a sign-in page: the hosted console opens to a login page and nothing behind it renders until the browser holds a bearer token, and that token arrives through an access link instead of a `localStorage.setItem` line in the developer tools. Based on `main` at 6b82669 (the handoff briefs). Decided with the owner on 2026-09-10: this first, then Sign in with Twitch as the real feature on the same page.

## What was missing

Gateway auth is on for the hosted demo, but the console had no notion of being signed in: the shell loaded for anyone, every panel then got a 401 from the gateway, and the only way in was pasting a token into local storage by hand. There was nothing to tell a viewer their token had run out and no way to sign out.

## What changed

- **The token travels in the URL fragment.** `streamsense-deploy` prints `https://streamsense.dev/#token=<jwt>` (`access_link` in `tools/deploy/deploy.sh`, printed after every deploy and by the new `link` subcommand; `token` still prints the bare token and both take `--ttl-seconds`). Browsers never send the fragment, so the token stays out of Caddy's and nginx's access logs and never reaches the gateway as part of a URL. On load, `captureAccessLink` (`frontend/src/lib/auth-token.ts`, called first thing in `App`) stores the token under the existing `streamsense.authToken` key and replaces the URL with its path and query, so a copied or bookmarked address does not carry the token on. The token must look like a JWT (three base64url segments); the gateway remains the only verifier.
- **A sign-in page gates the console.** `features/access/AccessGate` wraps the router in `App.tsx`: without a token, or with one whose `exp` claim has passed, it renders `LoginPage` instead of the app. The page takes the access link pasted whole or the bare token (`authTokenFromInput`), rejects anything else with a message, and on success stores the token and re-renders; no reload is needed because every transport reads the token per request (the Apollo HTTP link per operation, the WebSocket client on connect). A tab opened from a share link passes the gate, since it authenticates with the share token and gets the reduced shell. Sign in with Twitch lands on this page later as a button next to the field.
- **The sidebar shows the access and signs out.** `features/access/AccessPanel` in the shell's footer shows "Until <date>" from the token's `exp` claim and a Sign out that clears the token and reloads (the reload drops the Apollo cache and the subscription socket).
- **A 401 is explained.** `describeError` maps a 401 from either transport (an `ApiError` with status 401, or Apollo's `ServerError` with status code 401) to "this console needs an access link; open the one you were sent", for the case where a token looks fine to the console but the gateway rejects it (rotated secret, tampered token).
- **Blocked storage no longer loses the token.** Both `auth-token.ts` and `share-token.ts` keep the token captured during this page load in memory when local or session storage is unavailable or refuses the write, and read it back from there. That closes the Codex P2 from the redesign review ("share token lost when session storage is unavailable"). The Apollo link and WebSocket helpers no longer evaluate `window.localStorage` as a default argument, which threw at module load in a browser that blocks storage.
- **Docs.** `docs/hosting.md` (what the deploy prints, the `link` subcommand, the Sharing section rewritten around the link and the sign-in page), `CLAUDE.md` (hosted demo subcommands, the frontend layout paragraph, `describeError`), the handoff's status line, the redesign README's P2 list.

## Deliberately left alone

- **The security model.** Possession of a token is still the credential and every token is equal; the gate is a frontend courtesy, the gateway is the control. Real identity, a signed-in streamer's own channel by default, and an operator allow-list for `/ops` come with Twitch sign-in (option 3 in the handoff).
- **No session cookie** (option 2 in the handoff): the token stays in local storage, as before.
- **The expiry check is advisory.** The console decodes `exp` without verifying the signature only to decide whether to show the sign-in page; a token with no `exp` is treated as usable and left to the gateway.
- **`tools/mint-jwt.py`** is unchanged; the link is assembled by the deploy script around it.
- **Coverage floors** stay at 85/80/80/85 per the minimal-tests decision; the new code is covered by unit tests for the helpers and two small component tests.

## Verification

| Check | Command | Result |
|---|---|---|
| Frontend gate | `npm run codegen:check`, `lint`, `format:check`, `test:coverage`, `build` (Node 24 locally; CI runs 20) | all pass; 29 files, 104 tests; statements 89.07 %, branches 81.33 %, functions 86.63 %, lines 89.07 % |
| Deploy script | `bash -n`, `shellcheck -S style` (0.11.0 via Docker) | clean |
| In a browser | `npm run dev` against the running Compose stack (gateway auth off locally, so any well-formed token passes), Chrome | the console opens to the sign-in page; `https://streamsense.dev/` pasted is rejected with the message; the access link pasted signs in without a reload and the home page loads; the sidebar shows "Until Oct 10, 2026" and Sign out returns to the sign-in page; opening `/ops?x=1#token=<jwt>` signs in, lands on `/ops?x=1` with the fragment gone, and the operations page loads with its subscriptions; no console errors |

Not verified here: the hosted deploy itself (the VM was not started for this branch), so the `link` subcommand and the printed instructions were exercised only through `bash -n` and shellcheck.

## What to check by hand

1. Merge, start the VM, `sudo streamsense-deploy`; the end of the output is one link.
2. Open the link in a private window: the console loads signed in, the address bar shows `https://streamsense.dev/` without the fragment, the panels fill, and the sidebar's Access entry shows a date 30 days out.
3. Open `https://streamsense.dev/` in a second private window: the sign-in page; paste the same link; the console loads without a reload.
4. Sign out from the sidebar: back to the sign-in page; reload: still the sign-in page.
5. `sudo streamsense-deploy link --ttl-seconds 60`, open it, wait a minute, reload: "Your access link has expired".
6. A share link (`/deals/<id>?share=<token>`) in a private window still opens the deal without the sign-in page.
