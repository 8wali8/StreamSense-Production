import pytest
from app.frame_store import _secret_env, load_frame_artifact, load_frame_image


def test_load_frame_image_decodes_rgb_frame(tmp_path):
    frame_path = tmp_path / "frame.ppm"
    frame_path.write_bytes(b"P6\n2 1\n255\n\xff\x00\x00\x00\xff\x00")

    frame_image = load_frame_image(f"file://{frame_path}")

    assert frame_image is not None
    assert frame_image.artifact.width == 2
    assert frame_image.artifact.height == 1
    assert frame_image.artifact.size_bytes == frame_path.stat().st_size
    assert frame_image.image.mode == "RGB"
    assert frame_image.image.size == (2, 1)
    assert frame_image.signature == frame_image.artifact.signature


def test_load_frame_artifact_remains_metadata_only_compatible(tmp_path):
    frame_path = tmp_path / "frame.ppm"
    frame_path.write_bytes(b"P6\n1 1\n255\n\xff\x00\x00")

    artifact = load_frame_artifact(f"file://{frame_path}")

    frame_image = load_frame_image(f"file://{frame_path}")

    assert artifact is not None
    assert frame_image is not None
    assert artifact.signature == frame_image.signature


def test_secret_env_prefers_file_over_env(monkeypatch, tmp_path):
    secret_file = tmp_path / "secret"
    secret_file.write_text("from-file\n", encoding="utf-8")
    monkeypatch.setenv("STREAMSENSE_TEST_SECRET", "from-env")
    monkeypatch.setenv("STREAMSENSE_TEST_SECRET_FILE", str(secret_file))

    assert _secret_env("STREAMSENSE_TEST_SECRET") == "from-file"


def test_secret_env_falls_back_to_env_then_default(monkeypatch):
    monkeypatch.delenv("STREAMSENSE_TEST_SECRET_FILE", raising=False)
    monkeypatch.setenv("STREAMSENSE_TEST_SECRET", " from-env ")

    assert _secret_env("STREAMSENSE_TEST_SECRET") == "from-env"

    monkeypatch.delenv("STREAMSENSE_TEST_SECRET")
    assert _secret_env("STREAMSENSE_TEST_SECRET") is None
    assert _secret_env("STREAMSENSE_TEST_SECRET", "fallback") == "fallback"


def test_secret_env_missing_file_is_a_clear_error(monkeypatch, tmp_path):
    monkeypatch.setenv("STREAMSENSE_TEST_SECRET_FILE", str(tmp_path / "missing"))

    with pytest.raises(ValueError, match="STREAMSENSE_TEST_SECRET_FILE"):
        _secret_env("STREAMSENSE_TEST_SECRET")
