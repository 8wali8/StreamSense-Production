"""One sequential ffmpeg pass over a recording, for a VOD import.

Seeking into a Twitch recording once per sample costs a playlist fetch, a segment download, and a
decode from a keyframe, twice per ten seconds of recording (a frame and an audio clip). One ffmpeg
run over the HLS playlist decodes the recording once and writes, as it goes, a frame every
``frame_interval`` seconds and the audio in ``transcript_interval``-second WAV chunks into a scratch
directory. The import consumes those files in order and deletes each after use. ffmpeg decodes faster
than the transcripts can be made, so it is paused (SIGSTOP) once it runs ``backlog`` samples ahead of
the consumer and resumed when the consumer catches up; the disk holds a handful of samples, never the
recording. Nothing is downloaded whole.

A file is complete once the next one has appeared, or once ffmpeg has exited. ffmpeg exiting before
the recording's end (a playlist that stopped answering, a broken segment) ends the pass; the caller
carries on from the offset reached by whatever means it has.
"""

from __future__ import annotations

import contextlib
import logging
import os
import shutil
import signal
import subprocess
import threading
import time
from pathlib import Path

from video_capture_service.process import ProcessCancelledError

logger = logging.getLogger(__name__)

POLL_SECONDS = 0.2


class SequentialPassError(Exception):
    """ffmpeg stopped producing samples while still running, or could not be started."""


