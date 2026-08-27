from app.main import app
from fastapi.testclient import TestClient
from PIL import Image

client = TestClient(app)


def test_segment_endpoint_returns_region_proposals(monkeypatch, tmp_path):
    monkeypatch.setenv("STREAMSENSE_SEGMENTATION_BACKEND", "heuristic")
    monkeypatch.setenv("STREAMSENSE_SEGMENTATION_MODEL_VERSION", "heuristic-test")
    frame_path = tmp_path / "frame.png"
    image = Image.new("RGB", (8, 8), "white")
    for x in range(4):
        for y in range(8):
            image.putpixel((x, y), (0, 0, 0))
    image.save(frame_path)

    response = client.post(
        "/ml/segment",
        json={"frameId": "frame-1", "frameRef": f"file://{frame_path}"},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["modelVersion"] == "heuristic-test"
    assert body["frameWidth"] == 8
    assert body["frameHeight"] == 8
    assert len(body["proposals"]) == 1
    assert body["proposals"][0]["source"] == "heuristic"


def test_segment_endpoint_missing_frame_returns_503(tmp_path):
    missing = tmp_path / "missing.png"

    response = client.post(
        "/ml/segment",
        json={"frameId": "frame-missing", "frameRef": f"file://{missing}"},
    )

    assert response.status_code == 503
    assert response.json()["detail"] == "frame artifact read failed"
