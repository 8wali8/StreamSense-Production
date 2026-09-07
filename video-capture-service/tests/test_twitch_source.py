import subprocess

import pytest

from video_capture_service.twitch_source import (
    TwitchSourceResolver,
    TwitchStreamOfflineError,
    TwitchStreamResolutionError,
)


def test_resolver_returns_hls_url(monkeypatch):
    captured_command = None

    def fake_run(*args, **kwargs):
        nonlocal captured_command
        captured_command = args[0]
        return subprocess.CompletedProcess(args[0], 0, stdout="https://example.com/live.m3u8\n", stderr="")

    monkeypatch.setattr("video_capture_service.twitch_source.run_bounded", fake_run)

    resolver = TwitchSourceResolver("best", 5)

    assert resolver.resolve("austincs") == "https://example.com/live.m3u8"
    assert "https://www.twitch.tv/austincs" in captured_command


def test_resolver_can_resolve_vod_url(monkeypatch):
    captured_command = None

    def fake_run(*args, **kwargs):
        nonlocal captured_command
        captured_command = args[0]
        return subprocess.CompletedProcess(args[0], 0, stdout="https://example.com/vod.m3u8\n", stderr="")

    monkeypatch.setattr("video_capture_service.twitch_source.run_bounded", fake_run)

    resolver = TwitchSourceResolver("best", 5)

    assert (
        resolver.resolve_url("https://www.twitch.tv/videos/2750461300", "redbull-testing")
        == "https://example.com/vod.m3u8"
    )
    assert "https://www.twitch.tv/videos/2750461300" in captured_command


def test_resolver_maps_offline_channel(monkeypatch):
    def fake_run(*args, **kwargs):
        return subprocess.CompletedProcess(args[0], 1, stdout="", stderr="No playable streams found on this URL")

    monkeypatch.setattr("video_capture_service.twitch_source.run_bounded", fake_run)

    resolver = TwitchSourceResolver("best", 5)

    with pytest.raises(TwitchStreamOfflineError):
        resolver.resolve("offline")


def test_resolver_maps_timeout(monkeypatch):
    def fake_run(*args, **kwargs):
        raise subprocess.TimeoutExpired(args[0], 5)

    monkeypatch.setattr("video_capture_service.twitch_source.run_bounded", fake_run)

    resolver = TwitchSourceResolver("best", 5)
    with pytest.raises(TwitchStreamResolutionError, match="timed out"):
        resolver.resolve("austincs")
