import logging
import os
import re
import unicodedata
from dataclasses import dataclass
from typing import Any, Protocol

logger = logging.getLogger(__name__)

DEFAULT_MODEL = "sentence-transformers/all-MiniLM-L6-v2"
DEFAULT_CACHE_DIR = "/models/relevance"
DEFAULT_MIN_SCORE = 0.50


@dataclass(frozen=True)
class SponsorRelevanceInput:
    text: str
    sponsor: str
    aliases: list[str]
    semantic_terms: list[str]
    min_score: float | None = None


@dataclass(frozen=True)
class SponsorRelevanceResult:
    sponsor_relevant: bool
    matched_sponsor: str | None
    matched_terms: list[str]
    relevance_score: float
    relevance_reason: str
    model_version: str


@dataclass(frozen=True)
class RelevanceConfig:
    backend: str
    model: str
    device: str
    cache_dir: str
    min_score: float
    preload: bool

    @classmethod
    def from_env(cls) -> "RelevanceConfig":
        return cls(
            backend=os.getenv("STREAMSENSE_RELEVANCE_BACKEND", "sentence-transformers")
            .strip()
            .lower(),
            model=os.getenv("STREAMSENSE_RELEVANCE_MODEL", DEFAULT_MODEL).strip()
            or DEFAULT_MODEL,
            device=os.getenv("STREAMSENSE_RELEVANCE_DEVICE", "cpu").strip() or "cpu",
            cache_dir=os.getenv("STREAMSENSE_RELEVANCE_CACHE_DIR", DEFAULT_CACHE_DIR).strip()
            or DEFAULT_CACHE_DIR,
            min_score=_env_float("STREAMSENSE_RELEVANCE_MIN_SCORE", DEFAULT_MIN_SCORE),
            preload=_env_bool("STREAMSENSE_RELEVANCE_PRELOAD", False),
        )


class RelevanceAnalyzer(Protocol):
    def analyze(self, request: SponsorRelevanceInput) -> SponsorRelevanceResult:
        pass


class EmbeddingRelevanceAnalyzer:
    def __init__(self, config: RelevanceConfig, fallback: RelevanceAnalyzer) -> None:
        self._config = config
        self._fallback = fallback
        self._model: Any | None = None

        if config.preload:
            try:
                self._load_model()
            except Exception:
                logger.exception("relevance embedding model preload failed")

    def analyze(self, request: SponsorRelevanceInput) -> SponsorRelevanceResult:
        direct = direct_match(request, self._config.min_score)
        if direct.sponsor_relevant:
            return direct

        terms = relevance_terms(request)
        if not request.text.strip() or not terms:
            return not_relevant(request.sponsor, self._config.model, "empty-text-or-terms")

        try:
            model = self._load_model()
            embeddings = model.encode(
                [request.text, *terms],
                convert_to_tensor=True,
                normalize_embeddings=True,
            )
            text_embedding = embeddings[0]
            term_embeddings = embeddings[1:]
            scores = term_embeddings @ text_embedding
            best_index = int(scores.argmax().item())
            best_score = round(float(scores[best_index].item()), 3)
            threshold = request.min_score if request.min_score is not None else self._config.min_score
            matched_term = terms[best_index]
            if best_score >= threshold:
                return SponsorRelevanceResult(
                    True,
                    clean_sponsor(request.sponsor),
                    [matched_term],
                    min(1.0, best_score),
                    "semantic-similarity",
                    self._config.model,
                )
            return SponsorRelevanceResult(
                False,
                None,
                [],
                max(0.0, best_score),
                "below-semantic-threshold",
                self._config.model,
            )
        except Exception:
            logger.exception("relevance embedding inference failed; using direct fallback")
            return self._fallback.analyze(request)

    def _load_model(self) -> Any:
        if self._model is not None:
            return self._model

        from sentence_transformers import SentenceTransformer

        self._model = SentenceTransformer(
            self._config.model,
            cache_folder=self._config.cache_dir,
            device=self._config.device,
        )
        return self._model


