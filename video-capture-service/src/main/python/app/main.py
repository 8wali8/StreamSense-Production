"""StreamSense Video Capture Service.

Built by :func:`create_app`. Configuration is read and validated, and the storage client, Kafka
publishers, and capture threads are created, inside the lifespan, never at import time. The
module-level ``app`` exists for ``uvicorn app.main:app``; importing this module has no side
effects beyond defining it.
"""

from __future__ import annotations

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any
from urllib.parse import unquote, urlparse

from botocore.exceptions import ClientError
from fastapi import FastAPI, HTTPException, Query, Request, Response
from fastapi.responses import JSONResponse
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest
from pydantic import BaseModel, Field

from app.capture_loop import CaptureManager
from app.config import CaptureConfig
from app.kafka_publisher import EventPublisher
from app.status import CaptureState, CaptureStatusStore, ChannelStatus
from app.storage import FrameStorage, S3FrameStorage, create_storage
from app.transcription_client import TranscriptionClient

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s [video-capture-service] %(message)s")
logger = logging.getLogger(__name__)


class ChannelSwitchRequest(BaseModel):
    channels: list[str] = Field(min_length=1, max_length=10)


class CaptureRuntime:
    """Everything with a lifetime: built in ``start``, torn down in ``stop``."""

    def __init__(self, config: CaptureConfig) -> None:
        self.config = config
        self.status_store = CaptureStatusStore(enabled=config.enabled)
        self.storage: FrameStorage | None = None
        self.publisher: EventPublisher | None = None
        self.transcript_publisher: EventPublisher | None = None
        self.transcription_client: TranscriptionClient | None = None
        self.manager: CaptureManager | None = None
        self.started = False

    def start(self) -> None:
        config = self.config
        if config.enabled:
            for channel in config.channels:
                self.status_store.statuses[channel] = ChannelStatus(channel=channel, state=CaptureState.STARTING)
            self.storage = create_storage(config.storage)
            self.publisher = EventPublisher(config.kafka_bootstrap_servers, config.video_frames_topic)
            self.publisher.connect()
            if config.transcript_enabled:
                self.transcription_client = TranscriptionClient(
                    config.ml_engine_url, config.transcript_request_timeout_seconds, config.transcript_language
                )
                self.transcript_publisher = EventPublisher(config.kafka_bootstrap_servers, config.transcript_segments_topic)
                self.transcript_publisher.connect()
        else:
            self.status_store.statuses["disabled"] = ChannelStatus(channel="disabled", state=CaptureState.DISABLED)

        self.manager = CaptureManager(
            config, self.status_store, self.storage, self.publisher, self.transcription_client, self.transcript_publisher
        )
        self.manager.start()
        self.started = True

    def stop(self) -> None:
        self.started = False
        if self.manager is not None:
            self.manager.stop()

    def readiness(self) -> tuple[bool, dict[str, Any]]:
        """Ready when started and, if capture is enabled, every channel worker is still alive."""
        if not self.started or self.manager is None:
            return False, {"status": "starting"}
        if not self.config.enabled:
            return True, {"status": "ready", "capture": "disabled"}
        alive = self.manager.workers_alive()
        expected = len(self.config.channels)
        detail = {"status": "ready" if alive == expected else "degraded", "workersAlive": alive, "workersExpected": expected}
        return alive == expected, detail


def get_runtime(request: Request) -> CaptureRuntime:
    runtime: CaptureRuntime | None = getattr(request.app.state, "runtime", None)
    if runtime is None or not runtime.started:
        raise HTTPException(status_code=503, detail="video-capture-service is starting")
    return runtime


def create_app(config: CaptureConfig | None = None) -> FastAPI:
    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        resolved = config or CaptureConfig.from_env()
        resolved.validate()
        runtime = CaptureRuntime(resolved)
        runtime.start()
        app.state.runtime = runtime
        try:
            yield
        finally:
            runtime.stop()

    app = FastAPI(title="StreamSense Video Capture Service", version="0.1.0", lifespan=lifespan)
    app.state.runtime = None

    @app.get("/health")
    def health(request: Request) -> dict:
        # Liveness for existing callers: the process is up. Readiness lives at /ready.
        runtime: CaptureRuntime | None = getattr(request.app.state, "runtime", None)
        return {"status": "ok", "service": "video-capture-service", "ready": bool(runtime and runtime.readiness()[0])}

    @app.get("/live")
    def live() -> dict:
        return {"status": "alive"}

    @app.get("/ready")
    def ready(request: Request) -> Response:
        runtime: CaptureRuntime | None = getattr(request.app.state, "runtime", None)
        if runtime is None:
            return JSONResponse(status_code=503, content={"status": "starting"})
        ok, detail = runtime.readiness()
        return JSONResponse(status_code=200 if ok else 503, content=detail)

    @app.get("/api/video/capture/status")
    def capture_status(request: Request) -> dict:
        return get_runtime(request).status_store.snapshot()

    @app.post("/api/video/capture/channels")
    def switch_capture_channels(request: Request, body: ChannelSwitchRequest) -> dict:
        runtime = get_runtime(request)
        try:
            return runtime.manager.switch_channels(body.channels)
        except ValueError as exc:
            raise HTTPException(status_code=400, detail=str(exc)) from exc
        except RuntimeError as exc:
            raise HTTPException(status_code=409, detail=str(exc)) from exc

    @app.get("/api/video/capture/frame")
    def capture_frame(request: Request, frameRef: str = Query(..., min_length=1, max_length=2048)) -> Response:
        runtime = get_runtime(request)
        data, content_type = _read_frame_artifact(runtime, frameRef)
        return Response(data, media_type=content_type, headers={"Cache-Control": "no-store"})

    @app.get("/metrics")
    def metrics() -> Response:
        return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)

    return app


def _read_frame_artifact(runtime: CaptureRuntime, frame_ref: str) -> tuple[bytes, str]:
    config = runtime.config
    parsed = urlparse(frame_ref)

    if parsed.scheme == "s3":
        bucket = parsed.netloc
        key = unquote(parsed.path.lstrip("/"))
        if bucket != config.storage.bucket or not key:
            raise HTTPException(status_code=403, detail="frameRef bucket is not allowed")
        if not isinstance(runtime.storage, S3FrameStorage):
            raise HTTPException(status_code=409, detail="s3 frame storage is not configured")
        try:
            response = runtime.storage.client.get_object(Bucket=bucket, Key=key)
        except ClientError as exc:
            status = exc.response.get("ResponseMetadata", {}).get("HTTPStatusCode", 502)
            if status == 404:
                raise HTTPException(status_code=404, detail="frame artifact not found") from exc
            raise HTTPException(status_code=502, detail="failed to read frame artifact") from exc

        content_type = response.get("ContentType") or _content_type_from_name(key)
        return response["Body"].read(), content_type

    if parsed.scheme == "file":
        root = Path(config.storage.filesystem_root).resolve()
        path = Path(unquote(parsed.path)).resolve()
        if root not in path.parents and path != root:
            raise HTTPException(status_code=403, detail="frameRef path is not allowed")
        if not path.is_file():
            raise HTTPException(status_code=404, detail="frame artifact not found")
        return path.read_bytes(), _content_type_from_name(path.name)

    raise HTTPException(status_code=400, detail="unsupported frameRef scheme")


def _content_type_from_name(name: str) -> str:
    return "image/png" if name.lower().endswith(".png") else "image/jpeg"


app = create_app()
