import subprocess

import pytest

from app.twitch_source import TwitchSourceResolver, TwitchStreamOffline, TwitchStreamResolutionError


def test_resolver_returns_hls_url(monkeypatch):
    def fake_run(*args, **kwargs):
        return subprocess.CompletedProcess(args[0], 0, stdout="https://example.com/live.m3u8\n", stderr="")

    monkeypatch.setattr(subprocess, "run", fake_run)

    resolver = TwitchSourceResolver("best", 5)

    assert resolver.resolve("austincs") == "https://example.com/live.m3u8"


def test_resolver_maps_offline_channel(monkeypatch):
    def fake_run(*args, **kwargs):
        return subprocess.CompletedProcess(args[0], 1, stdout="", stderr="No playable streams found on this URL")

    monkeypatch.setattr(subprocess, "run", fake_run)

    resolver = TwitchSourceResolver("best", 5)

    with pytest.raises(TwitchStreamOffline):
        resolver.resolve("offline")


def test_resolver_maps_timeout(monkeypatch):
    def fake_run(*args, **kwargs):
        raise subprocess.TimeoutExpired(args[0], 5)

    monkeypatch.setattr(subprocess, "run", fake_run)

    resolver = TwitchSourceResolver("best", 5)
    with pytest.raises(TwitchStreamResolutionError, match="timed out"):
        resolver.resolve("austincs")
