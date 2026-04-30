from app.kafka_publisher import FrameEvent, TranscriptSegmentEvent


def test_frame_event_serializes_phase_two_fields():
    event = FrameEvent(
        frameId="frame-1",
        streamer="austincs",
        frameRef="s3://streamsense-frames/twitch/austincs/frame.jpg",
        frameSequence=1,
        capturedAt=1710000000000,
        source="TWITCH",
        channelLogin="austincs",
        streamSessionId="austincs-1710000000000",
        twitchStreamId=None,
        videoTimestampMs=0,
        artifactContentType="image/jpeg",
        artifactSizeBytes=10,
        captureWorkerId="worker-1",
    )

    assert event.as_dict()["source"] == "TWITCH"
    assert event.as_dict()["streamSessionId"] == "austincs-1710000000000"


def test_transcript_segment_event_serializes_phase_two_point_five_fields():
    event = TranscriptSegmentEvent(
        segmentId="segment-1",
        streamer="austincs",
        text="hello stream",
        startedAt=1710000000000,
        endedAt=1710000005000,
        language="en",
        confidence=0.91,
        modelVersion="faster-whisper-small.en-int8",
        source="TWITCH",
        channelLogin="austincs",
        streamSessionId="austincs-1710000000000",
        twitchStreamId=None,
        videoTimestampMs=0,
        transcriptSequence=1,
        captureWorkerId="worker-1",
    )

    assert event.as_dict()["source"] == "TWITCH"
    assert event.as_dict()["text"] == "hello stream"
    assert event.as_dict()["modelVersion"] == "faster-whisper-small.en-int8"
