import time
from dataclasses import dataclass
from pathlib import Path

import requests


class TranscriptionClientError(Exception):
    pass


@dataclass(frozen=True)
class TranscriptionResult:
    text: str
    language: str | None
    confidence: float | None
    model_version: str


class TranscriptionClient:
    def __init__(self, ml_engine_url: str, timeout_seconds: int, language: str | None):
        self.endpoint = f"{ml_engine_url.rstrip('/')}/ml/transcribe"
        self.timeout_seconds = timeout_seconds
        self.language = language

    def transcribe(
        self,
        audio_path: Path,
        streamer: str,
        segment_id: str,
        started_at: int,
        ended_at: int,
    ) -> tuple[TranscriptionResult, int]:
        data = {
            "streamer": streamer,
            "segmentId": segment_id,
            "startedAt": str(started_at),
            "endedAt": str(ended_at),
        }
        if self.language:
            data["language"] = self.language

        start = time.monotonic()
        try:
            with audio_path.open("rb") as handle:
                response = requests.post(
                    self.endpoint,
                    files={"file": (audio_path.name, handle, "audio/wav")},
                    data=data,
                    timeout=self.timeout_seconds,
                )
            response.raise_for_status()
        except requests.RequestException as exc:
            raise TranscriptionClientError("ml-engine transcription request failed") from exc

        latency_ms = int((time.monotonic() - start) * 1000)
        body = response.json()
        return (
            TranscriptionResult(
                body.get("text", ""),
                body.get("language"),
                body.get("confidence"),
                body.get("modelVersion", "unknown"),
            ),
            latency_ms,
        )
