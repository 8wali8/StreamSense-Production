import logging
import os

from fastapi import FastAPI, HTTPException

from app.models import SentimentRequest, SentimentResponse
from app.sentiment import compute_sentiment

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
