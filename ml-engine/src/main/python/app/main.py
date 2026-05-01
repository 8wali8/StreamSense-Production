import logging
import os

from fastapi import FastAPI, File, Form, HTTPException, UploadFile

from app.frame_store import FrameArtifactError, frame_read_required, load_frame_artifact
from app.models import (
    SentimentRequest,
    SentimentResponse,
    SponsorRequest,
    SponsorResponse,
    TranscriptionResponse,
)
from app.sponsor import compute_sponsor_detection
from app.sentiment import compute_sentiment
from app.transcription import TranscriptionError, transcriber

logging.basicConfig(
    level=logging.INFO, format="%(asctime)s %(levelname)s [ml-engine] %(message)s"
)
logger = logging.getLogger(__name__)


def force_failure_enabled() -> bool:
    return os.getenv("ML_ENGINE_FORCE_FAILURE", "false").strip().lower() in {
        "1",
        "true",
        "yes",
        "on",
    }

app = FastAPI(title="StreamSense ML Engine", version="0.1.0")


@app.get("/ml/health")
def health():
    logger.info("health check hit")
    return {"status": "ok", "service": "ml-engine"}


@app.post("/ml/sentiment", response_model=SentimentResponse)
def sentiment(request: SentimentRequest):
    if force_failure_enabled():
        logger.warning(
            "forced sentiment failure eventId=%s streamer=%s user=%s",
            request.eventId,
            request.streamer,
            request.user,
        )
        raise HTTPException(status_code=503, detail="forced ml-engine failure")

    label, score = compute_sentiment(request.message)

    logger.info(
        "sentiment request processed eventId=%s streamer=%s user=%s label=%s score=%.3f",
        request.eventId,
        request.streamer,
        request.user,
        label,
        score,
    )

    return SentimentResponse(label=label, score=score, modelVersion="stub-v1")


@app.post("/ml/sponsor", response_model=SponsorResponse)
def sponsor(request: SponsorRequest):
    if force_failure_enabled():
        logger.warning(
            "forced sponsor failure frameId=%s streamer=%s frameRef=%s",
            request.frameId,
            request.streamer,
            request.frameRef,
        )
        raise HTTPException(status_code=503, detail="forced ml-engine failure")

    try:
        frame_artifact = load_frame_artifact(request.frameRef)
    except FrameArtifactError as exc:
        logger.warning(
            "sponsor frame artifact read failed frameId=%s streamer=%s frameRef=%s error=%s",
            request.frameId,
            request.streamer,
            request.frameRef,
            exc,
        )
        if frame_read_required():
            raise HTTPException(status_code=503, detail="frame artifact read failed") from exc
        frame_artifact = None

    sponsor_name, confidence, x, y, width, height = compute_sponsor_detection(
        request.frameRef,
        request.streamer,
        request.frameSequence,
        frame_artifact.signature if frame_artifact else None,
    )
    model_version = "frame-aware-stub-v1" if frame_artifact else "stub-v1"

    logger.info(
        "sponsor request processed frameId=%s streamer=%s sponsor=%s confidence=%.3f modelVersion=%s",
        request.frameId,
        request.streamer,
        sponsor_name,
        confidence,
        model_version,
    )

    return SponsorResponse(
        sponsor=sponsor_name,
        confidence=confidence,
        modelVersion=model_version,
        x=x,
        y=y,
        width=width,
        height=height,
    )


@app.post("/ml/transcribe", response_model=TranscriptionResponse)
async def transcribe(
    file: UploadFile = File(...),
    streamer: str = Form(...),
    segmentId: str = Form(...),
    startedAt: int = Form(...),
    endedAt: int = Form(...),
    language: str | None = Form(default=None),
):
    if force_failure_enabled():
        logger.warning(
            "forced transcription failure segmentId=%s streamer=%s", segmentId, streamer
        )
        raise HTTPException(status_code=503, detail="forced ml-engine failure")

    try:
        audio = await file.read()
        result = transcriber.transcribe_bytes(
            audio, file.filename or f"{segmentId}.wav", language
        )
    except TranscriptionError as exc:
        logger.warning(
            "transcription failed segmentId=%s streamer=%s durationMs=%s error=%s",
            segmentId,
            streamer,
            endedAt - startedAt,
            exc,
        )
        raise HTTPException(status_code=503, detail="local transcription failed") from exc

    logger.info(
        "transcription processed segmentId=%s streamer=%s durationMs=%s textLength=%s modelVersion=%s",
        segmentId,
        streamer,
        endedAt - startedAt,
        len(result.text),
        result.model_version,
    )
    return TranscriptionResponse(
        text=result.text,
        language=result.language,
        confidence=result.confidence,
        modelVersion=result.model_version,
    )
