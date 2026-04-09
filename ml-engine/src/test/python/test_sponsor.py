from fastapi.testclient import TestClient

import app.main as main_module
from app.main import app

client = TestClient(app)


def sponsor_payload(frame_ref: str = "frames/test-001.png", frame_sequence: int = 1):
    return {
        "frameId": f"frame-{frame_sequence}",
        "streamer": "test",
        "frameRef": frame_ref,
        "frameSequence": frame_sequence,
        "capturedAt": 1710000000000 + frame_sequence,
    }


def test_sponsor_endpoint_returns_valid_shape():
    response = client.post("/ml/sponsor", json=sponsor_payload())

    assert response.status_code == 200

    body = response.json()
    assert body["sponsor"] in ["Nike", "Red Bull", "Razer", "Prime", "Logitech"]
    assert 0.0 <= body["confidence"] <= 1.0
    assert body["modelVersion"] == "stub-v1"
    assert 0.0 <= body["x"] <= 1.0
    assert 0.0 <= body["y"] <= 1.0
    assert 0.0 <= body["width"] <= 1.0
    assert 0.0 <= body["height"] <= 1.0


def test_sponsor_detection_is_deterministic():
    response1 = client.post("/ml/sponsor", json=sponsor_payload())
    response2 = client.post("/ml/sponsor", json=sponsor_payload())

    assert response1.status_code == 200
    assert response2.status_code == 200
    assert response1.json() == response2.json()


def test_different_frames_can_produce_different_sponsor_results():
    response1 = client.post("/ml/sponsor", json=sponsor_payload("frames/test-001.png", 1))
    response2 = client.post("/ml/sponsor", json=sponsor_payload("frames/test-099.png", 99))

    assert response1.status_code == 200
    assert response2.status_code == 200
    assert response1.json() != response2.json()


def test_invalid_sponsor_payload_returns_validation_error():
    response = client.post(
        "/ml/sponsor",
        json={
            "frameId": "frame-invalid",
            "streamer": "test",
            "frameSequence": 1,
            "capturedAt": 1710000000000,
        },
    )

    assert response.status_code == 422


def test_force_failure_flag_returns_503_for_sponsor(monkeypatch):
    monkeypatch.setattr(main_module, "force_failure_enabled", lambda: True)

    response = client.post("/ml/sponsor", json=sponsor_payload())

    assert response.status_code == 503
    assert response.json()["detail"] == "forced ml-engine failure"
