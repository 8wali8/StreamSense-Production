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

## After merging `main` (console/01-view-as)

`main` narrowed what an operator is while this branch was open: `AuthScope.isOperator()` is now the operator
role alone, a token without a role (an access link) is no longer one, and `isConfined()` names the streamer
case the channel checks apply to. Merging it raised a question this branch had not: an access link is not
confined to a channel, so with the self-service paths carved out of the operator-only ones it could have
started capture on any channel. A viewer link must not be enough for that, so a **write** to a self-service
path now needs a scope that names a channel: an operator, or a streamer on their own channel
(`Auth.isSelfServiceWrite`, refused as `operator_required`). Reading one of those paths stays an ordinary
read, which an access link may do for any channel. `AccessLinkScopeIntegrationTest` covers both.

The console follows the same rule: `canStartMeasurement` (`src/lib/auth-token.ts`) is true for an operator
sign-in, for no token at all (auth off locally), and for a streamer, so an access-link tab is not shown a
button that would be refused. An operator viewing as a streamer keeps it: the token is still the operator's.

## Review round (Codex on #68)

Five findings on this branch, all fixed:

- **P1, a stopped capture worker was forgotten while still running.** `_stop_channel` popped the worker after
  a five-second join, but a frame or transcript capture blocks for its own timeout (15-60 s), so the channel
  could be reported stopped while the old loop still stored and published, and starting it again would have
  run a second worker under a new capture session. The worker is now kept until the thread is really gone
  (new `CaptureState.STOPPING`, which `remove_channel` returns), `_start_channel` refuses to start a second
  one for a channel that is winding down (409, "try again in a moment"), and the loop drops the frame or
  transcript segment a blocking call returned after its stop event was set, rather than publishing it into a
  session that is over.
- **P2, duplicate configured channels made readiness permanently 503.** Workers are keyed by channel, so
  `TWITCH_VIDEO_CHANNELS=ninja,ninja` started one while `config.channels` still held two, and readiness
  compares the two. One `normalize_channels` in `config.py` now serves `from_env`, the startup list, and the
  runtime switches.
- **P2, the cap did not apply to the deployment's own list.** Both services enforced `max-channels` on the
  runtime routes only, so an oversized startup configuration walked past the bound the cap exists to hold.
  `CaptureConfig.validate()` and chat-service's `start()` refuse it, the same way they refuse other
  misconfiguration, rather than quietly measuring fewer channels than asked.
- **P2, the IRC writer was visible before registration.** A per-channel `PUT` arriving mid-handshake could
  send `JOIN` before `PASS`, `NICK`, and `CAP`, and be told the channel was joined. `activeWriter` is now
  published after `authenticateAndJoin` returns; until then `joinChannel` takes the restart path.
- **P2, the console stopped loading after one of the two reads.** `loading` was a conjunction, so a fast chat
  answer with the capture read still out rendered "This channel is not being measured" and enabled **Start**
  without knowing whether video was already capturing. It is now true while either read is unanswered; the
  test holds the capture response open and fails against the old condition.

### Second round

Five more, four of them consequences of the first round's fixes:

- **Nothing reaped a worker that outlived its stop.** `_stop_channel` left it in place, so the channel read
  `STOPPING` for ever and the console went on saying "Video is being captured" after a successful Stop.
  `_reap_finished_workers` now runs before every read and every channel change: a worker whose thread has
  exited is dropped, and its status is removed when the channel is no longer configured or marked stopped
  when it is. The whole-service snapshot goes through the manager for the same reason.
- **A stopping worker did not hold its slot.** `remove_channel` had already taken the channel out of
  `config.channels`, so the capacity check ignored a worker still spending capture and transcription time.
  Capacity now counts configured channels and live workers together.
- **A restart did not wait for the old IRC connector.** `stop()` interrupted the worker but never joined it,
  and the loop ran on the shared `running` flag, which `start()` sets back to true: the old loop could
  reconnect beside its replacement and its `finally` could clear the replacement's writer. Each connector now
  carries its own flag, `stop()` clears that flag and joins the thread, and the writer is cleared only when
  it is still the one this connector published.
- **The chat cap counted repeats.** `normalizeChannels` kept `ninja` and `#Ninja` as two, so duplicates could
  fill the cap while IRC measured one channel. Both normalisers are `distinct()` now, like the Python side.
- **The new knobs were not in Compose.** `TWITCH_CHAT_MAX_CHANNELS` and `TWITCH_VIDEO_MAX_CHANNELS` were
  documented and read, but neither container's environment forwarded them, so both stayed at 10 whatever a
  deployment set. Both are in `docker-compose.yml` now, which the production overlay inherits.

### Third round

Five more, all in the same seam as the second round:

- **The retiring connector closed the shared socket.** The generation flag stopped the old loop reconnecting,
  but its `finally` still called a helper that closed whatever socket was current, so a loop that outlived a
  stop could close the replacement's connection. It now gives up the socket and the writer only while they
  are still its own; the try-with-resources closes what it opened.
- **A refused start still changed the configuration.** `add_channel` appended the channel before
  `_start_channel` raised "still stopping", so a 409 left readiness expecting a worker nothing would create.
  The stopping worker is checked before the configuration changes.
- **A list switch ignored a surviving worker.** Switching from A to B while A was blocked started B beside it,
  above the cap. The switch now waits, with the same message as the per-channel refusal.
- **A confined status kept the whole service's summary.** Filtering `channels` and `channelStatuses` left
  `state`, `lastFrameAt`, and `lastTranscriptAt` describing every channel, so a streamer whose channel was
  stopped could be shown another channel's `CAPTURING` and its timestamps. `CaptureStatusStore.snapshot`
  takes the channels it may summarise, so the summary always describes what the answer lists.
- **The switch request model capped the list at ten**, contradicting `TWITCH_VIDEO_MAX_CHANNELS` above that.
  The runtime cap is the only bound now.

## A flaky test the CI run found

`GatewayRateLimitIntegrationTest.rejectsRequestsAfterConfiguredBurstLimit` failed once on this branch
(`X-RateLimit-Remaining` expected 0, was 1) in code the branch does not touch. Root cause, from
`InMemoryRateLimiter`: the windows are aligned to the wall clock
(`windowStart = (now / windowSeconds) * windowSeconds`), so two requests a millisecond apart land in
different windows whenever the pair straddles a minute boundary, and the second is counted as the first of a
fresh window. The CI run crossed 02:38:00 mid-test. The test counts inside one window and never waits for one
to end, so it now pins the limiter's clock (`PinnedClockRateLimiters`, in the limiter's package because the
clock-taking constructor is not public) instead of rolling that die every run;
`aWindowBoundaryBetweenTwoRequestsRestartsTheCount` documents the mechanism as a unit test. The limiter itself
is unchanged: a fixed window is what it is meant to be.

## Verification

| Check | Result |
|---|---|
| `mvn verify` api-gateway | 130 tests, 5 skipped (Redis Testcontainer), Spotless, ArchUnit, JaCoCo floor green |
| `mvn verify` chat-service | 59 tests after the review rounds, Spotless, ArchUnit, JaCoCo floor green |
| video-capture-service `ruff check`, `ruff format --check`, `mypy` | clean |
| video-capture-service `pytest` | 69 tests after the review rounds |
| frontend `eslint`, `prettier --check`, `codegen:check`, `vite build` | clean |
| frontend `vitest run --coverage` | 36 files, 145 tests after the review round, floors held (88.2 / 83.7 / 86.3 / 88.2) |

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
