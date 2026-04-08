from fastapi.testclient import TestClient

import app.main as main_module
from app.main import app

client = TestClient(app)


def test_sentiment_endpoint_returns_valid_shape():
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
    assert body["modelVersion"] == "stub-v1"


def test_sentiment_is_deterministic():
    payload = {
        "eventId": "evt-123",
        "streamer": "xqc",
        "user": "wali",
        "message": "this stream is great",
        "timestamp": 1710000000000,
    }

    response1 = client.post("/ml/sentiment", json=payload)
    response2 = client.post("/ml/sentiment", json=payload)

    assert response1.status_code == 200
    assert response2.status_code == 200
    assert response1.json() == response2.json()


def test_different_messages_can_produce_different_scores():
    payload1 = {
        "eventId": "evt-1",
        "streamer": "xqc",
        "user": "wali",
        "message": "this stream is great",
        "timestamp": 1710000000000,
    }

    payload2 = {
        "eventId": "evt-2",
        "streamer": "xqc",
        "user": "wali",
        "message": "this stream is terrible",
        "timestamp": 1710000000001,
    }

    response1 = client.post("/ml/sentiment", json=payload1)
    response2 = client.post("/ml/sentiment", json=payload2)

    assert response1.status_code == 200
    assert response2.status_code == 200

    score1 = response1.json()["score"]
    score2 = response2.json()["score"]

    assert -1.0 <= score1 <= 1.0
    assert -1.0 <= score2 <= 1.0


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
