# Deals and sponsor profiles

A deal is one sponsorship: a sponsor on a channel over a date range, with the commercial terms the session report prices exposure at and the direct-response signals chat is counted for. Sessions belong to a deal by streamer and start time; nothing links them explicitly, so a deal created after the fact still covers the streams inside its dates.

## Deals (analytics-service)

- `POST /api/analytics/deals` creates one. Body: `streamer` and `sponsor` (required; the streamer is normalised to a Twitch login), `startsAt` (required, epoch millis), `endsAt` (optional, must be after `startsAt`; null means open-ended), `promisedStreams`, `fee` (what the streamer is paid; private to them), `currency` (default `USD`), `cpmPer30sEquivalent` and `hostReadRatePer1000` (default to `streamsense.analytics.value.*`), `trackedLink` (an absolute http(s) URL; its host is what chat posts are matched on), `chatCommand` (normalised to `!name`), `channelPointReward`. Returns 201 with the deal; bad input is a 400 problem.
- `GET /api/analytics/deals?streamer=&limit=` lists a streamer's deals, or every deal without a streamer, newest start first.
- `GET /api/analytics/deals/{id}` one deal, 404 when unknown. `active` is true while the dates cover now; `trackedLinkHost` is derived from the link; `logos` are the deal's logos, oldest upload first (empty means on-screen tracking is off for the deal).
- `PUT /api/analytics/deals/{id}` replaces the deal's terms: the create body without `streamer`, which is the deal's and never changes, validated the same way. It is the whole deal, not a patch: a term left out is cleared, a rate left out returns to the configured default. Returns 200 with the deal, 404 when unknown. Sessions are found by the deal's dates at read time, so every report inside the new dates is priced with the new terms on its next load; nothing is backfilled. Ending a deal is an update whose `endsAt` is now.
- `DELETE /api/analytics/deals/{id}` removes the deal for good: 204, 404 when unknown, 409 while the deal is shared (revoke the share link first, so a sent link never breaks silently). Operators only; a streamer gets 403 `operator_required`. The sessions inside it stay and belong to whichever older deal covers them, or to none.
- `POST /api/analytics/deals/{id}/logos` adds the sponsor's logo to the deal from the multipart part `file`: a PNG or JPEG decided from the bytes (the client's content type is ignored), at most `streamsense.logos.max-bytes` (5 MB) and between `min-pixels` (32) and `max-pixels` (8192) on each side, and at most `max-per-deal` (2) per deal. 201 with the logo (`id`, `dealId`, `contentType`, `width`, `height`, `sizeBytes`, `uploadedAt`, and `ref`, the `s3://` URI the detection pipeline reads it by), 404 for an unknown deal, 400 for a bad image, 409 when the deal is full. The bytes go to the `streamsense.logos.bucket` on the frame store (created on first use) under `deals/{id}/…`; `GET .../logos` lists them, `GET .../logos/{logoId}` serves the image with its stored content type (privately cacheable for an hour), and `DELETE .../logos/{logoId}` removes the logo and its object (204, 404 when the deal has no such logo). Deleting a deal removes its logos' objects too. A logo takes effect for detection from the moment it is added; earlier reports are never recomputed.
- `GET /api/analytics/streams/{streamer}/current-deal` the deal covering now on the channel, with its logos (their `ref` is what a frame is examined against), 404 when there is none. video-service asks this once a minute per channel (`docs/contracts/sponsor-pipeline.md`).
- `GET /api/analytics/deals/{id}/summary` the deal, `totals` over the sessions that started inside its dates (streams, live streams, streamed time, on-screen time and share, mentions by channel, mention-weighted sentiment, average viewers over sessions with viewer data, logo, host-read, and media value summed where a session could be priced and null otherwise, command uses, link posts), and each session's full report (`sessions`, newest first), each priced with the deal's terms.

Creating a deal whose dates cover now points sentiment-service's relevance scoring at its sponsor for the channel (`POST /api/sentiment/relevance/sponsors`), best effort: a failure is logged and the deal still exists. Updating or deleting a deal points relevance at whichever deal on the channel is current afterwards; when none is, relevance stays where it was, as it does when a deal ends on its own. This is the only outbound call analytics-service makes to another StreamSense service; `streamsense.services.sentiment-service.base-url` and its timeouts configure it, and without a base URL nothing is called.

The session report (`GET /api/analytics/sessions/{id}/summary`, GraphQL `sessionSummary`) resolves the deal a session fell inside: the newest deal on the channel covering the session's start, for the requested sponsor when one is named. The deal's sponsor, rates, command, and link host fill whatever the request left unspecified, and the report carries `dealId`. The Helix poller watches every streamer with an active deal in addition to the configured channels and the streamers with recent events.

## GraphQL (api-gateway)

```graphql
deals(streamer: String, limit: Int): [Deal!]!
deal(id: ID!): Deal
dealSummary(id: ID!): DealSummary
```

`Deal` mirrors the REST shape, with `logos: [DealLogo!]!` (`id`, `contentType`, `width`, `height`, `sizeBytes`, `uploadedAt`; the storage ref stays with the services, and the image itself comes from the REST route, so a share link sees the facts and not the bytes). `DealSummary` is `deal`, `totals` (`DealTotals`), and `sessions` (`[SessionSummary!]!`). There are no mutations; the console creates deals and uploads logos through the REST routes.

## Sponsor relevance profiles (sentiment-service)

The profile relevance scoring uses per streamer (sponsor, aliases, semantic terms, minimum score) is stored in `sponsor_relevance_profiles` and mirrored in memory. At start-up the stored rows load first; the `streamsense.sentiment.relevance.seeds` in config-repo only fill in streamers with no stored profile, so an operator's or a deal's choice survives a restart.

- `GET /api/sentiment/relevance/sponsors` every stored profile.
- `GET /api/sentiment/relevance/sponsors/{streamer}` the profile in effect, 404 when the channel has none.
- `POST /api/sentiment/relevance/sponsors` replaces the channel's profile (`streamer`, `sponsor`, optional `aliases`, `semanticTerms`, `minScore`; the sponsor's configured aliases and terms are merged in). Unknown fields, including the retired `campaignGoal`, are ignored.

## Share links

A deal can be shared read-only. `POST /api/analytics/deals/{id}/share` mints the deal's share token (or returns the existing one, so a sent link keeps working), `DELETE /api/analytics/deals/{id}/share` revokes it, and `GET /api/analytics/share/{token}` resolves a token to its deal (404 when unknown or revoked). The token is on the deal as `shareToken`, private to the streamer like the fee.

The console sends the token as the `X-StreamSense-Share` header on GraphQL requests when the tab has no bearer token (a link carries `?share=<token>`, kept in the tab's session storage). At the gateway, `GatewayAuthWebFilter` lets such a POST to `/graphql` through without a bearer token; nothing else (REST routes, subscriptions) is opened. `ShareLinkInterceptor` then resolves the token against analytics-service, allows only `deal`, `dealSummary`, `sessionSummary`, and `sponsorMoments`, and puts the deal on the GraphQL context, where the resolvers narrow to it: `deal` and `dealSummary` answer only for the shared deal and return it without `fee` or `shareToken`; `sessionSummary` and `sponsorMoments` are always about the deal's sponsor at the deal's terms and answer null for a session outside the deal. Refusals are GraphQL errors with `extensions.code` `SHARE_FORBIDDEN` (another query), `SHARE_TOKEN_INVALID` (unknown or revoked token), or `SHARE_UNAVAILABLE` (analytics-service unreachable).
