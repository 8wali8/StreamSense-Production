# imports/01-stop-resume

Requirement 1 of the PRD "Stop and speed up past-stream imports" (2026-09-20): a streamer can stop a running import from the row that started it and resume it later, without an operator and without SSH. No confirmation dialog yet and no speed work; those are later steps of the same PRD. Based on `main` after the deal-form change (32bef90).

## What changed

- **analytics-service owns the import's state.** New table `vod_imports` (V8), one row per streamer and recording: the import's state (QUEUED, IMPORTING, STOPPING, STOPPED, DONE, FAILED), the offset both halves have reached, and what chat-service and video-capture-service last reported for their half. The two services keep their status in memory and a restart wipes it, which is why the row exists. `VodImportService.refresh` asks both services where they are, sends a pending stop to a half still running, fails a half whose service has forgotten the import (it restarted) rather than resuming it, and combines the two into one state whose offset follows the slower half. It runs on a schedule (`streamsense.analytics.vod-import-refresh-ms`, 5 s) for every active import and on demand when a channel's imports are read. Routes, both under the channel-scoped path so a streamer reaches their own channel only: `GET /api/analytics/streams/{streamer}/vods/imports` and `POST .../vods/{vodId}/stop`. A resume is the existing import route: it refuses (409) while the import is active and otherwise passes both halves the recorded offset. `VodImport` gained a `status`.
- **chat-service.** The importer takes a `startOffsetSeconds` and pages Twitch from there; its status carries `offsetSeconds` (the last comment published) and `stopRequested`; a per-recording flag stops the page loop between pages (`forEachPage` gained a stop supplier), a queued import is settled at once, and `DELETE /api/chat/replay/{vodId}` is the stop (404 for an unknown recording, idempotent otherwise). The executor is injectable so the tests drive the import task by hand.
- **video-capture-service.** Each import has its own stop event: `stop(vod_id)` ends a queued import at once and a running one at its next sample, or inside the sample, because `run_bounded` now takes a cancel event and kills the ffmpeg process group when it is set (`ProcessCancelledError`); the two samplers pass it through. `shutdown()` is the process-exit path. Status carries `stopRequested`; `DELETE /api/video/capture/replay/{vodId}` is the stop, and both the status and the stop routes answer 404 to a streamer for another channel's import.
- **Console.** The recordings panel polls the analytics imports list (5 s) instead of the capture service's status, so the row reads the same in every tab and after a restart. A queued or importing row gets Stop (no confirmation); after Stop it reads "Stopping…" with the button disabled until both halves confirm, then "Stopped at 1h 12m of 12h 03m" with Resume. Failed imports get Resume too; a session with no recorded import keeps "Retry import".
- **Docs.** `docs/contracts/sessions.md` (the VOD paragraph), `CLAUDE.md` (the analytics row, the recordings panel; the frontend "Layout" paragraph had been duplicated by the two merges of 2026-09-19 and is one again).

## Verification

| Check | Command | Result |
|---|---|---|
| analytics-service | `mvn -pl analytics-service spotless:apply verify` in `maven:3.9-eclipse-temurin-21` | build success; 58 tests; coverage floor held |
| chat-service | `mvn -pl chat-service spotless:apply verify` | build success; 61 tests; coverage floor held |
| video-capture-service | `ruff format`, `ruff check`, `mypy`, `pytest` in `python:3.11.16-slim` with uv | clean; 74 tests |
| Frontend gate | `npm run codegen:check`, `lint`, `format:check`, `test:coverage`, `build` | all pass; 157 tests; statements 89.46 %, branches 84.92 % |

## What to check by hand (the PRD's acceptance for Stop, on the live site, signed in with Twitch, on a channel with recordings)

1. Start an import; wait for "Importing · N%". Press Stop. Within about 15 seconds the row reads "Stopped at …", and the chat and capture logs both show the replay ended (`VOD chat import stopped`, `VOD import stopped`). If a transcription call to ml-engine was in flight, the capture half confirms only when that call returns (up to its 60-second timeout); the row reads "Stopping…" meanwhile.
2. Reload the page in a new tab: the row still reads Stopped.
3. Restart video-capture-service and chat-service on the VM: the row still reads Stopped, not "Import not confirmed", and nothing restarts.
4. Press Resume: progress continues from the stopped offset on both halves (the chat log pages from that offset), the session's event count grows, no event id repeats.
5. Open the same deal on a share link: the recordings panel is not shown.
6. As the operator with imports running on two channels, stop one: only that one stops.
7. As a streamer, `DELETE /api/video/capture/replay/<vodId>` through the gateway is 403 `operator_required`; Stop works only through the analytics route.
8. Restart a service in the middle of an import: the row goes to FAILED with "lost track of the import (restarted?)" and offers Resume; it does not resume on its own.

## Follow-ups (the PRD's later steps)

- The baseline measurement of a one-hour import, then 2.1 (pin the transcript interval in analytics config), 2.2 (one sequential pass), 2.3 (concurrency), and the confirmation before a long import with the measured estimate.
- Imports made before this branch have a session but no `vod_imports` row; their row reads "Import not confirmed" with "Retry import", which creates the row.
