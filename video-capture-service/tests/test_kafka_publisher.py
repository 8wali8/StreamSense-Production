from video_capture_service.kafka_publisher import FrameEvent, TranscriptSegmentEvent


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


def test_publisher_is_idempotent_and_connects_lazily():
    from video_capture_service.kafka_publisher import EventPublisher

    created = []

    class FakeFuture:
        def get(self, timeout):
            return None

    class FakeProducer:
        def __init__(self, config):
            self.config = config
            self.sent = []
            self.flushed = 0
            self.closed = 0

        def send(self, topic, key, value):
            self.sent.append((topic, key, value))
            return FakeFuture()

        def flush(self, timeout):
            self.flushed += 1

        def close(self, timeout):
            self.closed += 1

    def factory(config):
        producer = FakeProducer(config)
        created.append(producer)
        return producer

    publisher = EventPublisher("kafka:9092", "stream.video.frames", producer_factory=factory)
    assert publisher.is_connected() is False

    event = FrameEvent(
        frameId="frame-1",
        streamer="austincs",
        frameRef="s3://b/k",
        frameSequence=1,
        capturedAt=1,
        source="TWITCH",
        channelLogin="austincs",
        streamSessionId="session-1",
        twitchStreamId=None,
        videoTimestampMs=0,
        artifactContentType="image/jpeg",
        artifactSizeBytes=1,
        captureWorkerId="w",
    )
    publisher.publish(event)
    publisher.publish(event)

    assert len(created) == 1
    producer = created[0]
    assert producer.config["enable_idempotence"] is True
    assert producer.config["acks"] == "all"
    assert producer.sent[0][:2] == ("stream.video.frames", "session-1")
    assert producer.flushed == 0, "flush must not happen per message"

    publisher.close()
    assert producer.flushed == 1
    assert producer.closed == 1
    assert publisher.is_connected() is False


def test_producer_config_has_bounded_timeouts():
    from video_capture_service.kafka_publisher import producer_config

    config = producer_config("kafka:9092")

    assert config["request_timeout_ms"] == 10_000
    assert config["retries"] >= 1
