from fastapi.testclient import TestClient

from app.config import CaptureConfig
from app.main import create_app


def disabled_config(monkeypatch) -> CaptureConfig:
    monkeypatch.setenv("STREAMSENSE_TWITCH_VIDEO_ENABLED", "false")
    monkeypatch.delenv("TWITCH_VIDEO_CHANNELS", raising=False)
    return CaptureConfig.from_env()


def test_import_has_no_side_effects_and_ready_is_503_before_startup(monkeypatch):
    app = create_app(disabled_config(monkeypatch))
    client = TestClient(app)  # no `with`: the lifespan has not run

    assert client.get("/ready").status_code == 503
    assert client.get("/ready").json() == {"status": "starting"}
    assert client.get("/live").json() == {"status": "alive"}
    assert client.get("/health").json()["ready"] is False
    assert client.get("/api/video/capture/status").status_code == 503


def test_disabled_capture_is_ready_after_startup(monkeypatch):
    with TestClient(create_app(disabled_config(monkeypatch))) as client:
        assert client.get("/ready").status_code == 200
        assert client.get("/ready").json() == {"status": "ready", "capture": "disabled"}
        assert client.get("/health").json() == {"status": "ok", "service": "video-capture-service", "ready": True}

        status = client.get("/api/video/capture/status").json()
        assert status["enabled"] is False
        assert status["state"] == "DISABLED"

        switch = client.post("/api/video/capture/channels", json={"channels": ["austincs"]})
        assert switch.status_code == 409

        assert b"streamsense_twitch_video_capture_enabled" in client.get("/metrics").content


def test_frame_endpoint_rejects_paths_outside_storage_root(monkeypatch, tmp_path):
    monkeypatch.setenv("STREAMSENSE_FRAME_STORAGE_BACKEND", "filesystem")
    monkeypatch.setenv("STREAMSENSE_FRAME_STORAGE_FILESYSTEM_ROOT", str(tmp_path / "frames"))
    config = disabled_config(monkeypatch)
    outside = tmp_path / "outside.jpg"
    outside.write_bytes(b"x")

    with TestClient(create_app(config)) as client:
        response = client.get("/api/video/capture/frame", params={"frameRef": f"file://{outside}"})

    assert response.status_code == 403


def test_frame_endpoint_serves_files_under_storage_root(monkeypatch, tmp_path):
    root = tmp_path / "frames"
    root.mkdir()
    (root / "frame.png").write_bytes(b"png-bytes")
    monkeypatch.setenv("STREAMSENSE_FRAME_STORAGE_BACKEND", "filesystem")
    monkeypatch.setenv("STREAMSENSE_FRAME_STORAGE_FILESYSTEM_ROOT", str(root))
    config = disabled_config(monkeypatch)

    with TestClient(create_app(config)) as client:
        response = client.get("/api/video/capture/frame", params={"frameRef": f"file://{root / 'frame.png'}"})

    assert response.status_code == 200
    assert response.headers["content-type"] == "image/png"
    assert response.content == b"png-bytes"
