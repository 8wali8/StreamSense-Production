from fastapi.testclient import TestClient

from video_capture_service.config import CaptureConfig
from video_capture_service.main import create_app
from video_capture_service.vod_import import import_schedule


def test_schedule_samples_every_frame_interval_and_transcribes_on_the_stride():
    schedule = import_schedule(35, 10, 20)
    assert schedule == [(0, True), (10, False), (20, True), (30, False)]
    assert import_schedule(35, 10, 0) == [(0, False), (10, False), (20, False), (30, False)]
    assert import_schedule(0, 10, 10) == []


def test_replay_is_refused_while_capture_is_disabled(monkeypatch):
    monkeypatch.setenv("STREAMSENSE_TWITCH_VIDEO_ENABLED", "false")
    monkeypatch.delenv("TWITCH_VIDEO_CHANNELS", raising=False)
    with TestClient(create_app(CaptureConfig.from_env())) as client:
        response = client.post(
            "/api/video/capture/replay",
            json={
                "channel": "racer",
                "vodId": "2750461300",
                "vodUrl": "https://www.twitch.tv/videos/2750461300",
                "baseTimeMs": 1788631200000,
                "durationSeconds": 7200,
                "streamSessionId": "racer-vod-2750461300",
            },
        )
        assert response.status_code == 409
        assert client.get("/api/video/capture/replay/2750461300").status_code == 404
        assert client.get("/api/video/capture/status").json()["imports"] == []
