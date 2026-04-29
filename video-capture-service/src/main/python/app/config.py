import os
from dataclasses import dataclass


def _bool_env(name: str, default: bool = False) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _int_env(name: str, default: int) -> int:
    raw = os.getenv(name)
    if raw is None or raw.strip() == "":
        return default
    return int(raw)


def _csv_env(name: str) -> list[str]:
    raw = os.getenv(name, "")
    return [item.strip().lower() for item in raw.split(",") if item.strip()]


@dataclass(frozen=True)
class StorageConfig:
    backend: str
    bucket: str
    endpoint: str | None
    region: str
    access_key: str | None
    secret_key: str | None
    path_prefix: str
    filesystem_root: str


@dataclass(frozen=True)
class CaptureConfig:
    enabled: bool
    channels: list[str]
    quality: str
    sample_interval_seconds: int
    stream_resolve_timeout_seconds: int
    frame_capture_timeout_seconds: int
    reconnect_delay_seconds: int
    max_reconnect_delay_seconds: int
    max_consecutive_failures: int
    output_format: str
    jpeg_quality: int
    twitch_oauth_token: str | None
    kafka_bootstrap_servers: str
    video_frames_topic: str
    transcript_enabled: bool
    transcript_segments_topic: str
    transcript_segment_duration_seconds: int
    transcript_audio_capture_timeout_seconds: int
    transcript_request_timeout_seconds: int
    transcript_language: str | None
    transcript_max_chars: int
    transcript_preview_chars: int
    ml_engine_url: str
    worker_id: str
    storage: StorageConfig

    @staticmethod
    def from_env() -> "CaptureConfig":
        storage = StorageConfig(
            backend=os.getenv("STREAMSENSE_FRAME_STORAGE_BACKEND", "s3").strip().lower(),
            bucket=os.getenv("STREAMSENSE_FRAME_STORAGE_BUCKET", "streamsense-frames").strip(),
            endpoint=os.getenv("STREAMSENSE_FRAME_STORAGE_ENDPOINT", "http://minio:9000").strip() or None,
            region=os.getenv("STREAMSENSE_FRAME_STORAGE_REGION", "us-east-1").strip(),
            access_key=os.getenv("STREAMSENSE_FRAME_STORAGE_ACCESS_KEY", "streamsense").strip() or None,
            secret_key=os.getenv("STREAMSENSE_FRAME_STORAGE_SECRET_KEY", "streamsense").strip() or None,
            path_prefix=os.getenv("STREAMSENSE_FRAME_STORAGE_PATH_PREFIX", "twitch").strip().strip("/"),
            filesystem_root=os.getenv("STREAMSENSE_FRAME_STORAGE_FILESYSTEM_ROOT", "/tmp/streamsense-frames").strip(),
        )
        return CaptureConfig(
            enabled=_bool_env("STREAMSENSE_TWITCH_VIDEO_ENABLED", False),
            channels=_csv_env("TWITCH_VIDEO_CHANNELS"),
            quality=os.getenv("TWITCH_VIDEO_QUALITY", "best").strip() or "best",
            sample_interval_seconds=_int_env("TWITCH_VIDEO_SAMPLE_INTERVAL_SECONDS", 10),
            stream_resolve_timeout_seconds=_int_env("TWITCH_VIDEO_STREAM_RESOLVE_TIMEOUT_SECONDS", 20),
            frame_capture_timeout_seconds=_int_env("TWITCH_VIDEO_FRAME_CAPTURE_TIMEOUT_SECONDS", 15),
            reconnect_delay_seconds=_int_env("TWITCH_VIDEO_RECONNECT_DELAY_SECONDS", 5),
            max_reconnect_delay_seconds=_int_env("TWITCH_VIDEO_MAX_RECONNECT_DELAY_SECONDS", 60),
            max_consecutive_failures=_int_env("TWITCH_VIDEO_MAX_CONSECUTIVE_FAILURES", 5),
            output_format=os.getenv("TWITCH_VIDEO_OUTPUT_FORMAT", "jpg").strip().lower() or "jpg",
            jpeg_quality=_int_env("TWITCH_VIDEO_JPEG_QUALITY", 85),
            twitch_oauth_token=os.getenv("TWITCH_VIDEO_OAUTH_TOKEN") or None,
            kafka_bootstrap_servers=os.getenv("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092").strip(),
            video_frames_topic=os.getenv("STREAMSENSE_VIDEO_FRAMES_TOPIC", "stream.video.frames").strip(),
            transcript_enabled=_bool_env("STREAMSENSE_TWITCH_TRANSCRIPT_ENABLED", False),
            transcript_segments_topic=os.getenv(
                "STREAMSENSE_TRANSCRIPT_SEGMENTS_TOPIC", "stream.transcript.segments"
            ).strip(),
            transcript_segment_duration_seconds=_int_env("TWITCH_TRANSCRIPT_SEGMENT_SECONDS", 10),
            transcript_audio_capture_timeout_seconds=_int_env("TWITCH_TRANSCRIPT_AUDIO_CAPTURE_TIMEOUT_SECONDS", 30),
            transcript_request_timeout_seconds=_int_env("TWITCH_TRANSCRIPT_REQUEST_TIMEOUT_SECONDS", 60),
            transcript_language=os.getenv("TWITCH_TRANSCRIPT_LANGUAGE", "en").strip() or None,
            transcript_max_chars=_int_env("STREAMSENSE_TRANSCRIPT_TEXT_MAX_CHARS", 4000),
            transcript_preview_chars=_int_env("STREAMSENSE_TRANSCRIPT_PREVIEW_CHARS", 160),
            ml_engine_url=os.getenv("ML_ENGINE_URL", "http://ml-engine:8000").strip().rstrip("/"),
            worker_id=os.getenv("STREAMSENSE_VIDEO_CAPTURE_WORKER_ID", "video-capture-service-1").strip(),
            storage=storage,
        )

    def validate(self) -> None:
        if not self.enabled:
            return
        if not self.channels:
            raise ValueError("TWITCH_VIDEO_CHANNELS is required when video capture is enabled")
        if self.sample_interval_seconds < 5:
            raise ValueError("TWITCH_VIDEO_SAMPLE_INTERVAL_SECONDS must be at least 5")
        if self.frame_capture_timeout_seconds < 1:
            raise ValueError("TWITCH_VIDEO_FRAME_CAPTURE_TIMEOUT_SECONDS must be positive")
        if self.transcript_enabled:
            if self.transcript_segment_duration_seconds < 1:
                raise ValueError("TWITCH_TRANSCRIPT_SEGMENT_SECONDS must be positive")
            if self.transcript_audio_capture_timeout_seconds <= self.transcript_segment_duration_seconds:
                raise ValueError("TWITCH_TRANSCRIPT_AUDIO_CAPTURE_TIMEOUT_SECONDS must exceed segment duration")
            if self.transcript_request_timeout_seconds < 1:
                raise ValueError("TWITCH_TRANSCRIPT_REQUEST_TIMEOUT_SECONDS must be positive")
            if self.transcript_max_chars < 1:
                raise ValueError("STREAMSENSE_TRANSCRIPT_TEXT_MAX_CHARS must be positive")
            if self.transcript_preview_chars < 1:
                raise ValueError("STREAMSENSE_TRANSCRIPT_PREVIEW_CHARS must be positive")
            if not self.transcript_segments_topic:
                raise ValueError("STREAMSENSE_TRANSCRIPT_SEGMENTS_TOPIC is required")
            if not self.ml_engine_url:
                raise ValueError("ML_ENGINE_URL is required")
        if self.stream_resolve_timeout_seconds < 1:
            raise ValueError("TWITCH_VIDEO_STREAM_RESOLVE_TIMEOUT_SECONDS must be positive")
        if self.storage.backend not in {"s3", "filesystem"}:
            raise ValueError("STREAMSENSE_FRAME_STORAGE_BACKEND must be s3 or filesystem")
        if self.storage.backend == "s3":
            if not self.storage.bucket:
                raise ValueError("STREAMSENSE_FRAME_STORAGE_BUCKET is required for s3 storage")
            if not self.storage.access_key or not self.storage.secret_key:
                raise ValueError("S3 frame storage access key and secret key are required")
