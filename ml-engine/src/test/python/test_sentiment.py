import os

import pytest

from app.sentiment import (
    LexicalFallbackSentimentAnalyzer,
    SentimentConfig,
    SentimentResult,
    create_sentiment_analyzer,
    preprocess_text,
)
from conftest import FakeRegistry, FakeSentiment, make_settings


def payload(message: str = "this stream is great", event_id: str = "evt-123") -> dict:
    return {"eventId": event_id, "streamer": "xqc", "user": "wali", "message": message, "timestamp": 1710000000000}


def test_sentiment_endpoint_returns_valid_shape(make_client):
    settings = make_settings()
    registry = FakeRegistry(settings=settings, sentiment=FakeSentiment(SentimentResult("POSITIVE", 0.87, "test-model-v1")))
    client, _ = make_client(registry)

    response = client.post("/ml/sentiment", json=payload())

    assert response.status_code == 200
    body = response.json()
    assert body == {"label": "POSITIVE", "score": 0.87, "modelVersion": "test-model-v1"}
    assert registry.sentiment.calls == ["this stream is great"]


@pytest.mark.parametrize(
    ("result", "expected_sign"),
    [
        (SentimentResult("POSITIVE", 0.91, "m"), 1),
        (SentimentResult("NEGATIVE", -0.76, "m"), -1),
        (SentimentResult("NEUTRAL", 0.02, "m"), 0),
    ],
)
def test_endpoint_passes_analyzer_result_through(make_client, result, expected_sign):
    client, registry = make_client()
    registry.sentiment = FakeSentiment(result)

    body = client.post("/ml/sentiment", json=payload()).json()

    assert body["label"] == result.label
    if expected_sign > 0:
        assert body["score"] > 0
    elif expected_sign < 0:
        assert body["score"] < 0
    else:
        assert abs(body["score"]) < 0.1


def test_lexical_backend_end_to_end(real_lightweight_client):
    positive = real_lightweight_client.post("/ml/sentiment", json=payload("I love this stream, this is amazing")).json()
    negative = real_lightweight_client.post("/ml/sentiment", json=payload("this is awful and terrible")).json()

    assert positive["label"] == "POSITIVE" and positive["score"] > 0
    assert negative["label"] == "NEGATIVE" and negative["score"] < 0
    assert positive["modelVersion"] == "lexical-v1"


def test_preprocess_replaces_urls_and_mentions():
    assert preprocess_text("  hey @viewer check https://example.com/test  ", 1000) == "hey @user check http"


def test_invalid_payload_returns_validation_error(client):
    response = client.post(
        "/ml/sentiment",
        json={"eventId": "evt-invalid", "streamer": "xqc", "user": "wali", "timestamp": 1710000000000},
    )

    assert response.status_code == 422


def test_force_failure_flag_returns_503(make_client):
    client, _ = make_client(ml_engine_force_failure=True)

    response = client.post("/ml/sentiment", json=payload(event_id="evt-force"))

    assert response.status_code == 503
    assert response.json()["detail"] == "forced ml-engine failure"


def test_create_sentiment_analyzer_selects_backend_from_config():
    config = SentimentConfig(backend="lexical", model="m", device="cpu", cache_dir="/tmp", max_chars=10, preload=False)

    assert isinstance(create_sentiment_analyzer(config), LexicalFallbackSentimentAnalyzer)


@pytest.mark.skipif(
    os.getenv("STREAMSENSE_RUN_REAL_SENTIMENT_TESTS") != "true",
    reason="real sentiment model test is opt-in",
)
def test_real_sentiment_model_when_enabled():
    config = SentimentConfig(
        backend="transformers",
        model="cardiffnlp/twitter-roberta-base-sentiment-latest",
        device="cpu",
        cache_dir="/models/sentiment",
        max_chars=1000,
        preload=True,
    )
    analyzer = create_sentiment_analyzer(config)

    positive = analyzer.analyze("I love this stream, this is amazing")
    negative = analyzer.analyze("this is awful and terrible")

    assert positive.label == "POSITIVE" and positive.score > 0
    assert negative.label == "NEGATIVE" and negative.score < 0
