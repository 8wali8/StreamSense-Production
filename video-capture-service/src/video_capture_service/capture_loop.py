import logging
import tempfile
import threading
import time
import uuid
from dataclasses import replace
from pathlib import Path

from boto3.exceptions import Boto3Error
from botocore.exceptions import BotoCoreError, ClientError
from kafka.errors import KafkaError

from video_capture_service import metrics
from video_capture_service.audio_sampler import AudioCaptureError, AudioSampler
from video_capture_service.config import CaptureConfig, ReplayAliasConfig, normalize_channels
from video_capture_service.frame_sampler import FrameCaptureError, FrameSampler
from video_capture_service.kafka_publisher import (
    FrameEvent,
    FrameEventPublisher,
    TranscriptEventPublisher,
    TranscriptSegmentEvent,
)
from video_capture_service.status import CaptureState, CaptureStatusStore, ChannelStatus
from video_capture_service.storage import FrameStorage
from video_capture_service.transcription_client import TranscriptionClient, TranscriptionClientError
from video_capture_service.twitch_source import (
    TwitchSourceResolver,
    TwitchStreamOfflineError,
    TwitchStreamResolutionError,
)

logger = logging.getLogger(__name__)

# How long a stop waits for a worker before leaving it to wind down on its own. A frame or transcript
# capture blocks for its own timeout (15-60 s), so the wait is a courtesy, not a guarantee.
WORKER_STOP_TIMEOUT_SECONDS = 5


