"""Failure classification in the capture loop, exercised with fakes for every collaborator."""

from __future__ import annotations

import threading
from pathlib import Path

from boto3.exceptions import S3UploadFailedError
from botocore.exceptions import ClientError
from kafka.errors import KafkaTimeoutError

from app.capture_loop import CaptureManager
from app.config import CaptureConfig
from app.status import CaptureState, CaptureStatusStore, ChannelStatus
from app.storage import StoredFrame


class FakeResolver:
    def __init__(self, *args, **kwargs):
        pass

    def resolve(self, channel):
        return "https://example.com/live.m3u8"

    def resolve_url(self, url, label):
        return "https://example.com/vod.m3u8"


class FakeSampler:
    def __init__(self, *args, **kwargs):
        pass

    def capture(self, hls_url, output_path: Path, seek_seconds=None):
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_bytes(b"frame")
        return output_path, 1


class FakeStorage:
    def __init__(self, error: Exception | None = None):
        self.error = error

    def store(self, source_path, object_key, content_type):
        if self.error:
            raise self.error
        return StoredFrame(frame_ref=f"file:///{object_key}", content_type=content_type, size_bytes=5, latency_ms=1)


class FakePublisher:
    def __init__(self, error: Exception | None = None):
        self.error = error
        self.published = []

    def publish(self, event):
        if self.error:
            raise self.error
        self.published.append(event)
        return 1

    def close(self):
        pass


def enabled_config(monkeypatch) -> CaptureConfig:
    monkeypatch.setenv("STREAMSENSE_TWITCH_VIDEO_ENABLED", "true")
    monkeypatch.setenv("TWITCH_VIDEO_CHANNELS", "austincs")
    monkeypatch.setenv("STREAMSENSE_FRAME_STORAGE_BACKEND", "filesystem")
    monkeypatch.setenv("TWITCH_VIDEO_SAMPLE_INTERVAL_SECONDS", "5")
    return CaptureConfig.from_env()


def run_one_iteration(monkeypatch, storage, publisher) -> tuple[CaptureManager, ChannelStatus]:
    monkeypatch.setattr("app.capture_loop.TwitchSourceResolver", FakeResolver)
    monkeypatch.setattr("app.capture_loop.FrameSampler", FakeSampler)
    config = enabled_config(monkeypatch)
    store = CaptureStatusStore(enabled=True)
    store.statuses["austincs"] = ChannelStatus(channel="austincs", state=CaptureState.STARTING)
    manager = CaptureManager(config, store, storage, publisher)
    stop_event = threading.Event()
    # Stop after the first sleep so the loop runs exactly once.
    manager._sleep = lambda event, seconds: event.set()  # type: ignore[method-assign]
    manager._capture_channel("austincs", stop_event)
    return manager, store.statuses["austincs"]


def test_successful_iteration_publishes_and_marks_capturing(monkeypatch):
    publisher = FakePublisher()

    _, status = run_one_iteration(monkeypatch, FakeStorage(), publisher)

    assert status.state == CaptureState.CAPTURING
    assert status.frames_published == 1
    assert publisher.published[0].streamer == "austincs"


def test_kafka_error_is_classified_as_degraded_kafka(monkeypatch):
    _, status = run_one_iteration(monkeypatch, FakeStorage(), FakePublisher(error=KafkaTimeoutError("broker gone")))

    assert status.state == CaptureState.DEGRADED_KAFKA
    assert status.frames_skipped == 1
    assert status.frames_published == 0


def test_storage_client_error_is_classified_as_degraded_storage(monkeypatch):
    error = ClientError({"Error": {"Code": "AccessDenied"}, "ResponseMetadata": {"HTTPStatusCode": 403}}, "PutObject")

    _, status = run_one_iteration(monkeypatch, FakeStorage(error=error), FakePublisher())

    assert status.state == CaptureState.DEGRADED_STORAGE
    assert status.frames_stored == 0


def test_wrapped_upload_failure_is_classified_as_degraded_storage(monkeypatch):
    # boto3's upload_file wraps the underlying ClientError; the wrapper is not a botocore exception.
    error = S3UploadFailedError("Failed to upload frame.png to frames/x: An error occurred (AccessDenied)")

    _, status = run_one_iteration(monkeypatch, FakeStorage(error=error), FakePublisher())

    assert status.state == CaptureState.DEGRADED_STORAGE
    assert status.frames_stored == 0


def test_unexpected_error_keeps_the_worker_alive_and_is_labelled(monkeypatch):
    _, status = run_one_iteration(monkeypatch, FakeStorage(error=RuntimeError("boom")), FakePublisher())

    assert status.state == CaptureState.RECONNECTING
    assert status.last_error == "boom"


def test_workers_have_independent_stop_events(monkeypatch):
    monkeypatch.setattr("app.capture_loop.TwitchSourceResolver", FakeResolver)
    monkeypatch.setattr("app.capture_loop.FrameSampler", FakeSampler)
    config = enabled_config(monkeypatch)
    store = CaptureStatusStore(enabled=True)
    store.statuses["austincs"] = ChannelStatus(channel="austincs", state=CaptureState.STARTING)
    manager = CaptureManager(config, store, FakeStorage(), FakePublisher())

    manager.start()
    assert manager.workers_alive() == 1
    first_event = manager.workers[0][1]

    manager.switch_channels(["other", "second"])
    assert first_event.is_set()
    assert [thread.name for thread, _ in manager.workers] == ["capture-other", "capture-second"]
    assert manager.workers_alive() == 2
    # Readiness must count against the switched configuration, not the start-up one.
    assert len(manager.config.channels) == 2
    assert len(config.channels) == 1

    manager.stop()
    assert manager.workers_alive() == 0
