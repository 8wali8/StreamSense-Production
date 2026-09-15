"""Failure classification in the capture loop, exercised with fakes for every collaborator."""

from __future__ import annotations

import threading
import time
from dataclasses import replace
from pathlib import Path

import pytest
from boto3.exceptions import S3UploadFailedError
from botocore.exceptions import ClientError
from kafka.errors import KafkaTimeoutError

from video_capture_service.capture_loop import CaptureManager
from video_capture_service.config import CaptureConfig
from video_capture_service.status import CaptureState, CaptureStatusStore, ChannelStatus
from video_capture_service.storage import StoredFrame


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
    monkeypatch.setattr("video_capture_service.capture_loop.TwitchSourceResolver", FakeResolver)
    monkeypatch.setattr("video_capture_service.capture_loop.FrameSampler", FakeSampler)
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
    monkeypatch.setattr("video_capture_service.capture_loop.TwitchSourceResolver", FakeResolver)
    monkeypatch.setattr("video_capture_service.capture_loop.FrameSampler", FakeSampler)
    config = enabled_config(monkeypatch)
    store = CaptureStatusStore(enabled=True)
    store.statuses["austincs"] = ChannelStatus(channel="austincs", state=CaptureState.STARTING)
    manager = CaptureManager(config, store, FakeStorage(), FakePublisher())

    manager.start()
    assert manager.workers_alive() == 1
    first_event = manager.workers["austincs"][1]

    manager.switch_channels(["other", "second"])
    assert first_event.is_set()
    assert [thread.name for thread, _ in manager.workers.values()] == ["capture-other", "capture-second"]
    assert manager.workers_alive() == 2
    # Readiness must count against the switched configuration, not the start-up one.
    assert len(manager.config.channels) == 2
    assert len(config.channels) == 1

    manager.stop()
    assert manager.workers_alive() == 0


def test_switch_channels_leaves_a_channel_that_stays_running(monkeypatch):
    """A channel that survives the switch keeps its worker, so its stream is not split into two sessions."""
    manager = started_manager(monkeypatch)
    kept_thread, kept_event = manager.workers["austincs"]
    kept_session = manager.status_store.statuses["austincs"].capture_session_id

    manager.switch_channels(["austincs", "second"])

    assert manager.workers["austincs"] == (kept_thread, kept_event)
    assert not kept_event.is_set()
    assert manager.status_store.statuses["austincs"].capture_session_id == kept_session
    assert manager.workers_alive() == 2
    manager.stop()


def test_add_channel_starts_only_that_channel(monkeypatch):
    manager = started_manager(monkeypatch)
    kept_thread, _ = manager.workers["austincs"]

    status = manager.add_channel("@Ninja")

    assert status["channel"] == "ninja"
    assert manager.config.channels == ["austincs", "ninja"]
    assert manager.workers["austincs"][0] is kept_thread
    assert manager.workers_alive() == 2

    # Idempotent: asking again neither restarts the worker nor lists the channel twice.
    added_thread = manager.workers["ninja"][0]
    manager.add_channel("ninja")
    assert manager.config.channels == ["austincs", "ninja"]
    assert manager.workers["ninja"][0] is added_thread
    manager.stop()


def test_add_channel_is_refused_when_capture_is_full(monkeypatch):
    manager = started_manager(monkeypatch)
    manager.config = replace(manager.config, max_channels=1)

    with pytest.raises(RuntimeError, match="already measuring"):
        manager.add_channel("ninja")

    assert manager.config.channels == ["austincs"]
    manager.stop()


def test_add_channel_is_refused_when_capture_is_disabled(monkeypatch):
    config = replace(enabled_config(monkeypatch), enabled=False)
    manager = CaptureManager(config, CaptureStatusStore(enabled=False), FakeStorage(), FakePublisher())

    with pytest.raises(RuntimeError, match="disabled"):
        manager.add_channel("ninja")


def test_add_channel_refuses_a_blank_channel(monkeypatch):
    manager = started_manager(monkeypatch)

    with pytest.raises(ValueError, match="channel is required"):
        manager.add_channel("  ")

    manager.stop()


def test_remove_channel_leaves_the_others_capturing(monkeypatch):
    manager = started_manager(monkeypatch)
    manager.add_channel("ninja")
    kept_thread, kept_event = manager.workers["austincs"]

    status = manager.remove_channel("ninja")

    assert status["channel"] == "ninja"
    assert manager.config.channels == ["austincs"]
    assert "ninja" not in manager.workers
    assert "ninja" not in manager.status_store.statuses
    assert not kept_event.is_set()
    assert manager.workers["austincs"][0] is kept_thread

    # Idempotent: removing it again is not an error.
    manager.remove_channel("ninja")
    assert manager.config.channels == ["austincs"]
    manager.stop()


