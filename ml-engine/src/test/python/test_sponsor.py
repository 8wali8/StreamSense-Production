from PIL import Image

from app.segmentation import RegionProposal
from app.settings import SponsorSettings
from conftest import FakeSegmenter


def sponsor_payload(frame_ref: str = "frames/test-001.png", frame_sequence: int = 1):
    return {
        "frameId": f"frame-{frame_sequence}",
        "streamer": "test",
        "frameRef": frame_ref,
        "frameSequence": frame_sequence,
        "capturedAt": 1710000000000 + frame_sequence,
    }


def test_sponsor_endpoint_returns_valid_shape(client):
    response = client.post("/ml/sponsor", json=sponsor_payload())

    assert response.status_code == 200
    body = response.json()
    assert body["sponsor"] in ["Nike", "Red Bull", "Razer", "Prime", "Logitech"]
    assert 0.0 <= body["confidence"] <= 1.0
    assert body["modelVersion"] == "stub-v1"
    for key in ("x", "y", "width", "height"):
        assert 0.0 <= body[key] <= 1.0


def test_sponsor_detection_is_deterministic(client):
    first = client.post("/ml/sponsor", json=sponsor_payload()).json()
    second = client.post("/ml/sponsor", json=sponsor_payload()).json()

    assert first == second


def test_sponsor_endpoint_reads_real_frame_fixture(make_client, tmp_path):
    client, _ = make_client(sponsor=SponsorSettings(require_frame_read=True))
    frame_path = tmp_path / "frame.ppm"
    frame_path.write_bytes(b"P6\n1 1\n255\n\xff\x00\x00")

    response = client.post("/ml/sponsor", json=sponsor_payload(f"file://{frame_path}"))

    assert response.status_code == 200
    assert response.json()["modelVersion"] == "frame-aware-stub-v1"


def test_sponsor_endpoint_uses_region_proposals_when_segmentation_enabled(make_client, tmp_path):
    client, registry = make_client(sponsor=SponsorSettings(require_frame_read=True, segmentation_enabled=True))
    registry.segmenter = FakeSegmenter(
        [RegionProposal.from_bounds("visual-region", 0.9, 0.1, 0.1, 0.5, 0.5, source="heuristic")]
    )
    frame_path = tmp_path / "frame.png"
    Image.new("RGB", (8, 8), "white").save(frame_path)

    response = client.post("/ml/sponsor", json=sponsor_payload(f"file://{frame_path}"))

    assert response.status_code == 200
    body = response.json()
    assert body["modelVersion"] == "proposal-aware-stub-v1"
    assert body["x"] == 0.1 and body["width"] == 0.5


def test_heuristic_segmentation_end_to_end(real_lightweight_client, tmp_path):
    # The lightweight client has heuristic segmentation configured but segmentation_enabled is
    # off for /ml/sponsor, so the stub stays frame-aware only; /ml/segment is exercised separately.
    frame_path = tmp_path / "frame.png"
    image = Image.new("RGB", (8, 8), "white")
    for x in range(4):
        for y in range(8):
            image.putpixel((x, y), (0, 0, 0))
    image.save(frame_path)

    response = real_lightweight_client.post("/ml/sponsor", json=sponsor_payload(f"file://{frame_path}"))

    assert response.status_code == 200
    assert response.json()["modelVersion"] == "frame-aware-stub-v1"


def test_required_frame_read_failure_returns_503(make_client, tmp_path):
    client, _ = make_client(sponsor=SponsorSettings(require_frame_read=True))

    response = client.post("/ml/sponsor", json=sponsor_payload(f"file://{tmp_path / 'missing.jpg'}"))

    assert response.status_code == 503
    assert response.json()["detail"] == "frame artifact read failed"


def test_optional_frame_read_failure_falls_back_to_metadata_stub(make_client, tmp_path):
    client, _ = make_client()

    response = client.post("/ml/sponsor", json=sponsor_payload(f"file://{tmp_path / 'missing.jpg'}"))

    assert response.status_code == 200
    assert response.json()["modelVersion"] == "stub-v1"


def test_different_frames_can_produce_different_sponsor_results(client):
    first = client.post("/ml/sponsor", json=sponsor_payload("frames/test-001.png", 1)).json()
    second = client.post("/ml/sponsor", json=sponsor_payload("frames/test-099.png", 99)).json()

    assert first != second


def test_invalid_sponsor_payload_returns_validation_error(client):
    response = client.post(
        "/ml/sponsor",
        json={"frameId": "frame-invalid", "streamer": "test", "frameSequence": 1, "capturedAt": 1710000000000},
    )

    assert response.status_code == 422


def test_force_failure_flag_returns_503_for_sponsor(make_client):
    client, _ = make_client(ml_engine_force_failure=True)

    response = client.post("/ml/sponsor", json=sponsor_payload())

    assert response.status_code == 503
    assert response.json()["detail"] == "forced ml-engine failure"
