from __future__ import annotations

import os
import sys
import threading
import time
from pathlib import Path

import pytest

from video_capture_service.process import ProcessCancelledError
from video_capture_service.vod_stream import SequentialPass, SequentialPassError

FAKE = Path(__file__).with_name("fake_ffmpeg_pass.py")


class FakePass(SequentialPass):
    """The real pass driven by a script that plays ffmpeg's part: it writes the files the pass expects."""

    def __init__(self, scratch_dir: Path, frames: int, delay: float, idle: bool = False, **kwargs):
        super().__init__("https://example.com/vod.m3u8", scratch_dir, 0, 10, 10, True, **kwargs)
        self.frames = frames
        self.delay = delay
        self.idle = idle

    def command(self) -> list[str]:
        args = [sys.executable, str(FAKE), str(self.scratch_dir), str(self.frames), str(self.delay)]
        return args + (["idle"] if self.idle else [])


def test_the_command_reads_once_from_the_offset_and_splits_frames_and_audio(tmp_path):
    run = SequentialPass("https://example.com/vod.m3u8", tmp_path, 620, 10, 10, True, "jpg", 85, 15, 60, 30)
    command = run.command()
    assert command[0] == "ffmpeg"
    assert command[command.index("-ss") + 1] == "620.000"
    assert command[command.index("-i") + 1] == "https://example.com/vod.m3u8"
    assert command[command.index("-vf") + 1] == "fps=1/10"
    assert command[command.index("-segment_time") + 1] == "10"
    assert command[-1].endswith("a-%08d.wav")
    # No seek and no audio branch when neither is wanted.
    quiet = SequentialPass("u", tmp_path, 0, 10, 0, False).command()
    assert "-ss" not in quiet
    assert "-segment_time" not in quiet


def test_files_are_complete_when_the_next_one_exists_or_ffmpeg_has_exited(tmp_path):
    run = FakePass(tmp_path / "pass", frames=3, delay=0.15, idle_timeout_seconds=10)
    run.start()
    cancel = threading.Event()
    try:
        first = run.wait_for(run.frame_path(0), run.frame_path(1), cancel)
        assert first is not None
        assert first.exists()
        # The second frame exists as soon as the first completes; the third only completes when ffmpeg exits.
        assert run.wait_for(run.audio_path(1), run.audio_path(2), cancel) == run.audio_path(1)
        last = run.wait_for(run.frame_path(2), run.frame_path(3), cancel)
        assert last == run.frame_path(2)
        assert run.alive() is False
        # Past the end: nothing more comes.
        assert run.wait_for(run.frame_path(3), run.frame_path(4), cancel) is None
        assert run.returncode() == 0
    finally:
        run.close()
    assert not (tmp_path / "pass").exists()


def test_a_cancel_kills_ffmpeg_and_an_idle_run_times_out(tmp_path):
    run = FakePass(tmp_path / "idle", frames=0, delay=0, idle=True, idle_timeout_seconds=1)
    run.start()
    cancel = threading.Event()
    threading.Timer(0.3, cancel.set).start()
    started = time.monotonic()
    with pytest.raises(ProcessCancelledError):
        run.wait_for(run.frame_path(0), run.frame_path(1), cancel)
    assert time.monotonic() - started < 5
    assert run.alive() is False

    quiet = FakePass(tmp_path / "quiet", frames=0, delay=0, idle=True, idle_timeout_seconds=1)
    quiet.start()
    with pytest.raises(SequentialPassError, match="produced nothing"):
        quiet.wait_for(quiet.frame_path(0), quiet.frame_path(1), threading.Event())
    assert quiet.alive() is False
    quiet.close()


@pytest.mark.skipif(os.name == "nt", reason="pausing a process group needs POSIX signals")
def test_ffmpeg_is_paused_when_it_runs_ahead_and_resumed_when_the_consumer_catches_up(tmp_path):
    run = FakePass(tmp_path / "ahead", frames=40, delay=0.02, idle_timeout_seconds=10, backlog=6)
    run.start()
    cancel = threading.Event()
    try:
        # Let the writer race ahead, then consume nothing: it must be paused.
        run.wait_for(run.frame_path(0), run.frame_path(1), cancel)
        time.sleep(0.5)
        run.throttle(0)
        assert run.paused() is True
        produced = len(list((tmp_path / "ahead").glob("f-*")))
        time.sleep(0.4)
        assert len(list((tmp_path / "ahead").glob("f-*"))) == produced
        # Catching up resumes it, and it runs to the end.
        run.throttle(produced)
        assert run.paused() is False
        deadline = time.monotonic() + 10
        while run.alive() and time.monotonic() < deadline:
            time.sleep(0.05)
        assert run.alive() is False
        assert len(list((tmp_path / "ahead").glob("f-*"))) == 40
    finally:
        run.close()
