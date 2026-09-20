# auth/06-retire-access-links

The owner decided on 2026-09-19 to retire access links (handoff: `docs/planning/handoffs/retire-access-links.md`). Sign in with Twitch covers the operator and every streamer, a sponsor gets a share link, and `/demo` is the answer for a look without an account. A token minted by hand without a role, carried in `#token=<jwt>`, served nobody any more and was a third kind of scope to reason about. Based on `main` after #71.

## What changed

- **Gateway.** `auth/JwtAuthTokenValidator` refuses a token whose `role` claim is missing or not `operator`/`streamer` with a new reason, `missing_role` (401, on HTTP and on the subscription socket alike), so every accepted token names an operator or a streamer. `auth/AuthScope` is down to two cases: `isOperator()`, and everyone else confined to their own channel; `isConfined()` is gone, as is the auth filter's "role-less token on a self-service write" branch and `GatewayEdgeProperties.isSelfServiceWrite`, which only that branch used. `GatewayWebSocketAuthInterceptor` always puts the role on the session. Tests: `AccessLinkScopeIntegrationTest` became `RoleClaimIntegrationTest` (a role-less token is refused on a GraphQL read, two REST reads, a pipeline write, and a connection init; an operator still reads any channel and steers the pipeline); `JwtAuthTokenValidatorTest` gained the missing-role and unknown-role refusals and re-minted its dev-tool fixture with a role; `TestJwtTokens.validToken` became `tokenWithoutRole` (used only to prove the refusal) and every other helper mints a role.
- **Minting.** `tools/mint-jwt.py` requires `--role operator|streamer` and says what each is for. `tools/deploy/deploy.sh` lost `link`, `access_link`, and the sharing instructions; a deploy ends with the console URL and nothing else; `verify_edge` and `streamsense-deploy token` mint an operator token (subject `streamsense-deploy`), and `token` says so on stderr.
- **Console.** `LoginPage` is the header, the failure sentence from `?signin=<reason>`, the Twitch button when `GET /auth/providers` offers it (a sentence that sign-in is not switched on when it does not, and one to reload when the gateway did not answer), and the demo line. No field, no "Open the console". `AccessGate` no longer takes a pasted token: the token arrives with the page from the Twitch callback, which `App` captures before the gate renders (`captureAccessLink` stays for that). `authTokenFromInput` and its tests are gone. `AccessPanel` names a sign-in by login and role, or by the role alone for a token minted by hand, and lost the "Until <date>" state. The 401 sentence from `describeError` now says to sign in with Twitch again. Fixtures in every test carry a role.
- **Docs.** `docs/hosting.md` (verify output, the subcommands, "Sharing the console" rewritten around Twitch sign-in, share links, and the demo), `docs/howtorun.md` (both `mint-jwt.py` examples pass `--role`), `CLAUDE.md` (the auth bullet and the frontend sign-in paragraph), `docker-compose.prod.yml`'s header, `tools/deploy/twitch.env.example`, and the comment in `config-server/config-repo/api-gateway.yml`. Nothing in `k8s/` or `docs/kubernetes-kind.md` told a reader to mint a token, so they are untouched. `docs/planning/handoffs/console-auth.md` and `docs/planning/cloud-hosting.md` keep the history.

## Verification

| Check | Command | Result |
|---|---|---|
| api-gateway | `mvn -pl api-gateway spotless:apply verify` in `maven:3.9-eclipse-temurin-21` | build success; 132 tests, 5 skipped (Docker-backed Redis), coverage floor held |
| Frontend gate | `npm run codegen:check`, `lint`, `format:check`, `test:coverage`, `build` | all pass; 156 tests; statements 89.36 %, branches 84.67 % |
| Deploy script | `shellcheck -S style tools/deploy/deploy.sh` (`koalaman/shellcheck:stable`) and `bash -n` | clean |
| mint-jwt.py | `python tools/mint-jwt.py --secret …` without `--role` | `error: the following arguments are required: --role` |

## What to check by hand

1. After the deploy, open the console in a private window: the sign-in page offers "Sign in with Twitch" and the demo, and nothing to paste into. Sign in as the operator: the Operations link is there; "View as a streamer" still works.
2. From the VM, a token minted without a role is refused: with the secret from `secrets/`, `python3 tools/mint-jwt.py --role operator` is the only shape the tool mints, so build the old shape by hand or take the last printed access link's token and send it to `/graphql` with a health query: 401 with `"reason":"missing_role"`.
3. `sudo streamsense-deploy verify` still passes (it now mints an operator token for the health query), and a deploy ends with the console URL and no sharing block.

## Follow-ups

- `streamsense-deploy token` still defaults to a 30-day lifetime; that is a long-lived operator credential, so pass `--ttl-seconds` for anything that leaves the VM, or shorten the default if the command ever gets regular use.
