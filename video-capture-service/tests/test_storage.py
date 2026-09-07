from video_capture_service.config import StorageConfig
from video_capture_service.storage import FilesystemFrameStorage


def test_filesystem_storage_writes_frame(tmp_path):
    source = tmp_path / "source.jpg"
    source.write_bytes(b"frame")
    config = StorageConfig(
        backend="filesystem",
        bucket="unused",
        endpoint=None,
        region="us-east-1",
        access_key=None,
        secret_key=None,
        path_prefix="twitch",
        filesystem_root=str(tmp_path / "stored"),
    )

    stored = FilesystemFrameStorage(config).store(source, "twitch/austincs/frame.jpg", "image/jpeg")

    assert stored.frame_ref.startswith("file://")
    assert stored.content_type == "image/jpeg"
    assert stored.size_bytes == 5
