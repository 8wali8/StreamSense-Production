"""The sentiment request and response models match the schemas sentiment-service is tested against."""

from __future__ import annotations

import json
from pathlib import Path

from jsonschema import Draft202012Validator

from ml_engine.models import SentimentRequest, SentimentResponse

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


def test_schema_rejects_an_unknown_label() -> None:
    response = SentimentResponse(label="MIXED", score=0.0, modelVersion="lexical-v1")

    assert violations("ml-sentiment-response.schema.json", response.model_dump()) != []
