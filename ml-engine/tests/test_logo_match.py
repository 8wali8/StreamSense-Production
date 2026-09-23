"""The matcher finds the deal's logo where it is, says NOT_DETECTED where it is not, and answers the same twice."""

from __future__ import annotations

import pytest
from logo_fixtures import PLACEMENTS, composite, iou, jpeg_roundtrip, make_background, make_logo
from PIL import Image

from ml_engine.frame_store import FrameArtifactError, FrameStore
from ml_engine.logo_match import LogoMatchConfig, LogoMatchDetector
from ml_engine.settings import SponsorSettings
from ml_engine.sponsor import SponsorDetectionContext


@pytest.fixture(scope="module")
def logo_file(tmp_path_factory: pytest.TempPathFactory) -> str:
    path = tmp_path_factory.mktemp("logos") / "brand.png"
    make_logo(1).save(path)
    return path.as_uri()


@pytest.fixture(scope="module")
def detector() -> LogoMatchDetector:
    return LogoMatchDetector(LogoMatchConfig(), FrameStore())


def context(frame: Image.Image, logo_ref: str, sponsor: str = "Red Bull") -> SponsorDetectionContext:
    return SponsorDetectionContext(
        frame_ref="file:///frame.jpg",
        streamer="racer",
        frame_sequence=1,
        sponsor=sponsor,
        logo_refs=(logo_ref,),
        frame_image=frame,
    )


@pytest.mark.parametrize("placement", ["corner", "lower-third", "full"])
def test_finds_the_logo_at_each_placement_with_a_box_where_it_is(detector, logo_file, placement):
    expected = PLACEMENTS[placement]
    frame = jpeg_roundtrip(composite(make_background(7), make_logo(1), expected))

    detection = detector.detect(context(frame, logo_file))

    assert detection.outcome == "DETECTED", placement
    assert detection.sponsor == "Red Bull"
    assert detection.model_version == "logo-match-v1"
    assert detection.confidence >= 0.5
    assert (
        iou(
            (detection.x, detection.y, detection.width, detection.height),
            (expected.x, expected.y, expected.width, expected.height),
        )
        > 0.6
    )


def test_a_frame_without_the_logo_is_not_detected(detector, logo_file):
    detection = detector.detect(context(jpeg_roundtrip(make_background(11)), logo_file))

    assert detection.outcome == "NOT_DETECTED"
    assert detection.confidence == 0.0
    assert (detection.x, detection.y, detection.width, detection.height) == (0.0, 0.0, 0.0, 0.0)


def test_a_different_logo_in_the_frame_is_not_this_one(detector, logo_file):
    frame = jpeg_roundtrip(composite(make_background(13), make_logo(2), PLACEMENTS["lower-third"]))

    assert detector.detect(context(frame, logo_file)).outcome == "NOT_DETECTED"


def test_the_same_frame_and_logo_answer_the_same_twice(detector, logo_file):
    frame = jpeg_roundtrip(composite(make_background(17), make_logo(1), PLACEMENTS["corner"]))

    assert detector.detect(context(frame, logo_file)) == detector.detect(context(frame, logo_file))


def test_without_logo_refs_or_a_frame_the_answer_is_not_a_guess(detector, logo_file):
    frame = jpeg_roundtrip(make_background(19))
    no_logo = detector.detect(SponsorDetectionContext("file:///f.jpg", "racer", 1, frame_image=frame))
    assert no_logo.outcome == "NOT_DETECTED"
    assert no_logo.sponsor == "UNKNOWN"

    with pytest.raises(FrameArtifactError):
        detector.detect(SponsorDetectionContext("file:///f.jpg", "racer", 1, logo_refs=(logo_file,)))


def test_the_endpoint_runs_the_matcher_and_reports_it(make_client, tmp_path, logo_file):
    client, _ = make_client(sponsor=SponsorSettings(backend="logo-match"))
    frame_path = tmp_path / "frame.jpg"
    composite(make_background(23), make_logo(1), PLACEMENTS["lower-third"]).save(frame_path, quality=85)
    payload = {
        "frameId": "frame-1",
        "streamer": "racer",
        "frameRef": frame_path.as_uri(),
        "frameSequence": 1,
        "capturedAt": 1710000000000,
        "sponsor": "Red Bull",
        "dealId": 3,
        "logoId": 7,
        "logoRefs": [logo_file],
    }

    body = client.post("/ml/sponsor", json=payload).json()
    assert body["outcome"] == "DETECTED"
    assert body["sponsor"] == "Red Bull"
    assert body["modelVersion"] == "logo-match-v1"

    # A frame the store cannot read is an outage for that frame, not a detection of nothing.
    assert client.post("/ml/sponsor", json=payload | {"frameRef": "frames/not-readable.png"}).status_code == 503

    info = client.get("/ml/info").json()
    sponsor = next(backend for backend in info["backends"] if backend["name"] == "sponsor")
    assert sponsor["backend"] == "logo-match"
    assert sponsor["model"] == "logo-match-v1"


def test_the_stub_is_only_run_when_asked_for_by_name(make_client):
    client, _ = make_client(sponsor=SponsorSettings(backend="stub"))
    info = client.get("/ml/info").json()
    sponsor = next(backend for backend in info["backends"] if backend["name"] == "sponsor")
    assert sponsor["backend"] == "deterministic-stub"
    assert sponsor["model"] == "stub-v1"
