from PIL import Image


def _striped_frame(path):
    image = Image.new("RGB", (8, 8), "white")
    for x in range(4):
        for y in range(8):
            image.putpixel((x, y), (0, 0, 0))
    image.save(path)


def test_segment_endpoint_returns_region_proposals(real_lightweight_client, tmp_path):
    frame_path = tmp_path / "frame.png"
    _striped_frame(frame_path)

    response = real_lightweight_client.post(
        "/ml/segment", json={"frameId": "frame-1", "frameRef": f"file://{frame_path}"}
    )

    assert response.status_code == 200
    body = response.json()
    assert body["modelVersion"] == "heuristic-test"
    assert body["frameWidth"] == 8
    assert body["frameHeight"] == 8
    assert len(body["proposals"]) == 1
    assert body["proposals"][0]["source"] == "heuristic"


def test_segment_endpoint_missing_frame_returns_503(client, tmp_path):
    response = client.post(
        "/ml/segment", json={"frameId": "frame-missing", "frameRef": f"file://{tmp_path / 'missing.png'}"}
    )

    assert response.status_code == 503
    assert response.json()["detail"] == "frame artifact read failed"


def test_segment_endpoint_rejects_unreadable_scheme(client):
    response = client.post("/ml/segment", json={"frameRef": "frames/relative.png"})

    assert response.status_code == 400
    assert response.json()["detail"] == "segmentation requires readable frameRef"


def test_segment_endpoint_honours_force_failure(make_client, tmp_path):
    client, _ = make_client(ml_engine_force_failure=True)

    response = client.post("/ml/segment", json={"frameRef": f"file://{tmp_path / 'x.png'}"})

    assert response.status_code == 503
    assert response.json()["detail"] == "forced ml-engine failure"
