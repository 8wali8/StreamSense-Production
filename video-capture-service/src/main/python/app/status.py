from dataclasses import dataclass, field
from enum import Enum


class CaptureState(str, Enum):
    DISABLED = "DISABLED"
    STARTING = "STARTING"
    RESOLVING_STREAM = "RESOLVING_STREAM"
    CAPTURING = "CAPTURING"
    IDLE_OFFLINE = "IDLE_OFFLINE"
    RECONNECTING = "RECONNECTING"
    DEGRADED_STORAGE = "DEGRADED_STORAGE"
    DEGRADED_KAFKA = "DEGRADED_KAFKA"
    FAILED = "FAILED"
    STOPPED = "STOPPED"


@dataclass
class ChannelStatus:
    channel: str
    state: CaptureState = CaptureState.DISABLED
    capture_session_id: str | None = None
    last_frame_at: int | None = None
    last_frame_ref: str | None = None
    last_error: str | None = None
    frames_captured: int = 0
    frames_stored: int = 0
    frames_published: int = 0
    frames_skipped: int = 0
    transcript_segments_captured: int = 0
    transcript_segments_transcribed: int = 0
    transcript_segments_published: int = 0
    transcript_segments_skipped: int = 0
    last_transcript_at: int | None = None
    last_transcript_segment_id: str | None = None
    last_transcript_preview: str | None = None
    reconnect_attempts: int = 0
    consecutive_failures: int = 0

    def as_dict(self) -> dict:
        return {
            "channel": self.channel,
            "state": self.state.value,
            "captureSessionId": self.capture_session_id,
            "lastFrameAt": self.last_frame_at,
            "lastFrameRef": self.last_frame_ref,
            "lastError": self.last_error,
            "framesCaptured": self.frames_captured,
            "framesStored": self.frames_stored,
            "framesPublished": self.frames_published,
            "framesSkipped": self.frames_skipped,
            "transcriptSegmentsCaptured": self.transcript_segments_captured,
            "transcriptSegmentsTranscribed": self.transcript_segments_transcribed,
            "transcriptSegmentsPublished": self.transcript_segments_published,
            "transcriptSegmentsSkipped": self.transcript_segments_skipped,
            "lastTranscriptAt": self.last_transcript_at,
            "lastTranscriptSegmentId": self.last_transcript_segment_id,
            "lastTranscriptPreview": self.last_transcript_preview,
            "reconnectAttempts": self.reconnect_attempts,
        }


@dataclass
class CaptureStatusStore:
    enabled: bool
    statuses: dict[str, ChannelStatus] = field(default_factory=dict)

    def snapshot(self) -> dict:
        channel_statuses = [status.as_dict() for status in self.statuses.values()]
        states = {status.state for status in self.statuses.values()}
        if not self.enabled:
            state = CaptureState.DISABLED.value
        elif CaptureState.CAPTURING in states:
            state = CaptureState.CAPTURING.value
        elif CaptureState.FAILED in states:
            state = CaptureState.FAILED.value
        elif states:
            state = min(item.value for item in states)
        else:
            state = CaptureState.DISABLED.value

        last_frame = max(
            (status.last_frame_at for status in self.statuses.values() if status.last_frame_at),
            default=None,
        )
        last_transcript = max(
            (status.last_transcript_at for status in self.statuses.values() if status.last_transcript_at),
            default=None,
        )
        return {
            "enabled": self.enabled,
            "state": state,
            "channels": list(self.statuses.keys()),
            "lastFrameAt": last_frame,
            "lastTranscriptAt": last_transcript,
            "channelStatuses": channel_statuses,
        }
