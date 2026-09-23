# imports/04-sequential-pass

Step 2.2 of the PRD "Stop and speed up past-stream imports": one sequential ffmpeg pass over the recording instead of a seek per sample. Measured against the baseline in `imports-02-transcript-interval.md` (capture half at 2.1 times real time, about 28 minutes of wall time per hour of recording, 0 failures). Based on `main` at 3f0b758.

## What changed

- **`vod_stream.SequentialPass`.** One ffmpeg run reads the HLS playlist from the resume offset (`-ss` before `-i`) and writes, as it decodes, a frame every sample interval (`fps=1/N`, numbered from 0) and the audio in transcript-interval WAV chunks (`-f segment`) into a scratch directory. A file is complete once its successor exists or ffmpeg has exited. The consumer publishes each frame and transcribes each chunk in order, deletes them, and after every sample tells the pass where it is: once ffmpeg is more than `TWITCH_VOD_IMPORT_BACKLOG_SAMPLES` (30) ahead it is paused with SIGSTOP and resumed when the consumer has caught up, so the disk holds a handful of samples and nothing is downloaded whole. A stop kills the process group; an idle run (nothing new for four capture timeouts) is ended with its last error.
- **`VodImportManager`** runs the pass by default (`TWITCH_VOD_IMPORT_MODE=sequential`) and, when it ends early (the playlist stopped answering, a broken segment, could not start), continues from the offset reached with the older seek-per-sample loop, which is also the whole path under `TWITCH_VOD_IMPORT_MODE=seek`. Frame and transcript ids and the event contents are unchanged; frame publishing and transcription are split from their captures so both paths share them.
- Compose gains the two variables; `CLAUDE.md` and `docs/contracts/sessions.md` say how a recording is read.

## Verification

| Check | Command | Result |
|---|---|---|
| video-capture-service | `ruff format`, `ruff check`, `mypy`, `pytest` in `python:3.11.16-slim` with uv | clean; 81 tests |
| Measurement on the VM | the same recording as the baseline (xqc 2872612045), capture half, polled every 30 s | pending: needs this build on the VM |

## What to check by hand

1. After the deploy, import a recording: the capture log shows one `ffmpeg` process for the import (not one per sample), `ls /tmp/streamsense-video-capture/vod-pass-<id>` inside the container holds a few dozen files at most, and the import's offset advances faster than the baseline.
2. Stop the import: the ffmpeg process is gone within a sample interval and the scratch directory is removed.
3. Set `TWITCH_VOD_IMPORT_MODE=seek` on the VM and import again: the older path runs unchanged.

## Follow-ups

- Step 2.3 (bounded concurrency for transcription) if the measured time is still above the PRD's target; the confirmation before a long import with the measured estimate.
