import pytest

from app.config import CaptureConfig


def test_disabled_config_does_not_require_channel(monkeypatch):
    monkeypatch.setenv("STREAMSENSE_TWITCH_VIDEO_ENABLED", "false")
    monkeypatch.delenv("TWITCH_VIDEO_CHANNELS", raising=False)

    config = CaptureConfig.from_env()

    config.validate()
    assert config.enabled is False


def test_enabled_config_can_wait_for_runtime_channel(monkeypatch):
    monkeypatch.setenv("STREAMSENSE_TWITCH_VIDEO_ENABLED", "true")
    monkeypatch.delenv("TWITCH_VIDEO_CHANNELS", raising=False)
    monkeypatch.setenv("STREAMSENSE_FRAME_STORAGE_BACKEND", "filesystem")

    config = CaptureConfig.from_env()

    config.validate()
    assert config.channels == []


def test_enabled_config_binds_channels_and_storage(monkeypatch):
    monkeypatch.setenv("STREAMSENSE_TWITCH_VIDEO_ENABLED", "true")
    monkeypatch.setenv("TWITCH_VIDEO_CHANNELS", "AustInCS, Example")
    monkeypatch.setenv("STREAMSENSE_FRAME_STORAGE_BACKEND", "filesystem")

    config = CaptureConfig.from_env()

    config.validate()

    assert config.channels == ["austincs", "example"]
    assert config.storage.backend == "filesystem"


def test_transcript_config_binds_ml_and_topic(monkeypatch):
    monkeypatch.setenv("STREAMSENSE_TWITCH_VIDEO_ENABLED", "true")
    monkeypatch.setenv("STREAMSENSE_TWITCH_TRANSCRIPT_ENABLED", "true")
    monkeypatch.setenv("TWITCH_VIDEO_CHANNELS", "AustInCS")
    monkeypatch.setenv("STREAMSENSE_FRAME_STORAGE_BACKEND", "filesystem")
    monkeypatch.setenv("ML_ENGINE_URL", "http://ml-engine:8000/")
    monkeypatch.setenv("STREAMSENSE_TRANSCRIPT_SEGMENTS_TOPIC", "stream.transcript.segments")
    monkeypatch.setenv("TWITCH_TRANSCRIPT_SEGMENT_SECONDS", "5")
    monkeypatch.setenv("TWITCH_TRANSCRIPT_AUDIO_CAPTURE_TIMEOUT_SECONDS", "10")

    config = CaptureConfig.from_env()

    config.validate()

    assert config.transcript_enabled is True
    assert config.ml_engine_url == "http://ml-engine:8000"
    assert config.transcript_segments_topic == "stream.transcript.segments"
    assert config.transcript_segment_duration_seconds == 5
