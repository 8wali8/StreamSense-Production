import logging

from fastapi import FastAPI, HTTPException, Response
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


@app.get("/metrics")
def metrics() -> Response:
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)
