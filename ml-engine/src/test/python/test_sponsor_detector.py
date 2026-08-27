from app.segmentation import RegionProposal
from app.sponsor import compute_sponsor_detection, detect_sponsor


def test_detector_uses_proposal_box_when_available():
    proposal = RegionProposal.from_bounds(
        label="candidate-logo-region",
        confidence=0.87,
        x=0.2,
        y=0.3,
        width=0.4,
        height=0.5,
        source="test",
    )

    detection = detect_sponsor(
        frame_ref="file:///frame.png",
        streamer="test",
        frame_sequence=1,
        frame_signature="checksum:8x8:100",
        proposals=[proposal],
    )

    assert detection.model_version == "proposal-aware-stub-v1"
    assert detection.x == 0.2
    assert detection.y == 0.3
    assert detection.width == 0.4
    assert detection.height == 0.5
    assert detection.confidence >= proposal.confidence


def test_detector_uses_highest_confidence_proposal():
    lower = RegionProposal.from_bounds("low", 0.2, 0.1, 0.1, 0.2, 0.2, "test")
    higher = RegionProposal.from_bounds("high", 0.9, 0.5, 0.4, 0.3, 0.2, "test")

    detection = detect_sponsor(
        frame_ref="file:///frame.png",
        streamer="test",
        frame_sequence=1,
        frame_signature="checksum:8x8:100",
        proposals=[lower, higher],
    )

    assert detection.x == 0.5
    assert detection.y == 0.4
    assert detection.width == 0.3
    assert detection.height == 0.2


def test_detector_model_version_reflects_frame_awareness():
    frame_detection = detect_sponsor(
        frame_ref="file:///frame.png",
        streamer="test",
        frame_sequence=1,
        frame_signature="checksum:8x8:100",
    )
    metadata_detection = detect_sponsor(
        frame_ref="frames/test.png",
        streamer="test",
        frame_sequence=1,
    )

    assert frame_detection.model_version == "frame-aware-stub-v1"
    assert metadata_detection.model_version == "stub-v1"


def test_legacy_compute_sponsor_detection_tuple_shape_stays_compatible():
    detection = compute_sponsor_detection("frames/test.png", "test", 1)

    assert len(detection) == 6
    assert 0.0 <= detection[1] <= 1.0
    assert 0.0 <= detection[2] <= 1.0
    assert 0.0 <= detection[3] <= 1.0
    assert 0.0 <= detection[4] <= 1.0
    assert 0.0 <= detection[5] <= 1.0
