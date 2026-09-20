"""Bounded subprocess execution for the ffmpeg and streamlink helpers.

``subprocess.run(timeout=...)`` kills only the direct child. ffmpeg and streamlink can spawn
helpers of their own, so every command runs in its own session and, on timeout, the whole
process group is killed before ``subprocess.TimeoutExpired`` is re-raised. The return type is
the standard ``CompletedProcess`` so callers and tests keep using the familiar shape.

A caller that may need to abandon the command early (an import being stopped) passes a
``cancel`` event: once it is set the process group is killed and ``ProcessCancelledError`` is raised,
so a stop does not wait for a 30-second capture to run its course.
"""

from __future__ import annotations

import contextlib
import os
import signal
import subprocess
import threading

# How often the watcher looks at the cancel event while the command runs.
CANCEL_POLL_SECONDS = 0.2


class ProcessCancelledError(Exception):
    """The command was killed because its ``cancel`` event was set."""


def run_bounded(
    args: list[str], timeout_seconds: float, cancel: threading.Event | None = None
) -> subprocess.CompletedProcess:
    """Run ``args`` with captured text output; kill its whole process group on timeout or cancel."""
    popen_kwargs: dict = {"stdout": subprocess.PIPE, "stderr": subprocess.PIPE, "text": True}
    if os.name != "nt":
        popen_kwargs["start_new_session"] = True
    process = subprocess.Popen(args, **popen_kwargs)  # noqa: S603 - argv built from config, never a shell string
    finished = threading.Event()
    if cancel is not None:
        threading.Thread(
            target=_kill_when_cancelled, args=(process, cancel, finished), name="run-bounded-cancel", daemon=True
        ).start()
    try:
        stdout, stderr = process.communicate(timeout=timeout_seconds)
    except subprocess.TimeoutExpired:
        _kill_group(process)
        try:
            process.communicate(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
            process.communicate()
        raise
    finally:
        finished.set()
    if cancel is not None and cancel.is_set():
        raise ProcessCancelledError(f"{args[0]} was cancelled")
    return subprocess.CompletedProcess(list(args), process.returncode, stdout or "", stderr or "")


def _kill_when_cancelled(process: subprocess.Popen, cancel: threading.Event, finished: threading.Event) -> None:
    while not finished.wait(CANCEL_POLL_SECONDS):
        if cancel.is_set():
            _kill_group(process)
            return


def _kill_group(process: subprocess.Popen) -> None:
    if os.name == "nt":
        process.kill()
        return
    with contextlib.suppress(ProcessLookupError):
        os.killpg(process.pid, signal.SIGKILL)
