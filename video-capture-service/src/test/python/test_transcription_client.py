from app.transcription_client import TranscriptionClient


class FakeResponse:
    def raise_for_status(self):
        return None

    def json(self):
        return {
            "text": "hello stream",
            "language": "en",
            "confidence": 0.9,
            "modelVersion": "fake-whisper",
        }


def test_transcription_client_posts_audio(monkeypatch, tmp_path):
    audio_path = tmp_path / "segment.wav"
    audio_path.write_bytes(b"wav-bytes")
    captured = {}

    def fake_post(endpoint, files, data, timeout):
        captured["endpoint"] = endpoint
        captured["file_name"] = files["file"][0]
        captured["file_bytes"] = files["file"][1].read()
        captured["data"] = data
        captured["timeout"] = timeout
        return FakeResponse()

    monkeypatch.setattr("requests.post", fake_post)
    client = TranscriptionClient("http://ml-engine:8000", 30, "en")

    result, latency_ms = client.transcribe(
        audio_path, "austincs", "segment-1", 1710000000000, 1710000005000
    )

    assert result.text == "hello stream"
    assert result.language == "en"
    assert result.confidence == 0.9
    assert result.model_version == "fake-whisper"
    assert latency_ms >= 0
    assert captured["endpoint"] == "http://ml-engine:8000/ml/transcribe"
    assert captured["file_name"] == "segment.wav"
    assert captured["file_bytes"] == b"wav-bytes"
    assert captured["data"]["streamer"] == "austincs"
    assert captured["data"]["language"] == "en"
    assert captured["timeout"] == 30
