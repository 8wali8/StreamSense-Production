import threading
from pathlib import Path
from types import SimpleNamespace

import pytest

from app.transcription import TranscriptionError, TranscriptionResult, WhisperConfig, WhisperTranscriber
from conftest import FakeTranscriber


class FakeSegment:
    def __init__(self, text: str, avg_logprob: float | None = -0.2):
        self.text = text
        self.avg_logprob = avg_logprob


def form(**overrides):
    data = {"streamer": "austincs", "segmentId": "segment-1", "startedAt": "1710000000000", "endedAt": "1710000005000"}
    data.update(overrides)
    return data


def test_whisper_transcriber_returns_text_and_deletes_temp_file(monkeypatch):
    seen_paths = []

    class FakeModel:
        def transcribe(self, path, **kwargs):
            seen_paths.append(path)
            assert kwargs["language"] == "en"
            return [FakeSegment(" hello "), FakeSegment("world")], SimpleNamespace(language="en")

    transcriber = WhisperTranscriber(WhisperConfig())
    monkeypatch.setattr(transcriber, "_load_model", lambda: FakeModel())

    result = transcriber.transcribe_bytes(b"fake-wav", "segment.wav")

    assert result.text == "hello world"
    assert result.language == "en"
    assert result.confidence == pytest.approx(0.8)
    assert result.model_version == "faster-whisper-small.en-int8"
    assert seen_paths
    assert not any(Path(path).exists() for path in seen_paths)


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


def test_whisper_model_loads_once_across_threads(monkeypatch):
    loads = []

    class FakeWhisperModel:
        def __init__(self, *args, **kwargs):
            loads.append(kwargs)

    import types

    fake_module = types.SimpleNamespace(WhisperModel=FakeWhisperModel)
    monkeypatch.setitem(__import__("sys").modules, "faster_whisper", fake_module)
    transcriber = WhisperTranscriber(WhisperConfig(model_name="tiny.en", model_cache="/tmp/whisper-test"))

    threads = [threading.Thread(target=transcriber.warm_up) for _ in range(8)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()

    assert len(loads) == 1
    assert loads[0]["download_root"] == "/tmp/whisper-test"
    assert transcriber.is_loaded() is True


def test_transcribe_endpoint_returns_valid_shape(make_client):
    client, registry = make_client()
    registry.transcriber = FakeTranscriber(TranscriptionResult("hello stream", "en", 0.91, "fake-whisper"))

    response = client.post(
        "/ml/transcribe",
        files={"file": ("segment.wav", b"fake-wav", "audio/wav")},
        data=form(language="en"),
    )

    assert response.status_code == 200
    assert response.json() == {
        "text": "hello stream",
        "language": "en",
        "confidence": 0.91,
        "modelVersion": "fake-whisper",
    }
    assert registry.transcriber.calls == [(b"fake-wav", "segment.wav", "en")]


def test_transcribe_endpoint_returns_503_for_invalid_audio(make_client):
    client, registry = make_client()
    registry.transcriber = FakeTranscriber(error=TranscriptionError("bad audio"))

    response = client.post("/ml/transcribe", files={"file": ("segment.wav", b"bad", "audio/wav")}, data=form())

    assert response.status_code == 503
    assert response.json()["detail"] == "local transcription failed"


def test_force_failure_flag_returns_503_for_transcription(make_client):
    client, _ = make_client(ml_engine_force_failure=True)

    response = client.post("/ml/transcribe", files={"file": ("segment.wav", b"fake-wav", "audio/wav")}, data=form())

    assert response.status_code == 503
    assert response.json()["detail"] == "forced ml-engine failure"
