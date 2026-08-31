"""All environment-driven configuration for ml-engine.

Every ``os.environ`` read for the service lives here. Each backend has its own settings class
with the env prefix it has always used, so nothing about the deployed environment variables
changes; what changes is that they are parsed once, validated at startup, and passed to the
backends as plain config objects instead of being re-read on every request.
"""

from __future__ import annotations

import os
from functools import lru_cache
from typing import Any

from pydantic import AliasChoices, Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

from ml_engine.relevance import DEFAULT_CACHE_DIR as DEFAULT_RELEVANCE_CACHE_DIR
from ml_engine.relevance import DEFAULT_MIN_SCORE, RelevanceConfig
from ml_engine.relevance import DEFAULT_MODEL as DEFAULT_RELEVANCE_MODEL
from ml_engine.segmentation import SAM_VIT_B_CHECKPOINT_URL, SegmentationConfig
from ml_engine.sentiment import DEFAULT_CACHE_DIR as DEFAULT_SENTIMENT_CACHE_DIR
from ml_engine.sentiment import DEFAULT_MODEL as DEFAULT_SENTIMENT_MODEL
from ml_engine.sentiment import SentimentConfig
from ml_engine.transcription import WhisperConfig


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


class _EnvSettings(BaseSettings):
    """Base class: an environment variable set to an empty string means "use the default"."""

    model_config = SettingsConfigDict(extra="ignore", populate_by_name=True)

    @model_validator(mode="before")
    @classmethod
    def _blank_strings_use_defaults(cls, values: Any) -> Any:
        if not isinstance(values, dict):
            return values
        return {key: value for key, value in values.items() if not (isinstance(value, str) and not value.strip())}


class SentimentSettings(_EnvSettings):
    model_config = SettingsConfigDict(env_prefix="STREAMSENSE_SENTIMENT_", extra="ignore")

    backend: str = "transformers"
    model: str = DEFAULT_SENTIMENT_MODEL
    device: str = "cpu"
    cache_dir: str = DEFAULT_SENTIMENT_CACHE_DIR
    max_chars: int = 1000
    preload: bool = False

    def to_config(self) -> SentimentConfig:
        return SentimentConfig(
            backend=self.backend.strip().lower(),
            model=self.model.strip(),
            device=self.device.strip(),
            cache_dir=self.cache_dir.strip(),
            max_chars=self.max_chars,
            preload=self.preload,
        )


class RelevanceSettings(_EnvSettings):
    model_config = SettingsConfigDict(env_prefix="STREAMSENSE_RELEVANCE_", extra="ignore")

    backend: str = "sentence-transformers"
    model: str = DEFAULT_RELEVANCE_MODEL
    device: str = "cpu"
    cache_dir: str = DEFAULT_RELEVANCE_CACHE_DIR
    min_score: float = DEFAULT_MIN_SCORE
    preload: bool = False

    def to_config(self) -> RelevanceConfig:
        return RelevanceConfig(
            backend=self.backend.strip().lower(),
            model=self.model.strip(),
            device=self.device.strip(),
            cache_dir=self.cache_dir.strip(),
            min_score=self.min_score,
            preload=self.preload,
        )