class SequentialPass:
    def __init__(
        self,
        hls_url: str,
        scratch_dir: Path,
        start_offset_seconds: int,
        frame_interval_seconds: int,
        transcript_interval_seconds: int,
        transcribe: bool,
        output_format: str = "jpg",
        jpeg_quality: int = 85,
        rw_timeout_seconds: int = 15,
        idle_timeout_seconds: int = 60,
        backlog: int = 30,
    ) -> None:
        self.hls_url = hls_url
        self.scratch_dir = scratch_dir
        self.start_offset_seconds = max(0, start_offset_seconds)
        self.frame_interval_seconds = frame_interval_seconds
        self.transcript_interval_seconds = transcript_interval_seconds
        self.transcribe = transcribe and transcript_interval_seconds > 0
        self.suffix = "jpg" if output_format in {"jpg", "jpeg"} else output_format
        self.jpeg_quality = jpeg_quality
        self.rw_timeout_seconds = rw_timeout_seconds
        self.idle_timeout_seconds = idle_timeout_seconds
        self.backlog = max(2, backlog)
        self._process: subprocess.Popen | None = None
        self._paused = False
        self._stderr: list[str] = []
        self._reader: threading.Thread | None = None

    # ---- command ------------------------------------------------------------------------------------

    def command(self) -> list[str]:
        quality = max(2, min(31, round((100 - self.jpeg_quality) / 4) + 2))
        command = [
            "ffmpeg",
            "-y",
            "-loglevel",
            "warning",
            "-nostdin",
            "-rw_timeout",
            str(self.rw_timeout_seconds * 1_000_000),
        ]
        if self.start_offset_seconds > 0:
            command.extend(["-ss", f"{self.start_offset_seconds:.3f}"])
        command.extend(["-i", self.hls_url])
        # The frame at every interval, numbered from 0 so index i is offset start + i * interval.
        command.extend(
            [
                "-map",
                "0:v:0",
                "-vf",
                f"fps=1/{self.frame_interval_seconds}",
                "-fps_mode",
                "passthrough",
                "-q:v",
                str(quality),
                "-start_number",
                "0",
                str(self.scratch_dir / f"f-%08d.{self.suffix}"),
            ]
        )
        if self.transcribe:
            command.extend(
                [
                    "-map",
                    "0:a:0",
                    "-vn",
                    "-ac",
                    "1",
                    "-ar",
                    "16000",
                    "-c:a",
                    "pcm_s16le",
                    "-f",
                    "segment",
                    "-segment_time",
                    str(self.transcript_interval_seconds),
                    "-reset_timestamps",
                    "1",
                    str(self.scratch_dir / "a-%08d.wav"),
                ]
            )
        return command

    # ---- lifecycle ----------------------------------------------------------------------------------

    def start(self) -> None:
        self.scratch_dir.mkdir(parents=True, exist_ok=True)
        popen_kwargs: dict = {"stdout": subprocess.DEVNULL, "stderr": subprocess.PIPE, "text": True}
        if os.name != "nt":
            popen_kwargs["start_new_session"] = True
        try:
            self._process = subprocess.Popen(self.command(), **popen_kwargs)  # noqa: S603 - argv, never a shell
        except OSError as exc:
            raise SequentialPassError(f"ffmpeg could not be started: {exc}") from exc
        self._reader = threading.Thread(target=self._drain_stderr, name="vod-pass-stderr", daemon=True)
        self._reader.start()

    def _drain_stderr(self) -> None:
        process = self._process
        if process is None or process.stderr is None:
            return
        for line in process.stderr:
            text = line.rstrip()
            if text:
                self._stderr.append(text)
                del self._stderr[:-20]

    def alive(self) -> bool:
        return self._process is not None and self._process.poll() is None

    def returncode(self) -> int | None:
        return None if self._process is None else self._process.returncode

    def last_error(self) -> str:
        return " | ".join(self._stderr[-3:])[-500:]

    def close(self) -> None:
        """Ends ffmpeg if it is still running and removes the scratch directory."""
        process = self._process
        if process is not None and process.poll() is None:
            self._resume()
            self._kill_group(process)
            with contextlib.suppress(subprocess.TimeoutExpired):
                process.wait(timeout=5)
        shutil.rmtree(self.scratch_dir, ignore_errors=True)

    # ---- files --------------------------------------------------------------------------------------

    def frame_path(self, index: int) -> Path:
        return self.scratch_dir / f"f-{index:08d}.{self.suffix}"

    def audio_path(self, index: int) -> Path:
        return self.scratch_dir / f"a-{index:08d}.wav"

    def wait_for(self, path: Path, next_path: Path, cancel: threading.Event) -> Path | None:
        """Blocks until ``path`` is complete: its successor exists, or ffmpeg has exited.

        Returns None when ffmpeg exited without producing it (the recording ended, or the run died).
        Raises ProcessCancelledError once ``cancel`` is set, and SequentialPassError when ffmpeg is
        alive but nothing new has appeared for the idle timeout.
        """
        deadline = time.monotonic() + self.idle_timeout_seconds
        last_seen = self._newest_mtime()
        while True:
            if cancel.is_set():
                self.close()
                raise ProcessCancelledError("ffmpeg was cancelled")
            exists = path.exists() and path.stat().st_size > 0
            if exists and (next_path.exists() or not self.alive()):
                return path
            if not self.alive():
                # A short grace for the last file to be flushed after exit.
                time.sleep(POLL_SECONDS)
                return path if path.exists() and path.stat().st_size > 0 else None
            newest = self._newest_mtime()
            if newest > last_seen:
                last_seen = newest
                deadline = time.monotonic() + self.idle_timeout_seconds
            if time.monotonic() > deadline:
                self.close()
                detail = self.last_error() or "no error output"
                raise SequentialPassError(f"ffmpeg produced nothing for {self.idle_timeout_seconds}s; {detail}")
            time.sleep(POLL_SECONDS)

    def _newest_mtime(self) -> float:
        newest = 0.0
        with contextlib.suppress(OSError):
            for entry in os.scandir(self.scratch_dir):
                newest = max(newest, entry.stat().st_mtime)
        return newest

    # ---- pacing -------------------------------------------------------------------------------------

    def throttle(self, consumed_frames: int) -> None:
        """Pauses ffmpeg when it is far ahead of the consumer, resumes it when the consumer has caught up."""
        produced = self._produced_frames()
        ahead = produced - consumed_frames
        if not self._paused and ahead > self.backlog:
            self._pause()
        elif self._paused and ahead <= self.backlog // 2:
            self._resume()

    def _produced_frames(self) -> int:
        count = 0
        with contextlib.suppress(OSError):
            for entry in os.scandir(self.scratch_dir):
                if entry.name.startswith("f-"):
                    count += 1
        return count

    def paused(self) -> bool:
        return self._paused

    def _pause(self) -> None:
        if os.name == "nt" or not self.alive():
            return
        with contextlib.suppress(ProcessLookupError, PermissionError):
            os.killpg(self._process.pid, signal.SIGSTOP)  # type: ignore[union-attr]
            self._paused = True

    def _resume(self) -> None:
        if os.name == "nt" or not self._paused or self._process is None:
            self._paused = False
            return
        with contextlib.suppress(ProcessLookupError, PermissionError):
            os.killpg(self._process.pid, signal.SIGCONT)
        self._paused = False

    @staticmethod
    def _kill_group(process: subprocess.Popen) -> None:
        if os.name == "nt":
            process.kill()
            return
        with contextlib.suppress(ProcessLookupError):
            os.killpg(process.pid, signal.SIGKILL)
