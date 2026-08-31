"""Holds every ML backend for the lifetime of the process.

Backends are constructed once from the settings, loaded lazily behind a lock (or eagerly when
their ``preload`` flag is set), and reported through ``/ml/info`` and ``/ml/ready``. Nothing
here reads the environment.
"""

from __future__ import annotations

import logging
import threading
from dataclasses import dataclass
from typing import Any

from app.frame_store import FrameStore
from app.relevance import RelevanceAnalyzer, create_relevance_analyzer
from app.segmentation import Segmenter, create_segmenter
from app.sentiment import SentimentAnalyzer, create_sentiment_analyzer
from app.settings import Settings
from app.sponsor import DeterministicSponsorDetector, SponsorDetector
from app.transcription import WhisperTranscriber

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class BackendInfo:
    name: str
    backend: str
    model: str
    loaded: bool


class BackendRegistry:
    """One instance per process, created by the application lifespan."""

    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        self._lock = threading.Lock()
        self._sentiment: SentimentAnalyzer | None = None
        self._relevance: RelevanceAnalyzer | None = None
        self._segmenter: Segmenter | None = None
        self._transcriber: WhisperTranscriber | None = None
        self._sponsor_detector: SponsorDetector = DeterministicSponsorDetector()
        self._frame_store = FrameStore(settings.frame_storage, require_frame_read=settings.sponsor.require_frame_read)
        self._ready = False

    # ------------------------------------------------------------------ lifecycle
    def start(self) -> None:
        """Construct every backend and warm up the ones configured to preload."""
        with self._lock:
            self._sentiment = create_sentiment_analyzer(self.settings.sentiment.to_config())
            self._relevance = create_relevance_analyzer(self.settings.relevance.to_config())
            self._segmenter = create_segmenter(self.settings.segmentation.to_config())
            self._transcriber = WhisperTranscriber(self.settings.whisper.to_config())
        self.warm_up()
        self._ready = True
        logger.info("ml-engine backends ready: %s", ", ".join(f"{i.name}={i.backend}" for i in self.info()))

    def warm_up(self) -> None:
        """Load models that are configured to preload. Failures are logged; the backends fall back."""
        if self.settings.segmentation.preload:
            self._warm("segmentation", getattr(self.segmenter, "warm_up", None))
        if self.settings.whisper.preload:
            self._warm("transcription", self.transcriber.warm_up)

    @staticmethod
    def _warm(name: str, loader: Any) -> None:
        if loader is None:
            return
        try:
            loader()
        except Exception:
            logger.exception("%s preload failed; the backend will retry on first use", name)

    def stop(self) -> None:
        self._ready = False
        self._frame_store.close()

    @property
    def ready(self) -> bool:
        return self._ready

    # ------------------------------------------------------------------ accessors
    @property
    def sentiment(self) -> SentimentAnalyzer:
        return self._require(self._sentiment, "sentiment")

    @property
    def relevance(self) -> RelevanceAnalyzer:
        return self._require(self._relevance, "relevance")

    @property
    def segmenter(self) -> Segmenter:
        return self._require(self._segmenter, "segmentation")

    @property
    def transcriber(self) -> WhisperTranscriber:
        return self._require(self._transcriber, "transcription")

    @property
    def sponsor_detector(self) -> SponsorDetector:
        return self._sponsor_detector

    @property
    def frame_store(self) -> FrameStore:
        return self._frame_store

    @staticmethod
    def _require(backend: Any, name: str) -> Any:
        if backend is None:
            raise ModelNotReadyError(name)
        return backend

    # ------------------------------------------------------------------ introspection
    def info(self) -> list[BackendInfo]:
        s = self.settings
        return [
            BackendInfo("sentiment", s.sentiment.backend, s.sentiment.model, _loaded(self._sentiment)),
            BackendInfo("relevance", s.relevance.backend, s.relevance.model, _loaded(self._relevance)),
            BackendInfo(
                "segmentation", s.segmentation.backend or "none", s.segmentation.model_version, _loaded(self._segmenter)
            ),
            BackendInfo("transcription", "faster-whisper", s.whisper.model, _loaded(self._transcriber)),
            BackendInfo("sponsor", "deterministic-stub", "stub-v1", True),
        ]


class ModelNotReadyError(RuntimeError):
    def __init__(self, backend: str) -> None:
        super().__init__(f"{backend} backend is not ready")
        self.backend = backend


def _loaded(backend: Any) -> bool:
    if backend is None:
        return False
    probe = getattr(backend, "is_loaded", None)
    return bool(probe()) if callable(probe) else True
