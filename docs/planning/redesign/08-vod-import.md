# 08-vod-import: importing past streams from Twitch recordings

Branch `redesign/08-vod-import`, cut from `redesign/main` after 07. Serves a new story, S8: a streamer who joins with a deal already running wants the streams they have already done for it in the deal. Contract in `docs/contracts/sessions.md` (VOD sessions and the import endpoints).

## How it works

A recording is imported by replaying it through the normal pipeline with its original timestamps. analytics-service reads the recording's metadata from the Helix videos endpoint, records a closed session with Twitch's start and duration (source `VOD`, or the existing Helix session of the same broadcast when the channel was being watched live), then asks chat-service to publish every comment Twitch still has for the video at `createdAt + offset`, and video-capture-service to sample the recording's frames at the live sample interval and transcribe its audio in consecutive segments, stamping each sample at `createdAt + offset`. Every event carries `streamSessionId = <login>-vod-<videoId>` and `source = TWITCH_VOD_IMPORT`; analytics-service recognises the key and does not open a capture session for it. Sentiment, relevance, detection, and the per-minute buckets are computed exactly as for a live stream, so the report, the deal totals, and the track record need nothing special. Twitch keeps no concurrent viewer history for a recording, so the streamer may enter the stream's average viewers from their dashboard; it becomes the session's one viewer sample and prices the value tiles. Without it those tiles stay empty for that stream.

## What changed

- **analytics-service**: `V6__vod_sessions.sql` (`vod_id` on sessions); `TwitchHelixClient.archives` and `video` (Helix `users` and `videos`, durations like `3h2m1s`); `StreamSessionService.recordVod`, source `VOD`, capture sessions never opened for an imported key, VOD sessions hide overlapping capture sessions like Helix ones; `VodImportService` and `VodImportController` (`GET /api/analytics/streams/{streamer}/vods`, `POST .../vods/{vodId}/import`); `ChatReplayClient` and `CaptureReplayClient` (bounded, exist only with `streamsense.services.chat-service` and `video-capture-service` base URLs); `StreamSession.vodId`.
- **chat-service**: `POST /api/chat/replay` and `GET /api/chat/replay/{vodId}` (`TwitchVodChatImportService`): the whole comment list from the Twitch GraphQL endpoint, published at the original times with idempotent event ids; `max-pages` raised so a long stream's chat is walked in full.
- **video-capture-service**: `vod_import.py` (`VodImportManager`, one import at a time, frames at the live sample interval so the exposure arithmetic holds, consecutive transcript segments by default, the HLS playlist re-resolved when it expires, progress and failures in the status); `POST /api/video/capture/replay`, `GET /api/video/capture/replay/{vodId}` (under the capture prefix the gateway routes to this service), and `imports` on the capture status.
- **api-gateway**: `StreamSession.vodId` in the schema.
- **Frontend**: the deal page's owner view gains "Earlier streams on Twitch" (`features/deals/ImportStreams.tsx`, pure helpers in `import-streams.ts`): the recordings inside the deal's dates (or all of them), an average-viewers field, Import, progress from the capture status, and "Open report" once imported. VOD links on the report now use the session's `vodId`, so an imported or live-watched-then-imported stream links to the recording at the moment.
- **Config and cluster**: `streamsense.services.chat-service` and `video-capture-service` in `analytics-service.yml`; network policy egress from analytics-service to both and the matching ingress rules; `TWITCH_VOD_IMPORT` documented as a source value in the event schemas.
- **Found and fixed on the running stack while testing the import**: on Postgres a duplicate-key insert aborts the transaction, so the "insert, ignore the duplicate, then update" in `SponsorMentionRepository` and `ChatResponseRepository` (branch 03) dead-lettered every second mention or command in a bucket; both now use `on conflict do nothing` on Postgres, as the older bucket repositories do (`persistence/Dialect`). The Helix client retries once on a transient I/O failure and its timeouts allow the container's slow first name lookup. The ml-engine image now creates the four model cache directories before handing them to uid 10001, so a fresh Compose volume is writable and Whisper can download; before this, transcription failed on every fresh stack with "local Whisper model could not be loaded".
- **Tests** (minimal): Helix video parsing and durations; `VodImportTest` (list, import as a closed VOD session with the viewer sample, a replayed event joins it without opening another, re-import reuses it, unknown video is 400); chat import service (timestamps, ids, key, source); capture import schedule and the disabled-capture refusal; frontend recordings filter, progress labels, and the import flow on the deal page.

## Verification

Run on 2026-09-09.

| Check | Result |
|---|---|
| `mvn verify` analytics-service | 34 tests pass, coverage floor holds |
| `mvn verify` chat-service | 44 tests pass |
| `mvn verify` api-gateway | 101 tests pass (Docker was running, so the Redis Testcontainer tests ran too) |
| video-capture-service `ruff check`, `ruff format` | clean |
| video-capture-service `pytest` | 51 pass; `test_frame_endpoint_serves_files_under_storage_root` fails on Windows only (path check), untouched by this branch and green on Linux CI |
| video-capture-service `mypy` | two pre-existing Windows-only errors in `process.py` (`signal.SIGKILL`), untouched by this branch |
| `npm run test:coverage` | 26 files, 93 tests pass, floors hold |
| `npm run lint`, `format:check`, `codegen:check`, `npm run build` | clean |
| `tools/k8s/check_network_policies.py`, `kubectl kustomize .` | OK |

## Manual checks for the reviewer

1. With Helix enabled and a deal on a real channel, open the deal: "Earlier streams on Twitch" lists the channel's recordings inside the deal's dates.
2. Enter an average viewer figure and click Import. (Verified on 2026-09-09 against the official `redbull` channel: its 46-minute "Red Bull Reshuffle 2026" recording imported as a closed VOD session, 53 chat comments landed at their original times, frames were sampled every 10 seconds with detections priced against the viewer figure, and the report opened from the deal.) The row shows "Importing · N%" from the capture status; chat lines appear in the session's report within a minute, frames and transcripts follow at roughly the recording's length divided by the sampling speed of the machine.
3. Open the report: it says it is part of the deal, the timeline's VOD links open the recording at the moment, and the value tiles price at the deal's terms with the viewer figure entered.
4. Import the same recording again: the same session is reused and no duplicate appears in the deal.

## Left for later branches

- An import runs one recording at a time per capture service and takes roughly a frame sample per second of wall time; a long backlog is hours of work. Sampling frames more sparsely would undercount exposure unless the per-detection credit scaled with it.
- Relevance scoring during an import uses the channel's current profile; importing a recording from a deal with a different sponsor than the active one needs the profile pointed at that sponsor first.
- Twitch deletes recordings after 7 to 60 days depending on the account; older streams cannot be imported.
- Channel point redemptions and tracked-link clicks are not in recordings.
