# Stream sessions

A session is one stream: the unit the session report, the deal page, and the home page's history are built on.

## Where sessions come from

- **HELIX.** analytics-service polls the Twitch Helix `streams` endpoint for every watched channel (the configured `streamsense.twitch.helix.channels` plus every streamer that produced an event in the last `watch-streamers-seen-within-hours`). A channel that is live gets a session keyed by its Twitch stream id, with the title, category, and a viewer sample per poll. A watched channel that stops being live closes its open session at the last poll that saw it. The poller runs only when `streamsense.twitch.helix.enabled=true` and the `TWITCH_CLIENT_ID` and `TWITCH_CLIENT_SECRET` secrets are present; it authenticates with the client-credentials app token, never a user token.
- **CAPTURE.** Every aggregated event that carries a `streamSessionId` (frames, transcript segments, and the sentiment derived from them; chat messages when the source sets one) opens or extends a capture session keyed by that id. A capture session with no event for `streamsense.analytics.capture-session-idle-close-minutes` closes at its last event. This is what the replay alias produces, since it is not a real Twitch channel.
- **VOD.** A recording imported from Twitch (`POST /api/analytics/streams/{streamer}/vods/{vodId}/import`). The session takes the recording's title, start, and duration from the Helix `videos` endpoint and is closed from the start; when the channel was being watched live for that broadcast, the existing Helix session is reused (matched on Twitch's `stream_id`) and gains the `vodId`. The recording's chat, frames, and audio are then replayed through chat-service and video-capture-service with their original timestamps (`createdAt + offset`) and `streamSessionId = <login>-vod-<videoId>`, `source = TWITCH_VOD_IMPORT`; analytics-service recognises that key and never opens a capture session for it, so the events land in the imported session and every number is computed as for a live stream. Twitch keeps no concurrent viewer history for a recording: the streamer may supply `averageViewers`, which becomes the session's one viewer sample; without it the value fields are null. `GET /api/analytics/streams/{streamer}/vods?limit=` lists the channel's recordings (`vodId`, `streamId`, `title`, `createdAt`, `durationMs`, `url`, `viewCount`, and `sessionId` once imported); both need Helix enabled (409 otherwise). chat-service exposes `POST /api/chat/replay` and `GET /api/chat/replay/{vodId}`, video-capture-service `POST /api/video/capture/replay`, `GET /api/video/capture/replay/{vodId}`, and `imports` on its capture status (all under the capture prefix the gateway routes to it); analytics-service calls them through `streamsense.services.chat-service` and `video-capture-service`. Every id along the way is deterministic (frame `vod-<id>-f<offset>`, audio segment `vod-<id>-a<offset>`, chat `vod-<id>-import-<comment>`, and the detection and sentiment ids derived from them), so importing a recording again, or resuming a failed import from where it stopped (the capture service does that on its own when asked again), never counts anything twice.

When a capture session overlaps a Helix session of the same streamer, only the Helix one is reported. Metrics are not re-keyed: a session's numbers are the buckets for that streamer between `startedAt` and `endedAt`.

## REST (analytics-service)

- `GET /api/analytics/streams/{streamer}/sessions?from=&to=&limit=` sessions overlapping `[from, to)` in epoch millis (both optional), newest first, `limit` 1 to 200 (default 20).
- `GET /api/analytics/sessions/{id}` one session, 404 when unknown.

## GraphQL (api-gateway)

```graphql
sessions(streamer: String!, from: Float, to: Float, limit: Int): [StreamSession!]!
session(id: ID!): StreamSession
```

`StreamSession`: `id`, `streamer`, `source` (`HELIX`, `CAPTURE`, or `VOD`), `twitchStreamId`, `streamSessionId`, `channelLogin`, `title`, `category`, `startedAt`, `endedAt` (null while live), `live`, `durationMs` (to now while live), `peakViewers`, `averageViewers`, `viewerSamples` (viewer fields null or 0 until the poller has sampled), `vodId` (the Twitch recording an imported session came from, also set on a Helix session imported afterwards; the console's VOD links use it).

## Session report

- `GET /api/analytics/sessions/{id}/summary?sponsor&chatCommand&trackedLinkHost&cpmPer30sEquivalent&hostReadRatePer1000` (analytics-service), every option optional. The sponsor defaults to the most visible one in the session. Returns exposure (`onScreenMs`, `onScreenShare`), mentions by channel with sentiment shares, viewers, risk, the stream baseline, `value` (logo value from prominence-weighted viewer-minutes at a CPM, host reads at a per-listener rate, null while the session has no viewer samples, with the `basis` spelled out), and `response` (uses and distinct users of the chat command, posts of the tracked link host). Chat commands, link hosts, sponsor mentions per channel, and detection box areas are counted per bucket at ingest; nothing is re-aggregated per session.
- The four existing analytics endpoints (`summary`, `timeseries`, `sponsors`, `risk`) accept `sessionId` (a whole stream) or `from` and `to` (an absolute range, at most `max-range-hours`) instead of `windowMinutes`.
- `GET /api/sentiment/sponsor/range` and `/api/sentiment/transcript/sponsor/range` (sentiment-service) and `GET /api/video/detections/range` (video-service) return the raw sponsor-relevant lines and detections in `[from, to]`, oldest first, for the timeline.

GraphQL:

```graphql
sessionSummary(sessionId: ID!, sponsor: String, chatCommand: String, trackedLinkHost: String, cpmPer30sEquivalent: Float, hostReadRatePer1000: Float): SessionSummary
sponsorMoments(sessionId: ID!, sponsor: String): SponsorMoments
streamMetricsSummary(streamer: String!, streamSessionId: String, windowMinutes: Int, sessionId: ID, from: Float, to: Float): StreamMetricsSummary!
```

`sponsorMoments` is composed in the gateway: detections collapse into on-screen `segments` (a gap over 30 seconds starts a new one; `offsetMs` is from the session start, `videoTimestampMs` when capture recorded one), sponsor-relevant chat groups into per-minute `chatMoments`, voice lines are `voiceMentions`, buckets flagged as negative spikes are `riskSpikes`, `best` is the strongest positive chat minute, and `weakest` is the most negative mention when there is one.

## Configuration

`streamsense.analytics.value.*` (`cpm-per-30s-equivalent`, `host-read-rate-per-1000`, `prominence-base`, `prominence-area-scale`) and `streamsense.analytics.response.*` (`default-chat-command`, `default-tracked-link-host`) are the summary defaults a request or, later, a deal overrides. `streamsense.analytics.max-range-hours` bounds absolute-range queries.


`config-server/config-repo/analytics-service.yml`, `streamsense.twitch.helix.*`: `enabled`, `client-id`, `client-secret` (from the secrets), `base-url`, `token-url`, `poll-interval-ms`, `channels`, `watch-streamers-seen-within-hours`, `connect-timeout-ms`, `read-timeout-ms`, `token-refresh-margin-seconds`. `streamsense.analytics.capture-session-idle-close-minutes` closes idle capture sessions.

Kubernetes: analytics-service's network policy allows egress to public addresses on 443 for `api.twitch.tv` and `id.twitch.tv`.
