"""The events this service publishes conform to the JSON Schemas under docs/schemas.

The schemas are the contract with sentiment-service, video-service, and the gateway; a field
added to a dataclass here without a schema change is a contract change, and fails here first.
"""

from __future__ import annotations

import json
from pathlib import Path

import pytest
from jsonschema import Draft202012Validator

from app.kafka_publisher import FrameEvent, TranscriptSegmentEvent

SCHEMA_DIR = Path(__file__).resolve().parents[4] / "docs" / "schemas"


def load_schema(name: str) -> dict:
    return json.loads((SCHEMA_DIR / name).read_text(encoding="utf-8"))


def violations(schema_name: str, document: dict) -> list[str]:
    validator = Draft202012Validator(load_schema(schema_name))
    return [error.message for error in validator.iter_errors(document)]


@pytest.mark.parametrize("schema_file", sorted(SCHEMA_DIR.glob("*.schema.json")), ids=lambda p: p.name)
def test_every_schema_is_a_valid_draft_2020_12_schema(schema_file: Path) -> None:
    Draft202012Validator.check_schema(json.loads(schema_file.read_text(encoding="utf-8")))


def test_frame_event_matches_schema() -> None:
    event = FrameEvent(
        frameId="frame-1",
        streamer="streamer-1",
        frameRef="frames/streamer-1/frame-1.jpg",
        frameSequence=1,
        capturedAt=1710000000000,
        source="TWITCH",
        channelLogin="streamer-1",
        streamSessionId="streamer-1-1710000000000",
        twitchStreamId=None,
        videoTimestampMs=0,
        artifactContentType="image/jpeg",
        artifactSizeBytes=123,
        captureWorkerId="worker-1",
    )

    assert violations("frame-data.schema.json", event.as_dict()) == []


def test_replayed_transcript_segment_matches_schema() -> None:
    event = TranscriptSegmentEvent(
        segmentId="seg-1",
        streamer="streamer-1",
        text="welcome back",
        startedAt=1710000000000,
        endedAt=1710000005000,
        language="en",
        confidence=0.92,
        modelVersion="faster-whisper-base",
        source="TWITCH_VOD_REPLAY",
        channelLogin="streamer-1",
        streamSessionId="streamer-1-1710000000000",
        twitchStreamId="12345",
        videoTimestampMs=15000,
        transcriptSequence=3,
        captureWorkerId="worker-1",
    )

    assert violations("transcript-segment-event.schema.json", event.as_dict()) == []


def test_schema_rejects_a_transcript_without_text() -> None:
    document = TranscriptSegmentEvent(
        segmentId="seg-1",
        streamer="streamer-1",
        text="",
        startedAt=1710000000000,
        endedAt=1710000005000,
        language=None,
        confidence=None,
        modelVersion="faster-whisper-base",
        source="TWITCH",
        channelLogin="streamer-1",
        streamSessionId="streamer-1-1710000000000",
        twitchStreamId=None,
        videoTimestampMs=0,
        transcriptSequence=0,
        captureWorkerId="worker-1",
    ).as_dict()
    document.pop("text")

    assert violations("transcript-segment-event.schema.json", document) != []
