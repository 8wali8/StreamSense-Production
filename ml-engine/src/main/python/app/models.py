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


class SponsorRequest(BaseModel):
    frameId: str
    streamer: str
    frameRef: str
    frameSequence: int
    capturedAt: int


class SponsorResponse(BaseModel):
    sponsor: str
    confidence: float
    modelVersion: str
    x: float
    y: float
    width: float
    height: float