class SegmentationSettings(_EnvSettings):
    """Segmentation settings. The ``STREAMSENSE_SPONSOR_*`` names are the pre-rename aliases."""

    model_config = SettingsConfigDict(extra="ignore", populate_by_name=True)

    backend: str = Field(
        "", validation_alias=AliasChoices("STREAMSENSE_SEGMENTATION_BACKEND", "STREAMSENSE_SPONSOR_MODEL_BACKEND")
    )
    model_path: str | None = Field(
        None, validation_alias=AliasChoices("STREAMSENSE_SAM_CHECKPOINT_PATH", "STREAMSENSE_SPONSOR_MODEL_PATH")
    )
    model_version: str = Field(
        "sam-vit-b",
        validation_alias=AliasChoices("STREAMSENSE_SEGMENTATION_MODEL_VERSION", "STREAMSENSE_SPONSOR_MODEL_VERSION"),
    )
    confidence_threshold: float = Field(
        0.25,
        validation_alias=AliasChoices(
            "STREAMSENSE_SEGMENTATION_CONFIDENCE_THRESHOLD", "STREAMSENSE_SPONSOR_CONFIDENCE_THRESHOLD"
        ),
    )
    iou_threshold: float = Field(
        0.5,
        validation_alias=AliasChoices("STREAMSENSE_SEGMENTATION_IOU_THRESHOLD", "STREAMSENSE_SPONSOR_IOU_THRESHOLD"),
    )
    max_proposals: int = Field(
        20, validation_alias=AliasChoices("STREAMSENSE_SEGMENTATION_MAX_PROPOSALS", "STREAMSENSE_SPONSOR_MAX_PROPOSALS")
    )
    model_input_size: int = Field(
        640,
        validation_alias=AliasChoices(
            "STREAMSENSE_SEGMENTATION_MODEL_INPUT_SIZE", "STREAMSENSE_SPONSOR_MODEL_INPUT_SIZE"
        ),
    )
    labels: str = Field(
        "segment", validation_alias=AliasChoices("STREAMSENSE_SEGMENTATION_LABELS", "STREAMSENSE_SPONSOR_MODEL_LABELS")
    )
    min_area_ratio: float = Field(0.0005, validation_alias="STREAMSENSE_SEGMENTATION_MIN_AREA_RATIO")
    sam_model_type: str = Field("vit_b", validation_alias="STREAMSENSE_SAM_MODEL_TYPE")
    sam_checkpoint_url: str = Field(SAM_VIT_B_CHECKPOINT_URL, validation_alias="STREAMSENSE_SAM_CHECKPOINT_URL")
    sam_cache_dir: str = Field("/models/sam", validation_alias="STREAMSENSE_SAM_CACHE_DIR")
    sam_device: str = Field("cpu", validation_alias="STREAMSENSE_SAM_DEVICE")
    sam_auto_download: bool = Field(True, validation_alias="STREAMSENSE_SAM_AUTO_DOWNLOAD")
    sam_points_per_side: int = Field(16, validation_alias="STREAMSENSE_SAM_POINTS_PER_SIDE")
    preload: bool = Field(False, validation_alias="STREAMSENSE_SEGMENTATION_PRELOAD")

    def to_config(self) -> SegmentationConfig:
        labels = tuple(item.strip() for item in self.labels.split(",") if item.strip()) or ("segment",)
        return SegmentationConfig(
            backend=self.backend.strip().lower(),
            model_path=(self.model_path or "").strip() or None,
            model_version=self.model_version.strip(),
            confidence_threshold=self.confidence_threshold,
            iou_threshold=self.iou_threshold,
            max_proposals=self.max_proposals,
            model_input_size=self.model_input_size,
            class_labels=labels,
            sam_model_type=self.sam_model_type.strip(),
            sam_checkpoint_url=self.sam_checkpoint_url.strip(),
            sam_cache_dir=self.sam_cache_dir.strip(),
            sam_device=self.sam_device.strip(),
            sam_auto_download=self.sam_auto_download,
            sam_points_per_side=self.sam_points_per_side,
            min_area_ratio=self.min_area_ratio,
        )


class WhisperSettings(_EnvSettings):
    model_config = SettingsConfigDict(env_prefix="STREAMSENSE_WHISPER_", extra="ignore")

    model: str = "small.en"
    compute_type: str = "int8"
    device: str = "cpu"
    language: str | None = "en"
    model_cache: str = "/models/whisper"
    preload: bool = False

    def to_config(self) -> WhisperConfig:
        return WhisperConfig(
            model_name=self.model.strip(),
            compute_type=self.compute_type.strip(),
            device=self.device.strip(),
            language=(self.language or "").strip() or None,
            model_cache=self.model_cache.strip(),
        )


class FrameStorageSettings(_EnvSettings):
    """S3-compatible frame storage. Credentials may arrive as ``<NAME>_FILE`` secret mounts."""

    model_config = SettingsConfigDict(env_prefix="STREAMSENSE_FRAME_STORAGE_", extra="ignore")

    endpoint: str | None = None
    region: str = "us-east-1"
    access_key: str | None = None
    secret_key: str | None = None

    @model_validator(mode="before")
    @classmethod
    def _credentials_from_secret_files(cls, values: Any) -> Any:
        if not isinstance(values, dict):
            return values
        values = dict(values)
        for field in ("access_key", "secret_key"):
            file_value = _secret_env(f"STREAMSENSE_FRAME_STORAGE_{field.upper()}")
            if file_value is not None:
                values[field] = file_value
        return values


class SponsorSettings(_EnvSettings):
    model_config = SettingsConfigDict(env_prefix="STREAMSENSE_SPONSOR_", extra="ignore")

    segmentation_enabled: bool = False
    require_frame_read: bool = False


class Settings(_EnvSettings):
    """Top-level settings. Sub-settings read their own prefixed variables."""

    model_config = SettingsConfigDict(extra="ignore")

    ml_engine_force_failure: bool = False
    service_name: str = "ml-engine"
    service_version: str = Field(
        "0.1.0", validation_alias=AliasChoices("STREAMSENSE_SERVICE_VERSION", "SERVICE_VERSION")
    )
    git_sha: str | None = Field(None, validation_alias=AliasChoices("STREAMSENSE_GIT_SHA", "GIT_SHA"))

    sentiment: SentimentSettings = Field(default_factory=SentimentSettings)
    relevance: RelevanceSettings = Field(default_factory=RelevanceSettings)
    segmentation: SegmentationSettings = Field(default_factory=SegmentationSettings)
    whisper: WhisperSettings = Field(default_factory=WhisperSettings)
    frame_storage: FrameStorageSettings = Field(default_factory=FrameStorageSettings)
    sponsor: SponsorSettings = Field(default_factory=SponsorSettings)


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """Process-wide settings, parsed once. Tests build ``Settings()`` directly and inject it."""
    return Settings()
