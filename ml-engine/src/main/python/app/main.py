"""StreamSense ML Engine.

Built by :func:`create_app`. Backends live in a :class:`BackendRegistry` created in the lifespan,
settings come from :mod:`app.settings`, and every route receives both through dependencies, so
tests swap fakes with ``app.dependency_overrides`` instead of patching module globals.
"""

from __future__ import annotations

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Annotated

from fastapi import APIRouter, Depends, FastAPI, File, Form, HTTPException, Request, UploadFile
from fastapi.responses import JSONResponse

from app import metrics
from app.frame_store import FrameArtifactError, readable_frame_ref
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
from app.registry import BackendRegistry, ModelNotReadyError
from app.relevance import SponsorRelevanceInput
from app.settings import Settings, get_settings
from app.sponsor import SponsorDetectionContext
from app.transcription import TranscriptionError

logger = logging.getLogger(__name__)

FORCED_FAILURE_DETAIL = "forced ml-engine failure"
FRAME_READ_FAILED_DETAIL = "frame artifact read failed"
TRANSCRIPTION_FAILED_DETAIL = "local transcription failed"


# ---------------------------------------------------------------------- dependencies
def get_registry(request: Request) -> BackendRegistry:
    registry: BackendRegistry | None = getattr(request.app.state, "registry", None)
    if registry is None or not registry.ready:
        raise ModelNotReadyError("registry")
    return registry


def settings_dependency(request: Request) -> Settings:
    return request.app.state.settings


SettingsDep = Annotated[Settings, Depends(settings_dependency)]
RegistryDep = Annotated[BackendRegistry, Depends(get_registry)]


def reject_when_forced(request: Request, settings: SettingsDep) -> None:
    """Router-level guard: with ML_ENGINE_FORCE_FAILURE on, every inference route returns 503."""
    if settings.ml_engine_force_failure:
        metrics.forced_failures.labels(endpoint=request.url.path).inc()
        logger.warning("forced failure path=%s", request.url.path)
        raise HTTPException(status_code=503, detail=FORCED_FAILURE_DETAIL)


# ---------------------------------------------------------------------- app factory
def _configure_logging() -> None:
    """Attach a root handler when nothing has (uvicorn only configures its own loggers).

    Without this the lifecycle and inference ``logger.info`` lines are dropped in the container,
    where the process is started by ``uvicorn app.main:app``.
    """
    root = logging.getLogger()
    if not root.handlers:
        logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s [ml-engine] %(message)s")
    elif root.level > logging.INFO or root.level == logging.NOTSET:
        root.setLevel(logging.INFO)


def create_app(settings: Settings | None = None) -> FastAPI:
    _configure_logging()
    resolved_settings = settings or get_settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        registry = BackendRegistry(app.state.settings)
        registry.start()
        app.state.registry = registry
        try:
            yield
        finally:
            registry.stop()

    app = FastAPI(title="StreamSense ML Engine", version=resolved_settings.service_version, lifespan=lifespan)
    app.state.settings = resolved_settings
    app.state.registry = None

    app.include_router(_operations_router())
    app.include_router(_inference_router())
    _register_error_handlers(app)
    metrics.install_http_metrics(app)
    return app


# ---------------------------------------------------------------------- operational routes
def _operations_router() -> APIRouter:
    router = APIRouter(prefix="/ml", tags=["operations"])

    # The operational handlers are coroutines on purpose: they do no blocking work, and a
    # plain `def` would queue behind slow inference calls in the thread pool, so a busy but
    # healthy pod could miss its liveness probe.
    @router.get("/health")
    async def health(request: Request, settings: SettingsDep):
        # Kept for existing callers: healthy means alive; readiness is reported separately.
        registry: BackendRegistry | None = getattr(request.app.state, "registry", None)
        return {"status": "ok", "service": settings.service_name, "ready": bool(registry and registry.ready)}

    @router.get("/live")
    async def live():
        return {"status": "alive"}

    @router.get("/ready")
    async def ready(request: Request):
        registry: BackendRegistry | None = getattr(request.app.state, "registry", None)
        if registry is None or not registry.ready:
            return JSONResponse(status_code=503, content={"status": "starting"})
        return {"status": "ready"}

    @router.get("/info")
    async def info(request: Request, settings: SettingsDep):
        registry: BackendRegistry | None = getattr(request.app.state, "registry", None)
        return {
            "service": settings.service_name,
            "version": settings.service_version,
            "gitSha": settings.git_sha,
            "ready": bool(registry and registry.ready),
            "forceFailure": settings.ml_engine_force_failure,
            "backends": [
                {"name": b.name, "backend": b.backend, "model": b.model, "loaded": b.loaded}
                for b in (registry.info() if registry else [])
            ],
        }

    return router


