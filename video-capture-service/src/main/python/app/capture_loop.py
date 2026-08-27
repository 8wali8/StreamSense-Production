import logging
import tempfile
import threading
import time
import uuid
from dataclasses import replace
from pathlib import Path

from botocore.exceptions import BotoCoreError, ClientError
from kafka.errors import KafkaError

from app import metrics
from app.audio_sampler import AudioCaptureError, AudioSampler
from app.config import CaptureConfig, ReplayAliasConfig
from app.frame_sampler import FrameCaptureError, FrameSampler
from app.kafka_publisher import (
    FrameEvent,
    FrameEventPublisher,
    TranscriptEventPublisher,
    TranscriptSegmentEvent,
)
from app.status import CaptureState, CaptureStatusStore, ChannelStatus
from app.storage import FrameStorage
from app.transcription_client import TranscriptionClient, TranscriptionClientError
from app.twitch_source import TwitchSourceResolver, TwitchStreamOffline, TwitchStreamResolutionError

logger = logging.getLogger(__name__)


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
        # One stop event per worker, so switching channels never races a still-running loop.
        self.workers: list[tuple[threading.Thread, threading.Event]] = []

    def start(self) -> None:
        metrics.capture_enabled.set(1 if self.config.enabled else 0)
        if not self.config.enabled:
            logger.info("Twitch video capture disabled")
            return
        if self.storage is None or self.publisher is None:
            raise ValueError("storage and publisher are required when capture is enabled")

        for channel in self.config.channels:
            status = self.status_store.statuses[channel]
            status.state = CaptureState.STARTING
            status.capture_session_id = self._session_id(channel)
            stop_event = threading.Event()
            thread = threading.Thread(
                target=self._capture_channel, args=(channel, stop_event), name=f"capture-{channel}", daemon=True
            )
            thread.start()
            self.workers.append((thread, stop_event))
            logger.info("started Twitch video capture loop channel=%s session=%s", channel, status.capture_session_id)

    def stop(self) -> None:
        self._stop_threads()
        if self.publisher:
            self.publisher.close()
        if self.transcript_publisher:
            self.transcript_publisher.close()

    def switch_channels(self, channels: list[str]) -> dict:
        normalized = _normalize_channels(channels)
        if not normalized:
            raise ValueError("at least one Twitch channel is required")
        if not self.config.enabled:
            raise RuntimeError("Twitch video capture is disabled")
        if self.storage is None or self.publisher is None:
            raise RuntimeError("storage and publisher are required when capture is enabled")

        self._stop_threads()
        self.config = replace(self.config, channels=normalized)
        self.status_store.statuses.clear()
        for channel in normalized:
            self.status_store.statuses[channel] = ChannelStatus(channel=channel, state=CaptureState.STARTING)
        self.start()
        return self.status_store.snapshot()

    def workers_alive(self) -> int:
        return sum(1 for thread, _ in self.workers if thread.is_alive())

    def _stop_threads(self) -> None:
        for _, stop_event in self.workers:
            stop_event.set()
        for thread, _ in self.workers:
            thread.join(timeout=5)
        self.workers.clear()
        for status in self.status_store.statuses.values():
            if status.state != CaptureState.DISABLED:
                status.state = CaptureState.STOPPED

    def _capture_channel(self, channel: str, stop_event: threading.Event) -> None:
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
                hls_url = resolver.resolve_url(replay_alias.vod_url, channel) if replay_alias else resolver.resolve(channel)
            except TwitchStreamOffline as exc:
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
                metrics.capture_latency.labels(channel=channel).observe(capture_latency_ms)
                status.frames_captured += 1
                metrics.frames_captured.labels(channel=channel).inc()

                object_key = self._object_key(channel, status.capture_session_id, sequence, frame_id, suffix)
                stored = self.storage.store(captured_path, object_key, _content_type(suffix))
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
                publish_latency_ms = self.publisher.publish(event)
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
                        channel, hls_url, status, audio_sampler, transcript_sequence, replay_alias, replay_offset_seconds
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
            except (BotoCoreError, ClientError, OSError) as exc:
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
        logger.warning("Twitch video capture issue channel=%s state=%s stage=%s error=%s", channel, state.value, stage, status.last_error)

    def _capture_transcript_segment(
        self,
        channel: str,
        hls_url: str,
        status,
        audio_sampler: AudioSampler,
        sequence: int,
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


def _normalize_channels(channels: list[str]) -> list[str]:
    normalized = []
    seen = set()
    for channel in channels:
        value = channel.strip().lower().lstrip("#@")
        if value and value not in seen:
            normalized.append(value)
            seen.add(value)
    return normalized