def test_channel_status_answers_for_one_channel_only(monkeypatch):
    manager = started_manager(monkeypatch)

    assert manager.channel_status("austincs")["channel"] == "austincs"
    assert manager.channel_status("ninja")["state"] == CaptureState.STOPPED.value
    assert "channels" not in manager.channel_status("austincs")
    manager.stop()


def test_a_worker_that_outlives_the_stop_keeps_its_place(monkeypatch):
    """A capture call blocks past the stop wait: the worker is kept, and nothing starts a second one."""
    entered, release = threading.Event(), threading.Event()

    class BlockingSampler:
        def __init__(self, *args, **kwargs):
            pass

        def capture(self, hls_url, output_path: Path, seek_seconds=None):
            entered.set()
            release.wait(10)
            output_path.parent.mkdir(parents=True, exist_ok=True)
            output_path.write_bytes(b"frame")
            return output_path, 1

    monkeypatch.setattr("video_capture_service.capture_loop.TwitchSourceResolver", FakeResolver)
    monkeypatch.setattr("video_capture_service.capture_loop.FrameSampler", BlockingSampler)
    monkeypatch.setattr("video_capture_service.capture_loop.WORKER_STOP_TIMEOUT_SECONDS", 0.1)
    publisher = FakePublisher()
    manager = CaptureManager(enabled_config(monkeypatch), CaptureStatusStore(enabled=True), FakeStorage(), publisher)
    manager.start()
    assert entered.wait(5)

    status = manager.remove_channel("austincs")

    assert status["state"] == CaptureState.STOPPING.value
    assert "austincs" in manager.workers
    with pytest.raises(RuntimeError, match="still stopping"):
        manager.add_channel("austincs")

    release.set()
    deadline = time.monotonic() + 5
    while manager.workers["austincs"][0].is_alive() and time.monotonic() < deadline:
        time.sleep(0.05)
    # The frame it was holding belongs to a session that is over, so it is never published.
    assert publisher.published == []
    # Once the worker is really gone the channel starts again.
    assert manager.add_channel("austincs")["channel"] == "austincs"
    manager.stop()


def test_a_stopping_worker_is_reaped_once_it_exits(monkeypatch):
    """Without reaping, a channel reads STOPPING for ever and the console keeps calling it captured."""
    entered, release = threading.Event(), threading.Event()

    class BlockingSampler:
        def __init__(self, *args, **kwargs):
            pass

        def capture(self, hls_url, output_path: Path, seek_seconds=None):
            entered.set()
            release.wait(10)
            output_path.parent.mkdir(parents=True, exist_ok=True)
            output_path.write_bytes(b"frame")
            return output_path, 1

    monkeypatch.setattr("video_capture_service.capture_loop.TwitchSourceResolver", FakeResolver)
    monkeypatch.setattr("video_capture_service.capture_loop.FrameSampler", BlockingSampler)
    monkeypatch.setattr("video_capture_service.capture_loop.WORKER_STOP_TIMEOUT_SECONDS", 0.1)
    manager = CaptureManager(
        enabled_config(monkeypatch), CaptureStatusStore(enabled=True), FakeStorage(), FakePublisher()
    )
    manager.start()
    assert entered.wait(5)
    assert manager.remove_channel("austincs")["state"] == CaptureState.STOPPING.value

    release.set()
    deadline = time.monotonic() + 5
    while manager.workers and time.monotonic() < deadline:
        manager.channel_status("austincs")
        time.sleep(0.05)

    # Reaped: the channel is gone from the workers, the status, and the whole-service snapshot.
    assert manager.workers == {}
    assert manager.channel_status("austincs")["state"] == CaptureState.STOPPED.value
    assert manager.snapshot()["channels"] == []
    manager.stop()


