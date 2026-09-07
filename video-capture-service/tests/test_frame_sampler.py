import subprocess

import pytest

from video_capture_service.frame_sampler import FrameCaptureError, FrameSampler


def test_sampler_requires_non_empty_output(monkeypatch, tmp_path):
    def fake_run(*args, **kwargs):
        output_path = args[0][-1]
        with open(output_path, "wb") as handle:
            handle.write(b"frame-bytes")
        return subprocess.CompletedProcess(args[0], 0, stdout="", stderr="")

    monkeypatch.setattr("video_capture_service.frame_sampler.run_bounded", fake_run)

    output, latency_ms = FrameSampler(5).capture("https://example.com/live.m3u8", tmp_path / "frame.jpg")

    assert output.exists()
    assert output.stat().st_size == len(b"frame-bytes")
    assert latency_ms >= 0


def test_sampler_adds_seek_for_replay(monkeypatch, tmp_path):
    captured_command = None

    def fake_run(*args, **kwargs):
        nonlocal captured_command
        captured_command = args[0]
        output_path = args[0][-1]
        with open(output_path, "wb") as handle:
            handle.write(b"frame-bytes")
        return subprocess.CompletedProcess(args[0], 0, stdout="", stderr="")

    monkeypatch.setattr("video_capture_service.frame_sampler.run_bounded", fake_run)

    FrameSampler(5).capture("https://example.com/vod.m3u8", tmp_path / "frame.jpg", 42.5)

    assert captured_command[captured_command.index("-ss") + 1] == "42.500"
    assert captured_command.index("-ss") < captured_command.index("-i")


def test_sampler_raises_on_ffmpeg_failure(monkeypatch, tmp_path):
    def fake_run(*args, **kwargs):
        return subprocess.CompletedProcess(args[0], 1, stdout="", stderr="input failed")

    monkeypatch.setattr("video_capture_service.frame_sampler.run_bounded", fake_run)

    with pytest.raises(FrameCaptureError, match="input failed"):
        FrameSampler(5).capture("https://example.com/live.m3u8", tmp_path / "frame.jpg")
