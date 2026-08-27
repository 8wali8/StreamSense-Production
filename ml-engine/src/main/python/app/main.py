import logging
import os

from fastapi import FastAPI, File, Form, HTTPException, UploadFile

from app.frame_store import FrameArtifactError, frame_read_required, load_frame_image
from app.models import (
    RegionProposalResponse,
    SegmentationRequest,
    SegmentationResponse,
    SentimentRequest,
    SentimentResponse,
    SponsorRelevanceRequest,
    SponsorRelevanceResponse,
    SponsorRequest,
    SponsorResponse,
    TranscriptionResponse,
)
from app.relevance import SponsorRelevanceInput, analyze_relevance
from app.segmentation import SegmentationConfig, propose_regions
from app.sentiment import analyze_sentiment
from app.sponsor import detect_sponsor
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


def sponsor_segmentation_enabled() -> bool:
    return os.getenv("STREAMSENSE_SPONSOR_SEGMENTATION_ENABLED", "false").strip().lower() in {
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

    result = analyze_sentiment(request.message)

    logger.info(
        "sentiment request processed eventId=%s streamer=%s user=%s label=%s score=%.3f modelVersion=%s",
        request.eventId,
        request.streamer,
        request.user,
        result.label,
        result.score,
        result.model_version,
    )

    return SentimentResponse(
        label=result.label,
        score=result.score,
        modelVersion=result.model_version,
    )


@app.post("/ml/relevance", response_model=SponsorRelevanceResponse)
def relevance(request: SponsorRelevanceRequest):
    if force_failure_enabled():
        logger.warning(
            "forced relevance failure eventId=%s streamer=%s sponsor=%s",
            request.eventId,
            request.streamer,
            request.sponsor,
        )
        raise HTTPException(status_code=503, detail="forced ml-engine failure")

    result = analyze_relevance(
        SponsorRelevanceInput(
            text=request.text,
            sponsor=request.sponsor,
            aliases=request.aliases,
            semantic_terms=request.semanticTerms,
            min_score=request.minScore,
        )
    )

    logger.info(
        "relevance request processed eventId=%s streamer=%s sponsor=%s relevant=%s score=%.3f reason=%s modelVersion=%s",
        request.eventId,
        request.streamer,
        request.sponsor,
        result.sponsor_relevant,
        result.relevance_score,
        result.relevance_reason,
        result.model_version,
    )

    return SponsorRelevanceResponse(
        sponsorRelevant=result.sponsor_relevant,
        matchedSponsor=result.matched_sponsor,
        matchedTerms=result.matched_terms,
        relevanceScore=result.relevance_score,
        relevanceReason=result.relevance_reason,
        modelVersion=result.model_version,
    )


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
        frame_image = load_frame_image(request.frameRef)
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
        frame_image = None

    proposals = propose_regions(frame_image.image) if frame_image and sponsor_segmentation_enabled() else []

    detection = detect_sponsor(
        request.frameRef,
        request.streamer,
        request.frameSequence,
        frame_image.signature if frame_image else None,
        proposals,
    )

    logger.info(
        "sponsor request processed frameId=%s streamer=%s sponsor=%s confidence=%.3f modelVersion=%s proposals=%s",
        request.frameId,
        request.streamer,
        detection.sponsor,
        detection.confidence,
        detection.model_version,
        len(proposals),
    )

    return SponsorResponse(
        sponsor=detection.sponsor,
        confidence=detection.confidence,
        modelVersion=detection.model_version,
        x=detection.x,
        y=detection.y,
        width=detection.width,
        height=detection.height,
    )


@app.post("/ml/segment", response_model=SegmentationResponse)
def segment(request: SegmentationRequest):
    try:
        frame_image = load_frame_image(request.frameRef)
    except FrameArtifactError as exc:
        logger.warning(
            "segmentation frame artifact read failed frameId=%s frameRef=%s error=%s",
            request.frameId,
            request.frameRef,
            exc,
        )
        raise HTTPException(status_code=503, detail="frame artifact read failed") from exc

    if frame_image is None:
        raise HTTPException(status_code=400, detail="segmentation requires readable frameRef")

    segmentation_config = SegmentationConfig.from_env()
    proposals = propose_regions(frame_image.image, segmentation_config)
    logger.info(
        "segmentation processed frameId=%s frameRef=%s proposals=%s",
        request.frameId,
        request.frameRef,
        len(proposals),
    )
    return SegmentationResponse(
        modelVersion=segmentation_config.model_version,
        frameWidth=frame_image.artifact.width,
        frameHeight=frame_image.artifact.height,
        proposals=[
            RegionProposalResponse(
                label=proposal.label,
                confidence=proposal.confidence,
                x=proposal.x,
                y=proposal.y,
                width=proposal.width,
                height=proposal.height,
                source=proposal.source,
                areaRatio=proposal.area_ratio,
            )
            for proposal in proposals
        ],
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
