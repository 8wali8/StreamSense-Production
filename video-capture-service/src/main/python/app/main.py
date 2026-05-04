import logging
from pathlib import Path
from urllib.parse import unquote, urlparse

import boto3
from botocore.client import Config
from botocore.exceptions import ClientError
from fastapi import FastAPI, HTTPException, Query, Response
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest
from pydantic import BaseModel, Field

from app.capture_loop import CaptureManager
from app.config import CaptureConfig
from app.kafka_publisher import FrameEventPublisher, TranscriptEventPublisher
from app.status import CaptureState, CaptureStatusStore, ChannelStatus
from app.storage import create_storage
from app.transcription_client import TranscriptionClient

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s [video-capture-service] %(message)s")
logger = logging.getLogger(__name__)

config = CaptureConfig.from_env()
try:
    config.validate()
except ValueError:
    logger.exception("invalid video capture configuration")
    raise

status_store = CaptureStatusStore(enabled=config.enabled)
if config.enabled:
    for channel in config.channels:
        status_store.statuses[channel] = ChannelStatus(channel=channel, state=CaptureState.STARTING)
else:
    status_store.statuses["disabled"] = ChannelStatus(channel="disabled", state=CaptureState.DISABLED)

storage = create_storage(config.storage) if config.enabled else None
publisher = FrameEventPublisher(config.kafka_bootstrap_servers, config.video_frames_topic) if config.enabled else None
transcription_client = (
    TranscriptionClient(config.ml_engine_url, config.transcript_request_timeout_seconds, config.transcript_language)
    if config.enabled and config.transcript_enabled
    else None
)
transcript_publisher = (
    TranscriptEventPublisher(config.kafka_bootstrap_servers, config.transcript_segments_topic)
    if config.enabled and config.transcript_enabled
    else None
)
capture_manager = CaptureManager(config, status_store, storage, publisher, transcription_client, transcript_publisher)

app = FastAPI(title="StreamSense Video Capture Service", version="0.1.0")


class ChannelSwitchRequest(BaseModel):
    channels: list[str] = Field(min_length=1, max_length=10)


@app.on_event("startup")
def startup() -> None:
    capture_manager.start()


@app.on_event("shutdown")
def shutdown() -> None:
    capture_manager.stop()


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "service": "video-capture-service"}


@app.get("/api/video/capture/status")
def capture_status() -> dict:
    return status_store.snapshot()


@app.post("/api/video/capture/channels")
def switch_capture_channels(request: ChannelSwitchRequest) -> dict:
    try:
        return capture_manager.switch_channels(request.channels)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except RuntimeError as exc:
        raise HTTPException(status_code=409, detail=str(exc)) from exc


@app.get("/api/video/capture/frame")
def capture_frame(frameRef: str = Query(..., min_length=1, max_length=2048)) -> Response:
    data, content_type = _read_frame_artifact(frameRef)
    return Response(data, media_type=content_type, headers={"Cache-Control": "no-store"})


@app.get("/metrics")
def metrics() -> Response:
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)


def _read_frame_artifact(frame_ref: str) -> tuple[bytes, str]:
    parsed = urlparse(frame_ref)

    if parsed.scheme == "s3":
        bucket = parsed.netloc
        key = unquote(parsed.path.lstrip("/"))
        if bucket != config.storage.bucket or not key:
            raise HTTPException(status_code=403, detail="frameRef bucket is not allowed")

        client = boto3.client(
            "s3",
            endpoint_url=config.storage.endpoint,
            region_name=config.storage.region,
            aws_access_key_id=config.storage.access_key,
            aws_secret_access_key=config.storage.secret_key,
            config=Config(signature_version="s3v4"),
        )
        try:
            response = client.get_object(Bucket=bucket, Key=key)
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
    lowered = name.lower()
    if lowered.endswith(".png"):
        return "image/png"
    return "image/jpeg"
