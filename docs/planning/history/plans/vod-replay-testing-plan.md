# VOD Replay Testing Plan

## Objective

Add a repeatable VOD replay mode so StreamSense can run the full live stack against a fixed Twitch video and chat history. This makes demos, debugging, sponsor relevance tuning, and regression testing reproducible without depending on an active livestream.

The first target replay alias is:

1. Streamer/channel input: `redbull-testing`
2. Twitch VOD: `https://www.twitch.tv/videos/2750461300`
3. Twitch VOD id: `2750461300`

When `redbull-testing` is loaded in the frontend, the system should behave as if it is watching a live channel. Video frames, transcript segments, chat messages, sentiment, sponsor detections, analytics, GraphQL queries, and subscriptions should all flow through the same existing Kafka/service paths.

## Current State

The current Twitch integration is live-channel oriented:

1. The frontend posts selected channels to `/api/chat/twitch/channels` and `/api/video/capture/channels`.
2. `chat-service` connects to Twitch IRC for live chat messages.
3. `video-capture-service` uses `streamlink --stream-url https://www.twitch.tv/{channel}` to resolve a live HLS URL.
4. `video-capture-service` samples frames and audio from the resolved live stream.
5. Transcript, sentiment, sponsor detection, analytics, GraphQL, and frontend views already operate on normal events keyed by `streamer`.

Sponsor-specific sentiment can legitimately be empty if the replay content does not mention the configured sponsor. Transcript and general sentiment should not depend on sponsor relevance.

## Recommended Direction

Add replay support as an alias layer at the ingestion boundary. Downstream services should not need replay-specific branches.

Recommended first version:

1. Recognize `redbull-testing` as a configured replay alias.
2. Route video/transcript capture to Twitch VOD `2750461300`.
3. Replay VOD chat messages on a wall-clock schedule.
4. Publish all replay data with `streamer = "redbull-testing"`.
5. Use current wall-clock timestamps for event timestamps so subscriptions and dashboards behave like live data.
6. Preserve VOD-relative timing in `videoTimestampMs` and `transcriptSequence`.
7. Mark replay events with `source = "TWITCH_VOD_REPLAY"`.

## Replay Alias Configuration

Add a small config block for replay aliases. This can start in environment/config-server and later move to a persisted admin model.

Suggested shape:

```yaml
streamsense:
  replay:
    aliases:
      redbull-testing:
        provider: twitch
        vodId: "2750461300"
        vodUrl: "https://www.twitch.tv/videos/2750461300"
        replaySpeed: 1.0
        startOffsetSeconds: 0
        source: TWITCH_VOD_REPLAY
```

Implementation notes:

1. Normalize aliases the same way live channels are normalized: lowercase, trim, remove leading `@` or `#`.
2. If a channel is not a replay alias, keep the current live Twitch behavior.
3. If a channel is a replay alias, both chat and video capture should enter replay mode.

## Video And Transcript Replay

Update `video-capture-service` to support VOD replay sources.

Steps:

1. Extend the channel switch path to resolve each requested channel as either live Twitch or replay alias.
2. Extend `TwitchSourceResolver` so it can resolve `https://www.twitch.tv/videos/2750461300`, not only `https://www.twitch.tv/{channel}`.
3. Create a replay session clock when capture starts:
   - `replayStartedAtMs = current wall-clock time`
   - `startOffsetSeconds = configured start offset`
   - `replaySpeed = configured speed`
4. For each sample, compute:
   - `elapsedSeconds = (nowMs - replayStartedAtMs) / 1000`
   - `vodOffsetSeconds = startOffsetSeconds + elapsedSeconds * replaySpeed`
5. Add optional seek support to frame capture and audio capture.
6. For VOD replay, run ffmpeg with `-ss <vodOffsetSeconds>` before `-i <hls_url>`.
7. Publish normal frame and transcript events.

Frame event fields:

1. `streamer = "redbull-testing"`
2. `source = "TWITCH_VOD_REPLAY"`
3. `channelLogin = "redbull-testing"`
4. `streamSessionId = "redbull-testing-2750461300-<session-start-ms>"`
5. `twitchStreamId = "2750461300"`
6. `videoTimestampMs = vodOffsetSeconds * 1000`
7. `capturedAt = current wall-clock time`

Transcript event fields:

