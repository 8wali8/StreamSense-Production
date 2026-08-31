"""Shared fixtures: an app built from explicit settings with fake backends injected.

No test here loads a real model or touches the network. Backends are swapped through
``app.dependency_overrides`` on ``get_registry``; settings are built directly from keyword
overrides instead of the process environment.
"""

from __future__ import annotations

from collections.abc import Callable, Iterator
from dataclasses import dataclass, field
from typing import Any

import pytest
from fastapi.testclient import TestClient
from PIL import Image

from ml_engine.frame_store import FrameStore
from ml_engine.main import create_app, get_registry
from ml_engine.registry import BackendInfo
from ml_engine.relevance import SponsorRelevanceInput, SponsorRelevanceResult
from ml_engine.segmentation import RegionProposal
from ml_engine.sentiment import SentimentResult
from ml_engine.settings import (
    FrameStorageSettings,
    RelevanceSettings,
    SegmentationSettings,
    SentimentSettings,
    Settings,
    SponsorSettings,
    WhisperSettings,
)
from ml_engine.sponsor import DeterministicSponsorDetector
from ml_engine.transcription import TranscriptionResult


def make_settings(**overrides: Any) -> Settings:
    """Settings that never load a model: lexical sentiment, direct relevance, no segmentation."""
    base: dict[str, Any] = {
        "ml_engine_force_failure": False,
        "sentiment": SentimentSettings(backend="lexical"),
        "relevance": RelevanceSettings(backend="direct"),
        "segmentation": SegmentationSettings(backend=""),
        "whisper": WhisperSettings(),
        "frame_storage": FrameStorageSettings(),
        "sponsor": SponsorSettings(),
    }
    base.update(overrides)
    return Settings(**base)


class FakeSentiment:
    def __init__(self, result: SentimentResult | None = None) -> None:
        self.result = result or SentimentResult("NEUTRAL", 0.0, "fake-sentiment")
        self.calls: list[str] = []

    def analyze(self, message: str) -> SentimentResult:
        self.calls.append(message)
        return self.result


class FakeRelevance:
    def __init__(self, result: SponsorRelevanceResult | None = None) -> None:
        self.result = result or SponsorRelevanceResult(False, None, [], 0.0, "fake", "fake-relevance")
        self.calls: list[SponsorRelevanceInput] = []

    def analyze(self, request: SponsorRelevanceInput) -> SponsorRelevanceResult:
        self.calls.append(request)
        return self.result


class FakeTranscriber:
    model_version = "fake-whisper"

    def __init__(self, result: TranscriptionResult | None = None, error: Exception | None = None) -> None:
        self.result = result or TranscriptionResult("hello stream", "en", 0.91, "fake-whisper")
        self.error = error
        self.calls: list[tuple[bytes, str, str | None]] = []

    def transcribe_bytes(self, audio: bytes, file_name: str, language: str | None = None) -> TranscriptionResult:
        self.calls.append((audio, file_name, language))
        if self.error:
            raise self.error
        return self.result

    def is_loaded(self) -> bool:
        return False


class FakeSegmenter:
    def __init__(self, proposals: list[RegionProposal] | None = None) -> None:
        self.proposals = proposals or []

    def propose(self, image: Image.Image) -> list[RegionProposal]:
        return list(self.proposals)


@dataclass
class FakeRegistry:
    """Stands in for BackendRegistry; every attribute the routes touch is here."""

    settings: Settings
    sentiment: Any = field(default_factory=FakeSentiment)
    relevance: Any = field(default_factory=FakeRelevance)
    transcriber: Any = field(default_factory=FakeTranscriber)
    segmenter: Any = field(default_factory=FakeSegmenter)
    sponsor_detector: Any = field(default_factory=DeterministicSponsorDetector)
    frame_store: FrameStore | None = None
    ready: bool = True

    def __post_init__(self) -> None:
        if self.frame_store is None:
            self.frame_store = FrameStore(
                self.settings.frame_storage, require_frame_read=self.settings.sponsor.require_frame_read
            )

    def info(self) -> list[BackendInfo]:
        return [BackendInfo("sentiment", "fake", "fake-model", True)]


ClientFactory = Callable[..., tuple[TestClient, FakeRegistry]]


@pytest.fixture
def make_client() -> Iterator[ClientFactory]:
    """Build a client whose registry is a FakeRegistry. Usage: client, registry = make_client(**settings)."""
    clients: list[TestClient] = []

    def factory(registry: FakeRegistry | None = None, **settings_overrides: Any) -> tuple[TestClient, FakeRegistry]:
        settings = make_settings(**settings_overrides)
        app = create_app(settings)
        fake = registry or FakeRegistry(settings=settings)
        app.dependency_overrides[get_registry] = lambda: fake
        # The lifespan still runs and builds the real (lexical/direct/no-model) registry, which
        # proves start-up does no model loading, but the routes see the fake.
        client = TestClient(app)
        client.__enter__()
        clients.append(client)
        return client, fake

    yield factory
    for client in clients:
        client.__exit__(None, None, None)


@pytest.fixture
def client(make_client: ClientFactory) -> TestClient:
    client, _ = make_client()
    return client


@pytest.fixture
def real_lightweight_client() -> Iterator[TestClient]:
    """A client with the real registry on model-free backends (lexical, direct, heuristic)."""
    app = create_app(
        make_settings(segmentation=SegmentationSettings(backend="heuristic", model_version="heuristic-test"))
    )
    with TestClient(app) as client:
        yield client
