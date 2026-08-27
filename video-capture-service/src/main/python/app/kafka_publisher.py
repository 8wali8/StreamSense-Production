"""Kafka publishing for frame and transcript events.

One long-lived, idempotent producer per publisher. ``publish`` waits for the broker
acknowledgement of that record (so the capture loop's counters stay truthful) but no longer
flushes the whole producer on every message; ``flush`` happens once, on ``close``.
"""

from __future__ import annotations

import json
import logging
import time
from collections.abc import Callable
from dataclasses import dataclass
from typing import Any, Protocol

logger = logging.getLogger(__name__)

DEFAULT_ACK_TIMEOUT_SECONDS = 10


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


class KeyedEvent(Protocol):
    streamer: str
    streamSessionId: str

    def as_dict(self) -> dict: ...


def producer_config(bootstrap_servers: str) -> dict[str, Any]:
    """kafka-python producer settings: idempotent, acknowledged by every in-sync replica, bounded."""
    return {
        "bootstrap_servers": bootstrap_servers,
        "key_serializer": lambda value: value.encode("utf-8"),
        "value_serializer": lambda value: json.dumps(value).encode("utf-8"),
        "acks": "all",
        "enable_idempotence": True,
        "max_in_flight_requests_per_connection": 5,
        "retries": 5,
        "linger_ms": 10,
        "request_timeout_ms": 10_000,
    }


class EventPublisher:
    """Publishes keyed JSON events to one topic. The producer is created on first use."""

    def __init__(
        self,
        bootstrap_servers: str,
        topic: str,
        *,
        producer_factory: Callable[[dict[str, Any]], Any] | None = None,
        ack_timeout_seconds: float = DEFAULT_ACK_TIMEOUT_SECONDS,
    ) -> None:
        self.bootstrap_servers = bootstrap_servers
        self.topic = topic
        self.ack_timeout_seconds = ack_timeout_seconds
        self._producer_factory = producer_factory or _default_producer_factory
        self._producer: Any | None = None

    def connect(self) -> None:
        """Create the producer now (start-up) instead of on the first publish."""
        self._get_producer()

    def publish(self, event: KeyedEvent) -> int:
        """Send one event and wait for its acknowledgement. Returns the round-trip in milliseconds."""
        start = time.monotonic()
        future = self._get_producer().send(self.topic, key=event.streamSessionId or event.streamer, value=event.as_dict())
        future.get(timeout=self.ack_timeout_seconds)
        return int((time.monotonic() - start) * 1000)

    def close(self) -> None:
        producer, self._producer = self._producer, None
        if producer is not None:
            try:
                producer.flush(timeout=self.ack_timeout_seconds)
            finally:
                producer.close(timeout=self.ack_timeout_seconds)

    def is_connected(self) -> bool:
        return self._producer is not None

    def _get_producer(self) -> Any:
        if self._producer is None:
            self._producer = self._producer_factory(producer_config(self.bootstrap_servers))
            logger.info("kafka producer ready topic=%s bootstrap=%s", self.topic, self.bootstrap_servers)
        return self._producer


def _default_producer_factory(config: dict[str, Any]) -> Any:
    from kafka import KafkaProducer

    return KafkaProducer(**config)


# Names kept for the capture loop and existing callers; both are the same publisher.
FrameEventPublisher = EventPublisher
TranscriptEventPublisher = EventPublisher
