# Deals and sponsor profiles

A deal is one sponsorship: a sponsor on a channel over a date range, with the commercial terms the session report prices exposure at and the direct-response signals chat is counted for. Sessions belong to a deal by streamer and start time; nothing links them explicitly, so a deal created after the fact still covers the streams inside its dates.

## Deals (analytics-service)

- `POST /api/analytics/deals` creates one. Body: `streamer` and `sponsor` (required; the streamer is normalised to a Twitch login), `startsAt` (required, epoch millis), `endsAt` (optional, must be after `startsAt`; null means open-ended), `promisedStreams`, `fee` (what the streamer is paid; private to them), `currency` (default `USD`), `cpmPer30sEquivalent` and `hostReadRatePer1000` (default to `streamsense.analytics.value.*`), `trackedLink` (an absolute http(s) URL; its host is what chat posts are matched on), `chatCommand` (normalised to `!name`), `channelPointReward`. Returns 201 with the deal; bad input is a 400 problem.
- `GET /api/analytics/deals?streamer=&limit=` lists a streamer's deals, or every deal without a streamer, newest start first.
- `GET /api/analytics/deals/{id}` one deal, 404 when unknown. `active` is true while the dates cover now; `trackedLinkHost` is derived from the link.
- `GET /api/analytics/deals/{id}/summary` the deal, `totals` over the sessions that started inside its dates (streams, live streams, streamed time, on-screen time and share, mentions by channel, mention-weighted sentiment, average viewers over sessions with viewer data, logo, host-read, and media value summed where a session could be priced and null otherwise, command uses, link posts), and each session's full report (`sessions`, newest first), each priced with the deal's terms.

Creating a deal whose dates cover now points sentiment-service's relevance scoring at its sponsor for the channel (`POST /api/sentiment/relevance/sponsors`), best effort: a failure is logged and the deal still exists. This is the only outbound call analytics-service makes to another StreamSense service; `streamsense.services.sentiment-service.base-url` and its timeouts configure it, and without a base URL nothing is called.

The session report (`GET /api/analytics/sessions/{id}/summary`, GraphQL `sessionSummary`) resolves the deal a session fell inside: the newest deal on the channel covering the session's start, for the requested sponsor when one is named. The deal's sponsor, rates, command, and link host fill whatever the request left unspecified, and the report carries `dealId`. The Helix poller watches every streamer with an active deal in addition to the configured channels and the streamers with recent events.

## GraphQL (api-gateway)

```graphql
deals(streamer: String, limit: Int): [Deal!]!
deal(id: ID!): Deal
dealSummary(id: ID!): DealSummary
```

`Deal` mirrors the REST shape. `DealSummary` is `deal`, `totals` (`DealTotals`), and `sessions` (`[SessionSummary!]!`). There are no mutations; the console creates deals through the REST route.

## Sponsor relevance profiles (sentiment-service)

The profile relevance scoring uses per streamer (sponsor, aliases, semantic terms, minimum score) is stored in `sponsor_relevance_profiles` and mirrored in memory. At start-up the stored rows load first; the `streamsense.sentiment.relevance.seeds` in config-repo only fill in streamers with no stored profile, so an operator's or a deal's choice survives a restart.

- `GET /api/sentiment/relevance/sponsors` every stored profile.
- `GET /api/sentiment/relevance/sponsors/{streamer}` the profile in effect, 404 when the channel has none.
- `POST /api/sentiment/relevance/sponsors` replaces the channel's profile (`streamer`, `sponsor`, optional `aliases`, `semanticTerms`, `minScore`; the sponsor's configured aliases and terms are merged in). Unknown fields, including the retired `campaignGoal`, are ignored.

## Share links

A deal can be shared read-only. `POST /api/analytics/deals/{id}/share` mints the deal's share token (or returns the existing one, so a sent link keeps working), `DELETE /api/analytics/deals/{id}/share` revokes it, and `GET /api/analytics/share/{token}` resolves a token to its deal (404 when unknown or revoked). The token is on the deal as `shareToken`, private to the streamer like the fee.

The console sends the token as the `X-StreamSense-Share` header on GraphQL requests when the tab has no bearer token (a link carries `?share=<token>`, kept in the tab's session storage). At the gateway, `GatewayAuthWebFilter` lets such a POST to `/graphql` through without a bearer token; nothing else (REST routes, subscriptions) is opened. `ShareLinkInterceptor` then resolves the token against analytics-service, allows only `deal`, `dealSummary`, `sessionSummary`, and `sponsorMoments`, and puts the deal on the GraphQL context, where the resolvers narrow to it: `deal` and `dealSummary` answer only for the shared deal and return it without `fee` or `shareToken`; `sessionSummary` and `sponsorMoments` are always about the deal's sponsor at the deal's terms and answer null for a session outside the deal. Refusals are GraphQL errors with `extensions.code` `SHARE_FORBIDDEN` (another query), `SHARE_TOKEN_INVALID` (unknown or revoked token), or `SHARE_UNAVAILABLE` (analytics-service unreachable).
