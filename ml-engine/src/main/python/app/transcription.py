import os
import tempfile
from dataclasses import dataclass
from pathlib import Path


class TranscriptionError(Exception):
    pass


@dataclass(frozen=True)
class TranscriptionResult:
    text: str
    language: str | None
    confidence: float | None
    model_version: str


class WhisperTranscriber:
    def __init__(self) -> None:
        self.model_name = os.getenv("STREAMSENSE_WHISPER_MODEL", "small.en")
        self.compute_type = os.getenv("STREAMSENSE_WHISPER_COMPUTE_TYPE", "int8")
        self.device = os.getenv("STREAMSENSE_WHISPER_DEVICE", "cpu")
        self.language = os.getenv("STREAMSENSE_WHISPER_LANGUAGE", "en") or None
        self.model_cache = os.getenv("STREAMSENSE_WHISPER_MODEL_CACHE", "/models/whisper")
        self._model = None

    @property
    def model_version(self) -> str:
        return f"faster-whisper-{self.model_name}-{self.compute_type}"

    def transcribe_bytes(
        self, audio: bytes, file_name: str, language: str | None = None
    ) -> TranscriptionResult:
        if not audio:
            raise TranscriptionError("audio upload is empty")

        suffix = Path(file_name).suffix or ".wav"
        temp_path = None
        try:
            with tempfile.NamedTemporaryFile(
                prefix="streamsense-transcript-", suffix=suffix, delete=False
            ) as handle:
                temp_path = Path(handle.name)
                handle.write(audio)
            return self.transcribe_file(temp_path, language)
        finally:
            if temp_path is not None:
                temp_path.unlink(missing_ok=True)

    def transcribe_file(
        self, audio_path: Path, language: str | None = None
    ) -> TranscriptionResult:
        try:
            model = self._load_model()
            resolved_language = language or self.language
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
            return TranscriptionResult(
                transcript, detected_language, confidence, self.model_version
            )
        except TranscriptionError:
            raise
        except Exception as exc:
            raise TranscriptionError("local Whisper transcription failed") from exc

    def _load_model(self):
        if self._model is None:
            try:
                from faster_whisper import WhisperModel

                self._model = WhisperModel(
                    self.model_name,
                    device=self.device,
                    compute_type=self.compute_type,
                    download_root=self.model_cache,
                )
            except Exception as exc:
                raise TranscriptionError("local Whisper model could not be loaded") from exc
        return self._model


transcriber = WhisperTranscriber()
