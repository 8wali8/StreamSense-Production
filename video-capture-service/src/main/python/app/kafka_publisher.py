import json
import time
from dataclasses import dataclass

from kafka import KafkaProducer


@dataclass(frozen=True)
class FrameEvent:
    frameId: str
    streamer: str
    frameRef: str
    frameSequence: int
    capturedAt: int
    source: str
    channelLogin: str
    streamSessionId: str
    twitchStreamId: str | None
    videoTimestampMs: int
    artifactContentType: str
    artifactSizeBytes: int
    captureWorkerId: str

    def as_dict(self) -> dict:
        return self.__dict__


@dataclass(frozen=True)
class TranscriptSegmentEvent:
    segmentId: str
    streamer: str
    text: str
    startedAt: int
    endedAt: int
    language: str | None
    confidence: float | None
    modelVersion: str
    source: str
    channelLogin: str
    streamSessionId: str
    twitchStreamId: str | None
    videoTimestampMs: int
    transcriptSequence: int
    captureWorkerId: str

    def as_dict(self) -> dict:
        return self.__dict__


class FrameEventPublisher:
    def __init__(self, bootstrap_servers: str, topic: str):
        self.topic = topic
        self.producer = KafkaProducer(
            bootstrap_servers=bootstrap_servers,
            key_serializer=lambda value: value.encode("utf-8"),
            value_serializer=lambda value: json.dumps(value).encode("utf-8"),
            acks="all",
            linger_ms=10,
        )

    def publish(self, event: FrameEvent) -> int:
        start = time.monotonic()
        future = self.producer.send(self.topic, key=event.streamSessionId or event.streamer, value=event.as_dict())
        future.get(timeout=10)
        self.producer.flush(timeout=10)
        return int((time.monotonic() - start) * 1000)

    def close(self) -> None:
        self.producer.close(timeout=10)


class TranscriptEventPublisher:
    def __init__(self, bootstrap_servers: str, topic: str):
        self.topic = topic
        self.producer = KafkaProducer(
            bootstrap_servers=bootstrap_servers,
            key_serializer=lambda value: value.encode("utf-8"),
            value_serializer=lambda value: json.dumps(value).encode("utf-8"),
            acks="all",
            linger_ms=10,
        )

    def publish(self, event: TranscriptSegmentEvent) -> int:
        start = time.monotonic()
        future = self.producer.send(
            self.topic, key=event.streamSessionId or event.streamer, value=event.as_dict()
        )
        future.get(timeout=10)
        self.producer.flush(timeout=10)
        return int((time.monotonic() - start) * 1000)

    def close(self) -> None:
        self.producer.close(timeout=10)