def test_a_stopping_worker_still_holds_its_slot(monkeypatch):
    """Its capture call is still running, so its resources are not free for another channel yet."""
    entered, release = threading.Event(), threading.Event()

    class BlockingSampler:
        def __init__(self, *args, **kwargs):
            pass

        def capture(self, hls_url, output_path: Path, seek_seconds=None):
            entered.set()
            release.wait(10)
            return output_path, 1

    monkeypatch.setattr("video_capture_service.capture_loop.TwitchSourceResolver", FakeResolver)
    monkeypatch.setattr("video_capture_service.capture_loop.FrameSampler", BlockingSampler)
    monkeypatch.setattr("video_capture_service.capture_loop.WORKER_STOP_TIMEOUT_SECONDS", 0.1)
    config = replace(enabled_config(monkeypatch), max_channels=1)
    manager = CaptureManager(config, CaptureStatusStore(enabled=True), FakeStorage(), FakePublisher())
    manager.start()
    assert entered.wait(5)
    manager.remove_channel("austincs")

    with pytest.raises(RuntimeError, match="already measuring"):
        manager.add_channel("ninja")

    release.set()
    deadline = time.monotonic() + 5
    while manager.workers and time.monotonic() < deadline:
        manager.channel_status("austincs")
        time.sleep(0.05)
    # Reaped, so the slot is free again.
    assert manager.add_channel("ninja")["channel"] == "ninja"
    manager.stop()


def test_a_refused_start_leaves_the_configuration_alone(monkeypatch):
    """A 409 that had already added the channel would leave readiness expecting a worker for ever."""
    entered, release = threading.Event(), threading.Event()

    class BlockingSampler:
        def __init__(self, *args, **kwargs):
            pass

        def capture(self, hls_url, output_path: Path, seek_seconds=None):
            entered.set()
            release.wait(10)
            return output_path, 1

    monkeypatch.setattr("video_capture_service.capture_loop.TwitchSourceResolver", FakeResolver)
    monkeypatch.setattr("video_capture_service.capture_loop.FrameSampler", BlockingSampler)
    monkeypatch.setattr("video_capture_service.capture_loop.WORKER_STOP_TIMEOUT_SECONDS", 0.1)
    manager = CaptureManager(
        enabled_config(monkeypatch), CaptureStatusStore(enabled=True), FakeStorage(), FakePublisher()
    )
    manager.start()
    assert entered.wait(5)
    manager.remove_channel("austincs")

    with pytest.raises(RuntimeError, match="still stopping"):
        manager.add_channel("austincs")

    # Not configured by the refusal: readiness counts workers against this list.
    assert manager.config.channels == []
    release.set()
    manager.stop()


def test_a_switch_waits_for_a_channel_that_is_still_stopping(monkeypatch):
    """Starting the new list while the old worker runs would exceed the cap it is meant to hold."""
    entered, release = threading.Event(), threading.Event()

    class BlockingSampler:
        def __init__(self, *args, **kwargs):
            pass

        def capture(self, hls_url, output_path: Path, seek_seconds=None):
            entered.set()
            release.wait(10)
            return output_path, 1

    monkeypatch.setattr("video_capture_service.capture_loop.TwitchSourceResolver", FakeResolver)
    monkeypatch.setattr("video_capture_service.capture_loop.FrameSampler", BlockingSampler)
    monkeypatch.setattr("video_capture_service.capture_loop.WORKER_STOP_TIMEOUT_SECONDS", 0.1)
    config = replace(enabled_config(monkeypatch), max_channels=1)
    manager = CaptureManager(config, CaptureStatusStore(enabled=True), FakeStorage(), FakePublisher())
    manager.start()
    assert entered.wait(5)

    with pytest.raises(RuntimeError, match="still stopping"):
        manager.switch_channels(["ninja"])

    assert manager.config.channels == ["austincs"]
    release.set()
    manager.stop()


def test_duplicate_configured_channels_start_one_worker(monkeypatch):
    """Readiness counts workers against config.channels, so the two must agree."""
    monkeypatch.setattr("video_capture_service.capture_loop.TwitchSourceResolver", FakeResolver)
    monkeypatch.setattr("video_capture_service.capture_loop.FrameSampler", FakeSampler)
    config = replace(enabled_config(monkeypatch), channels=["austincs", "AustinCS", "@austincs"])
    manager = CaptureManager(config, CaptureStatusStore(enabled=True), FakeStorage(), FakePublisher())

    manager.start()

    assert manager.config.channels == ["austincs"]
    assert manager.workers_alive() == len(manager.config.channels)
    manager.stop()


def started_manager(monkeypatch) -> CaptureManager:
    """A manager capturing one channel, with every collaborator faked."""
    monkeypatch.setattr("video_capture_service.capture_loop.TwitchSourceResolver", FakeResolver)
    monkeypatch.setattr("video_capture_service.capture_loop.FrameSampler", FakeSampler)
    manager = CaptureManager(
        enabled_config(monkeypatch), CaptureStatusStore(enabled=True), FakeStorage(), FakePublisher()
    )
    manager.start()
    return manager
