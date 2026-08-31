import hashlib
import logging
import re
import threading
from dataclasses import dataclass
from typing import Any, ClassVar, Protocol

logger = logging.getLogger(__name__)

DEFAULT_MODEL = "cardiffnlp/twitter-roberta-base-sentiment-latest"
DEFAULT_CACHE_DIR = "/models/sentiment"

_URL_RE = re.compile(r"https?://\S+|www\.\S+", re.IGNORECASE)
_MENTION_RE = re.compile(r"(?<!\w)@\w+")


@dataclass(frozen=True)
class SentimentResult:
    label: str
    score: float
    model_version: str


@dataclass(frozen=True)
class SentimentConfig:
    backend: str
    model: str
    device: str
    cache_dir: str
    max_chars: int
    preload: bool


class SentimentAnalyzer(Protocol):
    def analyze(self, message: str) -> SentimentResult:
        pass


class TransformersSentimentAnalyzer:
    def __init__(
        self,
        config: SentimentConfig,
        fallback: SentimentAnalyzer,
    ) -> None:
        self._config = config
        self._fallback = fallback
        self._classifier: Any | None = None
        self._lock = threading.Lock()

        if config.preload:
            try:
                self._load_classifier()
            except Exception:
                logger.exception("sentiment transformer preload failed")

    def analyze(self, message: str) -> SentimentResult:
        text = preprocess_text(message, self._config.max_chars)
        if not text:
            return SentimentResult("NEUTRAL", 0.0, self._config.model)

        try:
            classifier = self._load_classifier()
            output = classifier(text, truncation=True)
            probabilities = _extract_probabilities(output)
            label = max(probabilities, key=lambda name: probabilities[name])
            score = _clamp_score(probabilities.get("POSITIVE", 0.0) - probabilities.get("NEGATIVE", 0.0))
            return SentimentResult(label, score, self._config.model)
        except Exception:
            logger.exception("sentiment transformer inference failed; using fallback")
            return self._fallback.analyze(message)

    def is_loaded(self) -> bool:
        return self._classifier is not None

    def warm_up(self) -> None:
        self._load_classifier()

    def _load_classifier(self) -> Any:
        if self._classifier is not None:
            return self._classifier
        with self._lock:
            if self._classifier is not None:
                return self._classifier
            return self._build_classifier()

    def _build_classifier(self) -> Any:
        from transformers import (
            AutoModelForSequenceClassification,
            AutoTokenizer,
            pipeline,
        )

        tokenizer = AutoTokenizer.from_pretrained(
            self._config.model,
            cache_dir=self._config.cache_dir,
        )
        model = AutoModelForSequenceClassification.from_pretrained(
            self._config.model,
            cache_dir=self._config.cache_dir,
        )
        self._classifier = pipeline(
            "text-classification",
            model=model,
            tokenizer=tokenizer,
            device=_pipeline_device(self._config.device),
            top_k=None,
        )
        return self._classifier


class LexicalFallbackSentimentAnalyzer:
    _positive_words: ClassVar[set[str]] = {
        "amazing",
        "awesome",
        "best",
        "fun",
        "good",
        "great",
        "happy",
        "hype",
        "incredible",
        "love",
        "nice",
        "perfect",
        "pog",
        "poggers",
        "win",
        "wow",
    }
    _negative_words: ClassVar[set[str]] = {
        "awful",
        "bad",
        "boring",
        "broken",
        "hate",
        "horrible",
        "lag",
        "lags",
        "losing",
        "sad",
        "terrible",
        "trash",
        "worst",
    }

    def __init__(self, model_version: str = "fallback", max_chars: int = 1000) -> None:
        self._model_version = model_version
        self._max_chars = max_chars

    def analyze(self, message: str) -> SentimentResult:
        text = preprocess_text(message, self._max_chars).lower()
        tokens = re.findall(r"[a-z']+", text)
        if not tokens:
            return SentimentResult("NEUTRAL", 0.0, self._model_version)

        positive = sum(1 for token in tokens if token in self._positive_words)
        negative = sum(1 for token in tokens if token in self._negative_words)
        score = _clamp_score((positive - negative) / max(len(tokens), 1))

        if score > 0.05:
            label = "POSITIVE"
        elif score < -0.05:
            label = "NEGATIVE"
        else:
            label = "NEUTRAL"

        return SentimentResult(label, score, self._model_version)


class StubSentimentAnalyzer:
    def analyze(self, message: str) -> SentimentResult:
        normalized = message.strip().lower()
        digest = hashlib.sha256(normalized.encode("utf-8")).hexdigest()
        raw_value = int(digest[:8], 16)
        score = _clamp_score((raw_value / 0xFFFFFFFF) * 2.0 - 1.0)

        if score > 0.2:
            label = "POSITIVE"
        elif score < -0.2:
            label = "NEGATIVE"
        else:
            label = "NEUTRAL"

        return SentimentResult(label, score, "stub-v1")


def create_sentiment_analyzer(config: SentimentConfig) -> SentimentAnalyzer:
    if config.backend == "transformers":
        return TransformersSentimentAnalyzer(
            config,
            LexicalFallbackSentimentAnalyzer(max_chars=config.max_chars),
        )
    if config.backend == "lexical":
        return LexicalFallbackSentimentAnalyzer("lexical-v1", config.max_chars)
    if config.backend == "stub":
        return StubSentimentAnalyzer()

    logger.warning(
        "unsupported sentiment backend=%s; using lexical fallback",
        config.backend,
    )
    return LexicalFallbackSentimentAnalyzer()


def preprocess_text(message: str, max_chars: int) -> str:
    text = message.strip()
    text = _URL_RE.sub("http", text)
    text = _MENTION_RE.sub("@user", text)
    if max_chars > 0:
        text = text[:max_chars]
    return text


def _extract_probabilities(output: Any) -> dict[str, float]:
    rows = output
    if isinstance(rows, list) and len(rows) == 1 and isinstance(rows[0], list):
        rows = rows[0]
    elif isinstance(rows, dict):
        rows = [rows]

    probabilities = {"NEGATIVE": 0.0, "NEUTRAL": 0.0, "POSITIVE": 0.0}
    for row in rows:
        label = _normalize_model_label(str(row["label"]))
        if label in probabilities:
            probabilities[label] = float(row["score"])

    if not any(probabilities.values()):
        raise ValueError(f"sentiment model returned no usable probabilities: {output!r}")

    return probabilities


def _normalize_model_label(label: str) -> str:
    normalized = label.strip().lower()
    if normalized in {"negative", "neg", "label_0"}:
        return "NEGATIVE"
    if normalized in {"neutral", "neu", "label_1"}:
        return "NEUTRAL"
    if normalized in {"positive", "pos", "label_2"}:
        return "POSITIVE"
    return label.strip().upper()


def _pipeline_device(raw_device: str) -> int | str:
    device = raw_device.strip().lower()
    if device in {"", "cpu", "-1"}:
        return -1
    if device.startswith("cuda:"):
        return int(device.split(":", 1)[1])
    try:
        return int(device)
    except ValueError:
        return raw_device


def _clamp_score(score: float) -> float:
    return round(max(-1.0, min(1.0, score)), 3)
