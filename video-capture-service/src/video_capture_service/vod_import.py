"""Importing a Twitch recording with its original timestamps.

A live capture stamps every frame and transcript segment with the wall clock. An import walks a
recording from start to end instead, stamping each sample with the broadcast's start time plus the
offset into the recording and tagging it with the session key the importer chose, so the rest of the
pipeline treats the events exactly like a live stream that happened back then. Frames are sampled at
the live sample interval, because the exposure arithmetic downstream credits one interval per
accepted detection; audio is transcribed in consecutive segments unless a wider stride is asked for.

The recording is read in one sequential ffmpeg pass (``vod_stream``): a frame every interval and the
audio in interval-long chunks, consumed in order as ffmpeg decodes, with ffmpeg paused when it runs
ahead. A pass that dies is continued by seeking into the recording once per sample, the older path
(``TWITCH_VOD_IMPORT_MODE=seek`` makes it the only path).

An import can be stopped by the recording it belongs to: the loop checks its own stop event before
every sample and an ffmpeg run in flight is killed, so a stop lands within a sample interval rather
than after the capture timeout. What was published stays; a later request for the same recording
resumes from the offset reached, and every id is deterministic, so the overlap never double-counts.
"""

from __future__ import annotations

import logging
import shutil
import tempfile
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field, replace
from pathlib import Path

from video_capture_service.audio_sampler import AudioCaptureError, AudioSampler
from video_capture_service.config import CaptureConfig
from video_capture_service.frame_sampler import FrameCaptureError, FrameSampler
from video_capture_service.kafka_publisher import (
    EventPublisher,
    FrameEvent,
    TranscriptSegmentEvent,
)
from video_capture_service.process import ProcessCancelledError
from video_capture_service.storage import FrameStorage
from video_capture_service.transcription_client import TranscriptionClient, TranscriptionClientError
from video_capture_service.twitch_source import TwitchSourceResolver, TwitchStreamResolutionError
from video_capture_service.vod_stream import SequentialPass, SequentialPassError

logger = logging.getLogger(__name__)

SOURCE = "TWITCH_VOD_IMPORT"
# An import spans a whole recording; Twitch throttling or a flaky link must not end it after a few misses.
MAX_CONSECUTIVE_FAILURES = 20
FAILURE_BACKOFF_SECONDS = 5
MAX_BACKOFF_SECONDS = 60

ACTIVE_STATES = frozenset({"QUEUED", "RUNNING"})
RESUMABLE_STATES = frozenset({"FAILED", "STOPPED"})


@dataclass(frozen=True)
class VodImportRequest:
    channel: str
    vod_id: str
    vod_url: str
    base_time_ms: int
    duration_seconds: int
    stream_session_id: str
    frame_interval_seconds: int
    transcript_interval_seconds: int
    """Where to start; a resumed import continues from where the failed or stopped one reached."""
    start_offset_seconds: int = 0


@dataclass
class VodImportStatus:
    vod_id: str
    channel: str
    state: str = "QUEUED"
    offset_seconds: int = 0
    duration_seconds: int = 0
    frames_published: int = 0
    transcript_segments_published: int = 0
    failures: int = 0
    last_error: str | None = None
    started_at: int = field(default_factory=lambda: int(time.time() * 1000))
    updated_at: int = field(default_factory=lambda: int(time.time() * 1000))
    # Set by stop(); the import thread sees it before the next sample and inside a running ffmpeg.
    stop_event: threading.Event = field(default_factory=threading.Event, repr=False, compare=False)

    def as_dict(self) -> dict:
        return {
            "vodId": self.vod_id,
            "channel": self.channel,
            "state": self.state,
            "offsetSeconds": self.offset_seconds,
            "durationSeconds": self.duration_seconds,
            "framesPublished": self.frames_published,
            "transcriptSegmentsPublished": self.transcript_segments_published,
            "failures": self.failures,
            "lastError": self.last_error,
            "stopRequested": self.stop_event.is_set(),
            "startedAt": self.started_at,
            "updatedAt": self.updated_at,
        }


