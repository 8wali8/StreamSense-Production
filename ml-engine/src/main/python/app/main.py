import logging

from fastapi import FastAPI

from app.models import SentimentRequest, SentimentResponse
from app.sentiment import compute_sentiment

logging.basicConfig(
    level=logging.INFO, format="%(asctime)s %(levelname)s [ml-engine] %(message)s"
)
logger = logging.getLogger(__name__)

app = FastAPI(title="StreamSense ML Engine", version="0.1.0")


@app.get("/ml/health")
def health():
    logger.info("health check hit")
    return {"status": "ok", "service": "ml-engine"}


@app.post("/ml/sentiment", response_model=SentimentResponse)
def sentiment(request: SentimentRequest):
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
