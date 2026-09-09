# Stream sessions

A session is one stream: the unit the session report, the deal page, and the home page's history are built on.

## Where sessions come from

- **HELIX.** analytics-service polls the Twitch Helix `streams` endpoint for every watched channel (the configured `streamsense.twitch.helix.channels` plus every streamer that produced an event in the last `watch-streamers-seen-within-hours`). A channel that is live gets a session keyed by its Twitch stream id, with the title, category, and a viewer sample per poll. A watched channel that stops being live closes its open session at the last poll that saw it. The poller runs only when `streamsense.twitch.helix.enabled=true` and the `TWITCH_CLIENT_ID` and `TWITCH_CLIENT_SECRET` secrets are present; it authenticates with the client-credentials app token, never a user token.
- **CAPTURE.** Every aggregated event that carries a `streamSessionId` (frames, transcript segments, and the sentiment derived from them; chat messages when the source sets one) opens or extends a capture session keyed by that id. A capture session with no event for `streamsense.analytics.capture-session-idle-close-minutes` closes at its last event. This is what the replay alias produces, since it is not a real Twitch channel.

When a capture session overlaps a Helix session of the same streamer, only the Helix one is reported. Metrics are not re-keyed: a session's numbers are the buckets for that streamer between `startedAt` and `endedAt`.

## REST (analytics-service)

- `GET /api/analytics/streams/{streamer}/sessions?from=&to=&limit=` sessions overlapping `[from, to)` in epoch millis (both optional), newest first, `limit` 1 to 200 (default 20).
- `GET /api/analytics/sessions/{id}` one session, 404 when unknown.

## GraphQL (api-gateway)

```graphql
sessions(streamer: String!, from: Float, to: Float, limit: Int): [StreamSession!]!
session(id: ID!): StreamSession
```

`StreamSession`: `id`, `streamer`, `source` (`HELIX` or `CAPTURE`), `twitchStreamId`, `streamSessionId`, `channelLogin`, `title`, `category`, `startedAt`, `endedAt` (null while live), `live`, `durationMs` (to now while live), `peakViewers`, `averageViewers`, `viewerSamples` (viewer fields null or 0 until the poller has sampled).

## Configuration

`config-server/config-repo/analytics-service.yml`, `streamsense.twitch.helix.*`: `enabled`, `client-id`, `client-secret` (from the secrets), `base-url`, `token-url`, `poll-interval-ms`, `channels`, `watch-streamers-seen-within-hours`, `connect-timeout-ms`, `read-timeout-ms`, `token-refresh-margin-seconds`. `streamsense.analytics.capture-session-idle-close-minutes` closes idle capture sessions.

Kubernetes: analytics-service's network policy allows egress to public addresses on 443 for `api.twitch.tv` and `id.twitch.tv`.
