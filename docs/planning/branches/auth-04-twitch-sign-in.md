# auth/04-twitch-sign-in

Option 3 of `docs/planning/handoffs/console-auth.md`: a streamer signs in with their Twitch account and the console knows who they are. Based on `main` at 11d1d6d (the sign-in header). Decided with the owner 2026-09-11 after the access-link page (#52, #54, #55) shipped.

## Plan

**What a viewer sees.** The sign-in page gains a "Sign in with Twitch" button above the access-link field. It sends the browser to Twitch, Twitch asks the streamer to authorise the StreamSense application once, and the browser comes back signed in, on the page it started from, with the console showing that streamer's channel. The sidebar shows "@login" and whether they are a streamer or an operator. Streamers do not see the Operations page. The access link keeps working exactly as before for demos.

**How it works.** OAuth 2.0 authorization code on the gateway, with the gateway as the confidential client (it holds the Twitch application's client id and secret, the same pair analytics-service uses for Helix):

1. `GET /auth/twitch/login?return=/deals/3` on the gateway. It creates a random `state`, seals `{state, return path, expiry}` into an HMAC-signed, `HttpOnly`, `SameSite=Lax` cookie scoped to `/auth/twitch` (ten minutes; no server-side session, so it works across replicas), and answers 303 to `https://id.twitch.tv/oauth2/authorize` with the client id, the registered redirect URI, the `openid` scope (Twitch requires a scope; this one asks for identity and nothing else), and the state.
2. Twitch sends the browser to `GET /auth/twitch/callback?code=...&state=...`. The gateway checks the cookie's signature, expiry, and that its state matches; exchanges the code at `https://id.twitch.tv/oauth2/token` (client secret in the form body, never in a URL); learns who signed in from `https://id.twitch.tv/oauth2/validate` (`login`, `user_id`, and the `client_id` the token was issued to, which must be ours); revokes the Twitch token again, best effort, because the gateway keeps nothing of Twitch's; and mints its own HS256 JWT with the existing issuer, audience, and secret: `sub` and `login` = the Twitch login, `twitchUserId`, `provider: twitch`, `role: operator|streamer`, seven days.
3. It answers 303 to `<console origin><return path>#token=<jwt>`. That is the access-link mechanism the console already has: the fragment never reaches a server or a log, `captureAccessLink` stores the token and strips the fragment, and the gate opens. No cookie transport for the API, so no CSRF story for the REST writes, and every transport (HTTP, WebSocket) is unchanged.
4. Failures (`error=access_denied` from Twitch, a bad or missing state cookie, a failed exchange or validate) answer 303 to `/?signin=<reason>`, which the sign-in page turns into one sentence.

`GET /auth/providers` (public) tells the console whether Twitch sign-in is on, so the button appears only where it is configured.

**Authorization.** Tokens carry a `role`. `STREAMSENSE_GATEWAY_AUTH_OPERATORS` is a comma-separated list of Twitch logins that sign in as `operator`; everyone else is `streamer`. The auth filter refuses (403, problem details `forbidden`, reason `operator_required`) any request that is not a read on the paths that steer the pipeline: `/api/chat/**`, `/api/video/**`, `/api/sentiment/**`, and `/ml/**` (channel switching, the sponsor profile editor, manual ingest, frame upload, segmentation). Streamers keep every read, GraphQL, and the analytics writes that are theirs (deals, share links, imports). A token without a `role`, which is what `mint-jwt.py` and the access link produce today, keeps its full access, so nothing deployed changes behaviour; `mint-jwt.py` gains `--role` for later. Scoping reads to the signed-in streamer's own channel is the next step, not this branch.

**Frontend.** `LoginPage` shows the Twitch button (Twitch's brand purple and Glitch mark, as its brand guidelines require for this one control) when `/auth/providers` says so, and the `signin` failure sentence. `auth-token.ts` decodes `login` and `role` from the token; the sidebar shows "@login · streamer|operator" or the access-link expiry; the Operations link and route are hidden from streamers; `describeError` explains a 403. The streamer selection defaults to the signed-in login when the browser has none stored, and is pinned to it for streamers, who cannot switch channels anyway.

**Edges.** nginx proxies `/auth/` to the gateway; Vite's dev proxy does the same. Compose passes `STREAMSENSE_GATEWAY_AUTH_TWITCH_ENABLED`, `STREAMSENSE_GATEWAY_AUTH_TWITCH_REDIRECT_URI`, and `STREAMSENSE_GATEWAY_AUTH_OPERATORS` to the gateway and mounts the two Twitch secret files; the Kubernetes manifest gets the same env and the gateway's network policy an egress rule to public addresses on 443 for `id.twitch.tv`. Rate limits cover the two sign-in routes. The startup check refuses to start with Twitch sign-in on and no client id, secret, redirect URI, or gateway auth. `twitch.env.example` and `docs/hosting.md` explain the two things the owner does by hand: register `https://streamsense.dev/auth/twitch/callback` (and `http://localhost:3000/auth/twitch/callback` for local runs) as redirect URLs on the Twitch application, and set the three variables on the VM.

## Deliberately left alone

- **No PKCE.** The gateway is a confidential client with a secret; the signed state cookie is the CSRF defence. Twitch's authorization code flow does not use PKCE for confidential clients.
- **No Twitch token is kept.** Only identity is needed; the user token is revoked after `validate`. Reading a streamer's Helix data on their behalf (their own viewer counts, for instance) would need more scopes and storage, later.
- **No per-streamer read scoping yet.** A signed-in streamer can still look at another channel's reports; the role only gates the pipeline controls. The next branch teaches the resolvers the signed-in login.
- **Access links stay operator-grade.** As today. Demote them by minting with `--role streamer` once operators sign in with Twitch instead.
- **The role check is not on WebSocket.** Subscriptions are reads.

## Verification

| Check | Command | Result |
|---|---|---|
| Gateway | `mvn -pl api-gateway -am verify` (Maven 3.9, JDK 21 in Docker, Testcontainers over the Docker socket) | `BUILD SUCCESS`, 113 tests, JaCoCo floor met (0.81), Spotless and ArchUnit clean. The first run failed only in `RedisRateLimiterTest`, untouched by this branch: its first Redis call answered through the fail-open path (remaining = limit), a Docker-in-Docker start-up race; the rerun passed it outright without needing the retry |
| Sign-in flow | `TwitchSignInIntegrationTest` (8 tests against a MockWebServer standing in for `id.twitch.tv`) | providers advertised; login answers 303 with the client id, redirect URI, `openid` scope, state, and an `HttpOnly; SameSite=Lax; Path=/auth/twitch` cookie for 600 s; a forged or mismatched state, an `access_denied`, a failed exchange, and a token issued to another client id each bounce to `/?signin=<reason>`; a streamer's callback exchanges the code with the secret in the form body, validates with `Authorization: OAuth`, revokes, clears the cookie, lands on `/deals/3?tab=streams#token=…`, and the token (sub, login, twitchUserId, role streamer, provider twitch) opens `/graphql` but gets 403 `operator_required` on `POST /api/chat/twitch/channels` while a GET there passes the gate; an operator on the list gets role operator and passes the write, and a `return` of `https://evil.example/phish` lands on `/` |
| State cookie | `OAuthStateCodecTest` | round trip; tampered body, tampered signature, missing signature, empty, other secret, and expiry all decode to empty; return paths outside the console fall back to `/` |
| Startup check | `GatewayAuthStartupCheckTest` | Twitch on without the gate, without credentials, or with a relative redirect URI refuses to start; complete config starts |
| Frontend gate | `npm run codegen:check`, `lint`, `format:check`, `test:coverage`, `build` | all pass; 111 tests; statements 89.04 %, branches 81.07 %, functions 86.89 % |
| Edges | `docker compose -f docker-compose.yml -f docker-compose.prod.yml config -q`; `tools/k8s/check_network_policies.py`; `nginx -t` on `frontend/nginx.conf` (nginx-unprivileged 1.27); `tools/mint-jwt.py --role streamer` | Compose renders with the three new gateway variables; 67 edges, all allowed (the config-repo default for the redirect URI was left empty so the checker does not read `localhost:3000` as a dependency); nginx config valid; the minted token carries `"role":"streamer"` |
| Twitch's documentation | `/oauth2/authorize` parameters, `/oauth2/validate` | `scope` is required and must not be empty, hence `openid`; validate takes `Authorization: OAuth <token>` and returns `client_id`, `login`, `scopes`, `user_id`, `expires_in`, which is what `TwitchIdentityClient` reads |

Not exercised here: a real sign-in against Twitch. It needs the redirect URL registered on the Twitch application, which only the owner can do, so it is the first manual check.

## What to check by hand

1. On the Twitch application (dev.twitch.tv/console/apps): add `https://streamsense.dev/auth/twitch/callback` under OAuth Redirect URLs, exactly.
2. On the VM, in `/etc/streamsense/twitch.env`: `STREAMSENSE_GATEWAY_AUTH_TWITCH_ENABLED=true`, `STREAMSENSE_GATEWAY_AUTH_TWITCH_REDIRECT_URI=https://streamsense.dev/auth/twitch/callback`, `STREAMSENSE_GATEWAY_AUTH_OPERATORS=<your Twitch login>`; the two secret files already hold the application's id and secret. `sudo streamsense-deploy`.
3. Open `https://streamsense.dev/` in a private window: the sign-in page shows "Sign in with Twitch" above the access-link field. Click it, authorise on Twitch (it asks for nothing but your identity), and you land back on the console signed in; the sidebar reads "Signed in as @yourlogin · operator" and Operations is there.
4. Sign out, sign in with a second Twitch account that is not on the operator list: the sidebar reads "· streamer", Operations is gone, `/ops` typed by hand shows "There is no page here", and the home page shows that account's channel.
5. Cancel on Twitch's consent page: back on the sign-in page with "Twitch sign-in was cancelled."
6. Start a sign-in, wait eleven minutes before authorising: back on the sign-in page with the "took too long" sentence.
7. The access link printed by the deploy still opens the console with full access.
