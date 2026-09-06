from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient

import app.main as main_module
from app.main import app
from app.transcription import (
    TranscriptionError,
    TranscriptionResult,
    WhisperTranscriber,
)

client = TestClient(app)


class FakeSegment:
    def __init__(self, text: str, avg_logprob: float | None = -0.2):
        self.text = text
        self.avg_logprob = avg_logprob


def test_whisper_transcriber_returns_text_and_deletes_temp_file(monkeypatch):
    seen_paths = []

    class FakeModel:
        def transcribe(self, path, **kwargs):
            seen_paths.append(path)
            assert kwargs["language"] == "en"
            return [FakeSegment(" hello "), FakeSegment("world")], SimpleNamespace(language="en")

    transcriber = WhisperTranscriber()
    monkeypatch.setattr(transcriber, "_load_model", lambda: FakeModel())

    result = transcriber.transcribe_bytes(b"fake-wav", "segment.wav")

    assert result.text == "hello world"
    assert result.language == "en"
    assert result.confidence == pytest.approx(0.8)
    assert result.model_version == "faster-whisper-small.en-int8"
    assert seen_paths
    assert not any(path for path in seen_paths if __import__("pathlib").Path(path).exists())


def test_whisper_transcriber_returns_empty_text_for_silence(monkeypatch):
    class FakeModel:
        def transcribe(self, path, **kwargs):
            return [], SimpleNamespace(language="en")

    transcriber = WhisperTranscriber()
    monkeypatch.setattr(transcriber, "_load_model", lambda: FakeModel())

    result = transcriber.transcribe_bytes(b"silent-wav", "silence.wav")

    assert result.text == ""
    assert result.language == "en"
    assert result.confidence is None


def test_whisper_transcriber_wraps_invalid_audio_errors(monkeypatch):
    class FakeModel:
        def transcribe(self, path, **kwargs):
            raise RuntimeError("bad audio")

    transcriber = WhisperTranscriber()
    monkeypatch.setattr(transcriber, "_load_model", lambda: FakeModel())

    with pytest.raises(TranscriptionError):
        transcriber.transcribe_bytes(b"not-a-wav", "bad.wav")


def test_transcribe_endpoint_returns_valid_shape(monkeypatch):
    class FakeTranscriber:
        def transcribe_bytes(self, audio, file_name, language=None):
            assert audio == b"fake-wav"
            assert file_name == "segment.wav"
            assert language == "en"
            return TranscriptionResult("hello stream", "en", 0.91, "fake-whisper")

    monkeypatch.setattr(main_module, "transcriber", FakeTranscriber())

    response = client.post(
        "/ml/transcribe",
        files={"file": ("segment.wav", b"fake-wav", "audio/wav")},
        data={
            "streamer": "austincs",
            "segmentId": "segment-1",
            "startedAt": "1710000000000",
            "endedAt": "1710000005000",
            "language": "en",
        },
    )

    assert response.status_code == 200
    assert response.json() == {
        "text": "hello stream",
        "language": "en",
        "confidence": 0.91,
        "modelVersion": "fake-whisper",
    }


def test_transcribe_endpoint_returns_503_for_invalid_audio(monkeypatch):
    class FakeTranscriber:
        def transcribe_bytes(self, audio, file_name, language=None):
            raise TranscriptionError("bad audio")

    monkeypatch.setattr(main_module, "transcriber", FakeTranscriber())

    response = client.post(
        "/ml/transcribe",
        files={"file": ("segment.wav", b"bad", "audio/wav")},
        data={
            "streamer": "austincs",
            "segmentId": "segment-1",
            "startedAt": "1710000000000",
            "endedAt": "1710000005000",
        },
    )

    assert response.status_code == 503
    assert response.json()["detail"] == "local transcription failed"


def test_force_failure_flag_returns_503_for_transcription(monkeypatch):
    monkeypatch.setattr(main_module, "force_failure_enabled", lambda: True)

    response = client.post(
        "/ml/transcribe",
        files={"file": ("segment.wav", b"fake-wav", "audio/wav")},
        data={
            "streamer": "austincs",
            "segmentId": "segment-1",
            "startedAt": "1710000000000",
            "endedAt": "1710000005000",
        },
    )

    assert response.status_code == 503
    assert response.json()["detail"] == "forced ml-engine failure"
