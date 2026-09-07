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


def _float_env(name: str, default: float) -> float:
    raw = os.getenv(name)
    if raw is None or raw.strip() == "":
        return default
    return float(raw)


def _csv_env(name: str) -> list[str]:
    raw = os.getenv(name, "")
    return [item.strip().lower() for item in raw.split(",") if item.strip()]


def _secret_env(name: str, default: str | None = None) -> str | None:
    """Read a credential from ``<name>_FILE`` (a Docker/Kubernetes secret mount) or ``<name>``.

    The file form wins when both are set so that a mounted secret cannot be shadowed by a
    stale environment default. Values are stripped; an empty value counts as unset.
    """
    path = os.getenv(f"{name}_FILE")
    if path:
        try:
            with open(path, encoding="utf-8") as handle:
                value = handle.read().strip()
        except OSError as exc:
            raise ValueError(f"{name}_FILE points to a missing or unreadable file: {path}") from exc
        return value or None
    env_value = os.getenv(name)
    if env_value is None:
        return default
    return env_value.strip() or None


def _env_key(value: str) -> str:
    return "".join(char.upper() if char.isalnum() else "_" for char in value.strip())


def _normalize_alias(value: str) -> str:
    return value.strip().lower().lstrip("#@")


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
class ReplayAliasConfig:
    alias: str
    provider: str
    vod_id: str
    vod_url: str
    replay_speed: float
    start_offset_seconds: float
    source: str
    loop: bool


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
    replay_aliases: dict[str, ReplayAliasConfig]

    @staticmethod
    def from_env() -> "CaptureConfig":
        storage = StorageConfig(
            backend=os.getenv("STREAMSENSE_FRAME_STORAGE_BACKEND", "s3").strip().lower(),
            bucket=os.getenv("STREAMSENSE_FRAME_STORAGE_BUCKET", "streamsense-frames").strip(),
            endpoint=os.getenv("STREAMSENSE_FRAME_STORAGE_ENDPOINT", "http://minio:9000").strip() or None,
            region=os.getenv("STREAMSENSE_FRAME_STORAGE_REGION", "us-east-1").strip(),
            access_key=_secret_env("STREAMSENSE_FRAME_STORAGE_ACCESS_KEY"),
            secret_key=_secret_env("STREAMSENSE_FRAME_STORAGE_SECRET_KEY"),
            path_prefix=os.getenv("STREAMSENSE_FRAME_STORAGE_PATH_PREFIX", "twitch").strip().strip("/"),
            filesystem_root=os.getenv(
                "STREAMSENSE_FRAME_STORAGE_FILESYSTEM_ROOT",
                "/tmp/streamsense-frames",  # noqa: S108 - scratch default for the filesystem backend, overridable
            ).strip(),
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
            replay_aliases=_replay_aliases_from_env(),
        )

    def validate(self) -> None:
        if not self.enabled:
            return
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
        for alias, replay in self.replay_aliases.items():
            if replay.provider != "twitch":
                raise ValueError(f"unsupported replay provider for {alias}: {replay.provider}")
            if not replay.vod_id:
                raise ValueError(f"STREAMSENSE_REPLAY_{_env_key(alias)}_VOD_ID is required")
            if not replay.vod_url:
                raise ValueError(f"STREAMSENSE_REPLAY_{_env_key(alias)}_VOD_URL is required")
            if replay.replay_speed <= 0:
                raise ValueError(f"STREAMSENSE_REPLAY_{_env_key(alias)}_REPLAY_SPEED must be positive")
            if replay.start_offset_seconds < 0:
                raise ValueError(f"STREAMSENSE_REPLAY_{_env_key(alias)}_START_OFFSET_SECONDS must be non-negative")


def _replay_aliases_from_env() -> dict[str, ReplayAliasConfig]:
    aliases: dict[str, ReplayAliasConfig] = {}
    for raw_alias in _csv_env("STREAMSENSE_REPLAY_ALIASES"):
        alias = _normalize_alias(raw_alias)
        if not alias:
            continue
        key = _env_key(alias)
        vod_id = os.getenv(f"STREAMSENSE_REPLAY_{key}_VOD_ID", "").strip()
        vod_url = os.getenv(f"STREAMSENSE_REPLAY_{key}_VOD_URL", "").strip()
        aliases[alias] = ReplayAliasConfig(
            alias=alias,
            provider=os.getenv(f"STREAMSENSE_REPLAY_{key}_PROVIDER", "twitch").strip().lower() or "twitch",
            vod_id=vod_id,
            vod_url=vod_url,
            replay_speed=_float_env(f"STREAMSENSE_REPLAY_{key}_REPLAY_SPEED", 1.0),
            start_offset_seconds=_float_env(f"STREAMSENSE_REPLAY_{key}_START_OFFSET_SECONDS", 0.0),
            source=os.getenv(f"STREAMSENSE_REPLAY_{key}_SOURCE", "TWITCH_VOD_REPLAY").strip() or "TWITCH_VOD_REPLAY",
            loop=_bool_env(f"STREAMSENSE_REPLAY_{key}_LOOP", True),
        )
    return aliases
