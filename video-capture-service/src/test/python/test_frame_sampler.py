import subprocess

import pytest

from app.frame_sampler import FrameCaptureError, FrameSampler


def test_sampler_requires_non_empty_output(monkeypatch, tmp_path):
    def fake_run(*args, **kwargs):
        output_path = args[0][-1]
        with open(output_path, "wb") as handle:
            handle.write(b"frame-bytes")
        return subprocess.CompletedProcess(args[0], 0, stdout="", stderr="")

    monkeypatch.setattr(subprocess, "run", fake_run)

    output, latency_ms = FrameSampler(5).capture("https://example.com/live.m3u8", tmp_path / "frame.jpg")

    assert output.exists()
    assert output.stat().st_size == len(b"frame-bytes")
    assert latency_ms >= 0


def test_sampler_raises_on_ffmpeg_failure(monkeypatch, tmp_path):
    def fake_run(*args, **kwargs):
        return subprocess.CompletedProcess(args[0], 1, stdout="", stderr="input failed")

    monkeypatch.setattr(subprocess, "run", fake_run)

    with pytest.raises(FrameCaptureError, match="input failed"):
        FrameSampler(5).capture("https://example.com/live.m3u8", tmp_path / "frame.jpg")
