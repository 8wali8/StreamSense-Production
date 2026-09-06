"""Bounded subprocess execution for the ffmpeg and streamlink helpers.

``subprocess.run(timeout=...)`` kills only the direct child. ffmpeg and streamlink can spawn
helpers of their own, so every command runs in its own session and, on timeout, the whole
process group is killed before ``subprocess.TimeoutExpired`` is re-raised. The return type is
the standard ``CompletedProcess`` so callers and tests keep using the familiar shape.
"""

from __future__ import annotations

import os
import signal
import subprocess


def run_bounded(args: list[str], timeout_seconds: float) -> subprocess.CompletedProcess:
    """Run ``args`` with captured text output; kill its whole process group on timeout."""
    popen_kwargs: dict = {"stdout": subprocess.PIPE, "stderr": subprocess.PIPE, "text": True}
    if os.name != "nt":
        popen_kwargs["start_new_session"] = True
    process = subprocess.Popen(args, **popen_kwargs)
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
    return subprocess.CompletedProcess(list(args), process.returncode, stdout or "", stderr or "")


def _kill_group(process: subprocess.Popen) -> None:
    if os.name == "nt":
        process.kill()
        return
    try:
        os.killpg(process.pid, signal.SIGKILL)
    except ProcessLookupError:
        pass