class CaptureManager:
    def __init__(
        self,
        config: CaptureConfig,
        status_store: CaptureStatusStore,
        storage: FrameStorage | None,
        publisher: FrameEventPublisher | None,
        transcription_client: TranscriptionClient | None = None,
        transcript_publisher: TranscriptEventPublisher | None = None,
    ):
        self.config = config
        self.status_store = status_store
        self.storage = storage
        self.publisher = publisher
        self.transcription_client = transcription_client
        self.transcript_publisher = transcript_publisher
        # One stop event per worker, keyed by channel, so one channel can start or stop on its own and
        # switching channels never races a still-running loop.
        self.workers: dict[str, tuple[threading.Thread, threading.Event]] = {}
        # Held around every change to the channel set: two callers must not start the same worker twice.
        self._channels_lock = threading.RLock()

    def start(self) -> None:
        metrics.capture_enabled.set(1 if self.config.enabled else 0)
        if not self.config.enabled:
            logger.info("Twitch video capture disabled")
            return
        if self.storage is None or self.publisher is None:
            raise ValueError("storage and publisher are required when capture is enabled")

        with self._channels_lock:
            # The same normalisation the runtime switches use, so readiness counts workers against a
            # list that holds one entry per channel: a duplicate would expect a worker that cannot exist.
            self.config = replace(self.config, channels=normalize_channels(self.config.channels))
            for channel in self.config.channels:
                self._start_channel(channel)

    def stop(self) -> None:
        self._stop_threads()
        if self.publisher:
            self.publisher.close()
        if self.transcript_publisher:
            self.transcript_publisher.close()

    def switch_channels(self, channels: list[str]) -> dict:
        normalized = normalize_channels(channels)
        if not normalized:
            raise ValueError("at least one Twitch channel is required")
        if len(normalized) > self.config.max_channels:
            raise ValueError(f"at most {self.config.max_channels} Twitch channels")
        self._require_running_capture()

        with self._channels_lock:
            for channel in list(self.workers):
                if channel not in normalized:
                    self._stop_channel(channel)
            for channel in list(self.status_store.statuses):
                # A worker still winding down keeps its entry, so it is visible until it is gone.
                if channel not in normalized and channel not in self.workers:
                    del self.status_store.statuses[channel]
            self.config = replace(self.config, channels=normalized)
            # A channel that stays keeps its worker and its capture session id, so pointing capture at one
            # more channel does not split the stream of a channel already being captured into two sessions.
            for channel in normalized:
                self._start_channel(channel)
        return self.status_store.snapshot()

    def add_channel(self, channel: str) -> dict:
        """Starts capturing one channel and leaves the others alone. Idempotent."""
        name = _require_channel(channel)
        self._require_running_capture()

        with self._channels_lock:
            if name not in self.config.channels:
                if len(self.config.channels) >= self.config.max_channels:
                    raise RuntimeError(f"Twitch video capture is already measuring {self.config.max_channels} channels")
                self.config = replace(self.config, channels=[*self.config.channels, name])
            self._start_channel(name)
            return self.channel_status(name)

    def remove_channel(self, channel: str) -> dict:
        """Stops capturing one channel, leaving the others alone. Idempotent."""
        name = _require_channel(channel)

        with self._channels_lock:
            self.config = replace(self.config, channels=[c for c in self.config.channels if c != name])
            stopped = self._stop_channel(name)
            if not stopped:
                # Still winding down: keep its status so the console sees STOPPING rather than nothing.
                status = self.status_store.statuses.get(name)
                return (status or ChannelStatus(channel=name, state=CaptureState.STOPPING)).as_dict()
            status = self.status_store.statuses.pop(name, None)
            if status is not None:
                return status.as_dict()
            return ChannelStatus(channel=name, state=CaptureState.STOPPED).as_dict()

    def channel_status(self, channel: str) -> dict:
        """One channel's status, without naming the other channels being captured."""
        name = _require_channel(channel)
        status = self.status_store.statuses.get(name)
        if status is None:
            state = CaptureState.DISABLED if not self.config.enabled else CaptureState.STOPPED
            return ChannelStatus(channel=name, state=state).as_dict()
        return status.as_dict()

    def workers_alive(self) -> int:
        return sum(1 for thread, _ in self.workers.values() if thread.is_alive())

    def _require_running_capture(self) -> None:
        if not self.config.enabled:
            raise RuntimeError("Twitch video capture is disabled")
        if self.storage is None or self.publisher is None:
            raise RuntimeError("storage and publisher are required when capture is enabled")

    def _start_channel(self, channel: str) -> None:
        worker = self.workers.get(channel)
        if worker is not None and worker[0].is_alive():
            if worker[1].is_set():
                # Still inside a capture call it cannot be interrupted out of. A second worker would
                # publish alongside it under a new capture session, so the caller waits instead.
                raise RuntimeError(f"channel {channel} is still stopping; try again in a moment")
            return
        status = self.status_store.statuses.setdefault(channel, ChannelStatus(channel=channel))
        status.state = CaptureState.STARTING
        status.capture_session_id = self._session_id(channel)
        stop_event = threading.Event()
        thread = threading.Thread(
            target=self._capture_channel, args=(channel, stop_event), name=f"capture-{channel}", daemon=True
        )
        thread.start()
        self.workers[channel] = (thread, stop_event)
        logger.info("started Twitch video capture loop channel=%s session=%s", channel, status.capture_session_id)

    def _stop_channel(self, channel: str) -> bool:
        """Asks the worker to stop and waits for it. False when it outlived the wait and still exists."""
        worker = self.workers.get(channel)
        status = self.status_store.statuses.get(channel)
        if worker is None:
            if status is not None and status.state != CaptureState.DISABLED:
                status.state = CaptureState.STOPPED
            return True

        thread, stop_event = worker
        stop_event.set()
        thread.join(timeout=WORKER_STOP_TIMEOUT_SECONDS)
        if thread.is_alive():
            # A frame or transcript capture blocks for its own timeout, well past this wait. The worker
            # is kept until it is really gone, so nothing starts a second one for the channel and the
            # status says what is true: stopping, not stopped.
            logger.warning(
                "capture worker channel=%s did not stop within %ss; it is winding down",
                channel,
                WORKER_STOP_TIMEOUT_SECONDS,
            )
            if status is not None and status.state != CaptureState.DISABLED:
                status.state = CaptureState.STOPPING
            return False

        del self.workers[channel]
        if status is not None and status.state != CaptureState.DISABLED:
            status.state = CaptureState.STOPPED
        return True

    def _stop_threads(self) -> None:
        with self._channels_lock:
            # Every worker is asked to stop first, then joined, so shutdown is not one timeout per channel.
            for _, stop_event in self.workers.values():
                stop_event.set()
            for channel in list(self.workers):
                self._stop_channel(channel)
            for status in self.status_store.statuses.values():
                if status.state != CaptureState.DISABLED:
                    status.state = CaptureState.STOPPED

    def _capture_channel(self, channel: str, stop_event: threading.Event) -> None:
        # start() and switch_channels() refuse to run without sinks; bind them once so the loop below is typed
        storage, publisher = self.storage, self.publisher
        if storage is None or publisher is None:
            raise RuntimeError("storage and publisher are required when capture is enabled")
        status = self.status_store.statuses[channel]
        replay_alias = self.config.replay_aliases.get(channel)
        resolver = TwitchSourceResolver(
            self.config.quality,
            self.config.stream_resolve_timeout_seconds,
            self.config.twitch_oauth_token,
        )
        sampler = FrameSampler(
            self.config.frame_capture_timeout_seconds,
            self.config.output_format,
            self.config.jpeg_quality,
        )
        audio_sampler = AudioSampler(
            self.config.transcript_audio_capture_timeout_seconds,
            self.config.transcript_segment_duration_seconds,
        )
        sequence = 0
        transcript_sequence = 0
        replay_started_at = time.monotonic()

        while not stop_event.is_set():
            self._set_state(channel, CaptureState.RESOLVING_STREAM)
            try:
                hls_url = (
                    resolver.resolve_url(replay_alias.vod_url, channel) if replay_alias else resolver.resolve(channel)
                )
            except TwitchStreamOfflineError as exc:
                self._record_failure(channel, CaptureState.IDLE_OFFLINE, str(exc), "resolve", reconnect=False)
                self._sleep(stop_event, self.config.reconnect_delay_seconds)
                continue
            except TwitchStreamResolutionError as exc:
                self._record_failure(channel, CaptureState.RECONNECTING, str(exc), "resolve", reconnect=True)
                self._sleep(stop_event, self._backoff(status.consecutive_failures))
                continue

            sequence += 1
            frame_id = str(uuid.uuid4())
            captured_at = int(time.time() * 1000)
            replay_offset_seconds = _replay_offset_seconds(replay_alias, replay_started_at)
            suffix = "jpg" if self.config.output_format in {"jpg", "jpeg"} else self.config.output_format
            temp_path = Path(tempfile.gettempdir()) / "streamsense-video-capture" / f"{frame_id}.{suffix}"

            try:
                captured_path, capture_latency_ms = sampler.capture(hls_url, temp_path, replay_offset_seconds)
                if stop_event.is_set():
                    # The channel was stopped while this capture was running: its frame belongs to a
                    # session that is over, and another worker may already be starting.
                    return
                metrics.capture_latency.labels(channel=channel).observe(capture_latency_ms)
                status.frames_captured += 1
                metrics.frames_captured.labels(channel=channel).inc()

                object_key = self._object_key(channel, status.capture_session_id, sequence, frame_id, suffix)
                stored = storage.store(captured_path, object_key, _content_type(suffix))
                metrics.storage_latency.labels(channel=channel).observe(stored.latency_ms)
                status.frames_stored += 1
                metrics.frames_stored.labels(channel=channel).inc()

                event = FrameEvent(
                    frameId=frame_id,
                    streamer=channel,
                    frameRef=stored.frame_ref,
                    frameSequence=sequence,
                    capturedAt=captured_at,
                    source=replay_alias.source if replay_alias else "TWITCH",
                    channelLogin=channel,
                    streamSessionId=status.capture_session_id or f"{channel}-{captured_at}",
                    twitchStreamId=replay_alias.vod_id if replay_alias else None,
                    videoTimestampMs=_video_timestamp_ms(
                        replay_alias, replay_offset_seconds, sequence, self.config.sample_interval_seconds
                    ),
                    artifactContentType=stored.content_type,
                    artifactSizeBytes=stored.size_bytes,
                    captureWorkerId=self.config.worker_id,
                )
                publish_latency_ms = publisher.publish(event)
                metrics.publish_latency.labels(channel=channel).observe(publish_latency_ms)

                status.frames_published += 1
                status.last_frame_at = captured_at
                status.last_frame_ref = stored.frame_ref
                status.last_error = None
                status.consecutive_failures = 0
                status.state = CaptureState.CAPTURING
                metrics.frames_published.labels(channel=channel).inc()
                metrics.last_frame_age_seconds.labels(channel=channel).set(0)
                self._set_state(channel, CaptureState.CAPTURING)
                logger.info(
                    "captured Twitch frame channel=%s sequence=%s frameId=%s sizeBytes=%s",
                    channel,
                    sequence,
                    frame_id,
                    stored.size_bytes,
                )
                if self.config.transcript_enabled:
                    transcript_sequence += 1
                    self._capture_transcript_segment(
                        channel,
                        hls_url,
                        status,
                        audio_sampler,
                        transcript_sequence,
                        stop_event,
                        replay_alias,
                        replay_offset_seconds,
                    )
            except FrameCaptureError as exc:
                if replay_alias and replay_alias.loop:
                    replay_started_at = time.monotonic()
                    status.capture_session_id = self._session_id(channel)
                status.frames_skipped += 1
                metrics.frames_skipped.labels(channel=channel, reason="capture_error").inc()
                self._record_failure(channel, CaptureState.RECONNECTING, str(exc), "capture", reconnect=True)
            except KafkaError as exc:
                status.frames_skipped += 1
                metrics.kafka_publish_errors.labels(channel=channel).inc()
                metrics.frames_skipped.labels(channel=channel, reason="kafka").inc()
                self._record_failure(channel, CaptureState.DEGRADED_KAFKA, str(exc), "kafka", reconnect=True)
            # boto3's transfer layer wraps a failed upload_file in S3UploadFailedError, a Boto3Error
            # rather than a botocore exception, so it is listed here too.
            except (BotoCoreError, ClientError, Boto3Error, OSError) as exc:
                status.frames_skipped += 1
                metrics.storage_errors.labels(channel=channel).inc()
                metrics.frames_skipped.labels(channel=channel, reason="storage").inc()
                self._record_failure(channel, CaptureState.DEGRADED_STORAGE, str(exc), "storage", reconnect=True)
            except Exception as exc:
                # Isolation boundary: the worker must survive, but an unclassified failure is a bug to fix.
                logger.exception("unexpected capture failure channel=%s sequence=%s", channel, sequence)
                status.frames_skipped += 1
                metrics.frames_skipped.labels(channel=channel, reason="unexpected").inc()
                self._record_failure(channel, CaptureState.RECONNECTING, str(exc), "unexpected", reconnect=True)
            finally:
                try:
                    temp_path.unlink(missing_ok=True)
                except OSError:
                    logger.warning("failed to remove temp frame path=%s", temp_path)

            if status.consecutive_failures >= self.config.max_consecutive_failures:
                status.state = CaptureState.FAILED
                self._set_state(channel, CaptureState.FAILED)
            self._sleep(stop_event, self.config.sample_interval_seconds)

    def _record_failure(self, channel: str, state: CaptureState, error: str, stage: str, reconnect: bool) -> None:
        status = self.status_store.statuses[channel]
        status.state = state
        status.last_error = error[-500:]
        status.consecutive_failures += 1
        metrics.capture_errors.labels(channel=channel, stage=stage).inc()
        if reconnect:
            status.reconnect_attempts += 1
            metrics.reconnects.labels(channel=channel).inc()
        self._set_state(channel, state)
        logger.warning(
            "Twitch video capture issue channel=%s state=%s stage=%s error=%s",
            channel,
            state.value,
            stage,
            status.last_error,
        )

    def _capture_transcript_segment(
        self,
        channel: str,
        hls_url: str,
        status,
        audio_sampler: AudioSampler,
        sequence: int,
        stop_event: threading.Event,
        replay_alias: ReplayAliasConfig | None = None,
        replay_offset_seconds: float | None = None,
    ) -> None:
        if self.transcription_client is None or self.transcript_publisher is None:
            return

        segment_id = str(uuid.uuid4())
        started_at = int(time.time() * 1000)
        temp_path = Path(tempfile.gettempdir()) / "streamsense-video-capture" / f"{segment_id}.wav"
        try:
            audio_path, audio_latency_ms = audio_sampler.capture(hls_url, temp_path, replay_offset_seconds)
            status.transcript_segments_captured += 1
            metrics.transcript_audio_captured.labels(channel=channel).inc()
            metrics.transcript_audio_capture_latency.labels(channel=channel).observe(audio_latency_ms)

            ended_at = int(time.time() * 1000)
            result, transcription_latency_ms = self.transcription_client.transcribe(
                audio_path, channel, segment_id, started_at, ended_at
            )
            status.transcript_segments_transcribed += 1
            metrics.transcription_request_latency.labels(channel=channel).observe(transcription_latency_ms)

            text = result.text.strip()[: self.config.transcript_max_chars]
            if not text:
                status.transcript_segments_skipped += 1
                metrics.transcript_segments_skipped.labels(channel=channel, reason="empty_text").inc()
                return

            event = TranscriptSegmentEvent(
                segmentId=segment_id,
                streamer=channel,
                text=text,
                startedAt=started_at,
                endedAt=ended_at,
                language=result.language,
                confidence=result.confidence,
                modelVersion=result.model_version,
                source=replay_alias.source if replay_alias else "TWITCH",
                channelLogin=channel,
                streamSessionId=status.capture_session_id or f"{channel}-{started_at}",
                twitchStreamId=replay_alias.vod_id if replay_alias else None,
                videoTimestampMs=_video_timestamp_ms(
                    replay_alias, replay_offset_seconds, sequence, self.config.transcript_segment_duration_seconds
                ),
                transcriptSequence=sequence,
                captureWorkerId=self.config.worker_id,
            )
            if stop_event.is_set():
                # Stopped while the transcription call was out: this segment is from a session that is over.
                return
            self.transcript_publisher.publish(event)
            status.transcript_segments_published += 1
            status.last_transcript_at = ended_at
            status.last_transcript_segment_id = segment_id
            status.last_transcript_preview = text[: self.config.transcript_preview_chars]
            metrics.transcript_segments_published.labels(channel=channel).inc()
            logger.info(
                "published Twitch transcript channel=%s sequence=%s segmentId=%s textLength=%s",
                channel,
                sequence,
                segment_id,
                len(text),
            )
        except AudioCaptureError as exc:
            status.transcript_segments_skipped += 1
            metrics.transcript_errors.labels(channel=channel, stage="audio_capture").inc()
            metrics.transcript_segments_skipped.labels(channel=channel, reason="audio_capture").inc()
            logger.warning("Twitch transcript audio capture failed channel=%s error=%s", channel, exc)
        except TranscriptionClientError as exc:
            status.transcript_segments_skipped += 1
            metrics.transcript_errors.labels(channel=channel, stage="transcription").inc()
            metrics.transcript_segments_skipped.labels(channel=channel, reason="transcription").inc()
            logger.warning("Twitch transcription failed channel=%s error=%s", channel, exc)
        except KafkaError as exc:
            status.transcript_segments_skipped += 1
            metrics.transcript_errors.labels(channel=channel, stage="publish").inc()
            metrics.transcript_segments_skipped.labels(channel=channel, reason="publish").inc()
            logger.warning("Twitch transcript publish failed channel=%s error=%s", channel, exc)
        except Exception:
            status.transcript_segments_skipped += 1
            metrics.transcript_errors.labels(channel=channel, stage="unexpected").inc()
            metrics.transcript_segments_skipped.labels(channel=channel, reason="unexpected").inc()
            logger.exception("unexpected transcript failure channel=%s sequence=%s", channel, sequence)
        finally:
            try:
                temp_path.unlink(missing_ok=True)
            except OSError:
                logger.warning("failed to remove temp audio path=%s", temp_path)

    def _set_state(self, channel: str, state: CaptureState) -> None:
        for candidate in CaptureState:
            metrics.capture_state.labels(channel=channel, state=candidate.value).set(1 if candidate == state else 0)

    def _sleep(self, stop_event: threading.Event, seconds: int) -> None:
        stop_event.wait(seconds)

    def _backoff(self, failures: int) -> int:
        delay = self.config.reconnect_delay_seconds * max(1, failures)
        return min(delay, self.config.max_reconnect_delay_seconds)

    def _object_key(self, channel: str, session_id: str | None, sequence: int, frame_id: str, suffix: str) -> str:
        session = session_id or f"{channel}-{int(time.time() * 1000)}"
        return f"{self.config.storage.path_prefix}/{channel}/{session}/{sequence:06d}-{frame_id}.{suffix}"

    def _session_id(self, channel: str) -> str:
        replay_alias = self.config.replay_aliases.get(channel)
        timestamp = int(time.time() * 1000)
        if replay_alias:
            return f"{channel}-{replay_alias.vod_id}-{timestamp}"
        return f"{channel}-{timestamp}"


def _content_type(suffix: str) -> str:
    if suffix.lower() in {"jpg", "jpeg"}:
        return "image/jpeg"
    if suffix.lower() == "png":
        return "image/png"
    return "application/octet-stream"


def _replay_offset_seconds(replay_alias: ReplayAliasConfig | None, replay_started_at: float) -> float | None:
    if replay_alias is None:
        return None
    elapsed_seconds = time.monotonic() - replay_started_at
    return replay_alias.start_offset_seconds + elapsed_seconds * replay_alias.replay_speed


def _video_timestamp_ms(
    replay_alias: ReplayAliasConfig | None,
    replay_offset_seconds: float | None,
    sequence: int,
    interval_seconds: int,
) -> int:
    if replay_alias is not None and replay_offset_seconds is not None:
        return int(replay_offset_seconds * 1000)
    return (sequence - 1) * interval_seconds * 1000


def _require_channel(channel: str) -> str:
    """One channel, normalised the way the list is; blank is a client error."""
    normalized = normalize_channels([channel or ""])
    if not normalized:
        raise ValueError("a Twitch channel is required")
    return normalized[0]
