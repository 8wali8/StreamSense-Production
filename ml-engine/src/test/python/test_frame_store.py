import pytest

from app.frame_store import FrameArtifactError, FrameStore, load_frame_artifact, load_frame_image


def test_frame_reads_are_bounded_by_the_configured_size(tmp_path):
    frame_path = tmp_path / "frame.ppm"
    frame_path.write_bytes(b"P6\n2 1\n255\n\xff\x00\x00\x00\xff\x00")

    with pytest.raises(FrameArtifactError, match="exceeds 8 bytes"):
        FrameStore(max_frame_bytes=8).load_frame_image(f"file://{frame_path}")


def test_only_regular_files_are_read(tmp_path):
    directory = tmp_path / "frames"
    directory.mkdir()

    with pytest.raises(FrameArtifactError, match="not a regular file"):
        load_frame_image(f"file://{directory}")


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


def test_frame_store_requires_readable_scheme_only_when_asked():
    store = FrameStore()

    assert store.load_frame_image("frames/relative.png") is None
    with pytest.raises(FrameArtifactError, match="unsupported frameRef scheme"):
        store.load_frame_image("frames/relative.png", required=True)


def test_frame_store_without_s3_settings_reports_misconfiguration():
    store = FrameStore()

    with pytest.raises(FrameArtifactError, match="not configured"):
        store.load_frame_image("s3://bucket/key.jpg")
