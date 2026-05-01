import subprocess

import pytest

from app.audio_sampler import AudioCaptureError, AudioSampler


def test_audio_sampler_requires_non_empty_output(monkeypatch, tmp_path):
    def fake_run(*args, **kwargs):
        output_path = args[0][-1]
        with open(output_path, "wb") as handle:
            handle.write(b"wav-bytes")
        return subprocess.CompletedProcess(args[0], 0, stdout="", stderr="")

    monkeypatch.setattr(subprocess, "run", fake_run)

    output, latency_ms = AudioSampler(5, 2).capture(
        "https://example.com/live.m3u8", tmp_path / "segment.wav"
    )

    assert output.exists()
    assert output.stat().st_size == len(b"wav-bytes")
    assert latency_ms >= 0


def test_audio_sampler_raises_on_ffmpeg_failure(monkeypatch, tmp_path):
    def fake_run(*args, **kwargs):
        return subprocess.CompletedProcess(args[0], 1, stdout="", stderr="input failed")

    monkeypatch.setattr(subprocess, "run", fake_run)

    with pytest.raises(AudioCaptureError, match="input failed"):
        AudioSampler(5, 2).capture("https://example.com/live.m3u8", tmp_path / "segment.wav")
