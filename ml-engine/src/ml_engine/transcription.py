import tempfile
import threading
from dataclasses import dataclass
from pathlib import Path
from typing import Any


class TranscriptionError(Exception):
    pass


@dataclass(frozen=True)
class TranscriptionResult:
    text: str
    language: str | None
    confidence: float | None
    model_version: str


@dataclass(frozen=True)
class WhisperConfig:
    model_name: str = "small.en"
    compute_type: str = "int8"
    device: str = "cpu"
    language: str | None = "en"
    model_cache: str = "/models/whisper"


class WhisperTranscriber:
    """faster-whisper wrapper. The model loads once, behind a lock, on first use or ``warm_up``."""

    def __init__(self, config: WhisperConfig | None = None) -> None:
        self.config = config or WhisperConfig()
        self._model: Any | None = None
        self._lock = threading.Lock()

    @property
    def model_version(self) -> str:
        return f"faster-whisper-{self.config.model_name}-{self.config.compute_type}"

    def is_loaded(self) -> bool:
        return self._model is not None

    def warm_up(self) -> None:
        self._load_model()

    def transcribe_bytes(self, audio: bytes, file_name: str, language: str | None = None) -> TranscriptionResult:
        if not audio:
            raise TranscriptionError("audio upload is empty")

        suffix = Path(file_name).suffix or ".wav"
        temp_path = None
        try:
            with tempfile.NamedTemporaryFile(prefix="streamsense-transcript-", suffix=suffix, delete=False) as handle:
                temp_path = Path(handle.name)
                handle.write(audio)
            return self.transcribe_file(temp_path, language)
        finally:
            if temp_path is not None:
                temp_path.unlink(missing_ok=True)

    def transcribe_file(self, audio_path: Path, language: str | None = None) -> TranscriptionResult:
        try:
            model = self._load_model()
            resolved_language = language or self.config.language
            segments, info = model.transcribe(
                str(audio_path),
                language=resolved_language,
                vad_filter=True,
                beam_size=1,
            )
            texts: list[str] = []
            confidences: list[float] = []
            for segment in segments:
                text = segment.text.strip()
                if text:
                    texts.append(text)
                if getattr(segment, "avg_logprob", None) is not None:
                    confidences.append(max(0.0, min(1.0, 1.0 + float(segment.avg_logprob))))

            transcript = " ".join(texts).strip()
            confidence = sum(confidences) / len(confidences) if confidences else None
            detected_language = getattr(info, "language", None) or resolved_language
            return TranscriptionResult(transcript, detected_language, confidence, self.model_version)
        except TranscriptionError:
            raise
        except Exception as exc:
            raise TranscriptionError("local Whisper transcription failed") from exc

    def _load_model(self) -> Any:
        if self._model is not None:
            return self._model
        with self._lock:
            if self._model is None:
                try:
                    from faster_whisper import WhisperModel

                    self._model = WhisperModel(
                        self.config.model_name,
                        device=self.config.device,
                        compute_type=self.config.compute_type,
                        download_root=self.config.model_cache,
                    )
                except Exception as exc:
                    raise TranscriptionError("local Whisper model could not be loaded") from exc
        return self._model
