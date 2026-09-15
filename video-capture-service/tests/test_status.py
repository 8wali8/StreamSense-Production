"""The status summary, including the confined form a streamer is answered with."""

from __future__ import annotations

from video_capture_service.status import CaptureState, CaptureStatusStore, ChannelStatus


def store_with_two_channels() -> CaptureStatusStore:
    store = CaptureStatusStore(enabled=True)
    store.statuses["ninja"] = ChannelStatus(channel="ninja", state=CaptureState.STOPPED)
    store.statuses["pokimane"] = ChannelStatus(
        channel="pokimane", state=CaptureState.CAPTURING, last_frame_at=1710000009999
    )
    return store


def test_snapshot_summarises_every_channel():
    snapshot = store_with_two_channels().snapshot()

    assert snapshot["channels"] == ["ninja", "pokimane"]
    assert snapshot["state"] == CaptureState.CAPTURING.value
    assert snapshot["lastFrameAt"] == 1710000009999


def test_a_confined_snapshot_summarises_only_the_channel_it_lists():
    # Listing one channel while reporting another's state and timestamps would describe someone else.
    snapshot = store_with_two_channels().snapshot(only={"ninja"})

    assert snapshot["channels"] == ["ninja"]
    assert [status["channel"] for status in snapshot["channelStatuses"]] == ["ninja"]
    assert snapshot["state"] == CaptureState.STOPPED.value
    assert snapshot["lastFrameAt"] is None
