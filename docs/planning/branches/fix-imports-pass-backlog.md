# fix/imports-pass-backlog

Found on the VM on 2026-09-30 (UTC) while measuring the sequential pass of imports/04 (step 2.2 of the PRD "Stop and speed up past-stream imports"): ffmpeg was never paused, however far ahead of the import it ran. Based on `main` at b4054d3.

## The defect

`SequentialPass.throttle` pauses ffmpeg once it is more than `TWITCH_VOD_IMPORT_BACKLOG_SAMPLES` (30) samples ahead of the consumer. It took "how many frames ffmpeg has produced" from the number of frame files in the scratch directory, but the import deletes each frame after use, so that number is the backlog itself. Subtracting the consumed count from it a second time gives a figure that shrinks as the import goes on and is soon negative, and the pause never comes. imports/04's tests drove `throttle` without deleting anything, so they passed.

On the VM, at the 38th sample there were 45 frames waiting on disk and at the 77th there were 75, against a limit of 30, with ffmpeg running (`State: S`) both times. ffmpeg decoded the recording at about 4.6 times real time from the start, taking 3.5 to 3.9 of the machine's 8 processors next to Whisper's 3 (load average 12 to 14).

## What changed

- **`SequentialPass._produced_frames`** is one past the highest frame index on disk, which a deleted frame does not change. `throttle` is as it was.
- Tests: `test_frames_the_consumer_deleted_still_count_as_produced` (no process, runs anywhere) and `test_ffmpeg_is_paused_by_how_far_ahead_it_is_not_by_how_many_files_are_left`, which consumes and deletes three hundred frames the way the import does before it checks the pause, that nothing is written while paused, and the resume. Both fail on the code before this change (run against it in the gate's container).

## Measurement of imports/04 as merged (with the defect), on the VM, 2026-09-30 00:34 to 01:03 UTC

The same recording as the baseline (xqc 2872612045), capture half, resumed at 371 s with `TWITCH_VOD_IMPORT_MODE=sequential`, the offset polled every 30 s, stopped once it had covered an hour of recording. No live channel was being measured.

| Measure | Baseline (seek per sample, 2026-09-20) | Sequential pass as merged |
|---|---|---|
| Recording covered | 2,010 s in 942 s of wall time | 3,600 s (371 s to 3,971 s) in 1,718 s of wall time |
| Rate of the capture half | 2.1 times real time | 2.1 times real time (670 s at 301 s, 1,350 s at 603 s, 1,850 s at 904 s, 2,510 s at 1,206 s, 3,160 s at 1,507 s) |
| Wall time per hour of recording | about 28 minutes | 28.6 minutes |
| Wall time per sample (a frame and a 10 s transcript clip) | about 5.5 s per transcript clip | 4.8 s |
| Published | 202 frames, 155 transcript clips, 0 failures | 361 frames, 350 transcript clips, 0 failures |
| ffmpeg processes for the import | one per frame and one per clip | one |

**The pass as merged is no faster than the baseline.** The seeks it removed were not what the time went on: ml-engine answered 41 to 47 transcriptions in each three minutes sampled, about 4 s a clip, which is nearly all of the 4.8 s a sample took. Whether ffmpeg running unpaused next to Whisper slowed those transcriptions is what the measurement after this fix will show; it is not known yet.

The hand checks of imports/04 that do not depend on the pacing held: one ffmpeg process during the import; on the stop, `VOD import stopped vod=2872612045 channel=xqc at offset=3981s frames=361 transcripts=350` one second after the request, no ffmpeg process left, and the scratch directory removed. The check that the directory "holds a few dozen files at most" failed, which is this defect.

## Verification

| Check | Command | Result |
|---|---|---|
| video-capture-service | `ruff format`, `ruff check`, `mypy`, `pytest` in `python:3.11.16-slim` with uv | clean; 85 tests |
| Measurement on the VM with the fix | the same recording and method as above | pending: needs this build on the VM |

## What to check by hand

1. After the deploy, import a recording and, a few minutes in, count the files in `/tmp/streamsense-video-capture/vod-pass-<id>` inside the capture container: never more than about 60 (30 samples, a frame and a clip each), and `grep State /proc/<ffmpeg pid>/status` reads `T (stopped)` part of the time. The container has no `ps`; the process is found by its `/proc/<pid>/cmdline`.
2. Stop the import while ffmpeg is paused: it is gone within a sample interval and the directory is removed.

## Follow-ups

- Measure again with this fix deployed. With ffmpeg paused about half the time, Whisper has the machine to itself for that half; the rate it reaches then decides how much step 2.3 has to find.
- Step 2.3 is needed whatever that measurement says: at about 4 s of transcription per 10 s clip, one clip at a time cannot go below about 24 minutes per hour of recording, and the PRD's target is under half the recording's length. It is to be measured with a live channel running, as the PRD says.
- The deploy of 2026-09-30 failed at `docker compose pull`: quay.io answers 401 for the pinned MinIO image (`quay.io/minio/minio:RELEASE.2025-04-22T22-12-26Z`). The VM holds the image, so the deploy's steps were run by hand with `pull --ignore-pull-failures` and `streamsense-deploy verify` passed. `tools/deploy/deploy.sh` should tolerate an image the machine already has, or MinIO should come from a registry that serves it; a machine without the cached image cannot start the stack today.