def import_schedule(
    duration_seconds: int, frame_interval: int, transcript_interval: int, start_offset: int = 0
) -> list[tuple[int, bool]]:
    """Every offset a frame is sampled at, and whether a transcript segment starts there too."""
    if duration_seconds <= 0 or frame_interval <= 0:
        return []
    schedule: list[tuple[int, bool]] = []
    next_transcript = max(0, start_offset)
    for offset in range(max(0, start_offset), duration_seconds, frame_interval):
        transcribe = transcript_interval > 0 and offset >= next_transcript
        if transcribe:
            next_transcript = offset + transcript_interval
        schedule.append((offset, transcribe))
    return schedule


class VodImportManager:
    """Runs one import at a time on its own thread; later requests queue behind it."""

    def __init__(
        self,
        config: CaptureConfig,
        storage: FrameStorage | None,
        publisher: EventPublisher | None,
        transcription_client: TranscriptionClient | None,
        transcript_publisher: EventPublisher | None,
    ) -> None:
        self.config = config
        self.storage = storage
        self.publisher = publisher
        self.transcription_client = transcription_client
        self.transcript_publisher = transcript_publisher
        self.statuses: dict[str, VodImportStatus] = {}
        self._executor = ThreadPoolExecutor(max_workers=1, thread_name_prefix="vod-import")
        self._lock = threading.Lock()

    def start(self, request: VodImportRequest) -> VodImportStatus:
        if not self.config.enabled or self.storage is None or self.publisher is None:
            raise RuntimeError("Twitch video capture is disabled; a recording cannot be imported")
        if request.duration_seconds <= 0 or request.frame_interval_seconds <= 0:
            raise ValueError("durationSeconds and frameIntervalSeconds must be positive")
        with self._lock:
            current = self.statuses.get(request.vod_id)
            if current is not None and current.state in ACTIVE_STATES:
                raise RuntimeError(f"import of VOD {request.vod_id} is already running")
            if current is not None and current.state in RESUMABLE_STATES and request.start_offset_seconds == 0:
                # Pick up where the earlier run stopped; ids are deterministic, so an overlap is harmless.
                request = replace(request, start_offset_seconds=current.offset_seconds)
            status = VodImportStatus(
                vod_id=request.vod_id,
                channel=request.channel,
                duration_seconds=request.duration_seconds,
                offset_seconds=request.start_offset_seconds,
            )
            self.statuses[request.vod_id] = status
        self._executor.submit(self._run, request, status)
        return status

    def status(self, vod_id: str) -> VodImportStatus | None:
        return self.statuses.get(vod_id)

    def snapshot(self) -> list[dict]:
        return [status.as_dict() for status in self.statuses.values()]

    def stop(self, vod_id: str) -> VodImportStatus | None:
        """Stops this recording's import and no other. A queued one ends at once; a running one within a sample.

        Returns the status, or None when nothing is known about the recording. Stopping an import
        that already ended changes nothing.
        """
        with self._lock:
            status = self.statuses.get(vod_id)
            if status is None:
                return None
            if status.state == "QUEUED":
                status.state = "STOPPED"
            if status.state in ACTIVE_STATES:
                status.stop_event.set()
            status.updated_at = int(time.time() * 1000)
            return status

    def shutdown(self) -> None:
        """Process exit: every import ends; nothing resumes on its own when the service comes back."""
        with self._lock:
            for status in self.statuses.values():
                if status.state == "QUEUED":
                    status.state = "STOPPED"
                status.stop_event.set()
        self._executor.shutdown(wait=False, cancel_futures=True)

    def _run(self, request: VodImportRequest, status: VodImportStatus) -> None:
        storage, publisher = self.storage, self.publisher
        if storage is None or publisher is None:
            return
        if status.state != "QUEUED":
            # Stopped while it waited its turn.
            return
        status.state = "RUNNING"
        resolver = TwitchSourceResolver(
            self.config.quality, self.config.stream_resolve_timeout_seconds, self.config.twitch_oauth_token
        )
        transcribe_enabled = (
            self.config.transcript_enabled
            and self.transcription_client is not None
            and self.transcript_publisher is not None
        )
        suffix = "jpg" if self.config.output_format in {"jpg", "jpeg"} else self.config.output_format
        schedule = import_schedule(
            request.duration_seconds,
            request.frame_interval_seconds,
            request.transcript_interval_seconds,
            request.start_offset_seconds,
        )
        try:
            position = 0
            if self.config.vod_import_mode == "sequential" and schedule:
                reached = self._run_sequential(
                    request, status, schedule, storage, publisher, resolver, suffix, transcribe_enabled
                )
                if reached is None:
                    return
                position = reached
            if position < len(schedule):
                if position > 0:
                    logger.warning(
                        "VOD import continues by seeking vod=%s from offset=%ss",
                        request.vod_id,
                        schedule[position][0],
                    )
                finished = self._run_seeking(
                    request, status, schedule[position:], storage, publisher, resolver, suffix, transcribe_enabled
                )
                if not finished:
                    return
            status.offset_seconds = request.duration_seconds
            status.state = "DONE"
            logger.info(
                "VOD import finished vod=%s channel=%s frames=%s transcripts=%s failures=%s",
                request.vod_id,
                request.channel,
                status.frames_published,
                status.transcript_segments_published,
                status.failures,
            )
        finally:
            status.updated_at = int(time.time() * 1000)

    def _run_sequential(
        self,
        request: VodImportRequest,
        status: VodImportStatus,
        schedule: list[tuple[int, bool]],
        storage: FrameStorage,
        publisher: EventPublisher,
        resolver: TwitchSourceResolver,
        suffix: str,
        transcribe_enabled: bool,
    ) -> int | None:
        """One ffmpeg pass over the schedule. Returns how many entries were handled (the whole schedule
        when the pass ran to the end), or None when the import was stopped."""
        scratch = Path(tempfile.gettempdir()) / "streamsense-video-capture" / f"vod-pass-{request.vod_id}"
        shutil.rmtree(scratch, ignore_errors=True)
        try:
            hls_url = resolver.resolve_url(request.vod_url, request.channel)
        except TwitchStreamResolutionError as exc:
            self._failure(status, f"resolving the recording: {exc}")
            return 0
        run = SequentialPass(
            hls_url,
            scratch,
            request.start_offset_seconds,
            request.frame_interval_seconds,
            request.transcript_interval_seconds,
            transcribe_enabled,
            self.config.output_format,
            self.config.jpeg_quality,
            self.config.frame_capture_timeout_seconds,
            max(60, 4 * self.config.frame_capture_timeout_seconds),
            self.config.vod_import_backlog_samples,
        )
        try:
            run.start()
        except SequentialPassError as exc:
            self._failure(status, f"sequential pass: {exc}")
            return 0
        stop = status.stop_event
        sequence = 0
        transcript_sequence = 0
        index = 0
        try:
            for index, (offset, transcribe) in enumerate(schedule):
                if stop.is_set():
                    self._stopped(request, status)
                    return None
                status.offset_seconds = offset
                status.updated_at = int(time.time() * 1000)
                frame = run.wait_for(run.frame_path(index), run.frame_path(index + 1), stop)
                if frame is None:
                    if len(schedule) - index <= 1:
                        # The recording ended a hair before Twitch's stated length: nothing is missing.
                        return len(schedule)
                    logger.warning(
                        "VOD import pass ended early vod=%s at offset=%ss (exit=%s): %s",
                        request.vod_id,
                        offset,
                        run.returncode(),
                        run.last_error() or "no error output",
                    )
                    return index
                sequence += 1
                try:
                    self._publish_frame(request, status, storage, publisher, frame, offset, sequence, suffix)
                except Exception as exc:  # noqa: BLE001 - the pass must reach the end; failures are counted
                    self._failure(status, f"at {offset}s: {exc}")
                finally:
                    self._unlink(frame)
                if transcribe and transcribe_enabled:
                    audio_index = round((offset - request.start_offset_seconds) / request.transcript_interval_seconds)
                    audio = run.wait_for(run.audio_path(audio_index), run.audio_path(audio_index + 1), stop)
                    if audio is None:
                        self._failure(status, f"transcript at {offset}s: the pass produced no audio")
                    else:
                        transcript_sequence += 1
                        try:
                            self._transcribe_file(request, status, audio, offset, transcript_sequence)
                        finally:
                            self._unlink(audio)
                # The sample is done: a stop or a resume from here starts at the next one.
                status.offset_seconds = min(request.duration_seconds, offset + request.frame_interval_seconds)
                run.throttle(index + 1)
            return len(schedule)
        except ProcessCancelledError:
            self._stopped(request, status)
            return None
        except SequentialPassError as exc:
            self._failure(status, f"sequential pass at {status.offset_seconds}s: {exc}")
            return index
        finally:
            run.close()

    def _run_seeking(
        self,
        request: VodImportRequest,
        status: VodImportStatus,
        schedule: list[tuple[int, bool]],
        storage: FrameStorage,
        publisher: EventPublisher,
        resolver: TwitchSourceResolver,
        suffix: str,
        transcribe_enabled: bool,
    ) -> bool:
        """One ffmpeg seek per sample. Returns True when the schedule ran through, False when it stopped or gave up."""
        sampler = FrameSampler(
            self.config.frame_capture_timeout_seconds, self.config.output_format, self.config.jpeg_quality
        )
        audio_sampler = AudioSampler(
            self.config.transcript_audio_capture_timeout_seconds, self.config.transcript_segment_duration_seconds
        )
        hls_url: str | None = None
        consecutive_failures = 0
        sequence = status.frames_published
        transcript_sequence = status.transcript_segments_published
        stop = status.stop_event
        for offset, transcribe in schedule:
            if stop.is_set():
                self._stopped(request, status)
                return False
            status.offset_seconds = offset
            status.updated_at = int(time.time() * 1000)
            try:
                if hls_url is None:
                    hls_url = resolver.resolve_url(request.vod_url, request.channel)
                sequence += 1
                self._frame(request, status, storage, publisher, sampler, hls_url, offset, sequence, suffix)
                if transcribe and transcribe_enabled:
                    transcript_sequence += 1
                    self._transcript(request, status, audio_sampler, hls_url, offset, transcript_sequence)
                consecutive_failures = 0
                status.offset_seconds = min(request.duration_seconds, offset + request.frame_interval_seconds)
            except ProcessCancelledError:
                # The stop landed inside ffmpeg; the sample at this offset was not published.
                self._stopped(request, status)
                return False
            except (TwitchStreamResolutionError, FrameCaptureError) as exc:
                # The HLS playlist of a recording expires; resolve it again on the next sample.
                hls_url = None
                consecutive_failures += 1
                self._failure(status, f"capture at {offset}s: {exc}")
            except Exception as exc:  # noqa: BLE001 - the import must reach the end; failures are counted
                consecutive_failures += 1
                self._failure(status, f"at {offset}s: {exc}")
            if consecutive_failures >= MAX_CONSECUTIVE_FAILURES:
                status.state = "FAILED"
                logger.warning("VOD import gave up vod=%s after %s failures", request.vod_id, consecutive_failures)
                return False
            if consecutive_failures > 0:
                # Twitch throttles bursts of HLS seeks and the link can drop; wait before the next sample.
                stop.wait(min(MAX_BACKOFF_SECONDS, FAILURE_BACKOFF_SECONDS * consecutive_failures))
        return True

    @staticmethod
    def _stopped(request: VodImportRequest, status: VodImportStatus) -> None:
        status.state = "STOPPED"
        logger.info(
            "VOD import stopped vod=%s channel=%s at offset=%ss frames=%s transcripts=%s",
            request.vod_id,
            request.channel,
            status.offset_seconds,
            status.frames_published,
            status.transcript_segments_published,
        )

    def _frame(
        self,
        request: VodImportRequest,
        status: VodImportStatus,
        storage: FrameStorage,
        publisher: EventPublisher,
        sampler: FrameSampler,
        hls_url: str,
        offset: int,
        sequence: int,
        suffix: str,
    ) -> None:
        frame_id = f"vod-{request.vod_id}-f{offset}"
        temp_path = Path(tempfile.gettempdir()) / "streamsense-video-capture" / f"{frame_id}.{suffix}"
        try:
            captured_path, _ = sampler.capture(hls_url, temp_path, float(offset), status.stop_event)
            self._publish_frame(request, status, storage, publisher, captured_path, offset, sequence, suffix)
        finally:
            self._unlink(temp_path)

    def _publish_frame(
        self,
        request: VodImportRequest,
        status: VodImportStatus,
        storage: FrameStorage,
        publisher: EventPublisher,
        captured_path: Path,
        offset: int,
        sequence: int,
        suffix: str,
    ) -> None:
        # Deterministic per offset: a resumed import re-samples the same frame id, and video-service derives
        # the detection id from it, so analytics never counts an offset twice.
        frame_id = f"vod-{request.vod_id}-f{offset}"
        object_key = (
            f"{self.config.storage.path_prefix}/{request.channel}/{request.stream_session_id}/"
            f"{sequence:06d}-{frame_id}.{suffix}"
        )
        stored = storage.store(captured_path, object_key, "image/jpeg" if suffix == "jpg" else "image/png")
        publisher.publish(
            FrameEvent(
                frameId=frame_id,
                streamer=request.channel,
                frameRef=stored.frame_ref,
                frameSequence=sequence,
                capturedAt=request.base_time_ms + offset * 1000,
                source=SOURCE,
                channelLogin=request.channel,
                streamSessionId=request.stream_session_id,
                twitchStreamId=request.vod_id,
                videoTimestampMs=offset * 1000,
                artifactContentType=stored.content_type,
                artifactSizeBytes=stored.size_bytes,
                captureWorkerId=self.config.worker_id,
            )
        )
        status.frames_published += 1

    def _transcript(
        self,
        request: VodImportRequest,
        status: VodImportStatus,
        audio_sampler: AudioSampler,
        hls_url: str,
        offset: int,
        sequence: int,
    ) -> None:
        segment_id = f"vod-{request.vod_id}-a{offset}"
        temp_path = Path(tempfile.gettempdir()) / "streamsense-video-capture" / f"{segment_id}.wav"
        try:
            audio_path, _ = audio_sampler.capture(hls_url, temp_path, float(offset), status.stop_event)
            self._transcribe_file(request, status, audio_path, offset, sequence)
        except AudioCaptureError as exc:
            # A silent or failed segment is not a reason to stop the frames.
            self._failure(status, f"transcript at {offset}s: {exc}")
        finally:
            self._unlink(temp_path)

    def _transcribe_file(
        self,
        request: VodImportRequest,
        status: VodImportStatus,
        audio_path: Path,
        offset: int,
        sequence: int,
    ) -> None:
        client, publisher = self.transcription_client, self.transcript_publisher
        if client is None or publisher is None:
            return
        segment_id = f"vod-{request.vod_id}-a{offset}"
        started_at = request.base_time_ms + offset * 1000
        ended_at = started_at + self.config.transcript_segment_duration_seconds * 1000
        try:
            result, _ = client.transcribe(audio_path, request.channel, segment_id, started_at, ended_at)
            text = result.text.strip()[: self.config.transcript_max_chars]
            if not text:
                return
            publisher.publish(
                TranscriptSegmentEvent(
                    segmentId=segment_id,
                    streamer=request.channel,
                    text=text,
                    startedAt=started_at,
                    endedAt=ended_at,
                    language=result.language,
                    confidence=result.confidence,
                    modelVersion=result.model_version,
                    source=SOURCE,
                    channelLogin=request.channel,
                    streamSessionId=request.stream_session_id,
                    twitchStreamId=request.vod_id,
                    videoTimestampMs=offset * 1000,
                    transcriptSequence=sequence,
                    captureWorkerId=self.config.worker_id,
                )
            )
            status.transcript_segments_published += 1
        except TranscriptionClientError as exc:
            # A silent or failed segment is not a reason to stop the frames.
            self._failure(status, f"transcript at {offset}s: {exc}")

    @staticmethod
    def _unlink(path: Path) -> None:
        try:
            path.unlink(missing_ok=True)
        except OSError:
            logger.warning("failed to remove temp path=%s", path)

    @staticmethod
    def _failure(status: VodImportStatus, error: str) -> None:
        status.failures += 1
        status.last_error = error[-500:]
        status.updated_at = int(time.time() * 1000)
        logger.warning("VOD import issue vod=%s error=%s", status.vod_id, status.last_error)
