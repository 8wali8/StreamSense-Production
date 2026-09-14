# console/01-view-as

Two asks from the owner on 2026-09-13, after the demo shipped: a way to walk the console as a streamer sees it without a second account, and the Operations link only for the owner's own sign-in. Based on `main` after #65.

## What changed

- **View as a streamer** (`frontend/src/lib/view-as.ts`, `features/access/AccessPanel.tsx`). An operator's access panel gets a field, "View as a streamer", and a View button. The login is kept in session storage for the tab (`streamsense.viewAs`, an `@` and case dropped), the console goes back to the home and reloads, and from then on `readStoredSelection` pins every page to that channel, the Operations link and route are gone, and the panel reads "Viewing as @login · streamer" with "Signed in as @owner · operator" under it and "Back to my view". Sign-out drops the view with the token. The token does not change: the gateway still sees the operator, so this is the streamer's shape of the console over the operator's access, which is exactly what a walkthrough of the streamer's path needs. `isOperatorView()` is the one question the shell and the router ask.
- **Only an operator sign-in is an operator.** Before, a token without a role (an access link from `streamsense-deploy link` or `tools/mint-jwt.py`) counted as an operator in both the console and the gateway, so anyone holding a viewer link had the Operations page and the pipeline controls. Now `AuthScope.isOperator()` is true for the `operator` role only; a role-less token is neither operator nor confined (`isConfined()`, the streamer case): it reads any channel, as before, and is refused with `operator_required` on the operator-only paths. `JwtAuthTokenValidator.ValidationResult.isOperator()` agrees. In the console `isOperatorSession()` is true for the operator role or no token at all (auth off locally), never for an access link, so the Operations link appears only for a Twitch sign-in on `STREAMSENSE_GATEWAY_AUTH_OPERATORS`. `mint-jwt.py --role operator` mints an operator token when a script needs one.
- **Docs.** `CLAUDE.md`, `docs/hosting.md`, `docs/howtorun.md`, the deploy script's sharing text, and the `mint-jwt.py` help say what an access link can and cannot do.

## Verification

| Check | Command | Result |
|---|---|---|
| Frontend gate | `npm run codegen:check`, `lint`, `format:check`, `test:coverage`, `build` | all pass; 123 tests; statements 87.98 %, branches 81.74 % |
| api-gateway | `mvn -pl api-gateway verify` in `maven:3.9-eclipse-temurin-21` (Spotless applied first) | build success; 128 tests, 5 skipped (Docker-backed Redis), coverage floor held |
| Deploy script | `shellcheck -S style tools/deploy/deploy.sh` | clean |
| In a browser | `npm run dev`, an operator token in local storage | the panel offers "View as a streamer"; `@Ninja` pins the home to @ninja, hides Operations, shows "Viewing as @ninja · streamer" over "Signed in as @8wali8 · operator"; "Back to my view" restores the operator's view |

## What to check by hand

1. Sign in with Twitch as the operator: the Operations link is there; type a channel under "View as a streamer" and press View: the home is that channel's, Operations is gone; "Back to my view" returns.
2. Open a viewer access link in a private window: no Operations link, and `POST /api/chat/twitch/channels` with that token is 403 `operator_required`.

## Follow-ups

- A viewer link can still create deals and share links for any channel through analytics-service (it always could). A read-only `viewer` role, with the write controls hidden for it, is the next step if links go to people outside the team.
