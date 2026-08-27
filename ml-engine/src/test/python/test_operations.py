from fastapi.testclient import TestClient

from app.main import create_app
from conftest import make_settings


def test_health_reports_ok_and_ready(client):
    response = client.get("/ml/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "service": "ml-engine", "ready": True}


def test_live_is_always_alive(client):
    assert client.get("/ml/live").json() == {"status": "alive"}


def test_ready_is_503_before_the_lifespan_has_run():
    app = create_app(make_settings())
    # No `with`: the lifespan never runs, so the registry does not exist yet.
    client = TestClient(app)

    assert client.get("/ml/ready").status_code == 503
    assert client.get("/ml/ready").json() == {"status": "starting"}
    assert client.get("/ml/health").json()["ready"] is False


def test_ready_and_info_after_startup(real_lightweight_client):
    ready = real_lightweight_client.get("/ml/ready")
    info = real_lightweight_client.get("/ml/info")

    assert ready.status_code == 200
    assert ready.json() == {"status": "ready"}
    body = info.json()
    assert body["service"] == "ml-engine"
    assert body["ready"] is True
    assert body["forceFailure"] is False
    backends = {b["name"]: b for b in body["backends"]}
    assert backends["sentiment"]["backend"] == "lexical"
    assert backends["relevance"]["backend"] == "direct"
    assert backends["segmentation"]["backend"] == "heuristic"
    assert backends["transcription"]["loaded"] is False


def test_inference_route_is_503_before_startup():
    app = create_app(make_settings())
    client = TestClient(app)

    response = client.post(
        "/ml/sentiment",
        json={"eventId": "e", "streamer": "s", "user": "u", "message": "hi", "timestamp": 1},
    )

    assert response.status_code == 503
    assert "not ready" in response.json()["detail"]


def test_metrics_endpoint_exposes_http_and_inference_metrics(client):
    client.post(
        "/ml/sentiment",
        json={"eventId": "e", "streamer": "s", "user": "u", "message": "hi", "timestamp": 1},
    )

    metrics = client.get("/metrics")

    assert metrics.status_code == 200
    body = metrics.text
    assert "http_request_duration_seconds" in body
    assert 'streamsense_ml_inference_seconds_count{backend="sentiment"}' in body


def test_force_failure_applies_to_every_inference_route(make_client):
    client, _ = make_client(ml_engine_force_failure=True)
    payloads = [
        ("/ml/sentiment", {"json": {"eventId": "e", "streamer": "s", "user": "u", "message": "hi", "timestamp": 1}}),
        ("/ml/relevance", {"json": {"streamer": "s", "text": "t", "sponsor": "Nike"}}),
        ("/ml/sponsor", {"json": {"frameId": "f", "streamer": "s", "frameRef": "x", "frameSequence": 1, "capturedAt": 1}}),
        ("/ml/segment", {"json": {"frameRef": "x"}}),
        (
            "/ml/transcribe",
            {
                "files": {"file": ("a.wav", b"x", "audio/wav")},
                "data": {"streamer": "s", "segmentId": "1", "startedAt": "1", "endedAt": "2"},
            },
        ),
    ]

    for path, kwargs in payloads:
        response = client.post(path, **kwargs)
        assert response.status_code == 503, path
        assert response.json()["detail"] == "forced ml-engine failure", path

    assert client.get("/ml/health").status_code == 200
    assert client.get("/ml/info").json()["forceFailure"] is True
