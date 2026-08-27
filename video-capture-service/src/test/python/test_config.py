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


def test_replay_alias_config_binds_from_env(monkeypatch):
    monkeypatch.setenv("STREAMSENSE_TWITCH_VIDEO_ENABLED", "true")
    monkeypatch.setenv("STREAMSENSE_FRAME_STORAGE_BACKEND", "filesystem")
    monkeypatch.setenv("STREAMSENSE_REPLAY_ALIASES", "redbull-testing")
    monkeypatch.setenv("STREAMSENSE_REPLAY_REDBULL_TESTING_VOD_ID", "2750461300")
    monkeypatch.setenv("STREAMSENSE_REPLAY_REDBULL_TESTING_VOD_URL", "https://www.twitch.tv/videos/2750461300")

    config = CaptureConfig.from_env()

    config.validate()
    replay = config.replay_aliases["redbull-testing"]
    assert replay.provider == "twitch"
    assert replay.vod_id == "2750461300"
    assert replay.vod_url == "https://www.twitch.tv/videos/2750461300"
    assert replay.loop is True


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


def _clear_storage_credentials(monkeypatch):
    for name in (
        "STREAMSENSE_FRAME_STORAGE_ACCESS_KEY",
        "STREAMSENSE_FRAME_STORAGE_ACCESS_KEY_FILE",
        "STREAMSENSE_FRAME_STORAGE_SECRET_KEY",
        "STREAMSENSE_FRAME_STORAGE_SECRET_KEY_FILE",
    ):
        monkeypatch.delenv(name, raising=False)


def test_storage_credentials_prefer_secret_files_over_env(monkeypatch, tmp_path):
    _clear_storage_credentials(monkeypatch)
    access_file = tmp_path / "access"
    access_file.write_text("file-access\n", encoding="utf-8")
    secret_file = tmp_path / "secret"
    secret_file.write_text("file-secret", encoding="utf-8")
    monkeypatch.setenv("STREAMSENSE_TWITCH_VIDEO_ENABLED", "true")
    monkeypatch.setenv("STREAMSENSE_FRAME_STORAGE_ACCESS_KEY", "env-access")
    monkeypatch.setenv("STREAMSENSE_FRAME_STORAGE_ACCESS_KEY_FILE", str(access_file))
    monkeypatch.setenv("STREAMSENSE_FRAME_STORAGE_SECRET_KEY_FILE", str(secret_file))

    config = CaptureConfig.from_env()

    config.validate()
    assert config.storage.access_key == "file-access"
    assert config.storage.secret_key == "file-secret"


def test_storage_credentials_fall_back_to_env(monkeypatch):
    _clear_storage_credentials(monkeypatch)
    monkeypatch.setenv("STREAMSENSE_TWITCH_VIDEO_ENABLED", "true")
    monkeypatch.setenv("STREAMSENSE_FRAME_STORAGE_ACCESS_KEY", " env-access ")
    monkeypatch.setenv("STREAMSENSE_FRAME_STORAGE_SECRET_KEY", "env-secret")

    config = CaptureConfig.from_env()

    config.validate()
    assert config.storage.access_key == "env-access"
    assert config.storage.secret_key == "env-secret"


def test_s3_storage_has_no_default_credentials(monkeypatch):
    _clear_storage_credentials(monkeypatch)
    monkeypatch.setenv("STREAMSENSE_TWITCH_VIDEO_ENABLED", "true")
    monkeypatch.setenv("STREAMSENSE_FRAME_STORAGE_BACKEND", "s3")

    config = CaptureConfig.from_env()

    assert config.storage.access_key is None
    assert config.storage.secret_key is None
    with pytest.raises(ValueError, match="access key and secret key"):
        config.validate()


def test_missing_secret_file_is_a_clear_error(monkeypatch, tmp_path):
    _clear_storage_credentials(monkeypatch)
    monkeypatch.setenv("STREAMSENSE_FRAME_STORAGE_ACCESS_KEY_FILE", str(tmp_path / "missing"))

    with pytest.raises(ValueError, match="STREAMSENSE_FRAME_STORAGE_ACCESS_KEY_FILE"):
        CaptureConfig.from_env()
