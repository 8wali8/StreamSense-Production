"""The sentiment request and response models match the schemas sentiment-service is tested against."""

from __future__ import annotations

import json
from pathlib import Path

from jsonschema import Draft202012Validator

from ml_engine.models import SentimentRequest, SentimentResponse, SponsorRequest, SponsorResponse

SCHEMA_DIR = Path(__file__).resolve().parents[2] / "docs" / "schemas"


def violations(schema_name: str, document: dict) -> list[str]:
    schema = json.loads((SCHEMA_DIR / schema_name).read_text(encoding="utf-8"))
    return [error.message for error in Draft202012Validator(schema).iter_errors(document)]


def test_schema_valid_request_parses_into_the_model() -> None:
    sample = {
        "eventId": "evt-1",
        "streamer": "streamer-1",
        "user": "user-1",
        "message": "hello",
        "timestamp": 1710000000000,
    }
    assert violations("ml-sentiment-request.schema.json", sample) == []

    request = SentimentRequest.model_validate(sample)

    assert request.eventId == "evt-1"
    assert request.timestamp == 1710000000000


def test_response_model_matches_schema() -> None:
    response = SentimentResponse(label="POSITIVE", score=0.75, modelVersion="lexical-v1")

    assert violations("ml-sentiment-response.schema.json", response.model_dump()) == []


def test_sponsor_request_with_a_logo_parses_and_matches_its_schema() -> None:
    sample = {
        "frameId": "frame-1",
        "streamer": "racer",
        "frameRef": "s3://streamsense-frames/racer/s1/000001-frame-1.jpg",
        "frameSequence": 1,
        "capturedAt": 1710000000000,
        "source": "TWITCH",
        "sponsor": "Red Bull",
        "dealId": 3,
        "logoId": 7,
        "logoRefs": ["s3://streamsense-logos/deals/3/a.png"],
    }
    assert violations("ml-sponsor-request.schema.json", sample) == []

    request = SponsorRequest.model_validate(sample)

    assert request.logoRefs == ["s3://streamsense-logos/deals/3/a.png"]
    assert violations("ml-sponsor-request.schema.json", request.model_dump()) == []


def test_sponsor_response_matches_its_schema_for_both_outcomes() -> None:
    found = SponsorResponse(
        sponsor="Red Bull", confidence=0.9, modelVersion="stub-v1", x=0.1, y=0.1, width=0.2, height=0.2
    )
    missed = SponsorResponse(
        sponsor="Red Bull", confidence=0.0, modelVersion="stub-v1", x=0, y=0, width=0, height=0, outcome="NOT_DETECTED"
    )

    assert violations("ml-sponsor-response.schema.json", found.model_dump()) == []
    assert violations("ml-sponsor-response.schema.json", missed.model_dump()) == []
    assert violations("ml-sponsor-response.schema.json", {**found.model_dump(), "outcome": "MAYBE"}) != []


def test_schema_rejects_an_unknown_label() -> None:
    response = SentimentResponse(label="MIXED", score=0.0, modelVersion="lexical-v1")

    assert violations("ml-sentiment-response.schema.json", response.model_dump()) != []
