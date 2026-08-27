import os

import app.main as main_module
import pytest
from app.main import app
from app.sentiment import SentimentResult, analyze_sentiment, preprocess_text
from fastapi.testclient import TestClient

client = TestClient(app)


def test_sentiment_endpoint_returns_valid_shape(monkeypatch):
    monkeypatch.setattr(
        main_module,
        "analyze_sentiment",
        lambda message: SentimentResult("POSITIVE", 0.87, "test-model-v1"),
    )
    payload = {
        "eventId": "evt-123",
        "streamer": "xqc",
        "user": "wali",
        "message": "this stream is great",
        "timestamp": 1710000000000,
    }

    response = client.post("/ml/sentiment", json=payload)

    assert response.status_code == 200

    body = response.json()
    assert "label" in body
    assert "score" in body
    assert "modelVersion" in body

    assert body["label"] in ["POSITIVE", "NEUTRAL", "NEGATIVE"]
    assert -1.0 <= body["score"] <= 1.0
    assert body["modelVersion"] == "test-model-v1"


def test_positive_text_returns_positive_with_mock_analyzer(monkeypatch):
    monkeypatch.setattr(
        main_module,
        "analyze_sentiment",
        lambda message: SentimentResult("POSITIVE", 0.91, "test-model-v1"),
    )
    payload = {
        "eventId": "evt-pos",
        "streamer": "xqc",
        "user": "wali",
        "message": "I love this stream, this is amazing",
        "timestamp": 1710000000000,
    }

    response = client.post("/ml/sentiment", json=payload)

    assert response.status_code == 200
    body = response.json()
    assert body["label"] == "POSITIVE"
    assert body["score"] > 0


def test_negative_text_returns_negative_with_mock_analyzer(monkeypatch):
    monkeypatch.setattr(
        main_module,
        "analyze_sentiment",
        lambda message: SentimentResult("NEGATIVE", -0.76, "test-model-v1"),
    )
    payload = {
        "eventId": "evt-neg",
        "streamer": "xqc",
        "user": "wali",
        "message": "this is awful and terrible",
        "timestamp": 1710000000000,
    }

    response = client.post("/ml/sentiment", json=payload)

    assert response.status_code == 200
    body = response.json()
    assert body["label"] == "NEGATIVE"
    assert body["score"] < 0


def test_neutral_text_returns_near_neutral_with_mock_analyzer(monkeypatch):
    monkeypatch.setattr(
        main_module,
        "analyze_sentiment",
        lambda message: SentimentResult("NEUTRAL", 0.02, "test-model-v1"),
    )
    payload = {
        "eventId": "evt-neutral",
        "streamer": "xqc",
        "user": "wali",
        "message": "the stream started at noon",
        "timestamp": 1710000000000,
    }

    response = client.post("/ml/sentiment", json=payload)

    assert response.status_code == 200
    body = response.json()
    assert body["label"] == "NEUTRAL"
    assert abs(body["score"]) < 0.1


def test_preprocess_replaces_urls_and_mentions():
    text = preprocess_text("  hey @viewer check https://example.com/test  ", 1000)

    assert text == "hey @user check http"


def test_invalid_payload_returns_validation_error():
    response = client.post(
        "/ml/sentiment",
        json={
            "eventId": "evt-invalid",
            "streamer": "xqc",
            "user": "wali",
            "timestamp": 1710000000000,
        },
    )

    assert response.status_code == 422


def test_force_failure_flag_returns_503(monkeypatch):
    monkeypatch.setattr(main_module, "force_failure_enabled", lambda: True)

    response = client.post(
        "/ml/sentiment",
        json={
            "eventId": "evt-force",
            "streamer": "xqc",
            "user": "wali",
            "message": "this stream is great",
            "timestamp": 1710000000000,
        },
    )

    assert response.status_code == 503
    assert response.json()["detail"] == "forced ml-engine failure"


@pytest.mark.skipif(
    os.getenv("STREAMSENSE_RUN_REAL_SENTIMENT_TESTS") != "true",
    reason="real sentiment model test is opt-in",
)
def test_real_sentiment_model_when_enabled(monkeypatch):
    monkeypatch.setenv("STREAMSENSE_SENTIMENT_BACKEND", "transformers")
    monkeypatch.setenv(
        "STREAMSENSE_SENTIMENT_MODEL",
        "cardiffnlp/twitter-roberta-base-sentiment-latest",
    )

    import app.sentiment as sentiment_module

    monkeypatch.setattr(sentiment_module, "_sentiment_analyzer", None)

    positive = analyze_sentiment("I love this stream, this is amazing")
    negative = analyze_sentiment("this is awful and terrible")

    assert positive.label == "POSITIVE"
    assert positive.score > 0
    assert negative.label == "NEGATIVE"
    assert negative.score < 0
