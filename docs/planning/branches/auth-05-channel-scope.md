# auth/05-channel-scope

A streamer signed in with Twitch sees their own channel and nothing else. Follows `auth/04-twitch-sign-in` (#56), which gave tokens a role but only gated the pipeline controls; until now a streamer could open any channel's reports and deals by typing a different name. Based on `main` at d6e8b94.

## What changed

**Gateway.**
- `auth/AuthScope`: who a request is for (login, role), carried on the exchange by `GatewayAuthWebFilter`, on the WebSocket session by `GatewayWebSocketAuthInterceptor`, and on the GraphQL context by the new interceptor. `allows(channel)` is the one rule: an operator or an access link (no role) may read any channel, a streamer only their own login (case-insensitive, a leading `@` ignored).
- REST: for a streamer, the auth filter refuses (403, `channel_forbidden`) any `/api/**` request whose path names another channel (`/streams/{channel}/…`, `/relevance/sponsors/{channel}`) or whose `streamer` query parameter does. `config/AuthScopeHeadersFilter` then tells the proxied service who asked in `X-StreamSense-Auth-Login` and `X-StreamSense-Auth-Role`, after dropping anything a client sent under those names.
- GraphQL: `graphql/ChannelScopeInterceptor` reads the scope (HTTP exchange or WebSocket session), puts it on the context, and for a streamer parses the document: every top-level field with a `streamer` argument, literal or variable, must name their login, or the request is answered `CHANNEL_FORBIDDEN` before any resolver runs. That covers the queries and every subscription. Fields addressed by id (`deal`, `dealSummary`, `session`, `sessionSummary`, `sponsorMoments`) fetch first and compare the owner (`ownedOrForbidden`), raising `AuthScope.ChannelForbiddenException`, which `GraphQlErrorAdvice` turns into the same code.

**analytics-service.** `web/ChannelScopeFilter` enforces the same rule from the two headers for the routes the gateway cannot judge by path alone: a deal or session addressed by id (owner looked up through the services), the deals list (`streamer` required), and a deal being created (the body's `streamer`, read once and replayed to the controller). A request without the headers is unscoped, which is safe only because the service is reachable through the gateway alone (network policy, Compose network).

**Console.** `describeError` words `CHANNEL_FORBIDDEN` as "this channel is not yours to see". Nothing else changes: a streamer's selection was already pinned to their login in #56, so the console never asks for another channel on its own.

## Deliberately left alone

- **The other services stay unscoped by id.** Sentiment, video, chat, and recommendation routes name the channel in the path or a parameter, which the gateway checks; none of them addresses data by an id that hides its owner.
- **Share links are untouched.** They carry no bearer and are confined to one deal by `ShareLinkInterceptor` as before.
- **Operators see everything**, and so do access links; demoting the printed access link to the streamer role remains a deliberate later step.

## Verification

| Check | Command | Result |
|---|---|---|
| Gateway and analytics-service | `mvn -pl api-gateway,analytics-service -am verify` (Maven 3.9, JDK 21 in Docker) | `BUILD SUCCESS`: api-gateway 116 tests and analytics-service 36 tests, JaCoCo floors met, Spotless and ArchUnit clean. One earlier run failed only `GatewayRateLimitUntrustedProxyIntegrationTest`, untouched here: its two requests straddled 20:21:00 and the fixed one-minute window reset between them; the rerun passed |
| Gateway scoping | `ChannelScopeIntegrationTest` (3 tests, MockWebServer as analytics-service) | another channel by literal and by variable refused with `CHANNEL_FORBIDDEN`; own channel (mixed case) passes; an operator is not confined; a deal or session by id owned by someone else refused, own passes; REST path and query parameter refused at the gate; forwarded requests carry the scope headers and a client's own copies are dropped |
| analytics-service scoping | `ChannelScopeTest` (MockMvc) | path, query parameter, create body, deal and session by id: own passes, other refused with the problem `forbidden`/`channel_forbidden`; a missing session is a 404; operator and unscoped callers pass |
| Frontend gate | `npm run lint`, `format:check`, `test:coverage`, `build` | all pass; 112 tests |

## What to check by hand

1. Sign in with an account that is not on the operator list, then change the URL to `/deals/<someone else's deal id>` or `/sessions/<id>`: the page reports "this channel is not yours to see" instead of the report.
2. As the operator, the same URLs open.
3. The access link printed by the deploy still opens any channel.