class DirectRelevanceAnalyzer:
    def __init__(self, model_version: str = "direct-relevance-v1", min_score: float = DEFAULT_MIN_SCORE) -> None:
        self._model_version = model_version
        self._min_score = min_score

    def analyze(self, request: SponsorRelevanceInput) -> SponsorRelevanceResult:
        return direct_match(request, self._min_score, self._model_version)


def analyze_relevance(request: SponsorRelevanceInput) -> SponsorRelevanceResult:
    return _get_relevance_analyzer().analyze(request)


def create_relevance_analyzer(config: RelevanceConfig | None = None) -> RelevanceAnalyzer:
    config = config or RelevanceConfig.from_env()
    fallback = DirectRelevanceAnalyzer("direct-relevance-v1", config.min_score)
    if config.backend in {"sentence-transformers", "embedding", "embeddings"}:
        return EmbeddingRelevanceAnalyzer(config, fallback)
    if config.backend in {"direct", "lexical"}:
        return fallback

    logger.warning("unsupported relevance backend=%s; using direct fallback", config.backend)
    return fallback


def direct_match(
    request: SponsorRelevanceInput,
    default_min_score: float,
    model_version: str = "direct-relevance-v1",
) -> SponsorRelevanceResult:
    del default_min_score
    normalized_text = normalize_text(request.text)
    if not normalized_text:
        return not_relevant(request.sponsor, model_version, "empty-text")

    direct_terms = [clean_sponsor(request.sponsor), *request.aliases]
    semantic_terms = request.semantic_terms
    for term in unique_terms(direct_terms):
        if contains_term(normalized_text, term):
            return SponsorRelevanceResult(
                True,
                clean_sponsor(request.sponsor),
                [term],
                1.0,
                "direct-match",
                model_version,
            )

    for term in unique_terms(semantic_terms):
        if contains_term(normalized_text, term):
            return SponsorRelevanceResult(
                True,
                clean_sponsor(request.sponsor),
                [term],
                0.82,
                "semantic-term-match",
                model_version,
            )

    return not_relevant(request.sponsor, model_version, "no-direct-match")


def relevance_terms(request: SponsorRelevanceInput) -> list[str]:
    return unique_terms([request.sponsor, *request.aliases, *request.semantic_terms])


def unique_terms(terms: list[str]) -> list[str]:
    seen: set[str] = set()
    cleaned: list[str] = []
    for term in terms:
        normalized = normalize_text(term)
        if normalized and normalized not in seen:
            seen.add(normalized)
            cleaned.append(normalized)
    return cleaned


def contains_term(normalized_text: str, raw_term: str) -> bool:
    term = normalize_text(raw_term)
    if not term:
        return False

    padded_text = f" {normalized_text} "
    if f" {term} " in padded_text:
        return True

    if " " not in term:
        suffixes = ("ad", "ads", "partner", "sponsor", "sponsored")
        return any(f" {term}{suffix} " in padded_text for suffix in suffixes)
    return False


def normalize_text(value: str) -> str:
    text = unicodedata.normalize("NFKD", value.strip().lower())
    text = "".join(char for char in text if not unicodedata.combining(char))
    text = re.sub(r"https?://\S+|www\.\S+", " ", text)
    text = re.sub(r"[^a-z0-9]+", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def clean_sponsor(value: str) -> str:
    return value.strip()


def not_relevant(sponsor: str, model_version: str, reason: str) -> SponsorRelevanceResult:
    del sponsor
    return SponsorRelevanceResult(False, None, [], 0.0, reason, model_version)


def _get_relevance_analyzer() -> RelevanceAnalyzer:
    global _relevance_analyzer

    if _relevance_analyzer is None:
        _relevance_analyzer = create_relevance_analyzer()
    return _relevance_analyzer


def _env_bool(name: str, default: bool) -> bool:
    raw_value = os.getenv(name)
    if raw_value is None:
        return default
    return raw_value.strip().lower() in {"1", "true", "yes", "on"}


def _env_float(name: str, default: float) -> float:
    raw_value = os.getenv(name)
    if raw_value is None:
        return default
    try:
        return float(raw_value)
    except ValueError:
        logger.warning("invalid float env %s=%r; using %s", name, raw_value, default)
        return default


_relevance_analyzer: RelevanceAnalyzer | None = None
