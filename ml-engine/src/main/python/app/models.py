from pydantic import BaseModel


class SentimentRequest(BaseModel):
    eventId: str
    streamer: str
    user: str
    message: str
    timestamp: int


class SentimentResponse(BaseModel):
    label: str
    score: float
    modelVersion: str
