from __future__ import annotations

import threading
import time
from pathlib import Path

from fastapi.testclient import TestClient

from video_capture_service.config import CaptureConfig
from video_capture_service.main import create_app
from video_capture_service.process import ProcessCancelledError
from video_capture_service.storage import StoredFrame
from video_capture_service.vod_import import VodImportManager, VodImportRequest, import_schedule


def test_schedule_samples_every_frame_interval_and_transcribes_on_the_stride():
    schedule = import_schedule(35, 10, 20)
    assert schedule == [(0, True), (10, False), (20, True), (30, False)]
    assert import_schedule(35, 10, 0) == [(0, False), (10, False), (20, False), (30, False)]
    assert import_schedule(0, 10, 10) == []
    # A resumed import starts at the last offset and transcribes from there.
    assert import_schedule(35, 10, 20, 20) == [(20, True), (30, False)]


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
        # Stopping a recording nobody is importing answers cleanly and changes nothing.
        assert client.delete("/api/video/capture/replay/2750461300").status_code == 404
        assert client.get("/api/video/capture/status").json()["imports"] == []


class FakeResolver:
    def __init__(self, *args, **kwargs):
        pass

    def resolve_url(self, url, label):
        return "https://example.com/vod.m3u8"


class HoldingSampler:
    """Answers the first sample at once and holds every later one until it is cancelled or released."""

    release = threading.Event()

    def __init__(self, *args, **kwargs):
        pass

    def capture(self, hls_url, output_path: Path, seek_seconds=None, cancel=None):
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_bytes(b"frame")
        if seek_seconds and seek_seconds > 0:
            # ffmpeg in flight: the stop must reach in here rather than wait for the capture timeout.
            assert cancel is not None
            while not HoldingSampler.release.is_set():
                if cancel.is_set():
                    raise ProcessCancelledError("ffmpeg was cancelled")
                time.sleep(0.01)
        return output_path, 1


class FakeStorage:
    def store(self, source_path, object_key, content_type):
        return StoredFrame(frame_ref=f"file:///{object_key}", content_type=content_type, size_bytes=5, latency_ms=1)


class FakePublisher:
    def __init__(self):
        self.published = []

    def publish(self, event):
        self.published.append(event)
        return 1

    def close(self):
        pass


def manager(monkeypatch) -> tuple[VodImportManager, FakePublisher]:
    monkeypatch.setenv("STREAMSENSE_TWITCH_VIDEO_ENABLED", "true")
    monkeypatch.setenv("TWITCH_VIDEO_CHANNELS", "racer")
    monkeypatch.setenv("STREAMSENSE_FRAME_STORAGE_BACKEND", "filesystem")
    monkeypatch.setattr("video_capture_service.vod_import.TwitchSourceResolver", FakeResolver)
    monkeypatch.setattr("video_capture_service.vod_import.FrameSampler", HoldingSampler)
    HoldingSampler.release = threading.Event()
    publisher = FakePublisher()
    return VodImportManager(CaptureConfig.from_env(), FakeStorage(), publisher, None, None), publisher


def request(vod_id: str, start_offset: int = 0) -> VodImportRequest:
    return VodImportRequest(
        channel="racer",
        vod_id=vod_id,
        vod_url=f"https://www.twitch.tv/videos/{vod_id}",
        base_time_ms=1788631200000,
        duration_seconds=100,
        stream_session_id=f"racer-vod-{vod_id}",
        frame_interval_seconds=10,
        transcript_interval_seconds=0,
        start_offset_seconds=start_offset,
    )


def wait_for(condition, timeout=5.0) -> bool:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if condition():
            return True
        time.sleep(0.02)
    return condition()


def test_stop_ends_one_running_import_inside_its_sample_and_leaves_the_queued_one_alone(monkeypatch):
    imports, publisher = manager(monkeypatch)
    try:
        running = imports.start(request("1"))
        queued = imports.start(request("2"))
        assert wait_for(lambda: running.state == "RUNNING" and running.frames_published == 1)
        assert queued.state == "QUEUED"

        assert imports.stop("nobody") is None
        stopped = imports.stop("1")
        assert stopped is running
        assert stopped.as_dict()["stopRequested"] is True
        # The stop reached the sampler holding the second frame: no capture timeout, no second frame.
        assert wait_for(lambda: running.state == "STOPPED", timeout=3)
        assert running.frames_published == 1
        assert running.offset_seconds == 10
        assert [event.frameId for event in publisher.published if event.frameId.startswith("vod-1-")] == ["vod-1-f0"]

        # The queued import was not touched by the stop and now gets its turn.
        assert wait_for(lambda: queued.state == "RUNNING")
        # Stopping an import that has already ended changes nothing.
        assert imports.stop("1").state == "STOPPED"
        HoldingSampler.release.set()
        assert wait_for(lambda: queued.state == "DONE")
        assert queued.offset_seconds == 100
    finally:
        HoldingSampler.release.set()
        imports.shutdown()


def test_a_queued_import_stops_at_once_and_a_stopped_one_resumes_from_its_offset(monkeypatch):
    imports, publisher = manager(monkeypatch)
    try:
        first = imports.start(request("1"))
        second = imports.start(request("2"))
        assert imports.stop("2").state == "STOPPED"
        assert wait_for(lambda: first.frames_published == 1)
        imports.stop("1")
        assert wait_for(lambda: first.state == "STOPPED", timeout=3)
        # The stopped one never ran: no frames, and the worker skipped it.
        assert second.frames_published == 0
        assert second.state == "STOPPED"

        # Asking again resumes from the offset reached; the frame ids show nothing before it is re-sampled.
        HoldingSampler.release.set()
        resumed = imports.start(request("1"))
        assert resumed.offset_seconds == 10
        assert wait_for(lambda: resumed.state == "DONE")
        assert [event.frameId for event in publisher.published][:3] == ["vod-1-f0", "vod-1-f10", "vod-1-f20"]
        assert imports.status("1") is resumed
    finally:
        HoldingSampler.release.set()
        imports.shutdown()


def test_stop_route_is_scoped_to_the_streamers_own_channel(monkeypatch):
    monkeypatch.setenv("STREAMSENSE_TWITCH_VIDEO_ENABLED", "false")
    monkeypatch.delenv("TWITCH_VIDEO_CHANNELS", raising=False)
    with TestClient(create_app(CaptureConfig.from_env())) as client:
        imports = client.app.state.runtime.imports
        # A finished import of another channel: the streamer is not told it exists, the operator is.
        from video_capture_service.vod_import import VodImportStatus

        imports.statuses["77"] = VodImportStatus(vod_id="77", channel="pokimane", state="DONE")
        mine = {"X-StreamSense-Auth-Role": "streamer", "X-StreamSense-Auth-Login": "ninja"}
        assert client.get("/api/video/capture/replay/77", headers=mine).status_code == 404
        assert client.delete("/api/video/capture/replay/77", headers=mine).status_code == 404
        assert client.delete("/api/video/capture/replay/77").json()["state"] == "DONE"