1. `streamer = "redbull-testing"`
2. `source = "TWITCH_VOD_REPLAY"`
3. `channelLogin = "redbull-testing"`
4. `streamSessionId = "redbull-testing-2750461300-<session-start-ms>"`
5. `twitchStreamId = "2750461300"`
6. `videoTimestampMs = vodOffsetSeconds * 1000`
7. `startedAt` and `endedAt` use current wall-clock time
8. `transcriptSequence` increments like live capture

## VOD Chat Replay

Live Twitch IRC cannot provide historical VOD chat, so replay mode needs a separate chat source.

Recommended path:

1. Download VOD chat for `2750461300` into a local/cacheable JSON artifact.
2. Parse comments into normalized messages with original VOD offsets.
3. Start a replay clock using the same `startOffsetSeconds` and `replaySpeed` as video capture.
4. Publish each chat message when its original VOD offset is reached.
5. Publish through the existing `ChatEventIngestService` path so Kafka topics and sentiment consumers remain unchanged.

Potential chat download options:

1. TwitchDownloaderCLI as an external tool for reliable VOD chat export.
2. Twitch comments GraphQL endpoint if we want direct integration and can tolerate endpoint instability.
3. A checked-in small fixture for tests, plus real downloader support for manual demos.

Chat event fields:

1. `eventId = "vod-2750461300-chat-<comment-id-or-sequence>"`
2. `streamer = "redbull-testing"`
3. `user = original commenter display name/login`
4. `message = original comment body`
5. `timestamp = current wall-clock time when replayed`

## Frontend Behavior

No special frontend workflow should be required.

Expected behavior:

1. User types `redbull-testing` into the streamer field.
2. User clicks `Load Console`.
3. Frontend posts `redbull-testing` to the existing chat and video channel switch endpoints.
4. The Twitch player area either embeds the VOD URL or shows a replay-mode panel if direct embedding is unreliable.
5. Transcript, chat, sentiment, sponsor detections, and metrics populate over time like a live stream.

Sponsor-specific sentiment should remain an optional filtered view. If no sponsor-related text exists, the UI should show an empty sponsor sentiment state while transcript and general chat sentiment continue to populate.

## Downstream Services

Keep these services replay-agnostic:

1. `sentiment-service`
2. `video-service`
3. `analytics-service`
4. `recommendation-service`
5. `api-gateway`
6. Frontend GraphQL queries and subscriptions

They should continue to consume and expose events by `streamer`. The replay source marker is metadata, not a separate pipeline.

## Implementation Order

1. Add replay alias config and resolver helpers.
2. Add VOD URL resolution in `video-capture-service`.
3. Add seek-aware frame capture.
4. Add seek-aware audio capture and transcript publishing.
5. Add VOD chat artifact download/cache support.
6. Add chat replay scheduler in `chat-service` or a small dedicated replay publisher.
7. Wire `redbull-testing` through existing channel switch endpoints.
8. Add frontend VOD embed or replay-mode placeholder for known replay aliases.
9. Add smoke tests and a startup command for replay mode.

## Tests

Unit tests:

1. `redbull-testing` resolves to VOD `2750461300`.
2. Normal Twitch channel names still resolve as live channels.
3. Replay clock computes expected VOD offsets for speed `1.0` and non-zero start offsets.
4. Frame/audio samplers include seek arguments only for replay sources.
5. VOD chat parser maps comments to normalized chat events.
6. Chat scheduler emits messages at the expected replay offsets.

Integration smoke test:

1. Start the stack.
2. POST `{"channels":["redbull-testing"]}` to `/api/video/capture/channels`.
3. POST `{"channels":["redbull-testing"]}` to `/api/chat/twitch/channels`.
4. Wait 30-60 seconds.
5. Verify recent transcript segments exist for `redbull-testing`.
6. Verify recent chat sentiment exists for `redbull-testing` once chat replay is enabled.
7. Verify recent frame/sponsor events include `source = "TWITCH_VOD_REPLAY"`.
8. Verify GraphQL subscriptions receive events without replay-specific query changes.

## Open Questions

1. Should VOD chat download happen on demand when `redbull-testing` is loaded, or should it be pre-cached as a fixture?
2. Should replay loop when it reaches the end of the VOD, or stop and report a completed state?
3. Should replay speed be fixed at `1.0` for demos, or configurable through an admin/debug endpoint?
4. Should the frontend show the actual Twitch VOD embed, or keep the current live player area and rely on sampled frames/transcripts?
