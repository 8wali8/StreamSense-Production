# capture/01-self-serve

A streamer signed in with Twitch can start and stop measurement of their own channel. Based on `main` after the home page began following its deal's sponsor (2026-09-13).

## What was missing

Sign in with Twitch shipped (`auth/04`), and channel scope is enforced end to end (`auth/05`), so a signed-in streamer sees their own reports and manages their own deals. Nothing let them be measured. Pointing chat ingest and video capture at a channel had exactly one route each, `POST /api/chat/twitch/channels` and `POST /api/video/capture/channels`, and both are

- **operator-only** at the gateway (`operator-only-paths` covers every non-read under `/api/chat/**` and `/api/video/**`), and
- **replace the whole list**: the operations page sends `[streamer]`, so pointing at one channel stops every other channel being measured.

The result was a product that reads self-serve and runs single-tenant: a streamer could sign in, create a deal, and see an empty report, because an operator had to point the pipeline at them by hand, and doing so ended someone else's measurement.

Two things made the list-replacing route unsuitable as the streamer's control even with the permission relaxed: it names the whole set, so a streamer could erase other channels with it, and both services tore every worker down and started them again, which mints a new capture session id and so splits a stream already being captured into two sessions.

## What changed

- **api-gateway.** `GatewayEdgeProperties.Auth.selfServicePaths` (`/api/chat/twitch/channels/*`, `/api/video/capture/channels/*`) is carved out of `operatorOnlyPaths`: a write there does not need the operator role. `AuthScope` reads the channel from those paths (`channelInPath`), so the scope check that already refuses another channel's REST paths refuses these too, and `GatewayAuthWebFilter` refuses a self-service path that names no channel rather than waving it through the way it does an unscoped read. The list-replacing routes next to them stay operator-only. Config: `streamsense.gateway.auth.self-service-paths` in `config-server/config-repo/api-gateway.yml`.
- **chat-service.** `TwitchChatLifecycleService.joinChannel` / `partChannel`: one channel, idempotent, leaving the others alone. With a live connector the change is a `JOIN`/`PART` on the socket, so nobody else's chat is dropped; without one, or when a replay alias is involved (not an IRC channel), the connector restarts with the new list. Writes to the socket are serialised on a lock, because the reader thread answers `PING` on the same writer. `PUT`, `DELETE`, and `GET /api/chat/twitch/channels/{channel}` expose them; `GET /status` now tells a streamer about their own channel only, from the scope headers the gateway sets. New `streamsense.twitch.chat.max-channels` (default 10) bounds both the join and the list-replacing route.
- **video-capture-service.** Capture workers are keyed by channel (`dict` instead of a list), with `_start_channel` / `_stop_channel` behind one lock. `add_channel` and `remove_channel` touch one channel; `switch_channels` now diffs instead of restarting everything, so **a channel that stays keeps its worker and its capture session id** — pointing capture at a second streamer no longer splits the first one's stream into two sessions. `PUT`, `DELETE`, and `GET /api/video/capture/channels/{channel}`; `GET /api/video/capture/status` is confined to the calling streamer's channel (channels, per-channel statuses, and imports). New `TWITCH_VIDEO_MAX_CHANNELS` (default 10, the cap the request model already implied).
- **Channel normalisation.** chat-service stripped a leading `#` but not `@`, so a login arriving as `@ninja` from a URL path would have been joined as `#@ninja` while the gateway compared it with the `@` stripped and called it the streamer's own. Both normalisers now strip `[@#]`, like `AuthScope` and analytics-service's `ChannelScopeFilter`. Found by the first test written against the new route.
- **Frontend.** `features/capture/`: `measurement.ts` (a pure reading of the two per-channel statuses), `useChannelMeasurement.ts` (both reads polled, both writes issued together), `MeasureChannel.tsx` (the control on the streamer home, above the live strip, not rendered in the demo). Chat ingest and video capture are separate services, so a half-failure names the half that refused ("Could not start video capture (…already measuring 10 channels)") instead of claiming the channel is or is not measured, and a channel running only one half says which report field will be empty. `src/api/chat.ts` and `src/api/video.ts` gained the per-channel calls; `src/test/msw.ts` learned `put` and `delete`.

## Verification

| Check | Result |
|---|---|
| `mvn verify` api-gateway | 123 tests, 5 skipped (Redis Testcontainer), Spotless, ArchUnit, JaCoCo floor green |
| `mvn verify` chat-service | 57 tests (18 new), Spotless, ArchUnit, JaCoCo floor green |
| video-capture-service `ruff check`, `ruff format --check`, `mypy` | clean |
| video-capture-service `pytest` | 59 tests (9 new) |
| frontend `eslint`, `prettier --check`, `codegen:check`, `vite build` | clean |
| frontend `vitest run --coverage` | 35 files, 138 tests (8 new), floors held (88.0 / 83.3 / 86.1 / 88.0) |

What the new tests pin down: the gateway lets a streamer `PUT` and `DELETE` their own channel and refuses another's with `channel_forbidden`, while the list-replacing route still answers `operator_required` (`ChannelScopeIntegrationTest`); join and part are idempotent, keep the other channels, respect the cap, and leave ingest waiting when the last channel goes (`TwitchChatLifecycleServiceTest`); the status read is confined to a streamer's own channel (`TwitchChatStatusControllerTest`, `test_status_tells_a_streamer_about_their_own_channel_only`); a channel that survives a switch keeps its worker and session id (`test_switch_channels_leaves_a_channel_that_stays_running`); and the control reports a half-failure by name (`MeasureChannel.test.tsx`).

## Manual checks for the reviewer

1. With `STREAMSENSE_GATEWAY_AUTH_TWITCH_ENABLED=true`, sign in as a streamer who is not on the operator list. The home page shows "This channel is not being measured" with a **Start measuring** button; the Operations link stays hidden.
2. Press it while another channel is already being measured. Both channels are measured afterwards: `GET /api/chat/twitch/status` and `GET /api/video/capture/status` as an operator list both, and the first channel's `captureSessionId` is unchanged (this is the session-splitting fix).
3. As the streamer, read `GET /api/chat/twitch/status` and `/api/video/capture/status` directly: each names only their own channel.
4. `curl -X PUT` the other channel's path with the streamer's token: 403 `channel_forbidden`.
5. Press **Stop measuring**: chat leaves the channel and capture stops, and the other channel keeps running.
6. With `STREAMSENSE_TWITCH_CHAT_ENABLED=false` and `STREAMSENSE_TWITCH_VIDEO_ENABLED=false`, the button is disabled and the line says an operator turns them on.

## Deliberately left alone

- **The operations page still uses the list-replacing routes.** They remain the operator's blunt tool for pointing the whole pipeline at one channel (the replay alias workflow depends on it). Only the permission and the restart behaviour changed underneath.
- **No per-streamer capacity accounting.** The cap is per service (10 channels each), first come first served; a streamer who arrives at the cap is told to try later rather than queued. A deployment measuring more channels than one capture container can sample needs more than a queue, and the numbers to size it do not exist yet.
- **Relevance still follows the deal, not this control.** Starting measurement does not point sponsor relevance anywhere: `DealService` does that from the deal's dates, which is what the home page follows.
- **No Helix watch-list change.** analytics-service already polls every streamer with a running deal and every login with recent events, so a channel that starts being measured is picked up by the next poll without a new dependency.
- **`lastError` in the chat status is not filtered for a streamer.** It describes the shared connector (missing credentials, a dropped socket), not another channel's data.
