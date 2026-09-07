import pytest

from app.settings import (
    FrameStorageSettings,
    SegmentationSettings,
    SentimentSettings,
    Settings,
    WhisperSettings,
    _secret_env,
)


def test_defaults_match_the_previous_hard_coded_values():
    settings = Settings()

    assert settings.ml_engine_force_failure is False
    assert settings.sentiment.backend == "transformers"
    assert settings.sentiment.model == "cardiffnlp/twitter-roberta-base-sentiment-latest"
    assert settings.sentiment.max_chars == 1000
    assert settings.relevance.backend == "sentence-transformers"
    assert settings.relevance.min_score == 0.5
    assert settings.segmentation.backend == ""
    assert settings.segmentation.max_proposals == 20
    assert settings.segmentation.sam_auto_download is True
    assert settings.whisper.model == "small.en"
    assert settings.whisper.to_config().model_cache == "/models/whisper"
    assert settings.sponsor.require_frame_read is False


def test_env_prefixes_are_unchanged(monkeypatch):
    monkeypatch.setenv("ML_ENGINE_FORCE_FAILURE", "TRUE")
    monkeypatch.setenv("STREAMSENSE_SENTIMENT_BACKEND", "Lexical")
    monkeypatch.setenv("STREAMSENSE_SENTIMENT_MAX_CHARS", "42")
    monkeypatch.setenv("STREAMSENSE_RELEVANCE_MIN_SCORE", "0.7")
    monkeypatch.setenv("STREAMSENSE_WHISPER_MODEL", "base.en")
    monkeypatch.setenv("STREAMSENSE_SPONSOR_REQUIRE_FRAME_READ", "yes")
    monkeypatch.setenv("STREAMSENSE_SPONSOR_SEGMENTATION_ENABLED", "1")

    settings = Settings()

    assert settings.ml_engine_force_failure is True
    assert settings.sentiment.to_config().backend == "lexical"
    assert settings.sentiment.max_chars == 42
    assert settings.relevance.min_score == 0.7
    assert settings.whisper.model == "base.en"
    assert settings.sponsor.require_frame_read is True
    assert settings.sponsor.segmentation_enabled is True


def test_blank_environment_values_fall_back_to_defaults(monkeypatch):
    monkeypatch.setenv("STREAMSENSE_SENTIMENT_MODEL", "   ")
    monkeypatch.setenv("STREAMSENSE_WHISPER_LANGUAGE", "")

    assert SentimentSettings().model == "cardiffnlp/twitter-roberta-base-sentiment-latest"
    assert WhisperSettings().language == "en"


def test_segmentation_reads_new_names_and_legacy_sponsor_aliases(monkeypatch):
    monkeypatch.setenv("STREAMSENSE_SEGMENTATION_BACKEND", "sam")
    monkeypatch.setenv("STREAMSENSE_SAM_CHECKPOINT_PATH", "/models/sam-vit-b.pth")
    monkeypatch.setenv("STREAMSENSE_SEGMENTATION_CONFIDENCE_THRESHOLD", "0.42")
    monkeypatch.setenv("STREAMSENSE_SPONSOR_IOU_THRESHOLD", "0.61")
    monkeypatch.setenv("STREAMSENSE_SPONSOR_MAX_PROPOSALS", "7")
    monkeypatch.setenv("STREAMSENSE_SAM_AUTO_DOWNLOAD", "false")
    monkeypatch.setenv("STREAMSENSE_SAM_POINTS_PER_SIDE", "8")
    monkeypatch.setenv("STREAMSENSE_SEGMENTATION_LABELS", "logo, banner")

    config = SegmentationSettings().to_config()

    assert config.backend == "sam"
    assert config.model_path == "/models/sam-vit-b.pth"
    assert config.confidence_threshold == 0.42
    assert config.iou_threshold == 0.61
    assert config.max_proposals == 7
    assert config.sam_auto_download is False
    assert config.sam_points_per_side == 8
    assert config.class_labels == ("logo", "banner")


def test_legacy_sponsor_backend_alias_selects_backend(monkeypatch):
    monkeypatch.delenv("STREAMSENSE_SEGMENTATION_BACKEND", raising=False)
    monkeypatch.setenv("STREAMSENSE_SPONSOR_MODEL_BACKEND", "heuristic")

    assert SegmentationSettings().to_config().backend == "heuristic"


def test_invalid_numeric_value_fails_at_startup(monkeypatch):
    monkeypatch.setenv("STREAMSENSE_SENTIMENT_MAX_CHARS", "lots")

    with pytest.raises(ValueError):
        SentimentSettings()


def test_frame_storage_credentials_prefer_secret_files(monkeypatch, tmp_path):
    secret = tmp_path / "secret"
    secret.write_text("from-file\n", encoding="utf-8")
    monkeypatch.setenv("STREAMSENSE_FRAME_STORAGE_ACCESS_KEY", "from-env")
    monkeypatch.setenv("STREAMSENSE_FRAME_STORAGE_SECRET_KEY", "env-secret")
    monkeypatch.setenv("STREAMSENSE_FRAME_STORAGE_SECRET_KEY_FILE", str(secret))

    storage = FrameStorageSettings()

    assert storage.access_key == "from-env"
    assert storage.secret_key == "from-file"


def test_secret_env_prefers_file_over_env(monkeypatch, tmp_path):
    secret_file = tmp_path / "secret"
    secret_file.write_text("from-file\n", encoding="utf-8")
    monkeypatch.setenv("STREAMSENSE_TEST_SECRET", "from-env")
    monkeypatch.setenv("STREAMSENSE_TEST_SECRET_FILE", str(secret_file))

    assert _secret_env("STREAMSENSE_TEST_SECRET") == "from-file"


def test_secret_env_missing_file_is_a_clear_error(monkeypatch, tmp_path):
    monkeypatch.setenv("STREAMSENSE_TEST_SECRET_FILE", str(tmp_path / "missing"))

    with pytest.raises(ValueError, match="STREAMSENSE_TEST_SECRET_FILE"):
        _secret_env("STREAMSENSE_TEST_SECRET")