# ---------------------------------------------------------------------- inference routes
def _inference_router() -> APIRouter:
    router = APIRouter(prefix="/ml", tags=["inference"], dependencies=[Depends(reject_when_forced)])

    @router.post("/sentiment", response_model=SentimentResponse)
    def sentiment(request: SentimentRequest, registry: RegistryDep):
        with metrics.timed("sentiment"):
            result = registry.sentiment.analyze(request.message)
        logger.info(
            "sentiment request processed eventId=%s streamer=%s user=%s label=%s score=%.3f modelVersion=%s",
            request.eventId,
            request.streamer,
            request.user,
            result.label,
            result.score,
            result.model_version,
        )
        return SentimentResponse(label=result.label, score=result.score, modelVersion=result.model_version)

    @router.post("/relevance", response_model=SponsorRelevanceResponse)
    def relevance(request: SponsorRelevanceRequest, registry: RegistryDep):
        with metrics.timed("relevance"):
            result = registry.relevance.analyze(
                SponsorRelevanceInput(
                    text=request.text,
                    sponsor=request.sponsor,
                    aliases=request.aliases,
                    semantic_terms=request.semanticTerms,
                    min_score=request.minScore,
                )
            )
        logger.info(
            "relevance request processed eventId=%s streamer=%s sponsor=%s relevant=%s score=%.3f reason=%s"
            " modelVersion=%s",
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

    @router.post("/sponsor", response_model=SponsorResponse)
    def sponsor(request: SponsorRequest, registry: RegistryDep, settings: SettingsDep):
        try:
            frame_image = registry.frame_store.load_frame_image(request.frameRef)
        except FrameArtifactError as exc:
            logger.warning(
                "sponsor frame artifact read failed frameId=%s streamer=%s frameRef=%s error=%s",
                request.frameId,
                request.streamer,
                request.frameRef,
                exc,
            )
            if settings.sponsor.require_frame_read:
                raise
            frame_image = None

        proposals = []
        if frame_image and settings.sponsor.segmentation_enabled:
            with metrics.timed("segmentation"):
                proposals = registry.segmenter.propose(frame_image.image)[: max(0, settings.segmentation.max_proposals)]

        with metrics.timed("sponsor"):
            detection = registry.sponsor_detector.detect(
                SponsorDetectionContext(
                    frame_ref=request.frameRef,
                    streamer=request.streamer,
                    frame_sequence=request.frameSequence,
                    frame_signature=frame_image.signature if frame_image else None,
                    proposals=proposals,
                )
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

    @router.post("/segment", response_model=SegmentationResponse)
    def segment(request: SegmentationRequest, registry: RegistryDep, settings: SettingsDep):
        # A reference this service cannot read is a client error (400), not an outage (503):
        # only a real read failure of a readable reference goes through FrameArtifactError.
        if not readable_frame_ref(request.frameRef):
            raise HTTPException(status_code=400, detail="segmentation requires readable frameRef")
        frame_image = registry.frame_store.load_frame_image(request.frameRef, required=True)
        if frame_image is None:
            raise HTTPException(status_code=400, detail="segmentation requires readable frameRef")

        with metrics.timed("segmentation"):
            proposals = registry.segmenter.propose(frame_image.image)[: max(0, settings.segmentation.max_proposals)]
        logger.info(
            "segmentation processed frameId=%s frameRef=%s proposals=%s",
            request.frameId,
            request.frameRef,
            len(proposals),
        )
        return SegmentationResponse(
            modelVersion=settings.segmentation.model_version,
            frameWidth=frame_image.artifact.width,
            frameHeight=frame_image.artifact.height,
            proposals=[
                RegionProposalResponse(
                    label=p.label,
                    confidence=p.confidence,
                    x=p.x,
                    y=p.y,
                    width=p.width,
                    height=p.height,
                    source=p.source,
                    areaRatio=p.area_ratio,
                )
                for p in proposals
            ],
        )

    # Deliberately a plain `def`: Whisper decoding blocks for seconds, so FastAPI runs this in
    # its worker thread pool and the event loop stays free for health checks and other requests.
    @router.post("/transcribe", response_model=TranscriptionResponse)
    def transcribe(
        registry: RegistryDep,
        file: UploadFile = File(...),
        streamer: str = Form(...),
        segmentId: str = Form(...),
        startedAt: int = Form(...),
        endedAt: int = Form(...),
        language: str | None = Form(default=None),
    ):
        audio = file.file.read()
        try:
            with metrics.timed("transcription"):
                result = registry.transcriber.transcribe_bytes(audio, file.filename or f"{segmentId}.wav", language)
        except TranscriptionError as exc:
            logger.warning(
                "transcription failed segmentId=%s streamer=%s durationMs=%s error=%s",
                segmentId,
                streamer,
                endedAt - startedAt,
                exc,
            )
            raise
        logger.info(
            "transcription processed segmentId=%s streamer=%s durationMs=%s textLength=%s modelVersion=%s",
            segmentId,
            streamer,
            endedAt - startedAt,
            len(result.text),
            result.model_version,
        )
        return TranscriptionResponse(
            text=result.text, language=result.language, confidence=result.confidence, modelVersion=result.model_version
        )

    return router


# ---------------------------------------------------------------------- error mapping
def _register_error_handlers(app: FastAPI) -> None:
    @app.exception_handler(ModelNotReadyError)
    async def model_not_ready(_: Request, exc: ModelNotReadyError) -> JSONResponse:
        return JSONResponse(status_code=503, content={"detail": str(exc)})

    @app.exception_handler(FrameArtifactError)
    async def frame_artifact_error(_: Request, exc: FrameArtifactError) -> JSONResponse:
        logger.warning("frame artifact error: %s", exc)
        return JSONResponse(status_code=503, content={"detail": FRAME_READ_FAILED_DETAIL})

    @app.exception_handler(TranscriptionError)
    async def transcription_error(_: Request, exc: TranscriptionError) -> JSONResponse:
        return JSONResponse(status_code=503, content={"detail": TRANSCRIPTION_FAILED_DETAIL})


# Module-level app for `uvicorn app.main:app`; construction does no I/O, the lifespan does.
app = create_app()
