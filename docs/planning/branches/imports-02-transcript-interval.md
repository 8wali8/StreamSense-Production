# imports/02-transcript-interval

Step 2.1 of the PRD "Stop and speed up past-stream imports" (2026-09-20), after the baseline measurement it prescribes. Based on `main` after imports/03 (e22330d), rebased on 2026-09-22 over #77 and #78; the stride goes along with the per-half capture offset.

## What changed

- **The transcript stride of an import is pinned in analytics-service config and sent every time.** `streamsense.analytics.vod-import-transcript-interval-seconds` (default 10, the live capture's segment length; `STREAMSENSE_ANALYTICS_VOD_IMPORT_TRANSCRIPT_INTERVAL_SECONDS` in `config-repo/analytics-service.yml`) is what `CaptureReplayClient.replay` puts in `transcriptIntervalSeconds` on every import and resume, so the import never depends on the capture service's own default and the console has no say in it. Nothing is sampled, and the report carries no sampling line. The value on the live site was already 10 by way of the capture service's default, so the report's transcript numbers do not move; this change fixes where the number lives.
- **Docs.** `docs/contracts/sessions.md`.

## Baseline (the PRD's first measurement), taken on the VM on 2026-09-20 before this change

Neither channel on the live site has a recording near an hour (xqc's run eight to fifteen hours; the owner's has none), so the baseline is "per hour of recording": xqc's shortest recording (2872612045, 489 min) was imported with imports/01 deployed and the capture half's offset was polled every 30 s.

| Measure | Value |
|---|---|
| Recording offset reached | 2,010 s (33.5 min) in 942 s of wall time; the run was cut short at 18:25 UTC by a redeploy of the stack (PR #74), not by a failure |
| Rate of the capture half (frames plus 10 s transcripts, sequential, per-sample HLS seeks) | 2.1 times real time, steady from the first sample to the last (540 s at 187 s, 1,090 s at 459 s, 1,670 s at 761 s, 2,010 s at 942 s) |
| Wall time per hour of recording, capture half | about 28 minutes |
| Frames and transcripts published | 202 frames, 155 transcript clips, 0 failures |
| Wall time per transcript clip, end to end (seek, capture, Whisper small.en on the CPU) | about 5.5 s per 10 s clip |
| Chat half | failed within 30 s (see below); its wall time could not be measured |

The redeploy also exercised the restart path for real: the capture service came back with no memory of the import and analytics marked the row FAILED with "video-capture-service lost track of the import (restarted?)" rather than resuming it. A resume afterwards continued (202) and a Stop at +90 s read STOPPING, then STOPPED within 5 s, with `VOD import stopped vod=2872612045 channel=xqc at offset=371s` in the capture log and the DELETE answered 200; a second Stop changed nothing. That resume started the capture half from the import's combined offset (23 s) rather than from where the capture half had been, because the failed chat half pins the combined offset at its own; a per-half resume offset is the fix and is a separate branch.

The chat half failed within 30 s: Twitch's undocumented comments endpoint answered the request with an integrity error. That is Twitch refusing automated access to that endpoint, not something this project should work around; the chat side of imports is unavailable on the live site until the owner decides what to do about it (accept imports without chat, or drop chat replay from imports). The capture-half figures above are therefore the baseline that matters for 2.2 and 2.3, whose cost is entirely on that side.

## Verification

| Check | Command | Result |
|---|---|---|
| analytics-service | `mvn -pl analytics-service spotless:apply verify` in `maven:3.9-eclipse-temurin-21` | build success; 58 tests; coverage floor held |

## What to check by hand

1. After the deploy, start an import and look at the capture service's log line for the replay request (or `GET /api/video/capture/replay/{vodId}` as the operator): the transcript stride is 10 and the transcripts published count grows one per 10 s of recording.
2. The session report of an import made after this change shows the same voice-mention numbers as one made before it for the same recording window.

## Follow-ups (the PRD's later steps)

- 2.2, one sequential pass over the recording (measure against the baseline above), then 2.3 if the target is still unmet, then the confirmation before a long import with the measured estimate.
- With one half failed, the import's combined offset holds at the failed half's offset, so the row reads "Importing · 0%" while the capture half runs on for hours. Truthful by the PRD's rule, but worth a line in the row ("chat failed; video continues") once the chat side's future is decided.
